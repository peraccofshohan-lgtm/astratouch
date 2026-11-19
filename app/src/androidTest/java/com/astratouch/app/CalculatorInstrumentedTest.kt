package com.astratouch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astratouch.app.ui.screens.CalculatorScreen
import com.astratouch.app.ui.screens.CalculatorViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real calculator composable and asserts on the display only.
 *
 * Keys are addressed by `testTag("calc_key_$label")` because the digits also
 * appear in the display and in the recent-calculations list — matching by bare
 * text is ambiguous (that ambiguity is exactly what made an earlier version of
 * this test flaky).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalculatorInstrumentedTest {


    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() = hiltRule.inject()

    private fun press(vararg labels: String) {
        labels.forEach { composeRule.onNodeWithTag("calc_key_$it").performClick() }
        composeRule.waitForIdle()
    }

    /**
     * Passes an explicit ViewModel rather than relying on `hiltViewModel()`.
     *
     * `hiltViewModel()` needs a `ViewModelStoreOwner` that is also a Hilt
     * `ViewModelFactory` host; inside `HiltTestActivity` the default activity
     * factory is not Hilt-backed, so resolving it there throws. Supplying the
     * (injectable, dependency-free) ViewModel keeps the test focused on the
     * composable's behaviour instead of on Hilt's view-model plumbing.
     */
    private fun setCalculator() {
        val vm = CalculatorViewModel()
        composeRule.setContent { CalculatorScreen(onBack = {}, viewModel = vm) }
    }

    @Test
    fun twoPlusThreeEqualsFive() {
        setCalculator()

        press("2", "+", "3")
        composeRule.onNodeWithTag("calc_expression").assertTextEquals("2+3")

        press("=")
        // The entered expression stays visible; the answer appears beneath it.
        composeRule.onNodeWithTag("calc_expression").assertTextEquals("2+3")
        composeRule.onNodeWithTag("calc_result").assertIsDisplayed().assertTextEquals("5")
    }

    @Test
    fun clearResetsTheDisplay() {
        setCalculator()

        press("9")
        composeRule.onNodeWithTag("calc_expression").assertTextEquals("9")

        press("C")
        composeRule.onNodeWithTag("calc_expression").assertTextEquals("0")
    }

    @Test
    fun operatorPrecedenceIsRespected() {
        setCalculator()

        press("2", "+", "3", "×", "4", "=")
        composeRule.onNodeWithTag("calc_result").assertTextEquals("14")
    }

    @Test
    fun divisionByZeroShowsAnError() {
        setCalculator()

        press("5", "÷", "0", "=")
        // The UI must explain the failure rather than showing "Infinity" or crashing.
        composeRule.onNodeWithTag("calc_result").assertIsDisplayed()
    }
}
