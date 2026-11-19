package com.astratouch.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val description: String,
    @ColumnInfo(name = "theme_id") val themeId: String,
    @ColumnInfo(name = "bubble_config_id") val bubbleConfigId: String,
    @ColumnInfo(name = "radial_config_id") val radialConfigId: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "auto_switch_package") val autoSwitchPackage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "bubble_configs",
    indices = [Index("profile_id")],
)
data class BubbleConfigEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    @ColumnInfo(name = "size_dp") val sizeDp: Int,
    val shape: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    val opacity: Float,
    @ColumnInfo(name = "color_start") val colorStart: Long,
    @ColumnInfo(name = "color_end") val colorEnd: Long,
    @ColumnInfo(name = "border_width_dp") val borderWidthDp: Int,
    @ColumnInfo(name = "shadow_radius_dp") val shadowRadiusDp: Int,
    @ColumnInfo(name = "blur_radius_dp") val blurRadiusDp: Int,
    @ColumnInfo(name = "icon_tint") val iconTint: Long,
    @ColumnInfo(name = "icon_size_dp") val iconSizeDp: Int,
    @ColumnInfo(name = "edge_margin_dp") val edgeMarginDp: Int,
    @ColumnInfo(name = "snap_mode") val snapMode: String,
    @ColumnInfo(name = "dock_side") val dockSide: String,
    @ColumnInfo(name = "position_x") val positionX: Int,
    @ColumnInfo(name = "position_y") val positionY: Int,
    @ColumnInfo(name = "auto_hide_ms") val autoHideMs: Long,
    @ColumnInfo(name = "auto_dim_alpha") val autoDimAlpha: Float,
    @ColumnInfo(name = "haptics_enabled") val hapticsEnabled: Boolean,
    @ColumnInfo(name = "animation_style") val animationStyle: String,
    @ColumnInfo(name = "animation_speed") val animationSpeed: Float,
    @ColumnInfo(name = "startup_on_boot") val startupOnBoot: Boolean,
    @ColumnInfo(name = "show_edge_handle") val showEdgeHandle: Boolean,
    @ColumnInfo(name = "edge_panel_enabled") val edgePanelEnabled: Boolean,
)

@Entity(tableName = "radial_configs")
data class RadialConfigEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val layout: String,
    @ColumnInfo(name = "radius_dp") val radiusDp: Int,
    @ColumnInfo(name = "spacing_dp") val spacingDp: Int,
    @ColumnInfo(name = "item_size_dp") val itemSizeDp: Int,
    @ColumnInfo(name = "background_start") val backgroundStart: Long,
    @ColumnInfo(name = "background_end") val backgroundEnd: Long,
    @ColumnInfo(name = "background_opacity") val backgroundOpacity: Float,
    @ColumnInfo(name = "blur_radius_dp") val blurRadiusDp: Int,
    val accent: Long,
    @ColumnInfo(name = "animation_style") val animationStyle: String,
    @ColumnInfo(name = "animation_speed") val animationSpeed: Float,
    @ColumnInfo(name = "haptics_enabled") val hapticsEnabled: Boolean,
    @ColumnInfo(name = "show_labels") val showLabels: Boolean,
)

@Entity(
    tableName = "radial_items",
    indices = [Index("radial_config_id"), Index(value = ["radial_config_id", "page", "slot"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = RadialConfigEntity::class,
            parentColumns = ["id"],
            childColumns = ["radial_config_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RadialItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "radial_config_id") val radialConfigId: String,
    val page: Int,
    val slot: Int,
    @ColumnInfo(name = "action_id") val actionId: String,
    val label: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    val category: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
)

@Entity(
    tableName = "gesture_mappings",
    indices = [Index(value = ["profile_id", "gesture"], unique = true)],
)
data class GestureMappingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val gesture: String,
    @ColumnInfo(name = "action_id") val actionId: String,
    @ColumnInfo(name = "swipe_threshold_px") val swipeThresholdPx: Int,
    val sensitivity: Float,
    @ColumnInfo(name = "haptics_enabled") val hapticsEnabled: Boolean,
)

@Entity(tableName = "macros", indices = [Index("profile_id")])
data class MacroEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val name: String,
    val description: String,
    @ColumnInfo(name = "icon_key") val iconKey: String,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean,
    @ColumnInfo(name = "timeout_ms") val timeoutMs: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "last_run_at") val lastRunAt: Long,
    @ColumnInfo(name = "last_run_status") val lastRunStatus: String,
)

@Entity(
    tableName = "macro_steps",
    indices = [Index("macro_id")],
    foreignKeys = [
        ForeignKey(
            entity = MacroEntity::class,
            parentColumns = ["id"],
            childColumns = ["macro_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MacroStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "macro_id") val macroId: String,
    @ColumnInfo(name = "step_order") val order: Int,
    val type: String,
    @ColumnInfo(name = "action_id") val actionId: String,
    val payload: String,
    @ColumnInfo(name = "delay_ms") val delayMs: Long,
    val label: String,
)

@Entity(tableName = "macro_executions", indices = [Index("macro_id")])
data class MacroExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "macro_id") val macroId: String,
    @ColumnInfo(name = "macro_name") val macroName: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
    val status: String,
    val detail: String,
)

@Entity(tableName = "theme_configs")
data class ThemeConfigEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "preset_id") val presetId: String,
    val name: String,
    val background: Long,
    val surface: Long,
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    @ColumnInfo(name = "on_background") val onBackground: Long,
    @ColumnInfo(name = "is_dark") val isDark: Boolean,
    @ColumnInfo(name = "corner_radius_dp") val cornerRadiusDp: Int,
    @ColumnInfo(name = "typography_scale") val typographyScale: Float,
    @ColumnInfo(name = "animation_speed") val animationSpeed: Float,
    @ColumnInfo(name = "haptic_intensity") val hapticIntensity: Float,
    @ColumnInfo(name = "use_monospace") val useMonospace: Boolean,
)

@Entity(tableName = "favorite_apps")
data class FavoriteAppEntity(
    @PrimaryKey @ColumnInfo(name = "package_name") val packageName: String,
    val label: String,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    val category: String,
    @ColumnInfo(name = "group_name") val groupName: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long,
    @ColumnInfo(name = "launch_count") val launchCount: Int,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "clipboard_records")
data class ClipboardRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "char_count") val charCount: Int,
    @ColumnInfo(name = "is_sensitive_redacted") val isSensitiveRedacted: Boolean,
)

@Entity(tableName = "recent_actions", indices = [Index(value = ["action_id"], unique = true)])
data class RecentActionEntity(
    @PrimaryKey @ColumnInfo(name = "action_id") val actionId: String,
    @ColumnInfo(name = "used_at") val usedAt: Long,
    @ColumnInfo(name = "use_count") val useCount: Int,
)

@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "remaining_ms") val remainingMs: Long,
    @ColumnInfo(name = "is_running") val isRunning: Boolean,
    @ColumnInfo(name = "ends_at_epoch_ms") val endsAtEpochMs: Long,
    @ColumnInfo(name = "ringtone_requested") val ringtoneRequested: Boolean,
)
