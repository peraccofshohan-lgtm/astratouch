package com.astratouch.app.domain.model

import kotlinx.serialization.Serializable

/** A note stored locally (Part 13). */
@Serializable
data class Note(
    val id: Long = 0,
    val title: String,
    val body: String,
    val isPinned: Boolean = false,
    val colorArgb: Long = 0xFF101320,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val displayTitle: String get() = title.ifBlank { body.lineSequence().firstOrNull()?.take(40) ?: "Untitled note" }
}

/** A clipboard capture. Sensitive content is never persisted (Part 13). */
@Serializable
data class ClipboardRecord(
    val id: Long = 0,
    val content: String,
    val isPinned: Boolean = false,
    val createdAt: Long = 0,
    val sourcePackage: String = "",
    val charCount: Int = content.length,
    val isSensitiveRedacted: Boolean = false,
)

/** A favourite or grouped app entry for the launcher (Part 11). */
@Serializable
data class FavoriteApp(
    val packageName: String,
    val label: String,
    val isFavorite: Boolean = false,
    val group: String = "",
    val lastUsedAt: Long = 0L,
    val launchCount: Int = 0,
)

@Serializable
data class LauncherApp(
    val packageName: String,
    val label: String,
    val isFavorite: Boolean = false,
    val group: String = "",
    val lastUsedAt: Long = 0L,
    val hasShortcuts: Boolean = false,
)

/** A timer/countdown created in the Timer screen. */
@Serializable
data class AstraTimer(
    val id: Long = 0,
    val label: String,
    val durationMs: Long,
    val remainingMs: Long = durationMs,
    val isRunning: Boolean = false,
    val endsAtEpochMs: Long = 0L,
    val ringtoneRequested: Boolean = true,
)

/** Settings that are not part of a themed profile, stored in DataStore. */
@Serializable
data class AppPreferences(
    val onboardingComplete: Boolean = false,
    val overlayEnabled: Boolean = false,
    val activeProfileId: String = ProfileType.DEFAULT.id,
    val hapticsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val clipboardCaptureEnabled: Boolean = false,
    val clipboardRetentionDays: Int = 7,
    val showRecentsInRadial: Boolean = true,
    val experimentalGestures: Boolean = false,
    val experimentalOcr: Boolean = false,
    val experimentalEdgePanel: Boolean = true,
    val crashRecoveryNotice: Boolean = true,
    val lastBackupAt: Long = 0L,
)
