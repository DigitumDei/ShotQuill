package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.image.RenderOutcome
import com.digitumdei.shotquill.image.RenderRequest
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.share.ShareResult
import com.digitumdei.shotquill.shared.ai.AiHttpResponse
import com.digitumdei.shotquill.shared.ai.AiHttpTransport
import com.digitumdei.shotquill.shared.ai.AiOperation
import com.digitumdei.shotquill.shared.ai.AiSettings
import com.digitumdei.shotquill.shared.ai.AltTextWriter
import com.digitumdei.shotquill.shared.ai.ChatCompletionsClient
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.post.EditablePost
import com.digitumdei.shotquill.shared.post.PostEditorStore
import com.digitumdei.shotquill.shared.post.PostSummary
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import com.digitumdei.shotquill.shared.social.AccountLookup
import com.digitumdei.shotquill.shared.social.AccountSuggestion
import com.digitumdei.shotquill.shared.social.MentionStatus
import com.digitumdei.shotquill.shared.social.PhotoTag
import com.digitumdei.shotquill.shared.social.PostPublisher
import com.digitumdei.shotquill.shared.social.PostToPublish
import com.digitumdei.shotquill.shared.social.PublishResult
import com.digitumdei.shotquill.shared.social.PublishTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostEditorViewModelTest {
    private val draftId = PostDraftId("draft-1")
    private val photoUri = "file:///photos/one.jpg"
    private val logoUri = "file:///brand/logo.png"

    private val store = FakeStore()
    private val renderer = FakeRenderer()
    private val clipboard = RecordingClipboard()
    private val shareLauncher = RecordingShareLauncher()
    private val settings = InMemoryLocalSettingsRepository(
        LocalAppSettings(
            defaultCropShape = CropShape.Square,
            look = LookSettings(warmth = 0.5f, contrast = 0f, saturation = 0f, fade = 0f),
            logo = LogoSettings(uri = logoUri, corner = LogoCorner.BottomStart),
            snippets = listOf(CaptionSnippet("18+", "Not for persons under the age of 18.")),
        ),
    )

    private val aiTransport = FakeAiTransport()
    private val bluesky = FakePublisher()
    private val instagram = FakePublisher(PublishTarget.Instagram, account = "@doctrinebrewing").apply { configured = false }
    private var withAi = false

    /** Saves a key and two models, so the view models made afterwards can ask for alt text. */
    private fun setUpAi() {
        settings.saveSettings(
            settings.readSettings().copy(
                ai = AiSettings().withModels(AiOperation.AltText, listOf("cheap/describer", "better/describer")),
            ),
        )
        settings.saveOpenAiApiKey("key-abcdef-123456")
        withAi = true
    }

    private fun viewModel(): PostEditorViewModel =
        PostEditorViewModel(
            draftId = draftId,
            store = store,
            settingsRepository = settings,
            renderer = renderer,
            clipboardWriter = clipboard,
            shareLauncher = shareLauncher,
            clock = object : EpochClock {
                override fun nowMillis(): Long = 1_700_000_000_000L
            },
            altTextWriter = if (withAi) AltTextWriter(settings, ChatCompletionsClient(aiTransport)) else null,
            publishers = listOf(instagram, bluesky),
        )

    private fun loadedViewModel(): PostEditorViewModel {
        store.post = EditablePost(draftId, photoUri, caption = "", altText = "", treatment = null, renderedUri = null, shared = false)
        renderer.sizes[photoUri] = PixelSize(4000, 3000)
        renderer.sizes[logoUri] = PixelSize(600, 200)
        return viewModel().also { it.load() }
    }

    @Test
    fun aNewPostStartsFromTheSettings() {
        val state = loadedViewModel().state

        assertTrue(state.isLoaded)
        assertEquals(photoUri, state.originalUri)
        assertEquals(PixelSize(4000, 3000), state.imageSize)
        assertEquals(CropShape.Square, state.treatment.cropShape)
        assertTrue(state.treatment.lookEnabled)
        assertTrue(state.treatment.logoEnabled)
        assertEquals(LogoCorner.BottomStart, state.logoCorner)
        assertEquals(PixelSize(600, 200), state.logoSize)
        assertTrue(state.hasLogo)
        assertTrue(state.canShare)
        assertNull(state.message)
    }

    @Test
    fun withoutALogoThereIsNothingToStampButTheSwitchStaysOnForLater() {
        settings.saveSettings(settings.readSettings().copy(logo = LogoSettings()))

        val state = loadedViewModel().state

        assertFalse(state.hasLogo)
        assertTrue(state.treatment.logoEnabled)
        assertNull(state.logoSize)
    }

    @Test
    fun anExistingPostKeepsItsOwnTreatmentAndCaption() {
        val saved = PhotoTreatment(cropShape = CropShape.Story, zoom = 2f, logoCorner = LogoCorner.TopEnd)
        store.post = EditablePost(draftId, photoUri, caption = "Bottling day", altText = "", treatment = saved, renderedUri = "file:///edited/old.jpg", shared = true)
        renderer.sizes[photoUri] = PixelSize(3000, 4000)

        val state = viewModel().also { it.load() }.state

        assertEquals(saved, state.treatment)
        assertEquals("Bottling day", state.caption)
        assertEquals(LogoCorner.TopEnd, state.logoCorner)
        assertTrue(state.shared)
    }

    @Test
    fun aMissingPostIsReported() {
        val vm = viewModel().also { it.load() }

        assertTrue(vm.state.isMissing)
        assertFalse(vm.state.canShare)
        assertNotNull(vm.state.message)
    }

    @Test
    fun anUnreadablePhotoCannotBeShared() {
        store.post = EditablePost(draftId, photoUri, caption = "", altText = "", treatment = null, renderedUri = null, shared = false)

        val vm = viewModel().also { it.load() }
        vm.share()

        assertFalse(vm.state.canShare)
        assertEquals("The photo could not be read.", vm.state.message)
        assertTrue(renderer.requests.isEmpty())
    }

    @Test
    fun nothingChangesBeforeThePostHasLoaded() {
        val vm = viewModel()

        vm.updateCaption("Too early")
        vm.addSnippet(CaptionSnippet("x", "y"))
        vm.addDictation("spoken too early")
        vm.updateAltText("too early")
        vm.selectAltTextModel("any/model")
        vm.writeAltText()
        vm.copyAltText()
        vm.persistAltText()
        vm.updateBlueskyText("too early")
        vm.persistBlueskyText()
        vm.updateTags(PublishTarget.Bluesky, "#early")
        vm.persistTags()
        vm.publish(PublishTarget.Bluesky)
        vm.selectShape(CropShape.Story)
        vm.panZoom(0.5f, 0.5f, 2f)
        vm.setLookEnabled(false)
        vm.setLogoEnabled(false)
        vm.setLogoCorner(LogoCorner.TopStart)
        vm.persistCaption()
        vm.persistTreatment()
        vm.share()

        assertEquals(PostEditorState(), vm.state)
        assertTrue(store.savedCaptions.isEmpty())
        assertTrue(store.savedTreatments.isEmpty())
    }

    @Test
    fun captionEditsAreKeptAndCounted() {
        val vm = loadedViewModel()

        vm.updateCaption("Brew day #doctrine")
        vm.addSnippet(CaptionSnippet("18+", "Not for persons under the age of 18."))
        vm.persistCaption()

        assertEquals("Brew day #doctrine\n\nNot for persons under the age of 18.", vm.state.caption)
        assertEquals(1, vm.state.captionStats.hashtags)
        assertEquals(listOf(vm.state.caption), store.savedCaptions)
    }

    @Test
    fun dictatedPhrasesJoinTheCaptionAsSpoken() {
        val vm = loadedViewModel()

        vm.addDictation("first pour of the witbier.")
        vm.addDictation("it came out hazy")
        vm.persistCaption()

        assertEquals("First pour of the witbier. It came out hazy", vm.state.caption)
        assertEquals(listOf(vm.state.caption), store.savedCaptions)
    }

    @Test
    fun withoutAnAiServiceAltTextIsTypedByHand() {
        val vm = loadedViewModel()

        vm.updateAltText("A glass of beer on a bar.")
        vm.writeAltText()
        vm.persistAltText()
        vm.copyAltText()

        assertFalse(vm.state.aiConfigured)
        assertFalse(vm.state.canWriteAltText)
        assertTrue(vm.state.altTextModels.isEmpty())
        assertEquals("A glass of beer on a bar.", vm.state.altText)
        assertEquals(listOf("A glass of beer on a bar."), store.savedAltTexts)
        assertEquals(listOf("alt text" to "A glass of beer on a bar."), clipboard.copies)
        assertEquals("Alt text copied.", vm.state.message)
        assertTrue(renderer.described.isEmpty())
    }

    @Test
    fun anExistingPostShowsItsSavedAltText() {
        store.post = EditablePost(
            draftId, photoUri, caption = "", altText = "Saved earlier.", treatment = null, renderedUri = null, shared = false,
        )
        renderer.sizes[photoUri] = PixelSize(4000, 3000)

        assertEquals("Saved earlier.", viewModel().also { it.load() }.state.altText)
    }

    @Test
    fun altTextIsWrittenFromTheCroppedPhotoWithTheFirstModel() {
        setUpAi()
        val vm = loadedViewModel()
        vm.updateCaption("First pour of the witbier.")
        vm.selectShape(CropShape.Portrait)
        aiTransport.reply = AiHttpResponse(200, """{"choices":[{"message":{"content":"A full glass of hazy beer."}}]}""")

        vm.writeAltText()

        assertTrue(vm.state.aiConfigured)
        assertEquals(listOf("cheap/describer", "better/describer"), vm.state.altTextModels)
        assertEquals("cheap/describer", vm.state.altTextModel)
        assertEquals("A full glass of hazy beer.", vm.state.altText)
        assertFalse(vm.state.isWritingAltText)
        assertNull(vm.state.message)
        assertEquals(listOf("A full glass of hazy beer."), store.savedAltTexts)
        assertEquals(listOf(photoUri to CropShape.Portrait), renderer.described)
        assertTrue(aiTransport.body.contains("\"model\":\"cheap/describer\""))
        assertTrue(aiTransport.body.contains("First pour of the witbier."))
    }

    @Test
    fun anotherModelFromTheListCanBeChosen() {
        setUpAi()
        val vm = loadedViewModel()

        vm.selectAltTextModel("not/listed")
        assertEquals("cheap/describer", vm.state.altTextModel)

        vm.selectAltTextModel("better/describer")
        vm.writeAltText()

        assertTrue(aiTransport.body.contains("\"model\":\"better/describer\""))
    }

    @Test
    fun anAiFailureKeepsWhatWasThereAndSaysWhy() {
        setUpAi()
        val vm = loadedViewModel()
        vm.updateAltText("Typed by hand.")
        aiTransport.reply = AiHttpResponse(402, """{"error":{"message":"Insufficient credits"}}""")

        vm.writeAltText()

        assertEquals("Typed by hand.", vm.state.altText)
        assertTrue(vm.state.message.orEmpty().contains("run out of credit"))
        assertFalse(vm.state.isWritingAltText)
        assertTrue(store.savedAltTexts.isEmpty())
    }

    @Test
    fun aPhotoThatCannotBePreparedIsNotSent() {
        setUpAi()
        val vm = loadedViewModel()
        renderer.descriptionBytes = null

        vm.writeAltText()

        assertEquals("The photo could not be prepared for describing.", vm.state.message)
        assertNull(aiTransport.url)
    }

    @Test
    fun sharingPutsTheAltTextBehindTheCaptionOnTheClipboard() {
        val vm = loadedViewModel()
        vm.updateCaption("First pour.")
        vm.updateAltText("A glass of beer.")

        vm.share()

        assertEquals(listOf("alt text" to "A glass of beer.", "post caption" to "First pour."), clipboard.copies)
        assertEquals(listOf("A glass of beer."), store.savedAltTexts)
        assertTrue(vm.state.message.orEmpty().contains("clipboard history"))
    }

    @Test
    fun sharingWithOnlyAltTextCopiesThat() {
        val vm = loadedViewModel()
        vm.updateAltText("A glass of beer.")

        vm.share()

        assertEquals(listOf("alt text" to "A glass of beer."), clipboard.copies)
        assertTrue(vm.state.message.orEmpty().contains("alt text is on the clipboard"))
    }

    @Test
    fun aFailedAltTextCopyIsReported() {
        val vm = loadedViewModel()
        vm.updateAltText("A glass of beer.")
        clipboard.failure = IllegalStateException("clipboard busy")

        vm.copyAltText()
        assertEquals("The alt text could not be copied.", vm.state.message)

        vm.share()
        assertTrue(vm.state.message.orEmpty().contains("copy it by hand"))
    }

    @Test
    fun aServiceThatIsNotSetUpIsNotOffered() {
        bluesky.configured = false

        val vm = loadedViewModel()
        vm.publish(PublishTarget.Bluesky)

        assertTrue(vm.state.targets.isEmpty())
        assertFalse(vm.state.hasBluesky)
        assertTrue(bluesky.posts.isEmpty())
    }

    @Test
    fun aNewPostStartsWithTheUsualTagsAndDoesNotStoreThemUntilChanged() {
        settings.saveSettings(
            settings.readSettings()
                .withUsualTags(PublishTarget.Bluesky, "#craftbeer #bskybeer")
                .withUsualTags(PublishTarget.Instagram, "#craftbeer #kznbeer"),
        )
        val vm = loadedViewModel()

        assertEquals("#craftbeer #bskybeer", vm.state.tagsFor(PublishTarget.Bluesky))
        assertEquals("#craftbeer #kznbeer", vm.state.tagsFor(PublishTarget.Instagram))
        vm.persistTags()
        assertTrue(store.savedTags.isEmpty())

        vm.updateTags(PublishTarget.Bluesky, "#beersky")
        vm.persistTags()
        vm.persistTags()
        assertEquals(listOf(PublishTarget.Bluesky to "#beersky"), store.savedTags)
    }

    @Test
    fun tagsSetOnAPostWinOverTheUsualOnesEvenWhenEmpty() {
        settings.saveSettings(settings.readSettings().withUsualTags(PublishTarget.Bluesky, "#craftbeer"))
        store.post = EditablePost(
            draftId, photoUri, caption = "", altText = "", treatment = null, renderedUri = null, shared = false,
            tags = mapOf(PublishTarget.Bluesky to ""),
        )
        renderer.sizes[photoUri] = PixelSize(4000, 3000)

        assertEquals("", viewModel().also { it.load() }.state.tagsFor(PublishTarget.Bluesky))
    }

    @Test
    fun eachServiceGetsItsOwnTagsAfterItsOwnText() {
        instagram.configured = true
        val vm = loadedViewModel()
        vm.updateCaption("Say hello to Lager Thing!")
        vm.updateTags(PublishTarget.Instagram, "craftbeer kznbeer")
        vm.updateTags(PublishTarget.Bluesky, "#beersky @bskybeer.bsky.social")

        assertEquals("Say hello to Lager Thing!\n\n#craftbeer #kznbeer", vm.state.instagramPostText)
        assertEquals("Say hello to Lager Thing!\n\n#beersky @bskybeer.bsky.social", vm.state.blueskyPostText)
        assertEquals(2, vm.state.captionStats.hashtags)
        assertEquals(vm.state.instagramPostText.length, vm.state.captionStats.characters)

        vm.publish(PublishTarget.Bluesky)
        vm.publish(PublishTarget.Instagram)
        assertEquals(vm.state.blueskyPostText, bluesky.posts.single().text)
        assertEquals(vm.state.instagramPostText, instagram.posts.single().text)
        assertEquals(
            setOf(PublishTarget.Instagram to "craftbeer kznbeer", PublishTarget.Bluesky to "#beersky @bskybeer.bsky.social"),
            store.savedTags.toSet(),
        )

        vm.share()
        assertEquals("post caption" to "Say hello to Lager Thing!\n\n#craftbeer #kznbeer", clipboard.copies.last())
        assertEquals("Say hello to Lager Thing!\n\n#craftbeer #kznbeer", shareLauncher.shares.last().second)
    }

    @Test
    fun tagsTypedWhileAnEarlierSaveIsRunningAreStillSavedAfterwards() {
        val vm = loadedViewModel()
        vm.updateTags(PublishTarget.Bluesky, "#beersky")
        // Typing carries on while the first save is being written.
        store.whileSavingTags = { vm.updateTags(PublishTarget.Bluesky, "#beersky #craftbeer") }

        vm.persistTags()
        store.whileSavingTags = null
        assertEquals(listOf(PublishTarget.Bluesky to "#beersky"), store.savedTags)

        vm.persistTags()
        assertEquals(
            listOf(PublishTarget.Bluesky to "#beersky", PublishTarget.Bluesky to "#beersky #craftbeer"),
            store.savedTags,
        )
    }

    @Test
    fun blueskyTagsCountTowardsItsLimit() {
        val vm = loadedViewModel()
        vm.updateBlueskyText("a".repeat(295))
        assertFalse(vm.state.blueskyTooLong)

        vm.updateTags(PublishTarget.Bluesky, "#beersky")

        assertTrue(vm.state.blueskyTooLong)
        assertEquals(295 + 2 + 8, vm.state.blueskyLength)
    }

    @Test
    fun blueskyMentionsAreLookedUpOnceAndReportedAsFoundOrNot() {
        bluesky.accounts["doctrinebrewing.co.za"] = AccountLookup.Found("doctrinebrewing.co.za", "Dion")
        val vm = loadedViewModel()
        vm.updateBlueskyText("Cheers @doctrinebrewing.co.za and @nobody.example.com")
        vm.updateTags(PublishTarget.Bluesky, "#beersky @bskybeer")

        assertEquals(
            listOf(MentionStatus.Checking, MentionStatus.Checking, MentionStatus.Checking),
            vm.mentionChecks(PublishTarget.Bluesky).map { it.status },
        )

        vm.checkMentions()

        val checks = vm.mentionChecks(PublishTarget.Bluesky)
        assertEquals(listOf("doctrinebrewing.co.za", "nobody.example.com", "bskybeer"), checks.map { it.name })
        assertEquals(
            listOf(MentionStatus.Found, MentionStatus.NotFound, MentionStatus.Problem),
            checks.map { it.status },
        )
        assertEquals("Dion", checks[0].note)
        assertEquals(
            listOf("doctrinebrewing.co.za", "nobody.example.com", "bskybeer.bsky.social"),
            bluesky.lookedUp,
        )

        vm.updateBlueskyText("Cheers @doctrinebrewing.co.za and @nobody.example.com again")
        vm.checkMentions()
        assertEquals(3, bluesky.lookedUp.size)
    }

    @Test
    fun aMentionThatCouldNotBeCheckedIsTriedAgain() {
        val vm = loadedViewModel()
        vm.updateBlueskyText("Cheers @doctrinebrewing.co.za")
        bluesky.lookupFailure = IllegalStateException("no connection")

        vm.checkMentions()
        assertEquals(MentionStatus.Unreachable, vm.mentionChecks(PublishTarget.Bluesky).single().status)

        bluesky.lookupFailure = null
        bluesky.accounts["doctrinebrewing.co.za"] = AccountLookup.Found("doctrinebrewing.co.za")
        vm.checkMentions()
        assertEquals(MentionStatus.Found, vm.mentionChecks(PublishTarget.Bluesky).single().status)
    }

    @Test
    fun blueskyMentionsFallBackToTheCaptionAndNeedBlueskySetUp() {
        val vm = loadedViewModel()
        vm.updateCaption("With @doctrinebrewing.co.za")
        vm.checkMentions()
        assertEquals(listOf("doctrinebrewing.co.za"), bluesky.lookedUp)
        assertEquals(MentionStatus.NotFound, vm.mentionChecks(PublishTarget.Bluesky).single().status)

        bluesky.configured = false
        bluesky.lookedUp.clear()
        val withoutBluesky = loadedViewModel()
        withoutBluesky.updateCaption("With @someone.example.com")
        withoutBluesky.checkMentions()
        assertTrue(bluesky.lookedUp.isEmpty())
        assertTrue(withoutBluesky.mentionChecks(PublishTarget.Bluesky).isEmpty())

        val unloaded = viewModel()
        unloaded.checkMentions()
        assertTrue(unloaded.mentionChecks(PublishTarget.Instagram).isEmpty())
        assertTrue(bluesky.lookedUp.isEmpty())
    }

    @Test
    fun instagramNamesAreNotLookedUpUntilInstagramIsSetUp() {
        val vm = loadedViewModel()
        vm.updateCaption("Brewed with @some_friend")
        vm.updateTags(PublishTarget.Instagram, "#craftbeer @craft-beer")

        vm.checkMentions()

        val checks = vm.mentionChecks(PublishTarget.Instagram)
        assertEquals(listOf("some_friend", "craft-beer"), checks.map { it.name })
        assertEquals(listOf(MentionStatus.CannotCheck, MentionStatus.Problem), checks.map { it.status })
        assertEquals("https://www.instagram.com/some_friend/", checks[0].profileUrl)
        assertTrue(instagram.lookedUp.isEmpty())
    }

    @Test
    fun aMentionBeingTypedIsOfferedNamesUsedBeforeAndBlueskysOwnSuggestions() {
        settings.saveSettings(
            settings.readSettings().copy(
                mentionHistory = mapOf(
                    PublishTarget.Bluesky to listOf("doctrinebrewing.co.za"),
                    PublishTarget.Instagram to listOf("some_friend", "doctrine_fan"),
                ),
            ),
        )
        bluesky.suggestions["doc"] = listOf(AccountSuggestion("doctor.bsky.social", "A Doctor"))
        val vm = loadedViewModel()
        assertTrue(MentionField.entries.all { vm.suggestionsFor(it).isEmpty() })

        vm.updateBlueskyText("Cheers @Doc")
        // Names used before are there at once; Bluesky's own arrive after it has been asked.
        assertEquals(listOf("doctrinebrewing.co.za"), vm.suggestionsFor(MentionField.BlueskyText).map { it.handle })
        vm.fetchSuggestions()
        vm.fetchSuggestions()
        assertEquals(listOf("doc"), bluesky.suggestedFor)
        assertEquals(
            listOf(AccountSuggestion("doctrinebrewing.co.za"), AccountSuggestion("doctor.bsky.social", "A Doctor")),
            vm.suggestionsFor(MentionField.BlueskyText),
        )

        vm.applySuggestion(MentionField.BlueskyText, AccountSuggestion("doctor.bsky.social", "A Doctor"))
        assertEquals("Cheers @doctor.bsky.social ", vm.state.blueskyText)
        assertTrue(vm.suggestionsFor(MentionField.BlueskyText).isEmpty())

        // Instagram's boxes are offered Instagram names, and nothing is asked of any service for them.
        vm.updateCaption("With @doc")
        vm.fetchSuggestions()
        assertEquals(listOf("doctrine_fan"), vm.suggestionsFor(MentionField.Caption).map { it.handle })
        vm.applySuggestion(MentionField.Caption, AccountSuggestion("doctrine_fan"))
        assertEquals("With @doctrine_fan ", vm.state.caption)
        assertEquals(listOf("doc"), bluesky.suggestedFor)

        vm.updateTags(PublishTarget.Instagram, "#craftbeer @")
        assertEquals(listOf("some_friend", "doctrine_fan"), vm.suggestionsFor(MentionField.InstagramTags).map { it.handle })
        vm.applySuggestion(MentionField.InstagramTags, AccountSuggestion("some_friend"))
        assertEquals("#craftbeer @some_friend ", vm.state.tagsFor(PublishTarget.Instagram))
        vm.updateTags(PublishTarget.Bluesky, "@doctr")
        vm.applySuggestion(MentionField.BlueskyTags, AccountSuggestion("doctrinebrewing.co.za"))
        assertEquals("@doctrinebrewing.co.za ", vm.state.tagsFor(PublishTarget.Bluesky))
    }

    @Test
    fun whoWasMentionedInAPostIsRememberedForNextTime() {
        instagram.configured = true
        instagram.past = listOf("from_old_captions", "some_friend")
        val vm = loadedViewModel()
        vm.loadMentionHistory()
        vm.updateCaption("Brewed with @Some_Friend")
        vm.updateBlueskyText("Brewed with @friend.bsky.social and @bare")

        vm.publish(PublishTarget.Bluesky)
        vm.share()

        val saved = settings.readSettings()
        assertEquals(listOf("friend.bsky.social"), saved.mentionHistoryFor(PublishTarget.Bluesky))
        assertEquals(listOf("some_friend"), saved.mentionHistoryFor(PublishTarget.Instagram))
        // Offered next time: what was just used first, then what Instagram's old captions held.
        vm.updateTags(PublishTarget.Instagram, "@")
        assertEquals(
            listOf("some_friend", "from_old_captions"),
            vm.suggestionsFor(MentionField.InstagramTags).map { it.handle },
        )
        val reopened = loadedViewModel()
        reopened.updateBlueskyText("@fri")
        assertEquals(listOf("friend.bsky.social"), reopened.suggestionsFor(MentionField.BlueskyText).map { it.handle })
    }

    @Test
    fun anInstagramNameIsConfirmedWhenInstagramWillSay() {
        instagram.configured = true
        instagram.accounts["some_friend"] = AccountLookup.Found("some_friend", "Some Friend Brewing")
        val vm = loadedViewModel()
        vm.updateCaption("With @Some_Friend and @someone_else")

        vm.checkMentions()
        vm.checkMentions()

        val checks = vm.mentionChecks(PublishTarget.Instagram)
        assertEquals(listOf(MentionStatus.Found, MentionStatus.CannotCheck), checks.map { it.status })
        assertEquals("Some Friend Brewing", checks[0].note)
        assertEquals(listOf("some_friend", "someone_else"), instagram.lookedUp)
    }

    @Test
    fun peopleAreTaggedOnThePhotoMovedByATapAndRemoved() {
        instagram.configured = true
        val vm = loadedViewModel()
        assertTrue(vm.state.hasInstagram)

        assertFalse(vm.addPhotoTag("craft-beer"))
        assertEquals("That is not an Instagram name.", vm.state.message)
        assertTrue(vm.addPhotoTag(" @Some_Friend "))
        assertTrue(vm.addPhotoTag("another.one"))

        // A new tag starts in the middle and is the one the next tap moves.
        assertEquals(listOf(PhotoTag("some_friend"), PhotoTag("another.one")), vm.state.photoTags)
        assertEquals("another.one", vm.state.placingTag)
        vm.placePhotoTag(0.9f, 1.4f)
        assertEquals(PhotoTag("another.one", 0.9f, 1f), vm.state.photoTags[1])
        assertNull(vm.state.placingTag)

        // A tap with no tag chosen moves nothing.
        vm.placePhotoTag(0.1f, 0.1f)
        assertEquals(PhotoTag("some_friend"), vm.state.photoTags[0])
        vm.selectPhotoTag("some_friend")
        vm.placePhotoTag(0.1f, 0.2f)
        assertEquals(PhotoTag("some_friend", 0.1f, 0.2f), vm.state.photoTags[0])

        // Choosing the same tag twice lets go of it; adding a name already tagged just chooses it.
        vm.selectPhotoTag("some_friend")
        vm.selectPhotoTag("some_friend")
        assertNull(vm.state.placingTag)
        vm.selectPhotoTag("nobody")
        assertNull(vm.state.placingTag)
        assertTrue(vm.addPhotoTag("some_friend"))
        assertEquals(2, vm.state.photoTags.size)
        assertEquals("some_friend", vm.state.placingTag)

        vm.removePhotoTag("some_friend")
        assertEquals(listOf("another.one"), vm.state.photoTags.map { it.username })
        assertNull(vm.state.placingTag)

        vm.persistInstagramExtras()
        assertEquals(listOf(PhotoTag("another.one", 0.9f, 1f)) to "", store.savedExtras.last())
    }

    @Test
    fun tagsAndCollaboratorsGoToInstagramOnlyAndAreKeptWithThePost() {
        instagram.configured = true
        instagram.result = PublishResult.Success("https://www.instagram.com/p/ABC/", note = "Instagram left the photo tags off.")
        store.post = EditablePost(
            draftId, photoUri, caption = "Brewed together", altText = "", treatment = null, renderedUri = null,
            shared = false, photoTags = listOf(PhotoTag("some_friend", 0.3f, 0.6f)), collaborators = "@Some_Friend",
        )
        renderer.sizes[photoUri] = PixelSize(4000, 3000)
        val vm = viewModel().also { it.load() }
        assertEquals(listOf(PhotoTag("some_friend", 0.3f, 0.6f)), vm.state.photoTags)
        assertEquals("@Some_Friend", vm.state.collaborators)

        vm.updateCollaborators("a b c d")
        assertEquals("Instagram allows up to 3 collaborators and this has 4.", vm.state.collaboratorsProblem)
        vm.publish(PublishTarget.Instagram)
        assertEquals("Instagram allows up to 3 collaborators and this has 4.", vm.state.message)
        assertTrue(instagram.posts.isEmpty())

        vm.updateCollaborators("@Some_Friend, another.one")
        assertNull(vm.state.collaboratorsProblem)
        vm.publish(PublishTarget.Instagram)
        vm.publish(PublishTarget.Bluesky)

        val toInstagram = instagram.posts.single()
        assertEquals(listOf(PhotoTag("some_friend", 0.3f, 0.6f)), toInstagram.userTags)
        assertEquals(listOf("some_friend", "another.one"), toInstagram.collaborators)
        val toBluesky = bluesky.posts.single()
        assertTrue(toBluesky.userTags.isEmpty())
        assertTrue(toBluesky.collaborators.isEmpty())
        assertEquals(listOf(PhotoTag("some_friend", 0.3f, 0.6f)) to "@Some_Friend, another.one", store.savedExtras.last())
    }

    @Test
    fun whatInstagramLeftOffIsSaidAfterPosting() {
        instagram.configured = true
        instagram.result = PublishResult.Success("https://www.instagram.com/p/ABC/", note = "Instagram left the photo tags off.")
        val vm = loadedViewModel()

        vm.publish(PublishTarget.Instagram)

        assertEquals("Posted to Instagram as @doctrinebrewing. Instagram left the photo tags off.", vm.state.message)
    }

    @Test
    fun aPostCannotBeRemovedWhileItIsOnItsWay() {
        val vm = loadedViewModel()
        var stateDuringPost: PostEditorState? = null
        bluesky.whilePublishing = {
            vm.remove()
            stateDuringPost = vm.state
        }

        vm.publish(PublishTarget.Bluesky)

        // The removal asked for mid-post was refused, with a reason.
        assertFalse(store.removed)
        assertTrue(assertNotNull(stateDuringPost).isLoaded)
        assertEquals("Wait for this to finish before removing the post.", stateDuringPost?.message)
        // The post completed normally and is still there to be removed afterwards.
        assertEquals("Posted to Bluesky as @doctrine.bsky.social.", vm.state.message)
        assertNotNull(store.publishedTo)
        vm.remove()
        assertTrue(store.removed)
        assertTrue(vm.state.isMissing)
        // Asking again for a post that is already gone does nothing.
        vm.remove()
        assertNull(vm.state.message)
    }

    @Test
    fun aPostCannotBeRemovedWhileItsAltTextIsBeingWritten() {
        setUpAi()
        val vm = loadedViewModel()
        var removalMessage: String? = null
        aiTransport.whileDescribing = {
            vm.remove()
            removalMessage = vm.state.message
        }

        vm.writeAltText()

        assertEquals("Wait for this to finish before removing the post.", removalMessage)
        assertFalse(store.removed)
        assertEquals("A photo.", vm.state.altText)
        assertEquals(listOf("A photo."), store.savedAltTexts)
    }

    @Test
    fun theFramingCannotBeChangedWhileThePostIsOnItsWay() {
        val vm = loadedViewModel()
        val before = vm.state.treatment
        bluesky.whilePublishing = {
            vm.selectShape(CropShape.Story)
            vm.panZoom(0.2f, 0.2f, 1.5f)
            vm.setLookEnabled(!before.lookEnabled)
            vm.setLogoEnabled(!before.logoEnabled)
            vm.setLogoCorner(LogoCorner.TopStart)
            // Words are a different matter: they can still be typed.
            vm.updateCaption("Typed meanwhile")
        }

        vm.publish(PublishTarget.Bluesky)

        assertEquals(before, vm.state.treatment)
        assertEquals("Typed meanwhile", vm.state.caption)
        // Once it is out, the framing can be changed again.
        vm.selectShape(CropShape.Story)
        assertEquals(CropShape.Story, vm.state.treatment.cropShape)
    }

    @Test
    fun instagramGetsTheCaptionEvenWhenThereIsABlueskyText() {
        instagram.configured = true
        instagram.result = PublishResult.Success("https://www.instagram.com/p/ABC/")
        val vm = loadedViewModel()
        vm.updateCaption("  The full Instagram caption. #doctrine  ")
        vm.updateBlueskyText("Short Bluesky version.")

        vm.publish(PublishTarget.Instagram)

        assertEquals(listOf(PublishTarget.Instagram, PublishTarget.Bluesky), vm.state.targets.map { it.target })
        assertEquals("The full Instagram caption. #doctrine", instagram.posts.single().text)
        assertTrue(bluesky.posts.isEmpty())
        assertEquals("Posted to Instagram as @doctrinebrewing.", vm.state.message)
        assertEquals("https://www.instagram.com/p/ABC/", vm.state.targets.first().postedUrl)
        assertNull(vm.state.targets.last().postedUrl)
    }

    @Test
    fun aShapeTheServiceCannotTakeIsReportedAndNothingIsPosted() {
        instagram.configured = true
        instagram.imageProblem = "Instagram's posting service takes shapes from 4:5 to 1.91:1."
        val vm = loadedViewModel()

        vm.publish(PublishTarget.Instagram)

        assertEquals("Instagram's posting service takes shapes from 4:5 to 1.91:1.", vm.state.message)
        assertTrue(instagram.posts.isEmpty())
        assertEquals(listOf("file:///edited/draft-1-1700000000000.jpg"), renderer.discarded)
        assertFalse(vm.state.isPublishing)
    }

    @Test
    fun aConfiguredServiceIsOfferedWithItsAccountAndEarlierPost() {
        store.post = EditablePost(
            draftId, photoUri, caption = "", altText = "", treatment = null, renderedUri = null, shared = true,
            blueskyText = "Written for Bluesky.",
            published = mapOf(PublishTarget.Bluesky to "https://bsky.app/profile/doctrine/post/1"),
        )
        renderer.sizes[photoUri] = PixelSize(4000, 3000)

        val state = viewModel().also { it.load() }.state

        assertTrue(state.hasBluesky)
        val target = state.targets.single()
        assertEquals("@doctrine.bsky.social", target.account)
        assertEquals("https://bsky.app/profile/doctrine/post/1", target.postedUrl)
        assertEquals("Written for Bluesky.", state.blueskyText)
        assertEquals("Written for Bluesky.", state.blueskyPostText)
    }

    @Test
    fun postingToBlueskySendsTheRenderedImageTheCaptionAndTheAltText() {
        val vm = loadedViewModel()
        vm.updateCaption("  First pour.  ")
        vm.updateAltText(" A glass of beer. ")

        vm.publish(PublishTarget.Bluesky)

        val post = bluesky.posts.single()
        assertEquals("First pour.", post.text)
        assertEquals("A glass of beer.", post.altText)
        assertEquals(listOf<Byte>(7, 7), post.jpeg.toList())
        assertEquals(1080, post.imageWidth)
        assertEquals(1080, post.imageHeight)
        assertEquals(listOf("file:///edited/draft-1-1700000000000.jpg" to 950_000), renderer.fitted)
        assertEquals(
            Triple(PublishTarget.Bluesky, "https://bsky.app/profile/doctrine/post/new", "file:///edited/draft-1-1700000000000.jpg"),
            store.publishedTo,
        )
        val target = vm.state.targets.single()
        assertEquals("https://bsky.app/profile/doctrine/post/new", target.postedUrl)
        assertFalse(target.isPublishing)
        assertTrue(vm.state.shared)
        assertEquals("Posted to Bluesky as @doctrine.bsky.social.", vm.state.message)
        assertTrue(clipboard.copies.isEmpty())
        assertTrue(shareLauncher.shares.isEmpty())
        assertEquals(listOf("  First pour.  "), store.savedCaptions)
    }

    @Test
    fun textWrittenForBlueskyIsUsedInsteadOfTheCaption() {
        val vm = loadedViewModel()
        vm.updateCaption("A long Instagram caption with many hashtags.")
        vm.updateBlueskyText("Short and conversational.")
        vm.persistBlueskyText()

        vm.publish(PublishTarget.Bluesky)

        assertEquals("Short and conversational.", bluesky.posts.single().text)
        assertEquals(25, vm.state.blueskyLength)
        assertEquals("Short and conversational.", store.savedBlueskyTexts.last())
    }

    @Test
    fun aPostThatIsTooLongForBlueskyIsNotSent() {
        val vm = loadedViewModel()
        vm.updateCaption("a".repeat(301))

        vm.publish(PublishTarget.Bluesky)

        assertTrue(vm.state.blueskyTooLong)
        assertEquals("too long: 301", vm.state.message)
        assertTrue(bluesky.posts.isEmpty())
        assertTrue(renderer.requests.isEmpty())
    }

    @Test
    fun aFailedPostIsReportedAndItsImageDiscarded() {
        val vm = loadedViewModel()
        bluesky.result = PublishResult.Failure("Bluesky did not accept the handle or app password.")

        vm.publish(PublishTarget.Bluesky)

        assertEquals("Bluesky did not accept the handle or app password.", vm.state.message)
        assertNull(vm.state.targets.single().postedUrl)
        assertFalse(vm.state.shared)
        assertNull(store.publishedTo)
        assertEquals(listOf("file:///edited/draft-1-1700000000000.jpg"), renderer.discarded)
    }

    @Test
    fun problemsPreparingTheImageStopThePost() {
        val vm = loadedViewModel()
        renderer.outcome = RenderOutcome.Failure("The photo could not be opened.")
        vm.publish(PublishTarget.Bluesky)
        assertEquals("The photo could not be opened.", vm.state.message)

        renderer.outcome = null
        renderer.fittedBytes = null
        vm.publish(PublishTarget.Bluesky)
        assertEquals("The image could not be made small enough for Bluesky.", vm.state.message)
        assertTrue(bluesky.posts.isEmpty())
        assertFalse(vm.state.isPublishing)
    }

    @Test
    fun postingAgainReplacesTheEarlierImage() {
        store.post = EditablePost(
            draftId, photoUri, caption = "", altText = "", treatment = PhotoTreatment(),
            renderedUri = "file:///edited/old.jpg", shared = true,
        )
        renderer.sizes[photoUri] = PixelSize(4000, 3000)
        val vm = viewModel().also { it.load() }

        vm.publish(PublishTarget.Bluesky)

        assertEquals(listOf("file:///edited/old.jpg"), renderer.discarded)
    }

    @Test
    fun framingChangesAreKept() {
        val vm = loadedViewModel()

        vm.selectShape(CropShape.Portrait)
        vm.selectShape(CropShape.Portrait)
        vm.panZoom(panXFraction = 0.25f, panYFraction = 0f, zoomChange = 1f)
        vm.setLookEnabled(false)
        vm.setLogoEnabled(false)
        vm.setLogoCorner(LogoCorner.TopStart)
        vm.persistTreatment()

        val treatment = vm.state.treatment
        assertEquals(CropShape.Portrait, treatment.cropShape)
        assertTrue(treatment.centerX < 0.5f)
        assertFalse(treatment.lookEnabled)
        assertFalse(treatment.logoEnabled)
        assertEquals(LogoCorner.TopStart, treatment.logoCorner)
        assertEquals(listOf(treatment), store.savedTreatments)
    }

    @Test
    fun sharingRendersCopiesTheCaptionAndOpensTheShareSheet() {
        val vm = loadedViewModel()
        vm.updateCaption("  First pour.  ")

        vm.share()

        val request = renderer.requests.single()
        assertEquals(photoUri, request.sourceUri)
        assertEquals(LogoCorner.BottomStart, request.treatment.logoCorner)
        assertEquals(0.5f, request.look.warmth)
        assertEquals(logoUri, request.logo.uri)
        assertEquals("draft-1-1700000000000", request.outputName)
        assertEquals(listOf("post caption" to "First pour."), clipboard.copies)
        assertEquals(listOf<Pair<String?, String>>("file:///edited/draft-1-1700000000000.jpg" to "First pour."), shareLauncher.shares)
        assertEquals("file:///edited/draft-1-1700000000000.jpg", store.sharedWith)
        assertEquals(listOf("  First pour.  "), store.savedCaptions)
        assertTrue(vm.state.shared)
        assertFalse(vm.state.isSharing)
        assertTrue(vm.state.message.orEmpty().contains("clipboard"))
        assertTrue(renderer.discarded.isEmpty())
    }

    @Test
    fun sharingWithoutACaptionLeavesTheClipboardAlone() {
        val vm = loadedViewModel()

        vm.share()

        assertTrue(clipboard.copies.isEmpty())
        assertEquals("Image ready.", vm.state.message)
        assertTrue(vm.state.shared)
    }

    @Test
    fun aClipboardFailureDoesNotStopTheShare() {
        val vm = loadedViewModel()
        vm.updateCaption("Caption")
        clipboard.failure = IllegalStateException("clipboard busy")

        vm.share()

        assertEquals(1, shareLauncher.shares.size)
        assertTrue(vm.state.message.orEmpty().contains("copy it by hand"))
    }

    @Test
    fun sharingAgainReplacesTheEarlierImage() {
        store.post = EditablePost(draftId, photoUri, caption = "", altText = "", treatment = PhotoTreatment(), renderedUri = "file:///edited/old.jpg", shared = true)
        renderer.sizes[photoUri] = PixelSize(4000, 3000)
        val vm = viewModel().also { it.load() }

        vm.share()

        assertEquals(listOf("file:///edited/old.jpg"), renderer.discarded)
    }

    @Test
    fun aRenderFailureIsShownAndNothingIsShared() {
        val vm = loadedViewModel()
        renderer.outcome = RenderOutcome.Failure("The photo could not be opened.")

        vm.share()

        assertEquals("The photo could not be opened.", vm.state.message)
        assertFalse(vm.state.shared)
        assertTrue(shareLauncher.shares.isEmpty())
        assertNull(store.sharedWith)
    }

    @Test
    fun aShareSheetFailureDiscardsTheImage() {
        val vm = loadedViewModel()
        shareLauncher.result = ShareResult(success = false, errorMessage = "No app can receive images")

        vm.share()

        assertEquals("No app can receive images", vm.state.message)
        assertFalse(vm.state.shared)
        assertNull(store.sharedWith)
        assertEquals(listOf("file:///edited/draft-1-1700000000000.jpg"), renderer.discarded)

        shareLauncher.result = ShareResult(success = false)
        vm.share()
        assertEquals("The share sheet could not be opened.", vm.state.message)
    }

    @Test
    fun messagesCanBeDismissed() {
        val vm = loadedViewModel()
        vm.share()

        vm.dismissMessage()

        assertNull(vm.state.message)
    }

    @Test
    fun removingAPostDeletesItsImageAndStopsFurtherWrites() {
        store.post = EditablePost(draftId, photoUri, caption = "Old", altText = "", treatment = PhotoTreatment(), renderedUri = "file:///edited/old.jpg", shared = true)
        renderer.sizes[photoUri] = PixelSize(4000, 3000)
        val vm = viewModel().also { it.load() }

        vm.remove()
        vm.persistCaption()
        vm.persistTreatment()

        assertTrue(store.removed)
        assertEquals(listOf("file:///edited/old.jpg"), renderer.discarded)
        assertTrue(vm.state.isMissing)
        assertNull(vm.state.message)
        assertTrue(store.savedCaptions.isEmpty())
        assertTrue(store.savedTreatments.isEmpty())
    }
}

