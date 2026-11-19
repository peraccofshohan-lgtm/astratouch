package com.astratouch.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astratouch.app.ui.screens.CalculatorScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CalculatorInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun twoPlusThreeEqualsFive() {
        composeRule.setContent { CalculatorScreen(onBack = {}) }
        composeRule.onNodeWithText("2").performClick()
        composeRule.onNodeWithText("+").performClick()
        composeRule.onNodeWithText("3").performClick()
        composeRule.onNodeWithText("=").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun clearResetsTheDisplay() {
        composeRule.setContent { CalculatorScreen(onBack = {}) }
        composeRule.onNodeWithText("9").performClick()
        composeRule.onNodeWithText("C").performClick()
        composeRule.onNodeWithText("0").assertIsDisplayed()
    }
}
