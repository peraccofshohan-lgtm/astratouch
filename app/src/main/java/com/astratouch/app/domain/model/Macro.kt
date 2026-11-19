package com.astratouch.app.domain.model

/** A single executable step inside a macro (Part 10). */
data class MacroStep(
    val id: String = "",
    val macroId: String = "",
    val order: Int = 0,
    val type: StepType = StepType.ACTION,
    val actionId: String = "",
    val payload: String = "",
    val delayMs: Long = 0L,
    val label: String = "",
)

enum class StepType(val label: String) {
    ACTION("Run action"),
    DELAY("Wait"),
    CONDITION("Condition"),
    ;

    companion object {
        fun fromName(name: String?): StepType = entries.firstOrNull { it.name == name } ?: ACTION
    }
}

/** Supported conditional operators for CONDITION steps. */
enum class ConditionType(val label: String) {
    PROFILE_IS("Active profile is"),
    BUBBLE_VISIBLE("Bubble is visible"),
    ACCESSIBILITY_AVAILABLE("Accessibility service is available"),
    OVERLAY_GRANTED("Overlay permission is granted"),
    WRITE_SETTINGS_GRANTED("Modify system settings is granted"),
    BATTERY_SAVER_ON("Battery saver is on"),
    ;

    companion object {
        fun fromName(name: String?): ConditionType =
            entries.firstOrNull { it.name == name } ?: PROFILE_IS
    }
}

data class Macro(
    val id: String = "",
    val profileId: String = ProfileType.DEFAULT.id,
    val name: String,
    val description: String = "",
    val iconKey: String = "ic_bolt",
    val isEnabled: Boolean = true,
    val timeoutMs: Long = 15_000L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sortOrder: Int = 0,
    val lastRunAt: Long = 0L,
    val lastRunStatus: MacroRunStatus = MacroRunStatus.NEVER,
)

enum class MacroRunStatus(val label: String) {
    NEVER("Never run"),
    RUNNING("Running"),
    SUCCESS("Succeeded"),
    CANCELLED("Cancelled"),
    TIMED_OUT("Timed out"),
    FAILED("Failed"),
    SKIPPED("Skipped"),
    ;

    companion object {
        fun fromName(name: String?): MacroRunStatus = entries.firstOrNull { it.name == name } ?: NEVER
    }
}

data class MacroExecutionRecord(
    val id: Long = 0,
    val macroId: String,
    val macroName: String,
    val startedAt: Long,
    val finishedAt: Long,
    val status: MacroRunStatus,
    val detail: String = "",
)

/** The six default macros shipped on first run (Part 10). */
object DefaultMacros {
    private const val PROFILE = "default"

    fun all(): List<Pair<Macro, List<MacroStep>>> {
        fun macro(
            id: String,
            name: String,
            description: String,
            icon: String,
            steps: List<Triple<StepType, String, Long>>,
        ): Pair<Macro, List<MacroStep>> {
            val m = Macro(
                id = id,
                profileId = PROFILE,
                name = name,
                description = description,
                iconKey = icon,
            )
            val list = steps.mapIndexed { index, (type, actionId, delay) ->
                MacroStep(
                    id = "$id-step-$index",
                    macroId = id,
                    order = index,
                    type = type,
                    actionId = actionId,
                    delayMs = delay,
                )
            }
            return m to list
        }

        return listOf(
            macro(
                id = "macro-work",
                name = "Work Mode",
                description = "Silence notifications, dim the screen and open the note pad.",
                icon = "ic_work",
                steps = listOf(
                    Triple(StepType.ACTION, ActionIds.OPEN_QUICK_SETTINGS, 400L),
                    Triple(StepType.DELAY, "", 600L),
                    Triple(StepType.ACTION, ActionIds.OPEN_NOTES, 0L),
                ),
            ),
            macro(
                id = "macro-reading",
                name = "Reading Mode",
                description = "Lower the brightness twice and mute the device for calm reading.",
                icon = "ic_book",
                steps = listOf(
                    Triple(StepType.ACTION, ActionIds.BRIGHTNESS_DOWN, 0L),
                    Triple(StepType.ACTION, ActionIds.BRIGHTNESS_DOWN, 250L),
                    Triple(StepType.ACTION, ActionIds.VOLUME_DOWN, 250L),
                ),
            ),
            macro(
                id = "macro-gaming",
                name = "Gaming Mode",
                description = "Hide the bubble so nothing blocks gameplay.",
                icon = "ic_game",
                steps = listOf(
                    Triple(StepType.ACTION, ActionIds.HIDE_BUBBLE, 0L),
                ),
            ),
            macro(
                id = "macro-media",
                name = "Media Mode",
                description = "Open the volume panel then the quick settings panel.",
                icon = "ic_media",
                steps = listOf(
                    Triple(StepType.ACTION, ActionIds.VOLUME_PANEL, 500L),
                    Triple(StepType.DELAY, "", 700L),
                    Triple(StepType.ACTION, ActionIds.QUICK_SETTINGS, 0L),
                ),
            ),
            macro(
                id = "macro-screenshot",
                name = "Quick Screenshot",
                description = "Silence the device and capture the screen.",
                icon = "ic_camera",
                steps = listOf(
                    Triple(StepType.ACTION, ActionIds.SCREENSHOT, 450L),
                    Triple(StepType.ACTION, ActionIds.OPEN_SCREENSHOTS, 0L),
                ),
            ),
            macro(
                id = "macro-productivity",
                name = "Productivity Mode",
                description = "Open the calculator, then the clipboard manager.",
                icon = "ic_bolt",
                steps = listOf(
                    Triple(StepType.ACTION, ActionIds.OPEN_CALCULATOR, 300L),
                    Triple(StepType.DELAY, "", 500L),
                    Triple(StepType.ACTION, ActionIds.OPEN_CLIPBOARD, 0L),
                ),
            ),
        )
    }
}
