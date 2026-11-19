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

    private fun viewModel() = SettingsViewModel(repository, renderer)

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
    fun privacyNoteSaysWhatLeavesThePhone() {
        val offline = privacyNote(dictationOnDevice = null, aiConfigured = false)
        assertTrue(offline.contains("does not connect to the internet"))
        assertFalse(offline.contains("Dictation"))

        val withAi = privacyNote(dictationOnDevice = true, aiConfigured = true)
        assertTrue(withAi.contains("only when you ask for alt text"))
        assertTrue(withAi.contains("recognised on this phone"))

        assertTrue(privacyNote(dictationOnDevice = false, aiConfigured = false).contains("may send your voice"))
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