internal class FakeStore : PostEditorStore {
    var post: EditablePost? = null
    val savedCaptions = mutableListOf<String>()
    val savedTreatments = mutableListOf<PhotoTreatment>()
    val savedAltTexts = mutableListOf<String>()
    val savedBlueskyTexts = mutableListOf<String>()
    val savedTags = mutableListOf<Pair<PublishTarget, String>>()
    var whileSavingTags: (() -> Unit)? = null
    val savedExtras = mutableListOf<Pair<List<PhotoTag>, String>>()

    override fun saveInstagramExtras(id: PostDraftId, photoTags: List<PhotoTag>, collaborators: String) {
        savedExtras += photoTags to collaborators
    }
    var publishedTo: Triple<PublishTarget, String, String>? = null
    var sharedWith: String? = null
    var removed = false

    override fun listPosts(): List<PostSummary> = emptyList()

    override fun load(id: PostDraftId): EditablePost? = post

    override fun saveCaption(id: PostDraftId, caption: String) {
        savedCaptions += caption
    }

    override fun saveAltText(id: PostDraftId, altText: String) {
        // Like the real store, saving nothing over nothing is not recorded.
        if (altText.isNotEmpty() || savedAltTexts.isNotEmpty()) savedAltTexts += altText
    }

    override fun saveTreatment(id: PostDraftId, treatment: PhotoTreatment) {
        savedTreatments += treatment
    }

