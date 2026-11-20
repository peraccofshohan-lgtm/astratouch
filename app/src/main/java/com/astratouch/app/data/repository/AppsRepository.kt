package com.astratouch.app.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.mapper.toDomain
import com.astratouch.app.data.mapper.toEntity
import com.astratouch.app.domain.model.FavoriteApp
import com.astratouch.app.domain.model.LauncherApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers launchable apps through PackageManager and stores favourites.
 * No package data ever leaves the device.
 */
@Singleton
class AppsRepository @Inject constructor(
    private val context: Context,
    private val db: AstraDatabase,
) {
    private val pm: PackageManager get() = context.packageManager

    fun observeFavorites(): Flow<List<FavoriteApp>> =
        db.appDao().observeFavorites().map { l -> l.map { it.toDomain() } }

    fun observeAllTracked(): Flow<List<FavoriteApp>> =
        db.appDao().observeAll().map { l -> l.map { it.toDomain() } }

    /** All launcher apps, merged with the locally stored favourite metadata. */
    suspend fun discoverApps(): List<LauncherApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        } catch (e: Exception) {
            emptyList()
        }
        val stored = db.appDao().getAll().associateBy { it.packageName }
        resolved
            .asSequence()
            .filter { it.activityInfo != null }
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val record = stored[pkg]
                LauncherApp(
                    packageName = pkg,
                    label = (info.loadLabel(pm)?.toString() ?: pkg).trim(),
                    isFavorite = record?.isFavorite ?: false,
                    group = record?.groupName ?: "",
                    lastUsedAt = record?.lastUsedAt ?: 0L,
                )
            }
            .sortedWith(compareByDescending<LauncherApp> { it.isFavorite }.thenBy { it.label.lowercase() })
            .toList()
    }

    suspend fun launch(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return@withContext false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return@withContext try {
            context.startActivity(intent)
            val existing = db.appDao().get(packageName)
            if (existing == null) {
                db.appDao().upsert(
                    com.astratouch.app.data.local.entity.FavoriteAppEntity(
                        packageName = packageName,
                        label = appLabel(packageName),
                        isFavorite = false,
                        category = "uncategorized",
                        groupName = "",
                        lastUsedAt = System.currentTimeMillis(),
                        launchCount = 1,
                    ),
                )
            } else {
                db.appDao().recordLaunch(packageName, System.currentTimeMillis())
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun appLabel(packageName: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    suspend fun toggleFavorite(packageName: String, favorite: Boolean) {
        val existing = db.appDao().get(packageName)
        if (existing == null) {
            db.appDao().upsert(
                com.astratouch.app.data.local.entity.FavoriteAppEntity(
                    packageName = packageName,
                    label = appLabel(packageName),
                    isFavorite = favorite,
                    category = "uncategorized",
                    groupName = "",
                    lastUsedAt = System.currentTimeMillis(),
                    launchCount = 0,
                ),
            )
        } else {
            db.appDao().setFavorite(packageName, favorite)
        }
    }

    suspend fun setGroup(packageName: String, group: String) {
        val existing = db.appDao().get(packageName) ?: return
        db.appDao().upsert(existing.copy(groupName = group, category = group.ifBlank { "uncategorized" }))
    }

    suspend fun openAppDetails(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return@withContext try {
            context.startActivity(intent); true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isInstalled(packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            pm.getApplicationInfo(packageName, 0); true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
