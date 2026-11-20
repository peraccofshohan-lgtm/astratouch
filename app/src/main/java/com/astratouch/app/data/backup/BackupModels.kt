package com.astratouch.app.data.backup

import com.astratouch.app.domain.model.BubbleConfig
import com.astratouch.app.domain.model.FavoriteApp
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.Macro
import com.astratouch.app.domain.model.MacroStep
import com.astratouch.app.domain.model.Note
import com.astratouch.app.domain.model.Profile
import com.astratouch.app.domain.model.RadialConfig
import com.astratouch.app.domain.model.RadialMenuItem
import com.astratouch.app.domain.model.ThemeConfig
import kotlinx.serialization.Serializable

/**
 * Versioned, human-readable configuration snapshot.
 *
 * Clipboard history is deliberately **not** included: it is the most sensitive
 * local data and must not travel in a shareable file. Notes are included because
 * the user explicitly exports them.
 */
@Serializable
data class BackupEnvelope(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val appVersion: String,
    val appVersionCode: Int,
    val exportedAt: Long,
    val deviceSdk: Int,
    val profiles: List<Profile> = emptyList(),
    val bubbleConfigs: List<BubbleConfig> = emptyList(),
    val radialConfigs: List<RadialConfig> = emptyList(),
    val radialItems: List<RadialMenuItem> = emptyList(),
    val gestureMappings: List<GestureMapping> = emptyList(),
    val macros: List<Macro> = emptyList(),
    val macroSteps: List<MacroStep> = emptyList(),
    val themes: List<ThemeConfig> = emptyList(),
    val favoriteApps: List<FavoriteApp> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

sealed interface ImportResult {
    data class Success(val importedProfiles: Int, val importedMacros: Int, val importedThemes: Int) : ImportResult
    data class Failure(val reason: String) : ImportResult
}

sealed interface ExportResult {
    data object Success : ExportResult
    data class Failure(val reason: String) : ExportResult
}
