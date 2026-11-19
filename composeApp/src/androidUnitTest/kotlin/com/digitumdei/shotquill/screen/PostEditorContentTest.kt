package com.digitumdei.shotquill.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.digitumdei.shotquill.ScreenTestActivity
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.social.AccountSuggestion
import com.digitumdei.shotquill.shared.social.MentionCheck
import com.digitumdei.shotquill.shared.social.MentionStatus
import com.digitumdei.shotquill.shared.social.PublishTarget
import com.digitumdei.shotquill.speech.DictationState
import com.digitumdei.shotquill.ui.ShotQuillTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The editor as it is drawn, given a state: what appears on screen and what
 * each control reports back. Run by Robolectric, so no emulator is needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PostEditorContentTest {
    @get:Rule(order = 0)
    val screenTestActivity = ScreenTestActivity()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val captions = mutableListOf<String>()
    private val tagEdits = mutableListOf<Pair<PublishTarget, String>>()
    private val blueskyTexts = mutableListOf<String>()
    private val published = mutableListOf<PublishTarget>()
    private val opened = mutableListOf<String>()
    private val suggestions = mutableMapOf<MentionField, List<AccountSuggestion>>()
    private val picked = mutableListOf<Pair<MentionField, AccountSuggestion>>()
    private var removed = 0
    private var shared = 0

    private val bluesky = PublishTargetState(PublishTarget.Bluesky, account = "@doctrinebrewing.co.za")
    private val instagram = PublishTargetState(PublishTarget.Instagram, account = "@doctrinebrewing")

    private fun loaded(
        caption: String = "",
        altText: String = "",
        targets: List<PublishTargetState> = emptyList(),
        tags: Map<PublishTarget, String> = emptyMap(),
        shared: Boolean = false,
    ) = PostEditorState(
        isLoaded = true,
        originalUri = "file:///photos/one.jpg",
        imageSize = PixelSize(4000, 3000),
        caption = caption,
        altText = altText,
        targets = targets,
        tags = tags,
        shared = shared,
    )

    private fun show(
        state: PostEditorState,
        instagramMentions: List<MentionCheck> = emptyList(),
        blueskyMentions: List<MentionCheck> = emptyList(),
    ) {
        compose.setContent {
            // What is typed is kept, as the view model would keep it, so each
            // box behaves as it does in the app.
            var current by remember { mutableStateOf(state) }
            ShotQuillTheme {
                PostEditorContent(
                    state = current,
                    instagramMentions = instagramMentions,
                    blueskyMentions = blueskyMentions,
                    suggestionsFor = { suggestions[it].orEmpty() },
                    onApplySuggestion = { field, suggestion -> picked += field to suggestion },
                    onOpenLink = { opened += it },
                    onBack = {},
                    onOpenSettings = {},
                    dictationAvailable = false,
                    dictationState = DictationState.Idle,
                    onToggleDictation = {},
                    onCaptionChange = {
                        captions += it
                        current = current.copy(caption = it)
                    },
                    onAltTextChange = {},
                    onBlueskyTextChange = {
                        blueskyTexts += it
                        current = current.copy(blueskyText = it)
                    },
                    onTagsChange = { target, tags ->
                        tagEdits += target to tags
                        current = current.copy(tags = current.tags + (target to tags))
                    },
                    onPublish = { published += it },
                    onSelectAltTextModel = {},
                    onCopyAltText = {},
                    onWriteAltText = {},
                    onAddSnippet = {},
                    onSelectShape = {},
                    onPanZoom = { _, _, _ -> },
                    onLookEnabled = {},
                    onLogoEnabled = {},
                    onLogoCorner = {},
                    onDismissMessage = {},
                    onShare = { shared++ },
                    onRemove = { removed++ },
                )
            }
        }
    }

    @Test
    fun aNewPostShowsItsTitleCaptionAndAShareButton() {
        show(loaded(caption = "First pour of the witbier."))

        compose.onNodeWithText("New post").assertIsDisplayed()
        compose.onNodeWithText("First pour of the witbier.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Share").assertIsDisplayed().assertIsEnabled().performClick()
        assertEquals(1, shared)
    }

    @Test
    fun aSharedPostSaysSoAndOffersToShareAgain() {
        show(loaded(shared = true))

        compose.onNodeWithText("Shared post").assertIsDisplayed()
        compose.onNodeWithText("Share again").assertIsDisplayed()
    }

    @Test
    fun typingInTheCaptionAndTheHashtagBoxIsReported() {
        show(loaded())

        compose.onNodeWithText("Say it in your own words").performScrollTo().performTextInput("Say hello to Lager Thing!")
        compose.onNodeWithText("Instagram hashtags").performScrollTo().performTextInput("#craftbeer")

        assertEquals(listOf("Say hello to Lager Thing!"), captions)
        assertEquals(listOf(PublishTarget.Instagram to "#craftbeer"), tagEdits)
    }

    @Test
    fun theBlueskyBoxesOnlyAppearOnceBlueskyIsSetUp() {
        show(loaded(caption = "Hello"))
        assertTrue(compose.onAllNodesWithText("Bluesky hashtags").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Bluesky").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun withBlueskySetUpItsTextTagsAndLengthAreShown() {
        show(loaded(caption = "Hello", targets = listOf(bluesky), tags = mapOf(PublishTarget.Bluesky to "#beersky")))

        compose.onNodeWithText("Bluesky hashtags").performScrollTo().assertIsDisplayed()
        // "Hello", a blank line, then the tag: 5 + 2 + 8 characters.
        compose.onNodeWithText("15 / 300", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("using the caption", substring = true).assertExists()

        compose.onNodeWithText("Leave empty to post the caption as it is").performScrollTo().performTextInput("Short one")
        assertEquals(listOf("Short one"), blueskyTexts)
    }

    @Test
    fun mentionChecksAreShownAndAFoundAccountOpensItsProfile() {
        show(
            loaded(targets = listOf(bluesky)),
            instagramMentions = listOf(
                MentionCheck(
                    name = "some_friend",
                    status = MentionStatus.CannotCheck,
                    note = "Instagram gives apps no way to check this. Tap to look at the profile.",
                    profileUrl = "https://www.instagram.com/some_friend/",
                ),
            ),
            blueskyMentions = listOf(
                MentionCheck(
                    name = "doctrinebrewing.co.za",
                    status = MentionStatus.Found,
                    note = "Dion",
                    profileUrl = "https://bsky.app/profile/doctrinebrewing.co.za",
                ),
                MentionCheck("nobody.example.com", MentionStatus.NotFound, "No Bluesky account has this handle."),
                MentionCheck("later.bsky.social", MentionStatus.Checking, "Checking"),
            ),
        )

        compose.onNodeWithText("@some_friend", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("no way to check this", substring = true).assertExists()
        compose.onNodeWithText("@nobody.example.com", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("No Bluesky account has this handle", substring = true).assertExists()
        compose.onNodeWithText("@later.bsky.social", substring = true).assertExists()

        compose.onNodeWithText("✓ @doctrinebrewing.co.za", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("@some_friend", substring = true).performScrollTo().performClick()
        // A line with nothing to open does nothing when tapped.
        compose.onNodeWithText("@nobody.example.com", substring = true).performScrollTo().performClick()

        assertEquals(
            listOf("https://bsky.app/profile/doctrinebrewing.co.za", "https://www.instagram.com/some_friend/"),
            opened,
        )
    }

    @Test
    fun accountsOfferedForAMentionBeingTypedCanBePicked() {
        suggestions[MentionField.BlueskyText] = listOf(
            AccountSuggestion("doctrinebrewing.co.za", "Dion"),
            AccountSuggestion("doctor.bsky.social"),
        )
        suggestions[MentionField.Caption] = listOf(AccountSuggestion("some_friend"))
        show(loaded(targets = listOf(bluesky)))

        compose.onNodeWithText("@doctrinebrewing.co.za  \u00B7  Dion").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("@some_friend").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("@doctor.bsky.social").assertExists()

        assertEquals(
            listOf(
                MentionField.BlueskyText to AccountSuggestion("doctrinebrewing.co.za", "Dion"),
                MentionField.Caption to AccountSuggestion("some_friend"),
            ),
            picked,
        )
    }

    @Test
    fun postingAsksFirstAndWarnsAboutAMentionThatWillNotLinkAndMissingAltText() {
        show(
            loaded(caption = "With @bskybeer", targets = listOf(instagram, bluesky)),
            blueskyMentions = listOf(
                MentionCheck("bskybeer", MentionStatus.Problem, "Bluesky needs the whole handle."),
                MentionCheck("doctrinebrewing.co.za", MentionStatus.Found, "Dion"),
            ),
        )

        compose.onNodeWithText("Bluesky").performClick()

        compose.onNodeWithText("Post to Bluesky?").assertIsDisplayed()
        compose.onNodeWithText("This goes out publicly as @doctrinebrewing.co.za.", substring = true).assertExists()
        compose.onNodeWithText("There is no alt text yet.", substring = true).assertExists()
        compose.onNodeWithText("Look at @bskybeer first", substring = true).assertExists()
        assertTrue(published.isEmpty())

        compose.onNodeWithText("Post").performClick()

        assertEquals(listOf(PublishTarget.Bluesky), published)
        assertTrue(compose.onAllNodesWithText("Post to Bluesky?").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aPostWithAltTextAndNoDoubtfulMentionsIsConfirmedWithoutWarnings() {
        show(loaded(caption = "Hello", altText = "A glass of beer.", targets = listOf(instagram)))

        compose.onNodeWithText("Instagram").performClick()

        compose.onNodeWithText("Post to Instagram?").assertIsDisplayed()
        compose.onNodeWithText("This goes out publicly as @doctrinebrewing.", substring = true).assertExists()
        assertTrue(compose.onAllNodesWithText("no alt text", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Look at", substring = true).fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText("Not yet").performClick()
        assertTrue(published.isEmpty())
    }

    @Test
    fun removingAPostAsksFirst() {
        show(loaded())

        compose.onNodeWithContentDescription("Remove post").performClick()
        compose.onNodeWithText("Remove this post?").assertIsDisplayed()
        compose.onNodeWithText("Keep").performClick()
        assertEquals(0, removed)

        compose.onNodeWithContentDescription("Remove post").performClick()
        compose.onNodeWithText("Remove").performClick()
        assertEquals(1, removed)
    }
}
