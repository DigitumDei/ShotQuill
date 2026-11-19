package com.digitumdei.shotquill.screen

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.digitumdei.shotquill.ScreenTestActivity
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.post.EditablePost
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import com.digitumdei.shotquill.shared.social.AccountLookup
import com.digitumdei.shotquill.shared.social.PublishTarget
import com.digitumdei.shotquill.ui.ShotQuillTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The editor screen wired to its view model: loading a post, saving what is
 * typed once typing pauses, and looking up mentioned accounts. The store,
 * renderer and publisher are stand-ins, so nothing touches disk or network.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostEditorScreenTest {
    @get:Rule(order = 0)
    val screenTestActivity = ScreenTestActivity()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val draftId = PostDraftId("draft-1")
    private val photoUri = "file:///photos/one.jpg"
    private val store = FakeStore().apply {
        post = EditablePost(
            id = PostDraftId("draft-1"),
            originalUri = "file:///photos/one.jpg",
            caption = "Loaded caption",
            altText = "",
            treatment = null,
            renderedUri = null,
            shared = false,
        )
    }
    private val renderer = FakeRenderer().apply { sizes["file:///photos/one.jpg"] = PixelSize(4000, 3000) }
    private val settings = InMemoryLocalSettingsRepository(
        LocalAppSettings().withUsualTags(PublishTarget.Bluesky, "#craftbeer #bskybeer"),
    )
    private val bluesky = FakePublisher()

    private fun show() {
        compose.setContent {
            ShotQuillTheme {
                PostEditorScreen(
                    draftId = draftId,
                    store = store,
                    settingsRepository = settings,
                    renderer = renderer,
                    clipboardWriter = RecordingClipboard(),
                    shareLauncher = RecordingShareLauncher(),
                    dictation = null,
                    altTextWriter = null,
                    publishers = listOf(bluesky),
                    onBack = {},
                    onOpenSettings = {},
                )
            }
        }
        // The post is read off the main thread, so it appears a moment after the screen does.
        waitFor("the post to load") { compose.onAllNodesWithText("Loaded caption").fetchSemanticsNodes().isNotEmpty() }
    }

    /** Lets pauses in the screen run out and background work finish, until [done]. */
    private fun waitFor(what: String, done: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.mainClock.advanceTimeBy(250)
                done()
            }
        } catch (e: Exception) {
            throw AssertionError("Timed out waiting for $what", e)
        }
    }

    @Test
    fun aLoadedPostShowsItsCaptionAndTheUsualTagsForEachService() {
        show()

        compose.onNodeWithText("Loaded caption").performScrollTo().assertExists()
        compose.onNodeWithText("#craftbeer #bskybeer").performScrollTo().assertExists()
        assertTrue(store.savedTags.isEmpty())
    }

    @Test
    fun aTypedCaptionIsSavedOnceTypingPauses() {
        show()

        compose.onNodeWithText("Loaded caption").performScrollTo().performTextReplacement("Say hello to Lager Thing!")
        compose.onNodeWithText("Say hello to Lager Thing!").assertExists()

        waitFor("the caption to be saved") { store.savedCaptions.contains("Say hello to Lager Thing!") }
    }

    @Test
    fun changedHashtagsAreSavedForTheServiceTheyBelongTo() {
        show()

        compose.onNodeWithText("#craftbeer #bskybeer").performScrollTo().performTextReplacement("#beersky")

        waitFor("the tags to be saved") { store.savedTags.contains(PublishTarget.Bluesky to "#beersky") }
        assertTrue(store.savedTags.none { it.first == PublishTarget.Instagram })
    }

    @Test
    fun aMentionedBlueskyAccountIsLookedUpAndItsNameShown() {
        bluesky.accounts["doctrinebrewing.co.za"] = AccountLookup.Found("doctrinebrewing.co.za", "Dion")
        show()

        compose.onNodeWithText("Leave empty to post the caption as it is")
            .performScrollTo()
            .performTextReplacement("Cheers @doctrinebrewing.co.za and @nobody.example.com")

        waitFor("both mentions to be checked") {
            compose.onAllNodesWithText("Dion", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("No Bluesky account has this handle", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf("doctrinebrewing.co.za", "nobody.example.com"), bluesky.lookedUp)
    }
}
