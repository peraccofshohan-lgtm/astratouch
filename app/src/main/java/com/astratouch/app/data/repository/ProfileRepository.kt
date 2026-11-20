package com.astratouch.app.data.repository

import com.astratouch.app.data.local.AstraDatabase
import com.astratouch.app.data.local.entity.BubbleConfigEntity
import com.astratouch.app.data.local.entity.GestureMappingEntity
import com.astratouch.app.data.local.entity.ProfileEntity
import com.astratouch.app.data.local.entity.RadialConfigEntity
import com.astratouch.app.data.local.entity.RadialItemEntity
import com.astratouch.app.data.local.entity.ThemeConfigEntity
import com.astratouch.app.data.mapper.toDomain
import com.astratouch.app.data.mapper.toEntity
import com.astratouch.app.domain.model.BubbleConfig
import com.astratouch.app.domain.model.DefaultGestureMappings
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.Profile
import com.astratouch.app.domain.model.ProfileType
import com.astratouch.app.domain.model.RadialConfig
import com.astratouch.app.domain.model.RadialMenuItem
import com.astratouch.app.domain.model.ThemeConfig
import com.astratouch.app.domain.model.ThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val db: AstraDatabase,
) {
    fun observeProfiles(): Flow<List<Profile>> =
        db.profileDao().observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActiveProfile(): Flow<Profile?> =
        observeProfiles().map { list -> list.firstOrNull { it.isActive } ?: list.firstOrNull() }

    suspend fun getActiveProfile(): Profile? =
        (db.profileDao().getActive() ?: db.profileDao().getAll().firstOrNull())?.toDomain()

    suspend fun getProfile(id: String): Profile? = db.profileDao().getById(id)?.toDomain()

    suspend fun setActiveProfile(id: String) = db.profileDao().setActive(id)

    suspend fun saveProfile(profile: Profile) = db.profileDao().upsert(profile.toEntity())

    suspend fun deleteProfile(id: String) {
        val existing = db.profileDao().getById(id) ?: return
        if (existing.type != ProfileType.CUSTOM.id) return
        db.profileDao().delete(existing)
    }

    fun observeBubbleConfig(id: String): Flow<BubbleConfig?> =
        db.bubbleConfigDao().observeById(id).map { it?.toDomain() }

    suspend fun getBubbleConfig(id: String): BubbleConfig? = db.bubbleConfigDao().getById(id)?.toDomain()

    suspend fun saveBubbleConfig(config: BubbleConfig) = db.bubbleConfigDao().upsert(config.toEntity())

    suspend fun saveBubblePosition(id: String, x: Int, y: Int, dockSide: String) =
        db.bubbleConfigDao().updatePosition(id, x, y, dockSide)

    fun observeRadialConfig(id: String): Flow<RadialConfig?> =
        db.radialConfigDao().observeById(id).map { it?.toDomain() }

    suspend fun getRadialConfig(id: String): RadialConfig? = db.radialConfigDao().getById(id)?.toDomain()

    suspend fun saveRadialConfig(config: RadialConfig) = db.radialConfigDao().upsert(config.toEntity())

    fun observeRadialItems(configId: String): Flow<List<RadialMenuItem>> =
        db.radialConfigDao().observeItems(configId).map { list -> list.map { it.toDomain() } }

    suspend fun saveRadialItems(configId: String, items: List<RadialMenuItem>) =
        db.radialConfigDao().replaceItems(configId, items.map { it.toEntity() })

    fun observeGestures(profileId: String): Flow<List<GestureMapping>> =
        db.gestureDao().observeForProfile(profileId)
            .map { list -> list.mapNotNull { it.toDomain() } }

    suspend fun getGestures(profileId: String): List<GestureMapping> =
        db.gestureDao().getForProfile(profileId).mapNotNull { it.toDomain() }

    suspend fun saveGesture(mapping: GestureMapping) = db.gestureDao().upsert(mapping.toEntity())

    suspend fun saveGestures(mappings: List<GestureMapping>) =
        db.gestureDao().upsertAll(mappings.map { it.toEntity() })

    suspend fun resetGestures(profileId: String) {
        db.gestureDao().deleteForProfile(profileId)
        db.gestureDao().upsertAll(DefaultGestureMappings.forProfile(profileId).map { it.toEntity() })
    }

    suspend fun getTheme(id: String): ThemeConfig? = db.themeDao().getById(id)?.toDomain()

    fun observeTheme(id: String): Flow<ThemeConfig?> =
        db.themeDao().observeById(id).map { it?.toDomain() }

    fun observeThemes(): Flow<List<ThemeConfig>> =
        db.themeDao().observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun saveTheme(theme: ThemeConfig) = db.themeDao().upsert(theme.toEntity())

    /**
     * Creates any missing built-in profile and its dependent configuration rows.
     * Safe to call on every launch: existing rows are never overwritten.
     */
    suspend fun ensureSeeded() {
        val now = System.currentTimeMillis()
        val existing = db.profileDao().getAll().associateBy { it.id }
        val themes = db.themeDao().getAll().associateBy { it.id }

        ThemePreset.entries.forEach { preset ->
            val themeId = "theme-${preset.id}"
            if (themes[themeId] == null) {
                db.themeDao().upsert(ThemeConfig.fromPreset(preset).copy(id = themeId).toEntity())
            }
        }

        ProfileType.entries.forEachIndexed { index, type ->
            if (existing[type.id] != null) return@forEachIndexed
            val bubbleId = "bubble-${type.id}"
            val radialId = "radial-${type.id}"
            val themeId = "theme-${type.id}"

            if (db.bubbleConfigDao().getById(bubbleId) == null) {
                db.bubbleConfigDao().upsert(BubbleConfig(id = bubbleId, profileId = type.id).toEntity())
            }
            if (db.radialConfigDao().getById(radialId) == null) {
                db.radialConfigDao().upsert(RadialConfig(id = radialId, profileId = type.id).toEntity())
                db.radialConfigDao().replaceItems(
                    radialId,
                    defaultItemsFor(type, radialId),
                )
            }
            db.profileDao().upsert(
                ProfileEntity(
                    id = type.id,
                    type = type.id,
                    name = type.label,
                    description = type.description,
                    themeId = themeId,
                    bubbleConfigId = bubbleId,
                    radialConfigId = radialId,
                    isActive = type == ProfileType.DEFAULT,
                    autoSwitchPackage = null,
                    createdAt = now,
                    updatedAt = now,
                    sortOrder = index,
                ),
            )
            db.gestureDao().upsertAll(
                DefaultGestureMappings.forProfile(type.id).mapIndexed { i, g ->
                    GestureMappingEntity(
                        id = "${type.id}-${g.gesture.name}-$i",
                        profileId = type.id,
                        gesture = g.gesture.name,
                        actionId = g.actionId,
                        swipeThresholdPx = g.swipeThresholdPx,
                        sensitivity = g.sensitivity,
                        hapticsEnabled = g.hapticsEnabled,
                    )
                },
            )
        }

        // Ensure every profile has exactly one active row.
        if (db.profileDao().getActive() == null) {
            db.profileDao().setActive(ProfileType.DEFAULT.id)
        }
    }

    /** Sensible starting layout per profile so nothing opens empty. */
    private fun defaultItemsFor(type: ProfileType, radialId: String): List<RadialItemEntity> {
        val actions = when (type) {
            ProfileType.DEFAULT -> listOf(
                "nav.home", "nav.back", "nav.recents", "nav.notifications",
                "device.volume_panel", "capture.screenshot", "astra.clipboard_manager", "astra.settings",
            )
            ProfileType.WORK -> listOf(
                "nav.home", "productivity.notes", "productivity.calculator", "productivity.clipboard",
                "capture.screenshot", "astra.macros", "device.volume_panel", "nav.back",
            )
            ProfileType.GAMING -> listOf(
                "nav.home", "device.volume_panel", "device.flashlight", "capture.screenshot",
                "astra.app_launcher", "nav.back",
            )
            ProfileType.READING -> listOf(
                "nav.home", "productivity.notes", "device.brightness_down", "device.brightness_up",
                "nav.back", "capture.screenshot",
            )
            ProfileType.MEDIA -> listOf(
                "device.volume_up", "device.volume_down", "device.volume_panel", "capture.screen_record",
                "nav.home", "astra.app_launcher", "nav.back", "device.flashlight",
            )
            ProfileType.CUSTOM -> listOf(
                "nav.home", "nav.back", "nav.recents", "nav.notifications",
            )
        }
        return actions.mapIndexed { index, actionId ->
            RadialItemEntity(
                id = "$radialId-item-$index",
                radialConfigId = radialId,
                page = 0,
                slot = index,
                actionId = actionId,
                label = "",
                iconKey = "",
                category = "",
                isFavorite = index < 3,
            )
        }
    }

    suspend fun getAllThemes(): List<ThemeConfig> = db.themeDao().getAll().map { it.toDomain() }
}