    override fun markShared(id: PostDraftId, renderedUri: String) {
        sharedWith = renderedUri
    }

    override fun saveBlueskyText(id: PostDraftId, text: String) {
        if (text.isNotEmpty() || savedBlueskyTexts.isNotEmpty()) savedBlueskyTexts += text
    }

    override fun saveTags(id: PostDraftId, target: PublishTarget, tags: String) {
        savedTags += target to tags
        whileSavingTags?.invoke()
    }

    override fun markPublished(id: PostDraftId, target: PublishTarget, url: String, renderedUri: String) {
        publishedTo = Triple(target, url, renderedUri)
    }

    override fun remove(id: PostDraftId) {
        removed = true
    }
}

internal class FakeRenderer : PhotoRenderer {
    val sizes = mutableMapOf<String, PixelSize>()
    val requests = mutableListOf<RenderRequest>()
    val discarded = mutableListOf<String>()
    val described = mutableListOf<Pair<String, CropShape>>()
    var descriptionBytes: ByteArray? = byteArrayOf(9, 9, 9)
    val fitted = mutableListOf<Pair<String, Int>>()
    var fittedBytes: ByteArray? = byteArrayOf(7, 7)
    var outcome: RenderOutcome? = null

    override fun orientedSize(uri: String): PixelSize? = sizes[uri]

