package com.digitumdei.shotquill.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.image.RenderOutcome
import com.digitumdei.shotquill.image.RenderRequest
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CaptionStats
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.appendDictation
import com.digitumdei.shotquill.shared.domain.appendSnippet
import com.digitumdei.shotquill.shared.domain.captionStats
import com.digitumdei.shotquill.shared.domain.panZoomed
import com.digitumdei.shotquill.shared.domain.withShape
import com.digitumdei.shotquill.shared.post.PostEditorStore
import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository

data class PostEditorState(
    val isLoaded: Boolean = false,
    val isMissing: Boolean = false,
    val originalUri: String? = null,
    val imageSize: PixelSize? = null,
    val treatment: PhotoTreatment = PhotoTreatment(),
    val caption: String = "",
    val look: LookSettings = LookSettings(),
    val logo: LogoSettings = LogoSettings(),
    val logoSize: PixelSize? = null,
    val snippets: List<CaptionSnippet> = emptyList(),
    val isSharing: Boolean = false,
    val shared: Boolean = false,
    val message: String? = null,
) {
    val captionStats: CaptionStats get() = captionStats(caption)
    val hasLogo: Boolean get() = logo.uri != null
    val logoCorner: LogoCorner get() = treatment.logoCorner ?: logo.corner
    val canShare: Boolean get() = isLoaded && !isSharing && originalUri != null && imageSize != null
}

/**
 * Holds one post while it is being prepared. Reading and writing the store and
 * rendering are blocking calls: the screen runs them off the main thread.
 */
class PostEditorViewModel(
    private val draftId: PostDraftId,
    private val store: PostEditorStore,
    private val settingsRepository: LocalSettingsRepository,
    private val renderer: PhotoRenderer,
    private val clipboardWriter: ClipboardWriter,
    private val shareLauncher: PostShareLauncher,
    private val clock: EpochClock = EpochClock.Default,
) {
    var state: PostEditorState by mutableStateOf(PostEditorState())
        private set

    private var renderedUri: String? = null

    fun load() {
        val post = store.load(draftId)
        if (post == null) {
            state = PostEditorState(isMissing = true, message = "This post could not be found.")
            return
        }
        val settings = settingsRepository.readSettings()
        val logo = settings.logo
        val imageSize = renderer.orientedSize(post.originalUri)
        renderedUri = post.renderedUri
        state = PostEditorState(
            isLoaded = true,
            originalUri = post.originalUri,
            imageSize = imageSize,
            // The logo starts switched on even when none is chosen yet, so a logo
            // added later from settings appears without a second step.
            treatment = post.treatment ?: PhotoTreatment(cropShape = settings.defaultCropShape),
            caption = post.caption,
            look = settings.look,
            logo = logo,
            logoSize = logo.uri?.let(renderer::orientedSize),
            snippets = settings.snippets,
            shared = post.shared,
            message = if (imageSize == null) "The photo could not be read." else null,
        )
    }

    fun updateCaption(text: String) {
        if (!state.isLoaded) return
        state = state.copy(caption = text)
    }

    fun addSnippet(snippet: CaptionSnippet) {
        if (!state.isLoaded) return
        state = state.copy(caption = appendSnippet(state.caption, snippet.text))
    }

    /** Adds a phrase that was spoken rather than typed. */
    fun addDictation(spoken: String) {
        if (!state.isLoaded) return
        state = state.copy(caption = appendDictation(state.caption, spoken))
    }

    fun persistCaption() {
        if (!state.isLoaded) return
        store.saveCaption(draftId, state.caption)
    }

    fun selectShape(shape: CropShape) {
        if (!state.isLoaded || state.treatment.cropShape == shape) return
        state = state.copy(treatment = state.treatment.withShape(shape))
    }

    /** A drag and pinch on the photo, in fractions of the visible crop. */
    fun panZoom(panXFraction: Float, panYFraction: Float, zoomChange: Float) {
        val size = state.imageSize ?: return
        state = state.copy(
            treatment = state.treatment.panZoomed(
                panXFraction = panXFraction,
                panYFraction = panYFraction,
                zoomChange = zoomChange,
                imageWidth = size.width,
                imageHeight = size.height,
            ),
        )
    }

    fun setLookEnabled(enabled: Boolean) {
        if (!state.isLoaded) return
        state = state.copy(treatment = state.treatment.copy(lookEnabled = enabled))
    }

    fun setLogoEnabled(enabled: Boolean) {
        if (!state.isLoaded) return
        state = state.copy(treatment = state.treatment.copy(logoEnabled = enabled))
    }

    fun setLogoCorner(corner: LogoCorner) {
        if (!state.isLoaded) return
        state = state.copy(treatment = state.treatment.copy(logoCorner = corner))
    }

    fun persistTreatment() {
        if (!state.isLoaded) return
        store.saveTreatment(draftId, state.treatment)
    }

    fun dismissMessage() {
        state = state.copy(message = null)
    }

    /**
     * Writes the finished image, puts the caption on the clipboard and opens
     * the share sheet. Nothing is posted by the app itself.
     */
    fun share() {
        val current = state
        val sourceUri = current.originalUri
        if (!current.canShare || sourceUri == null) return
        state = current.copy(isSharing = true, message = null)
        store.saveCaption(draftId, current.caption)
        store.saveTreatment(draftId, current.treatment)

        val outcome = renderer.render(
            RenderRequest(
                sourceUri = sourceUri,
                treatment = current.treatment.copy(logoCorner = current.logoCorner),
                look = current.look,
                logo = current.logo,
                outputName = "${draftId.value}-${clock.nowMillis()}",
            ),
        )
        when (outcome) {
            is RenderOutcome.Failure -> {
                state = state.copy(isSharing = false, message = outcome.message)
            }

            is RenderOutcome.Success -> {
                val caption = current.caption.trim()
                var clipboardNote = ""
                if (caption.isNotEmpty()) {
                    clipboardNote = try {
                        clipboardWriter.copy("post caption", caption)
                        " Your caption is on the clipboard, ready to paste."
                    } catch (_: Exception) {
                        " The caption could not be copied, so copy it by hand."
                    }
                }
                val result = shareLauncher.share(outcome.uri, caption)
                if (result.success) {
                    val previous = renderedUri
                    store.markShared(draftId, outcome.uri)
                    renderedUri = outcome.uri
                    if (previous != null && previous != outcome.uri) renderer.discard(previous)
                    state = state.copy(isSharing = false, shared = true, message = "Image ready.$clipboardNote")
                } else {
                    renderer.discard(outcome.uri)
                    state = state.copy(
                        isSharing = false,
                        message = result.errorMessage ?: "The share sheet could not be opened.",
                    )
                }
            }
        }
    }

    /** Removes the post from the list and deletes the image made from it. */
    fun remove() {
        renderedUri?.let(renderer::discard)
        renderedUri = null
        store.remove(draftId)
        state = PostEditorState(isMissing = true)
    }
}
