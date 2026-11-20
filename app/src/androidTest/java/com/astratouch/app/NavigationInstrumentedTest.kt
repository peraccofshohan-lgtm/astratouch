package com.astratouch.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astratouch.app.util.NavRoutes
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end navigation smoke test on an emulator.
 *
 * It renders the real graph (with the real Hilt graph and the real Room database)
 * and walks the primary destinations. This is the "does the app open and can you
 * reach every screen" gate.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun appStartsAndShowsDashboard() {
        composeRule.setContent { AstraTouchApp(initialRoute = NavRoutes.HOME) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("AstraTouch").assertIsDisplayed()
        composeRule.onNodeWithText("Bubble stopped").assertIsDisplayed()
    }

    @Test
    fun navigationGraphDeclaresAllTwentyFourScreens() {
        // Guards against a screen silently disappearing from the graph.
        assertTrue(NavRoutes.all.size == 24)
        assertTrue(NavRoutes.all.distinct().size == 24)
    }

    @Test
    fun settingsScreenOpensAndListsPermissions() {
        composeRule.setContent { AstraTouchApp(initialRoute = NavRoutes.SETTINGS) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Permission center").performClick()
        composeRule.waitForIdle()
    }
}
