package com.digitumdei.shotquill.screen

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
        assertFalse(state.hasLegacyApiKey)
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
    fun aLeftOverApiKeyCanBeDeleted() {
        repository.saveOpenAiApiKey("sk-old-key")
        val vm = viewModel()
        assertTrue(vm.state.hasLegacyApiKey)

        vm.clearLegacyApiKey()

        assertFalse(vm.state.hasLegacyApiKey)
        assertFalse(repository.hasOpenAiApiKey())
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
