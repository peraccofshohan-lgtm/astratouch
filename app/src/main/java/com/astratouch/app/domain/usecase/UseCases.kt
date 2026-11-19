package com.astratouch.app.domain.usecase

import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.data.repository.MacroRepository
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.domain.model.ActionIds
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import com.astratouch.app.domain.model.Macro
import com.astratouch.app.domain.model.MacroRunStatus
import com.astratouch.app.domain.model.MacroStep
import com.astratouch.app.domain.model.Profile
import com.astratouch.app.domain.model.StepType
import com.astratouch.app.service.action.ActionRegistry
import com.astratouch.app.service.action.ActionResult
import com.astratouch.app.service.action.CapabilityState
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Switch the active profile and persist the choice. */
class SwitchProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferencesStore: PreferencesStore,
) {
    suspend operator fun invoke(profileId: String): Result<Unit> = runCatching {
        profileRepository.setActiveProfile(profileId)
        preferencesStore.setActiveProfileId(profileId)
    }
}

/** Validates and saves a profile, keeping dependent rows consistent. */
class SaveProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(profile: Profile): Result<Unit> = runCatching {
        require(profile.name.isNotBlank()) { "Profile name cannot be empty" }
        profileRepository.saveProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    }
}

/** Binds a gesture to an action, refusing actions that cannot run here. */
class BindGestureUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val actionRegistry: ActionRegistry,
) {
    suspend operator fun invoke(mapping: GestureMapping): Result<Unit> = runCatching {
        val capability = actionRegistry.capabilityOf(mapping.actionId)
        require(capability != CapabilityState.UNSUPPORTED_OS) {
            "That action is not supported on this Android version."
        }
        require(mapping.actionId.isNotBlank()) { "Choose an action for this gesture." }
        profileRepository.saveGesture(mapping)
    }

    suspend fun resetToDefaults(profileId: String) = profileRepository.resetGestures(profileId)
}

/**
 * Runs a macro: steps execute sequentially with their configured delays and are
 * guarded against recursion, runaway repetition and over-long runs.
 */
class RunMacroUseCase @Inject constructor(
    private val macroRepository: MacroRepository,
    private val actionRegistry: ActionRegistry,
    private val capabilities: com.astratouch.app.service.action.DeviceCapabilities,
) {
    /**
     * @param depth recursion depth; macros may not trigger macros beyond [MAX_DEPTH]
     */
    suspend operator fun invoke(macro: Macro, depth: Int = 0): MacroRunOutcome {
        if (depth > MAX_DEPTH) {
            return MacroRunOutcome(MacroRunStatus.SKIPPED, "Macro recursion limit reached.", 0, 0)
        }
        if (!macro.isEnabled) {
            return MacroRunOutcome(MacroRunStatus.SKIPPED, "This macro is disabled.", 0, 0)
        }
        val steps = macroRepository.getSteps(macro.id).sortedBy { it.order }
        if (steps.isEmpty()) {
            macroRepository.recordRun(macro, MacroRunStatus.SKIPPED, "Macro has no steps.")
            return MacroRunOutcome(MacroRunStatus.SKIPPED, "This macro has no steps yet.", 0, 0)
        }

        val started = System.currentTimeMillis()
        var executed = 0
        var skipped = 0
        val issues = mutableListOf<String>()

        for (step in steps) {
            if (System.currentTimeMillis() - started > macro.timeoutMs) {
                macroRepository.recordRun(macro, MacroRunStatus.TIMED_OUT, "Timed out after $executed steps.")
                return MacroRunOutcome(
                    MacroRunStatus.TIMED_OUT,
                    "Stopped after ${macro.timeoutMs / 1000}s to protect your device.",
                    executed,
                    skipped,
                )
            }
            when (step.type) {
                StepType.DELAY -> kotlinx.coroutines.delay(step.delayMs.coerceIn(0, MAX_DELAY_MS))

                StepType.CONDITION -> {
                    val passed = evaluateCondition(step)
                    if (!passed) {
                        skipped++
                        issues += "Condition \"${step.payload.ifBlank { "condition" }}\" was not met; remaining steps continue."
                    }
                    if (step.delayMs > 0) kotlinx.coroutines.delay(step.delayMs.coerceIn(0, MAX_DELAY_MS))
                }

                StepType.ACTION -> {
                    if (step.actionId == ActionIds.OPEN_MACROS) {
                        skipped++
                        issues += "Blocked a step that would re-enter the macro manager (loop prevention)."
                        continue
                    }
                    if (step.delayMs > 0) kotlinx.coroutines.delay(step.delayMs.coerceIn(0, MAX_DELAY_MS))
                    when (val result = actionRegistry.execute(step.actionId, step.payload.ifBlank { null })) {
                        is ActionResult.Success -> executed++
                        is ActionResult.Partial -> {
                            executed++
                            issues += result.explanation
                        }
                        is ActionResult.NeedsPermission -> {
                            skipped++
                            issues += "Skipped ${step.actionId}: ${result.explanation}"
                        }
                        is ActionResult.Unsupported -> {
                            skipped++
                            issues += "Skipped ${step.actionId}: ${result.explanation}"
                        }
                        is ActionResult.Failure -> {
                            skipped++
                            issues += "${step.actionId} failed: ${result.explanation}"
                        }
                        ActionResult.Cancelled -> {
                            macroRepository.recordRun(macro, MacroRunStatus.CANCELLED, "Cancelled after $executed steps.")
                            return MacroRunOutcome(MacroRunStatus.CANCELLED, "Macro cancelled.", executed, skipped)
                        }
                    }
                }
            }
        }

        val status = when {
            executed == 0 && skipped > 0 -> MacroRunStatus.FAILED
            skipped > 0 -> MacroRunStatus.SUCCESS
            else -> MacroRunStatus.SUCCESS
        }
        val detail = buildString {
            append("$executed step(s) ran")
            if (skipped > 0) append(", $skipped skipped")
            if (issues.isNotEmpty()) append(". ").append(issues.take(3).joinToString(" "))
        }
        macroRepository.recordRun(macro, status, detail)
        return MacroRunOutcome(status, detail, executed, skipped)
    }

    private suspend fun evaluateCondition(step: MacroStep): Boolean {
        val parts = step.payload.split('|', limit = 2)
        val condition = parts.getOrNull(0)?.let { com.astratouch.app.domain.model.ConditionType.fromName(it) }
            ?: return false
        val expected = parts.getOrNull(1)?.ifBlank { null }
        return when (condition) {
            com.astratouch.app.domain.model.ConditionType.ACCESSIBILITY_AVAILABLE ->
                actionRegistry.capabilityOf(ActionIds.HOME) == CapabilityState.AVAILABLE
            com.astratouch.app.domain.model.ConditionType.OVERLAY_GRANTED ->
                com.astratouch.app.service.overlay.OverlayController.isRunning()
            com.astratouch.app.domain.model.ConditionType.BUBBLE_VISIBLE ->
                com.astratouch.app.service.overlay.OverlayController.instance?.state?.value?.bubbleVisible == true
            com.astratouch.app.domain.model.ConditionType.BATTERY_SAVER_ON ->
                capabilities.isPowerSaveMode()
            com.astratouch.app.domain.model.ConditionType.WRITE_SETTINGS_GRANTED ->
                actionRegistry.capabilityOf(ActionIds.BRIGHTNESS_UP) == CapabilityState.AVAILABLE
            com.astratouch.app.domain.model.ConditionType.PROFILE_IS ->
                expected == null || com.astratouch.app.service.overlay.OverlayController.instance
                    ?.state?.value?.activeProfileId == expected
        }
    }

    companion object {
        const val MAX_DEPTH = 1
        const val MAX_DELAY_MS = 10_000L
    }
}

