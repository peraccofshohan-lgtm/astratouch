package com.astratouch.app.data

import com.astratouch.app.data.backup.BackupEnvelope
import com.astratouch.app.domain.model.BubbleConfig
import com.astratouch.app.domain.model.BubbleShape
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import com.astratouch.app.domain.model.Macro
import com.astratouch.app.domain.model.MacroStep
import com.astratouch.app.domain.model.Profile
import com.astratouch.app.domain.model.ProfileType
import com.astratouch.app.domain.model.RadialConfig
import com.astratouch.app.domain.model.RadialMenuItem
import com.astratouch.app.domain.model.RadialLayout
import com.astratouch.app.domain.model.StepType
import com.astratouch.app.domain.model.ThemeConfig
import com.astratouch.app.domain.model.ThemePreset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    private fun sampleEnvelope() = BackupEnvelope(
        appVersion = "1.0.0",
        appVersionCode = 10000,
        exportedAt = 1_700_000_000_000,
        deviceSdk = 34,
        profiles = listOf(Profile(id = "work", type = ProfileType.WORK, name = "Work")),
        bubbleConfigs = listOf(BubbleConfig(shape = BubbleShape.HEXAGON, sizeDp = 64)),
        radialConfigs = listOf(RadialConfig(layout = RadialLayout.GRID)),
        radialItems = listOf(RadialMenuItem(id = "i1", actionId = "nav.home", slot = 0)),
        gestureMappings = listOf(
            GestureMapping(gesture = GestureType.SWIPE_LEFT, actionId = "nav.back"),
        ),
        macros = listOf(Macro(id = "m1", name = "Test", description = "desc")),
        macroSteps = listOf(
            MacroStep(id = "s1", macroId = "m1", type = StepType.ACTION, actionId = "nav.home", order = 0),
        ),
        themes = listOf(ThemeConfig.fromPreset(ThemePreset.CYBER_CYAN).copy(id = "theme-cyber")),
    )

    @Test
    fun `backup round trips without loss`() {
        val original = sampleEnvelope()
        val encoded = json.encodeToString(BackupEnvelope.serializer(), original)
        val decoded = json.decodeFromString(BackupEnvelope.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `encoded backup is human readable json with a schema version`() {
        val encoded = json.encodeToString(BackupEnvelope.serializer(), sampleEnvelope())
        assertTrue(encoded.startsWith("{"))
        assertTrue(encoded.contains("\"schemaVersion\""))
        assertTrue(encoded.contains("\"appVersionCode\""))
        assertTrue(encoded.contains("Work"))
        // Clipboard history must never be part of a backup.
        assertTrue(!encoded.contains("clipboard", ignoreCase = true))
    }

    @Test
    fun `unknown fields from newer exports are ignored`() {
        val encoded = json.encodeToString(BackupEnvelope.serializer(), sampleEnvelope())
            .replaceFirst("{", "{\"futureField\":{\"a\":1},")
        val decoded = json.decodeFromString(BackupEnvelope.serializer(), encoded)
        assertEquals(1, decoded.profiles.size)
    }

    @Test
    fun `missing optional collections default to empty`() {
        val minimal = """{"appVersion":"1.0.0","appVersionCode":1,"exportedAt":0,"deviceSdk":34}"""
        val decoded = json.decodeFromString(BackupEnvelope.serializer(), minimal)
        assertTrue(decoded.profiles.isEmpty())
        assertTrue(decoded.macros.isEmpty())
    }

    @Test
    fun `malformed json fails loudly`() {
        val result = runCatching { json.decodeFromString(BackupEnvelope.serializer(), "{not json") }
        assertTrue(result.isFailure)
    }

    @Test
    fun `theme presets keep their identity`() {
        ThemePreset.entries.forEach { preset ->
            val config = ThemeConfig.fromPreset(preset)
            assertEquals(preset.id, config.presetId)
            assertEquals(preset.primary, config.primary)
        }
    }
}
