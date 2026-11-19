package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.shared.ai.AiOperation
import com.digitumdei.shotquill.shared.ai.AiSettings
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.social.BlueskySettings
import com.digitumdei.shotquill.shared.social.FirebaseHostSettings
import com.digitumdei.shotquill.shared.social.InstagramSettings
import com.digitumdei.shotquill.shared.social.PublishTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest {
    private val repository = InMemoryLocalSettingsRepository()
    private val renderer = FakeRenderer().apply {
        sizes["file:///brand/logo.png"] = PixelSize(600, 200)
        sizes["file:///brand/new.png"] = PixelSize(300, 300)
    }

    private val now = 1_795_000_000_000L
    private val clock = object : EpochClock {
        override fun nowMillis(): Long = now
    }

    private fun viewModel() = SettingsViewModel(repository, renderer, clock)

    @Test
    fun startsFromWhatIsStored() {
        repository.saveSettings(
            LocalAppSettings(defaultCropShape = CropShape.Story, logo = LogoSettings(uri = "file:///brand/logo.png")),
        )

        val state = viewModel().state

        assertEquals(CropShape.Story, state.settings.defaultCropShape)
        assertEquals(PixelSize(600, 200), state.logoSize)
        assertFalse(state.hasApiKey)
        assertEquals(AiSettings.DefaultBaseUrl, state.settings.ai.baseUrl)
    }

    @Test
    fun lookChangesAreSavedAtOnceAndCanBeReset() {
        val vm = viewModel()
        val look = LookSettings(warmth = -0.4f, contrast = 0.3f, saturation = 0.2f, fade = 0.5f)

        vm.setLook(look)
        assertEquals(look, repository.readSettings().look)

        vm.resetLook()
        assertEquals(LookSettings(), repository.readSettings().look)
        assertEquals(LookSettings(), vm.state.settings.look)
    }

    @Test
    fun usualTagsAreSavedPerService() {
        val vm = viewModel()

        vm.setUsualTags(PublishTarget.Bluesky, "#craftbeer #bskybeer")
        vm.setUsualTags(PublishTarget.Instagram, "#craftbeer")
        assertEquals("#craftbeer #bskybeer", repository.readSettings().usualTagsFor(PublishTarget.Bluesky))
        assertEquals("#craftbeer", vm.state.settings.usualTagsFor(PublishTarget.Instagram))

        vm.setUsualTags(PublishTarget.Instagram, "")
        assertEquals(mapOf(PublishTarget.Bluesky to "#craftbeer #bskybeer"), repository.readSettings().usualTags)
    }

    @Test
    fun defaultShapeIsSaved() {
        val vm = viewModel()

        vm.setDefaultShape(CropShape.Square)

        assertEquals(CropShape.Square, repository.readSettings().defaultCropShape)
    }

    @Test
    fun choosingALogoMeasuresItAndReplacingItDeletesTheOldFile() {
        val vm = viewModel()

        vm.setLogoUri("file:///brand/logo.png")
        assertEquals(PixelSize(600, 200), vm.state.logoSize)
        assertTrue(renderer.discarded.isEmpty())

        vm.setLogoUri("file:///brand/new.png")
        assertEquals(PixelSize(300, 300), vm.state.logoSize)
        assertEquals(listOf("file:///brand/logo.png"), renderer.discarded)

        vm.setLogoUri(null)
        assertNull(vm.state.logoSize)
        assertNull(repository.readSettings().logo.uri)
        assertEquals(listOf("file:///brand/logo.png", "file:///brand/new.png"), renderer.discarded)
    }

    @Test
    fun anImportedLogoIsUsedAndItsCautionShown() {
        val vm = viewModel()

        vm.logoImported("file:///brand/logo.png", caution = "It contains live text.")

        assertEquals("file:///brand/logo.png", repository.readSettings().logo.uri)
        assertEquals("It contains live text.", vm.state.logoNote)
        assertFalse(vm.state.logoNoteIsError)

        vm.logoImported("file:///brand/new.png", caution = null)
        assertNull(vm.state.logoNote)
    }

    @Test
    fun aFailedImportKeepsTheCurrentLogoAndSaysWhy() {
        val vm = viewModel()
        vm.logoImported("file:///brand/logo.png", caution = null)

        vm.logoImportFailed("This SVG could not be read.")

        assertEquals("file:///brand/logo.png", repository.readSettings().logo.uri)
        assertEquals("This SVG could not be read.", vm.state.logoNote)
        assertTrue(vm.state.logoNoteIsError)
        assertTrue(renderer.discarded.isEmpty())

        vm.setLogoUri(null)
        assertNull(vm.state.logoNote)
        assertFalse(vm.state.logoNoteIsError)
    }

    @Test
    fun logoSizeAndStrengthStayWithinBounds() {
        val vm = viewModel()

        vm.setLogoWidth(5f)
        vm.setLogoOpacity(0f)
        vm.setLogoCorner(LogoCorner.TopStart)

        val logo = repository.readSettings().logo
        assertEquals(LogoSettings.MaxWidthFraction, logo.widthFraction)
        assertEquals(LogoSettings.MinOpacity, logo.opacity)
        assertEquals(LogoCorner.TopStart, logo.corner)
    }

    @Test
    fun snippetsCanBeAddedEditedAndRemoved() {
        val vm = viewModel()
        val startCount = vm.state.settings.snippets.size

        vm.addSnippet()
        vm.updateSnippet(startCount, CaptionSnippet("Tags", "#doctrinebrewing"))
        vm.updateSnippet(99, CaptionSnippet("Ignored", "out of range"))
        assertEquals(CaptionSnippet("Tags", "#doctrinebrewing"), repository.readSettings().snippets.last())

        vm.removeSnippet(99)
        vm.removeSnippet(0)
        assertEquals(listOf(CaptionSnippet("Tags", "#doctrinebrewing")), repository.readSettings().snippets)
    }

    @Test
    fun aSavedApiKeyIsOnlyEverShownMasked() {
        repository.saveOpenAiApiKey("sk-or-v1-abcdefghijklmnop")

        val vm = viewModel()

        assertTrue(vm.state.hasApiKey)
        assertEquals("sk-...mnop", vm.state.apiKeyHint)
    }

    @Test
    fun theApiKeyCanBeSavedReplacedAndRemoved() {
        val vm = viewModel()

        vm.saveApiKey("   ")
        assertFalse(vm.state.hasApiKey)

        vm.saveApiKey("  first-key-000000001111  ")
        assertEquals("first-key-000000001111", repository.getOpenAiApiKey())
        assertEquals("fir...1111", vm.state.apiKeyHint)
        assertEquals("Key saved.", vm.state.apiKeyMessage)

        vm.saveApiKey("second-key-00000002222")
        assertEquals("second-key-00000002222", repository.getOpenAiApiKey())

        vm.clearApiKey()
        assertFalse(vm.state.hasApiKey)
        assertFalse(repository.hasOpenAiApiKey())
        assertTrue(vm.state.apiKeyMessage.orEmpty().contains("Nothing will be sent"))
    }

    @Test
    fun theAiAddressAndModelListsAreSaved() {
        val vm = viewModel()

        vm.setAiBaseUrl("  https://api.openai.com/v1  ")
        vm.setAiModels(AiOperation.AltText, listOf("one/model", " two/model ", "one/model"))

        val ai = repository.readSettings().ai
        assertEquals("https://api.openai.com/v1", ai.baseUrl)
        assertEquals(listOf("one/model", "two/model"), ai.modelsFor(AiOperation.AltText))

        vm.setAiModels(AiOperation.AltText, emptyList())
        assertEquals(AiOperation.AltText.defaultModels, repository.readSettings().ai.modelsFor(AiOperation.AltText))
    }

    @Test
    fun blueskyIsReadyOnceBothTheHandleAndAnAppPasswordAreSaved() {
        val vm = viewModel()
        assertFalse(vm.state.blueskyConfigured)

        vm.saveBlueskyPassword("  ")
        vm.saveBlueskyPassword(" abcd-efgh-ijkl-mnop ")
        assertTrue(vm.state.blueskyPasswordSaved)
        assertFalse(vm.state.blueskyConfigured)
        assertEquals("abcd-efgh-ijkl-mnop", repository.getSecret(BlueskySettings.AppPasswordSecret))
        assertEquals("App password saved.", vm.state.blueskyMessage)

        vm.setBlueskyHandle(" @doctrine.bsky.social ")
        vm.setBlueskyServer(" https://pds.example.org ")
        assertTrue(vm.state.blueskyConfigured)
        val saved = repository.readSettings().bluesky
        assertEquals("doctrine.bsky.social", saved.cleanHandle)
        assertEquals("https://pds.example.org", saved.server)

        assertTrue(viewModel().state.blueskyConfigured)

        vm.clearBlueskyPassword()
        assertFalse(vm.state.blueskyConfigured)
        assertNull(repository.getSecret(BlueskySettings.AppPasswordSecret))
    }

    @Test
    fun instagramNeedsBothATokenAndImageHosting() {
        val vm = viewModel()
        assertFalse(vm.state.instagramConfigured)
        assertTrue(instagramStatus(vm.state).contains("Not set up"))

        vm.saveInstagramToken("  ")
        vm.saveInstagramToken(" IGTOKEN-abcdef-123456 ")
        assertTrue(vm.state.instagramTokenSaved)
        assertFalse(vm.state.instagramConfigured)
        assertEquals("IGTOKEN-abcdef-123456", repository.getSecret(InstagramSettings.AccessTokenSecret))
        val saved = repository.readSettings().instagram
        assertEquals(now, saved.tokenSavedAtMillis)
        assertEquals(now + 60L * 86_400_000L, saved.tokenExpiresAtMillis)
        assertTrue(instagramStatus(vm.state).contains("A token is saved"))
        assertTrue(instagramStatus(vm.state).contains("good until about 2027-01-1"))
        assertTrue(instagramStatus(vm.state).contains("Image hosting still needs setting up"))

        vm.saveInstagramToken("EAAunified-abcdef-123456")
        assertEquals(0L, repository.readSettings().instagram.tokenExpiresAtMillis)
        assertEquals(now, repository.readSettings().instagram.tokenSavedAtMillis)
        assertTrue(instagramStatus(vm.state).contains("for as long as you chose"))
        assertFalse(instagramStatus(vm.state).contains("good until"))
        vm.saveInstagramToken(" IGTOKEN-abcdef-123456 ")

        vm.useFirebaseConfig("nothing useful")
        assertTrue(vm.state.firebaseMessage.orEmpty().contains("does not contain a Firebase project"))
        assertFalse(vm.state.settings.firebaseHost.hasProject)

        vm.setFirebaseEmail(" uploads@doctrine.example ")
        vm.useFirebaseConfig("""apiKey: "AIza", appId: "1:2:web:3", projectId: "doctrine-site", storageBucket: "doctrine-site.appspot.com"""")
        assertEquals("Using project doctrine-site, bucket doctrine-site.appspot.com.", vm.state.firebaseMessage)
        assertEquals(
            FirebaseHostSettings("AIza", "1:2:web:3", "doctrine-site", "doctrine-site.appspot.com", "uploads@doctrine.example"),
            repository.readSettings().firebaseHost,
        )
        assertFalse(vm.state.imageHostConfigured)

        vm.saveFirebasePassword("   ")
        vm.saveFirebasePassword(" upload-password-1 ")
        assertTrue(vm.state.imageHostConfigured)
        assertTrue(vm.state.instagramConfigured)
        assertEquals("upload-password-1", repository.getSecret(FirebaseHostSettings.UploadPasswordSecret))

        repository.saveSettings(
            repository.readSettings().let { it.copy(instagram = it.instagram.copy(username = "doctrinebrewing")) },
        )
        vm.showInstagramMessage("Connected to Instagram as @doctrinebrewing, and image hosting works.")
        assertEquals("doctrinebrewing", vm.state.settings.instagram.username)
        assertTrue(instagramStatus(vm.state).startsWith("Connected as @doctrinebrewing."))
        assertFalse(instagramStatus(vm.state).contains("Image hosting still"))

        val reopened = viewModel().state
        assertTrue(reopened.instagramConfigured)

        vm.clearFirebasePassword()
        assertFalse(vm.state.instagramConfigured)
        vm.clearFirebaseConfig()
        assertFalse(vm.state.settings.firebaseHost.hasProject)
        assertEquals("uploads@doctrine.example", vm.state.settings.firebaseHost.email)
        vm.clearInstagramToken()
        assertFalse(vm.state.instagramTokenSaved)
        assertEquals(InstagramSettings(), repository.readSettings().instagram)
        assertNull(repository.getSecret(InstagramSettings.AccessTokenSecret))

        vm.showBlueskyMessage("Signed in to Bluesky as @doctrine.bsky.social.")
        assertEquals("Signed in to Bluesky as @doctrine.bsky.social.", vm.state.blueskyMessage)
    }

    @Test
    fun aProblemWithSecureStorageIsPassedOnAndSavingASecretSaysWhy() {
        val broken = object : LocalSettingsRepository by repository {
            override val secretStorageNotice = "Secure storage could not be opened."
            override fun saveSecret(name: String, value: String): Unit = throw IllegalStateException("Secure storage could not be opened.")
            override fun getSecret(name: String): String? = null
        }
        val vm = SettingsViewModel(broken, renderer, clock)

        assertEquals("Secure storage could not be opened.", vm.state.secretStorageNotice)
        vm.saveInstagramToken("EAAunified-abcdef-123456")
        assertFalse(vm.state.instagramTokenSaved)
        assertTrue(vm.state.instagramMessage.orEmpty().contains("Secure storage could not be opened."))
        assertFalse(vm.state.instagramMessage.orEmpty().contains("EAAunified"))

        assertNull(viewModel().state.secretStorageNotice)
    }

    @Test
    fun privacyNoteSaysWhatLeavesThePhone() {
        val offline = privacyNote(dictationOnDevice = null, aiConfigured = false)
        assertTrue(offline.contains("does not connect to the internet"))
        assertFalse(offline.contains("Dictation"))

        val withAi = privacyNote(dictationOnDevice = true, aiConfigured = true)
        assertTrue(withAi.contains("only when you ask for alt text"))
        assertTrue(withAi.contains("recognised on this phone"))

        assertTrue(privacyNote(dictationOnDevice = false, aiConfigured = false).contains("may send your voice"))

        val everything = privacyNote(dictationOnDevice = null, aiConfigured = true, postingTo = listOf("Instagram", "Bluesky"))
        assertTrue(everything.contains("alt text"))
        assertTrue(everything.contains("and when you post to Instagram or Bluesky"))
        assertTrue(everything.contains("and to ask Bluesky whether an account you @mention exists."))
        assertFalse(
            privacyNote(dictationOnDevice = null, aiConfigured = false, postingTo = listOf("Instagram")).contains("@mention"),
        )
        assertTrue(
            privacyNote(dictationOnDevice = null, aiConfigured = false, postingTo = listOf("Bluesky"))
                .contains("only when you post to Bluesky"),
        )
    }

    @Test
    fun worksWithoutARenderer() {
        repository.saveSettings(LocalAppSettings(logo = LogoSettings(uri = "file:///brand/logo.png")))
        val vm = SettingsViewModel(repository, renderer = null)

        vm.setLogoUri("file:///brand/new.png")

        assertNull(vm.state.logoSize)
        assertEquals("file:///brand/new.png", repository.readSettings().logo.uri)
    }
}
