package com.astratouch.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.astratouch.app.domain.model.AppPreferences
import com.astratouch.app.domain.model.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed preferences for behaviour flags that are not part of a
 * themed profile. Exposed as a [Flow] of an immutable [AppPreferences].
 */
@Singleton
class PreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val overlayEnabled = booleanPreferencesKey("overlay_enabled")
        val activeProfileId = stringPreferencesKey("active_profile_id")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val clipboardCapture = booleanPreferencesKey("clipboard_capture_enabled")
        val clipboardRetentionDays = intPreferencesKey("clipboard_retention_days")
        val showRecents = booleanPreferencesKey("show_recents_in_radial")
        val experimentalGestures = booleanPreferencesKey("experimental_gestures")
        val experimentalOcr = booleanPreferencesKey("experimental_ocr")
        val experimentalEdgePanel = booleanPreferencesKey("experimental_edge_panel")
        val crashRecoveryNotice = booleanPreferencesKey("crash_recovery_notice")
        val lastBackupAt = longPreferencesKey("last_backup_at")
    }

    val preferences: Flow<AppPreferences> = dataStore.data.map { prefs ->
        AppPreferences(
            onboardingComplete = prefs[Keys.onboardingComplete] ?: false,
            overlayEnabled = prefs[Keys.overlayEnabled] ?: false,
            activeProfileId = prefs[Keys.activeProfileId] ?: ProfileType.DEFAULT.id,
            hapticsEnabled = prefs[Keys.hapticsEnabled] ?: true,
            reduceMotion = prefs[Keys.reduceMotion] ?: false,
            clipboardCaptureEnabled = prefs[Keys.clipboardCapture] ?: false,
            clipboardRetentionDays = prefs[Keys.clipboardRetentionDays] ?: 7,
            showRecentsInRadial = prefs[Keys.showRecents] ?: true,
            experimentalGestures = prefs[Keys.experimentalGestures] ?: false,
            experimentalOcr = prefs[Keys.experimentalOcr] ?: false,
            experimentalEdgePanel = prefs[Keys.experimentalEdgePanel] ?: true,
            crashRecoveryNotice = prefs[Keys.crashRecoveryNotice] ?: true,
            lastBackupAt = prefs[Keys.lastBackupAt] ?: 0L,
        )
    }

    suspend fun setOnboardingComplete(value: Boolean) = update { it[Keys.onboardingComplete] = value }

    suspend fun setOverlayEnabled(value: Boolean) = update { it[Keys.overlayEnabled] = value }

    suspend fun setActiveProfileId(value: String) = update { it[Keys.activeProfileId] = value }

    suspend fun setHapticsEnabled(value: Boolean) = update { it[Keys.hapticsEnabled] = value }

    suspend fun setReduceMotion(value: Boolean) = update { it[Keys.reduceMotion] = value }

    suspend fun setClipboardCaptureEnabled(value: Boolean) = update { it[Keys.clipboardCapture] = value }

    suspend fun setClipboardRetentionDays(value: Int) =
        update { it[Keys.clipboardRetentionDays] = value.coerceIn(1, 90) }

    suspend fun setShowRecentsInRadial(value: Boolean) = update { it[Keys.showRecents] = value }

    suspend fun setExperimentalGestures(value: Boolean) = update { it[Keys.experimentalGestures] = value }

    suspend fun setExperimentalOcr(value: Boolean) = update { it[Keys.experimentalOcr] = value }

    suspend fun setExperimentalEdgePanel(value: Boolean) = update { it[Keys.experimentalEdgePanel] = value }

    suspend fun setLastBackupAt(value: Long) = update { it[Keys.lastBackupAt] = value }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }
}
