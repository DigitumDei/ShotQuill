package com.digitumdei.shotquill

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Moving between screens, and staying on the same screen when Android throws
 * the app's state away and brings it back, as it does on rotation or when
 * memory runs short.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppNavigationTest {
    @get:Rule(order = 0)
    val screenTestActivity = ScreenTestActivity()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    @Test
    fun settingsOpensFromHomeAndBackReturnsThere() {
        compose.setContent { App() }

        compose.onNodeWithText("ShotQuill").assertExists()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertExists()
        assertTrue(compose.onAllNodesWithText("Your photos, your words.").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your photos, your words.").assertExists()
    }

    @Test
    fun theOpenScreenSurvivesSavedStateBeingRestored() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App() }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertExists()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Your photos, your words.").assertExists()
    }
}

/** The real activity with the real storage behind it, started and restarted by Robolectric. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityRestartTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppStartsOnHomeAndStaysOnSettingsThroughARestartOfTheActivity() {
        compose.onNodeWithText("Your photos, your words.").assertExists()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertExists()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("Settings").assertExists()
        assertTrue(compose.onAllNodesWithText("Your photos, your words.").fetchSemanticsNodes().isEmpty())
    }
}