data class MacroRunOutcome(
    val status: MacroRunStatus,
    val detail: String,
    val executedSteps: Int,
    val skippedSteps: Int,
)

/** Creates a duplicate macro with a unique id and "copy" name. */
class DuplicateMacroUseCase @Inject constructor(
    private val macroRepository: MacroRepository,
) {
    suspend operator fun invoke(macroId: String): Macro? =
        macroRepository.duplicateMacro(
            id = macroId,
            newId = "macro-" + java.util.UUID.randomUUID().toString().take(8),
            newName = (macroRepository.getMacro(macroId)?.name ?: "Macro") + " copy",
        )
}

/** Saves a macro while validating order and emptiness. */
class SaveMacroUseCase @Inject constructor(
    private val macroRepository: MacroRepository,
) {
    suspend operator fun invoke(macro: Macro, steps: List<MacroStep>): Result<Macro> = runCatching {
        require(macro.name.isNotBlank()) { "Give the macro a name." }
        val normalizedId = macro.id.ifBlank { "macro-" + java.util.UUID.randomUUID().toString().take(8) }
        val now = System.currentTimeMillis()
        val normalized = macro.copy(
            id = normalizedId,
            createdAt = if (macro.createdAt == 0L) now else macro.createdAt,
            updatedAt = now,
        )
        val normalizedSteps = steps.mapIndexed { index, step ->
            step.copy(
                id = step.id.ifBlank { "$normalizedId-step-$index" },
                macroId = normalizedId,
                order = index,
                delayMs = step.delayMs.coerceIn(0, RunMacroUseCase.MAX_DELAY_MS),
            )
        }
        macroRepository.saveMacro(normalized, normalizedSteps)
        normalized
    }
}

/** Determines which gestures are still unbound, for the gesture manager UI. */
class GestureCoverageUseCase @Inject constructor() {
    operator fun invoke(mappings: List<GestureMapping>): Map<GestureType, String?> =
        GestureType.entries.associateWith { type -> mappings.firstOrNull { it.gesture == type }?.actionId }
}

/** Whether the app still needs to show onboarding. */
class ShouldShowOnboardingUseCase @Inject constructor(
    private val preferencesStore: PreferencesStore,
) {
    suspend operator fun invoke(): Boolean = !preferencesStore.preferences.first().onboardingComplete
}