    override fun render(request: RenderRequest): RenderOutcome {
        requests += request
        return outcome ?: RenderOutcome.Success("file:///edited/${request.outputName}.jpg", PixelSize(1080, 1080))
    }

    override fun encodeForDescription(sourceUri: String, treatment: PhotoTreatment, maxEdge: Int): ByteArray? {
        described += sourceUri to treatment.cropShape
        return descriptionBytes
    }

    override fun jpegWithin(uri: String, maxBytes: Int): ByteArray? {
        fitted += uri to maxBytes
        return fittedBytes
    }

    override fun discard(uri: String) {
        discarded += uri
    }
}

internal class FakePublisher(
    override val target: PublishTarget = PublishTarget.Bluesky,
    private val account: String = "@doctrine.bsky.social",
) : PostPublisher {
    var configured = true
    var result: PublishResult = PublishResult.Success("https://bsky.app/profile/doctrine/post/new")
    var imageProblem: String? = null
    val posts = mutableListOf<PostToPublish>()
    val accounts = mutableMapOf<String, AccountLookup>()
    val lookedUp = mutableListOf<String>()
    var lookupFailure: Exception? = null

    val suggestions = mutableMapOf<String, List<AccountSuggestion>>()
    val suggestedFor = mutableListOf<String>()
    var past: List<String> = emptyList()

    override fun suggestAccounts(typed: String): List<AccountSuggestion> {
        suggestedFor += typed
        return suggestions[typed].orEmpty()
    }

    override fun pastMentions(): List<String> = past

    /** Runs while a post is "on its way", standing in for the wait on the network. */
    var whilePublishing: (() -> Unit)? = null

    override val isConfigured get() = configured
    override val accountLabel get() = if (configured) account else null
    override val maxImageBytes = 950_000

    override fun problemWithImage(width: Int, height: Int): String? = imageProblem

    override fun check(): String = "checked"

    override fun findAccount(name: String): AccountLookup {
        lookedUp += name
        lookupFailure?.let { throw it }
        return accounts[name] ?: AccountLookup.NotFound
    }

    override fun problemWith(text: String): String? = if (text.length > 300) "too long: ${text.length}" else null

    override fun publish(post: PostToPublish): PublishResult {
        posts += post
        whilePublishing?.invoke()
        return result
    }
}

private class FakeAiTransport : AiHttpTransport {
    var reply = AiHttpResponse(200, """{"choices":[{"message":{"content":"A photo."}}]}""")
    var url: String? = null
    var body: String = ""

    /** Runs while the description is "on its way". */
    var whileDescribing: (() -> Unit)? = null

    override fun postJson(url: String, headers: Map<String, String>, body: String): AiHttpResponse {
        whileDescribing?.invoke()
        this.url = url
        this.body = body
        return reply
    }
}

internal class RecordingClipboard : ClipboardWriter {
    val copies = mutableListOf<Pair<String, String>>()
    var failure: Exception? = null

    override fun copy(label: String, text: String) {
        failure?.let { throw it }
        copies += label to text
    }
}

internal class RecordingShareLauncher : PostShareLauncher {
    val shares = mutableListOf<Pair<String?, String>>()
    var result = ShareResult(success = true)

    override fun share(imageUri: String?, text: String): ShareResult {
        shares += imageUri to text
        return result
    }
}
