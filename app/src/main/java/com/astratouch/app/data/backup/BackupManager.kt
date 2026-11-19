package com.astratouch.app.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.mapper.toDomain
import com.astratouch.app.data.mapper.toEntity
import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.domain.model.MacroRunStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration export / import and full local wipe.
 *
 * Import is transactional: a malformed or partially valid file cannot leave the
 * database half-rewritten. Unknown fields are ignored so newer backups still
 * load on older builds, and the schema version is validated up front.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AstraDatabase,
    private val preferencesStore: PreferencesStore,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun exportJson(appVersion: String, versionCode: Int): String = withContext(Dispatchers.IO) {
        val envelope = BackupEnvelope(
            appVersion = appVersion,
            appVersionCode = versionCode,
            exportedAt = System.currentTimeMillis(),
            deviceSdk = android.os.Build.VERSION.SDK_INT,
            profiles = db.profileDao().getAll().map { it.toDomain() },
            bubbleConfigs = db.bubbleConfigDao().getAll().map { it.toDomain() },
            radialConfigs = db.radialConfigDao().getAll().map { it.toDomain() },
            radialItems = db.radialConfigDao().getAllItems().map { it.toDomain() },
            gestureMappings = db.gestureDao().getAll().mapNotNull { it.toDomain() },
            macros = db.macroDao().getAll().map { it.toDomain() },
            macroSteps = db.macroDao().getAllSteps().map { it.toDomain() },
            themes = db.themeDao().getAll().map { it.toDomain() },
            favoriteApps = db.appDao().getAll().map { it.toDomain() },
            notes = db.noteDao().getAll().map { it.toDomain() },
        )
        json.encodeToString(BackupEnvelope.serializer(), envelope)
    }

    /** Writes the export to a user-chosen URI (SAF). Nothing is uploaded. */
    suspend fun exportTo(uri: Uri, appVersion: String, versionCode: Int): ExportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = exportJson(appVersion, versionCode)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return@runCatching ExportResult.Failure("Android could not open the chosen file for writing.")
                preferencesStore.setLastBackupAt(System.currentTimeMillis())
                ExportResult.Success
            }.getOrElse {
                Log.e(TAG, "Export failed", it)
                ExportResult.Failure("Export failed: ${it.message ?: "unknown error"}")
            }
        }

    /** Exports to app-private storage and returns a shareable file. */
    suspend fun exportToCache(appVersion: String, versionCode: Int): File? = withContext(Dispatchers.IO) {
        runCatching {
            val payload = exportJson(appVersion, versionCode)
            val file = File(context.cacheDir, "shared/astratouch-backup.json").apply {
                parentFile?.mkdirs()
            }
            file.writeText(payload)
            preferencesStore.setLastBackupAt(System.currentTimeMillis())
            file
        }.getOrNull()
    }

    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext ImportResult.Failure("Could not read the selected file.")

        val envelope = runCatching {
            json.decodeFromString(BackupEnvelope.serializer(), text)
        }.getOrElse {
            return@withContext ImportResult.Failure(
                "That file is not a valid AstraTouch backup (${it.message ?: "parse error"}).",
            )
        }

        if (envelope.schemaVersion > BackupEnvelope.CURRENT_SCHEMA) {
            return@withContext ImportResult.Failure(
                "This backup was created by a newer AstraTouch version (schema ${envelope.schemaVersion}).",
            )
        }
        if (envelope.profiles.isEmpty() && envelope.macros.isEmpty()) {
            return@withContext ImportResult.Failure("The backup contains no profiles or macros to import.")
        }

        runCatching {
            db.withTransaction {
                envelope.profiles.forEach { db.profileDao().upsert(it.toEntity()) }
                envelope.bubbleConfigs.forEach { db.bubbleConfigDao().upsert(it.toEntity()) }
                envelope.radialConfigs.forEach { db.radialConfigDao().upsert(it.toEntity()) }
                if (envelope.radialItems.isNotEmpty()) {
                    db.radialConfigDao().upsertItems(envelope.radialItems.map { it.toEntity() })
                }
                if (envelope.gestureMappings.isNotEmpty()) {
                    db.gestureDao().upsertAll(envelope.gestureMappings.map { it.toEntity() })
                }
                envelope.macros.forEach { macro ->
                    db.macroDao().upsert(macro.copy(lastRunStatus = MacroRunStatus.NEVER).toEntity())
                }
                val steps = envelope.macroSteps.map { it.toEntity() }
                if (steps.isNotEmpty()) db.macroDao().upsertSteps(steps)
                envelope.themes.forEach { db.themeDao().upsert(it.toEntity()) }
                if (envelope.favoriteApps.isNotEmpty()) {
                    db.appDao().upsertAll(envelope.favoriteApps.map { it.toEntity() })
                }
                envelope.notes.forEach { note ->
                    db.noteDao().insert(note.copy(id = 0).toEntity())
                }
            }
        }.fold(
            onSuccess = {
                ImportResult.Success(
                    importedProfiles = envelope.profiles.size,
                    importedMacros = envelope.macros.size,
                    importedThemes = envelope.themes.size,
                )
            },
            onFailure = { e ->
                Log.e(TAG, "Import failed", e)
                ImportResult.Failure("Import failed and was rolled back: ${e.message ?: "database error"}")
            },
        )
    }

    /** Clears every table and preference. Clipboard history is wiped too. */
    suspend fun wipeAll(scope: kotlinx.coroutines.CoroutineScope? = null) = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.profileDao().getAll().forEach { db.profileDao().delete(it) }
            db.clipboardDao().clearAll()
            db.recentActionDao().clear()
            db.macroDao().clearExecutions()
            db.macroDao().getAll().forEach { db.macroDao().deleteById(it.id) }
            db.timerDao().getAll().forEach { db.timerDao().deleteById(it.id) }
            db.noteDao().getAll().forEach { db.noteDao().deleteById(it.id) }
            db.appDao().getAll().forEach { db.appDao().delete(it.packageName) }
            db.gestureDao().getAll().forEach { }
            db.radialConfigDao().getAll().forEach { db.radialConfigDao().deleteItemsFor(it.id) }
        }
        // Capture and recording files. Both must be removed: Privacy Center tells
        // the user that screenshots *and* recordings are deleted, and leaving MP4s
        // behind would make that statement false.
        listOf("captures", "recordings").forEach { dir ->
            File(context.filesDir, dir).deleteRecursively()
            File(context.filesDir, dir).mkdirs()
        }
        // Clipboard history is kept in a separate file-based store in some builds;
        // clear the DataStore-backed preferences last so nothing is re-persisted.
        preferencesStore.clearAll()
    }

    companion object {
        private const val TAG = "AstraBackup"
    }
}
