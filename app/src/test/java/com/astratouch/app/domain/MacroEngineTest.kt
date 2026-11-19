package com.astratouch.app.domain

import com.astratouch.app.data.repository.MacroRepository
import com.astratouch.app.domain.model.ActionIds
import com.astratouch.app.domain.model.ConditionType
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import com.astratouch.app.domain.model.Macro
import com.astratouch.app.domain.model.MacroRunStatus
import com.astratouch.app.domain.model.MacroStep
import com.astratouch.app.domain.model.StepType
import com.astratouch.app.domain.usecase.GestureCoverageUseCase
import com.astratouch.app.domain.usecase.RunMacroUseCase
import com.astratouch.app.domain.usecase.SaveMacroUseCase
import com.astratouch.app.service.action.ActionRegistry
import com.astratouch.app.service.action.ActionResult
import com.astratouch.app.service.action.CapabilityState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MacroEngineTest {

    private lateinit var macroRepository: MacroRepository
    private lateinit var actionRegistry: ActionRegistry
    private lateinit var runMacro: RunMacroUseCase

    private val macro = Macro(id = "m1", name = "Test macro", timeoutMs = 5_000)

    @Before
    fun setUp() {
        macroRepository = mockk(relaxed = true)
        actionRegistry = mockk(relaxed = true)
        every { actionRegistry.capabilityOf(any()) } returns CapabilityState.AVAILABLE
        every { actionRegistry.byId(any()) } returns null
        runMacro = RunMacroUseCase(macroRepository, actionRegistry)
    }

    @Test
    fun `steps execute in order`() = runTest {
        val steps = listOf(
            MacroStep(id = "1", macroId = "m1", order = 0, actionId = ActionIds.HOME),
            MacroStep(id = "2", macroId = "m1", order = 1, actionId = ActionIds.BACK),
            MacroStep(id = "3", macroId = "m1", order = 2, actionId = ActionIds.RECENTS),
        )
        coEvery { macroRepository.getSteps("m1") } returns steps
        coEvery { actionRegistry.execute(any(), any()) } returns ActionResult.Success

        val outcome = runMacro(macro)

        assertEquals(MacroRunStatus.SUCCESS, outcome.status)
        assertEquals(3, outcome.executedSteps)
        coVerify(exactly = 3) { actionRegistry.execute(any(), any()) }
        coVerify { macroRepository.recordRun(macro, MacroRunStatus.SUCCESS, any()) }
    }

    @Test
    fun `disabled macro does not execute anything`() = runTest {
        val outcome = runMacro(macro.copy(isEnabled = false))
        assertEquals(MacroRunStatus.SKIPPED, outcome.status)
        coVerify(exactly = 0) { actionRegistry.execute(any(), any()) }
    }

    @Test
    fun `empty macro reports skipped`() = runTest {
        coEvery { macroRepository.getSteps("m1") } returns emptyList()
        val outcome = runMacro(macro)
        assertEquals(MacroRunStatus.SKIPPED, outcome.status)
    }

    @Test
    fun `recursion is blocked beyond the depth limit`() = runTest {
        coEvery { macroRepository.getSteps("m1") } returns listOf(
            MacroStep(id = "1", macroId = "m1", actionId = ActionIds.HOME),
        )
        val outcome = runMacro(macro, depth = RunMacroUseCase.MAX_DEPTH + 1)
        assertEquals(MacroRunStatus.SKIPPED, outcome.status)
        assertTrue(outcome.detail.contains("recursion", ignoreCase = true))
    }

    @Test
    fun `steps that re-enter the macro manager are blocked`() = runTest {
        coEvery { macroRepository.getSteps("m1") } returns listOf(
            MacroStep(id = "1", macroId = "m1", actionId = ActionIds.OPEN_MACROS),
            MacroStep(id = "2", macroId = "m1", actionId = ActionIds.HOME),
        )
        coEvery { actionRegistry.execute(ActionIds.HOME, any()) } returns ActionResult.Success

        val outcome = runMacro(macro)

        assertEquals(1, outcome.skippedSteps)
        assertEquals(1, outcome.executedSteps)
        coVerify(exactly = 0) { actionRegistry.execute(ActionIds.OPEN_MACROS, any()) }
    }

    @Test
    fun `failing actions are reported but do not abort the macro`() = runTest {
        coEvery { macroRepository.getSteps("m1") } returns listOf(
            MacroStep(id = "1", macroId = "m1", actionId = ActionIds.FLASHLIGHT),
            MacroStep(id = "2", macroId = "m1", actionId = ActionIds.HOME),
        )
        coEvery { actionRegistry.execute(ActionIds.FLASHLIGHT, any()) } returns
            ActionResult.Failure(com.astratouch.app.service.action.FailureReason.NOT_SUPPORTED, "No torch")
        coEvery { actionRegistry.execute(ActionIds.HOME, any()) } returns ActionResult.Success

        val outcome = runMacro(macro)

        assertEquals(1, outcome.executedSteps)
        assertEquals(1, outcome.skippedSteps)
        assertEquals(MacroRunStatus.SUCCESS, outcome.status)
        assertTrue(outcome.detail.contains("No torch"))
    }

    @Test
    fun `a macro whose every step fails is marked failed`() = runTest {
        coEvery { macroRepository.getSteps("m1") } returns listOf(
            MacroStep(id = "1", macroId = "m1", actionId = ActionIds.HOME),
        )
        coEvery { actionRegistry.execute(any(), any()) } returns
            ActionResult.NeedsPermission(com.astratouch.app.domain.model.AccessId.ACCESSIBILITY, "enable")

        val outcome = runMacro(macro)
        assertEquals(MacroRunStatus.FAILED, outcome.status)
    }

    @Test
    fun `condition step that is not met is recorded as skipped`() = runTest {
        coEvery { macroRepository.getSteps("m1") } returns listOf(
            MacroStep(
                id = "1", macroId = "m1", type = StepType.CONDITION,
                payload = "${ConditionType.OVERLAY_GRANTED.name}|",
            ),
        )
        val outcome = runMacro(macro)
        assertEquals(1, outcome.skippedSteps)
    }

    @Test
    fun `save use case normalises ids and ordering`() = runTest {
        val repo = mockk<MacroRepository>(relaxed = true)
        val saveMacro = SaveMacroUseCase(repo)
        val capturedMacro = slot<Macro>()
        val capturedSteps = slot<List<MacroStep>>()
        coEvery { repo.saveMacro(capturedMacro.capture(), capturedSteps.capture()) } returns Unit

        val result = saveMacro(
            Macro(id = "", name = "Fresh"),
            listOf(
                MacroStep(id = "", order = 9, actionId = ActionIds.HOME, delayMs = 999_999),
                MacroStep(id = "", order = 3, actionId = ActionIds.BACK),
            ),
        )

        assertTrue(result.isSuccess)
        assertTrue(capturedMacro.captured.id.startsWith("macro-"))
        assertEquals(listOf(0, 1), capturedSteps.captured.map { it.order })
        assertEquals(RunMacroUseCase.MAX_DELAY_MS, capturedSteps.captured[0].delayMs)
    }

    @Test
    fun `save use case rejects a blank name`() = runTest {
        val repo = mockk<MacroRepository>(relaxed = true)
        val result = SaveMacroUseCase(repo)(Macro(id = "x", name = "   "), emptyList())
        assertTrue(result.isFailure)
    }

    @Test
    fun `gesture coverage lists every gesture exactly once`() {
        val mappings = listOf(
            GestureMapping(gesture = GestureType.SINGLE_TAP, actionId = "a"),
            GestureMapping(gesture = GestureType.DOUBLE_TAP, actionId = "b"),
        )
        val coverage = GestureCoverageUseCase()(mappings)
        assertEquals(GestureType.entries.size, coverage.size)
        assertEquals("a", coverage[GestureType.SINGLE_TAP])
        assertEquals(null, coverage[GestureType.LONG_PRESS])
        assertNotNull(coverage.keys)
    }
}
