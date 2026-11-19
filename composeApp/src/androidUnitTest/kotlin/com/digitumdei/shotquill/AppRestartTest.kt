package com.digitumdei.shotquill

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.digitumdei.shotquill.media.LogoPick
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun aLogoChosenWhileSettingsIsOpenIsTakenUpWithItsCaution() {
        val repository = InMemoryLocalSettingsRepository()
        var pick by mutableStateOf<LogoPick?>(null)
        var asked = 0
        compose.setContent {
            App(
                settingsRepository = repository,
                onPickLogo = { asked++ },
                logoPick = pick,
                onLogoPickHandled = { pick = null },
            )
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Choose logo").performScrollTo().performClick()
        assertEquals(1, asked)

        pick = LogoPick.Picked("file:///brand/logo.svg", caution = "This file has text that may not draw.")
        compose.waitForIdle()

        assertEquals("file:///brand/logo.svg", repository.readSettings().logo.uri)
        compose.onNodeWithText("This file has text that may not draw.").assertExists()
        assertNull(pick)

        pick = LogoPick.Failed("That file is not an image.")
        compose.waitForIdle()
        compose.onNodeWithText("That file is not an image.").assertExists()
        assertEquals("file:///brand/logo.svg", repository.readSettings().logo.uri)
    }

    @Test
    fun aLogoThatComesBackAfterTheAppWasRestartedIsStillKept() {
        // The app has been started again and is showing Home, with the chooser's
        // answer already waiting: nothing that asked for it is left to tell.
        val repository = InMemoryLocalSettingsRepository()
        var pick by mutableStateOf<LogoPick?>(LogoPick.Picked("file:///brand/logo.png"))
        compose.setContent {
            App(settingsRepository = repository, logoPick = pick, onLogoPickHandled = { pick = null })
        }
        compose.waitForIdle()

        assertEquals("file:///brand/logo.png", repository.readSettings().logo.uri)
        assertNull(pick)
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
