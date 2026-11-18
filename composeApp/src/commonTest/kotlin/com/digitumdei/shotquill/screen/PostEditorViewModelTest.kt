package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.image.RenderOutcome
import com.digitumdei.shotquill.image.RenderRequest
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.share.ShareResult
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
        )

    private fun loadedViewModel(): PostEditorViewModel {
        store.post = EditablePost(draftId, photoUri, caption = "", treatment = null, renderedUri = null, shared = false)
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
        store.post = EditablePost(draftId, photoUri, "Bottling day", saved, "file:///edited/old.jpg", shared = true)
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
        store.post = EditablePost(draftId, photoUri, "", null, null, shared = false)

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
        store.post = EditablePost(draftId, photoUri, "", PhotoTreatment(), "file:///edited/old.jpg", shared = true)
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
        store.post = EditablePost(draftId, photoUri, "Old", PhotoTreatment(), "file:///edited/old.jpg", shared = true)
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

private class FakeStore : PostEditorStore {
    var post: EditablePost? = null
    val savedCaptions = mutableListOf<String>()
    val savedTreatments = mutableListOf<PhotoTreatment>()
    var sharedWith: String? = null
    var removed = false

    override fun listPosts(): List<PostSummary> = emptyList()

    override fun load(id: PostDraftId): EditablePost? = post

    override fun saveCaption(id: PostDraftId, caption: String) {
        savedCaptions += caption
    }

    override fun saveTreatment(id: PostDraftId, treatment: PhotoTreatment) {
        savedTreatments += treatment
    }

    override fun markShared(id: PostDraftId, renderedUri: String) {
        sharedWith = renderedUri
    }

    override fun remove(id: PostDraftId) {
        removed = true
    }
}

internal class FakeRenderer : PhotoRenderer {
    val sizes = mutableMapOf<String, PixelSize>()
    val requests = mutableListOf<RenderRequest>()
    val discarded = mutableListOf<String>()
    var outcome: RenderOutcome? = null

    override fun orientedSize(uri: String): PixelSize? = sizes[uri]

    override fun render(request: RenderRequest): RenderOutcome {
        requests += request
        return outcome ?: RenderOutcome.Success("file:///edited/${request.outputName}.jpg", PixelSize(1080, 1080))
    }

    override fun discard(uri: String) {
        discarded += uri
    }
}

private class RecordingClipboard : ClipboardWriter {
    val copies = mutableListOf<Pair<String, String>>()
    var failure: Exception? = null

    override fun copy(label: String, text: String) {
        failure?.let { throw it }
        copies += label to text
    }
}

private class RecordingShareLauncher : PostShareLauncher {
    val shares = mutableListOf<Pair<String?, String>>()
    var result = ShareResult(success = true)

    override fun share(imageUri: String?, text: String): ShareResult {
        shares += imageUri to text
        return result
    }
}
