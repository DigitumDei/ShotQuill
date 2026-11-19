package com.digitumdei.shotquill.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.digitumdei.shotquill.ScreenTestActivity
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.social.PublishTarget
import com.digitumdei.shotquill.ui.ShotQuillTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The settings screen wired to its view model, saving into a stand-in for the phone's storage. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenTest {
    @get:Rule(order = 0)
    val screenTestActivity = ScreenTestActivity()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val repository = InMemoryLocalSettingsRepository()
    private var wentBack = 0

    private fun show() {
        compose.setContent {
            ShotQuillTheme {
                SettingsScreen(
                    viewModel = SettingsViewModel(repository, renderer = null),
                    previewUri = null,
                    previewSize = null,
                    dictationOnDevice = null,
                    publishers = emptyList(),
                    onPickLogo = null,
                    onBack = { wentBack++ },
                )
            }
        }
    }

    @Test
    fun showsItsGroupsAndGoesBack() {
        show()

        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("USUAL HASHTAGS").performScrollTo().assertExists()
        compose.onNodeWithText("never generates the image or the caption", substring = true).assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, wentBack)
    }

    @Test
    fun usualHashtagsAreSavedForEachServiceAsTheyAreTyped() {
        show()

        compose.onNodeWithText("Bluesky").performScrollTo().performTextReplacement("#craftbeer #bskybeer")
        compose.onNodeWithText("Instagram").performScrollTo().performTextReplacement("#craftbeer #kznbeer")

        val saved = repository.readSettings()
        assertEquals("#craftbeer #bskybeer", saved.usualTagsFor(PublishTarget.Bluesky))
        assertEquals("#craftbeer #kznbeer", saved.usualTagsFor(PublishTarget.Instagram))
    }

    @Test
    fun aBlueskyHandleIsSavedAndTheAccountCountsAsSetUpOnceItHasAPassword() {
        show()

        compose.onNodeWithText("Handle").performScrollTo().performTextReplacement("doctrinebrewing.co.za")
        assertEquals("doctrinebrewing.co.za", repository.readSettings().bluesky.handle)

        // Nothing is asked of the network without a password, and no check is offered yet.
        assertTrue(compose.onAllNodesWithText("Check sign-in").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun anEmptyModelsBoxOffersTheStartingModelsBack() {
        show()

        val label = "Alt text models, one per line"
        assertTrue(compose.onAllNodesWithText("Show the starting models").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText(label).performScrollTo().performTextReplacement("")
        compose.onNodeWithText("Show the starting models").performScrollTo().performClick()

        assertTrue(compose.onAllNodesWithText("Show the starting models").fetchSemanticsNodes().isEmpty())
        assertTrue(repository.readSettings().ai.modelsFor(com.digitumdei.shotquill.shared.ai.AiOperation.AltText).isNotEmpty())
    }
}
