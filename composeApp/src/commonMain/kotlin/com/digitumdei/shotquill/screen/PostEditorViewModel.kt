package com.digitumdei.shotquill.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.image.PhotoRenderer
import com.digitumdei.shotquill.image.RenderOutcome
import com.digitumdei.shotquill.image.RenderRequest
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.shared.ai.AiResult
import com.digitumdei.shotquill.shared.ai.AltTextWriter
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
import com.digitumdei.shotquill.shared.social.BlueskyTextLimit
import com.digitumdei.shotquill.shared.social.PostPublisher
import com.digitumdei.shotquill.shared.social.PostToPublish
import com.digitumdei.shotquill.shared.social.PublishResult
import com.digitumdei.shotquill.shared.social.PublishTarget
import com.digitumdei.shotquill.shared.social.blueskyLength
import com.digitumdei.shotquill.shared.social.blueskyTextFor

/** One service the post can be published to directly, and what has happened there so far. */
data class PublishTargetState(
    val target: PublishTarget,
    /** The account that will be posted as, such as `@name.bsky.social`. */
    val account: String,
    /** A link to the published post, or null when it has not been published there. */
    val postedUrl: String? = null,
    val isPublishing: Boolean = false,
)

data class PostEditorState(
    val isLoaded: Boolean = false,
    val isMissing: Boolean = false,
    val originalUri: String? = null,
    val imageSize: PixelSize? = null,
    val treatment: PhotoTreatment = PhotoTreatment(),
    val caption: String = "",
    val altText: String = "",
    /** True once an AI service has been set up, so alt text can be written on request. */
    val aiConfigured: Boolean = false,
    val altTextModels: List<String> = emptyList(),
    val altTextModel: String? = null,
    val isWritingAltText: Boolean = false,
    val look: LookSettings = LookSettings(),
    val logo: LogoSettings = LogoSettings(),
    val logoSize: PixelSize? = null,
    val snippets: List<CaptionSnippet> = emptyList(),
    val isSharing: Boolean = false,
    val shared: Boolean = false,
    val message: String? = null,
    /** The services set up for direct posting. Empty when none is. */
    val targets: List<PublishTargetState> = emptyList(),
    val blueskyText: String = "",
) {
    val isPublishing: Boolean get() = targets.any { it.isPublishing }
    val hasBluesky: Boolean get() = targets.any { it.target == PublishTarget.Bluesky }

    /** What would be posted to Bluesky right now, and how long Bluesky counts it as. */
    val blueskyPostText: String get() = blueskyTextFor(caption, blueskyText)
    val blueskyLength: Int get() = blueskyLength(blueskyPostText)
    val blueskyTooLong: Boolean get() = blueskyLength > BlueskyTextLimit

    val captionStats: CaptionStats get() = captionStats(caption)
    val hasLogo: Boolean get() = logo.uri != null
    val logoCorner: LogoCorner get() = treatment.logoCorner ?: logo.corner
    val canShare: Boolean
        get() = isLoaded && !isSharing && !isPublishing && originalUri != null && imageSize != null
    val canWriteAltText: Boolean
        get() = isLoaded && aiConfigured && !isWritingAltText && originalUri != null && imageSize != null
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
    private val altTextWriter: AltTextWriter? = null,
    private val publishers: List<PostPublisher> = emptyList(),
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
            altText = post.altText,
            aiConfigured = altTextWriter?.isConfigured == true,
            altTextModels = altTextWriter?.models().orEmpty(),
            altTextModel = altTextWriter?.models()?.firstOrNull(),
            look = settings.look,
            logo = logo,
            logoSize = logo.uri?.let(renderer::orientedSize),
            snippets = settings.snippets,
            shared = post.shared,
            targets = publishers.filter { it.isConfigured }.map { publisher ->
                PublishTargetState(
                    target = publisher.target,
                    account = publisher.accountLabel.orEmpty(),
                    postedUrl = post.published[publisher.target],
                )
            },
            blueskyText = post.blueskyText,
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

    fun updateBlueskyText(text: String) {
        if (!state.isLoaded) return
        state = state.copy(blueskyText = text)
    }

    fun persistBlueskyText() {
        if (!state.isLoaded) return
        store.saveBlueskyText(draftId, state.blueskyText)
    }

    fun updateAltText(text: String) {
        if (!state.isLoaded) return
        state = state.copy(altText = text)
    }

    fun persistAltText() {
        if (!state.isLoaded) return
        store.saveAltText(draftId, state.altText)
    }

    fun selectAltTextModel(model: String) {
        if (!state.isLoaded || model !in state.altTextModels) return
        state = state.copy(altTextModel = model)
    }

    /**
     * Sends the photo, cropped as it will be shared, to the AI service and
     * puts the description it returns into the alt text box, where it can be
     * edited like anything typed by hand.
     */
    fun writeAltText() {
        val current = state
        val writer = altTextWriter
        val sourceUri = current.originalUri
        if (!current.canWriteAltText || writer == null || sourceUri == null) return
        state = current.copy(isWritingAltText = true, message = null)
        val jpeg = renderer.encodeForDescription(sourceUri, current.treatment)
        if (jpeg == null) {
            state = state.copy(isWritingAltText = false, message = "The photo could not be prepared for describing.")
            return
        }
        when (val result = writer.write(jpeg, current.caption, current.altTextModel)) {
            is AiResult.Success -> {
                state = state.copy(isWritingAltText = false, altText = result.text)
                store.saveAltText(draftId, result.text)
            }

            is AiResult.Failure -> {
                state = state.copy(isWritingAltText = false, message = result.message)
            }
        }
    }

    fun copyAltText() {
        val text = state.altText.trim()
        if (text.isEmpty()) return
        state = try {
            clipboardWriter.copy("alt text", text)
            state.copy(message = "Alt text copied.")
        } catch (_: Exception) {
            state.copy(message = "The alt text could not be copied.")
        }
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
        store.saveAltText(draftId, current.altText)
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
                val altText = current.altText.trim()
                var clipboardNote = ""
                if (caption.isNotEmpty()) {
                    clipboardNote = try {
                        // The alt text goes first so the caption ends up on top;
                        // keyboards with clipboard history then offer both.
                        if (altText.isNotEmpty()) clipboardWriter.copy("alt text", altText)
                        clipboardWriter.copy("post caption", caption)
                        if (altText.isNotEmpty()) {
                            " Your caption is on the clipboard, with the alt text just behind it in your " +
                                "keyboard's clipboard history."
                        } else {
                            " Your caption is on the clipboard, ready to paste."
                        }
                    } catch (_: Exception) {
                        " The caption could not be copied, so copy it by hand."
                    }
                } else if (altText.isNotEmpty()) {
                    clipboardNote = try {
                        clipboardWriter.copy("alt text", altText)
                        " Your alt text is on the clipboard, ready to paste."
                    } catch (_: Exception) {
                        " The alt text could not be copied, so copy it by hand."
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

    /**
     * Publishes the post to [target] directly. The image is rendered exactly
     * as for sharing, and the words are the ones in the editor: nothing is
     * added or rewritten on the way out.
     */
    fun publish(target: PublishTarget) {
        val current = state
        val publisher = publishers.firstOrNull { it.target == target && it.isConfigured }
        val sourceUri = current.originalUri
        if (!current.canShare || publisher == null || sourceUri == null) return
        val text = when (target) {
            PublishTarget.Instagram -> current.caption.trim()
            PublishTarget.Bluesky -> current.blueskyPostText
        }
        publisher.problemWith(text)?.let { problem ->
            state = current.copy(message = problem)
            return
        }
        state = current.withTarget(target) { it.copy(isPublishing = true) }.copy(message = null)
        store.saveCaption(draftId, current.caption)
        store.saveAltText(draftId, current.altText)
        store.saveBlueskyText(draftId, current.blueskyText)
        store.saveTreatment(draftId, current.treatment)

        fun finish(message: String, postedUrl: String? = null) {
            state = state.withTarget(target) {
                it.copy(isPublishing = false, postedUrl = postedUrl ?: it.postedUrl)
            }.copy(message = message, shared = state.shared || postedUrl != null)
        }

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
            is RenderOutcome.Failure -> finish(outcome.message)

            is RenderOutcome.Success -> {
                publisher.problemWithImage(outcome.size.width, outcome.size.height)?.let { problem ->
                    renderer.discard(outcome.uri)
                    finish(problem)
                    return
                }
                val jpeg = renderer.jpegWithin(outcome.uri, publisher.maxImageBytes)
                if (jpeg == null) {
                    renderer.discard(outcome.uri)
                    finish("The image could not be made small enough for ${target.label}.")
                    return
                }
                val result = publisher.publish(
                    PostToPublish(
                        text = text,
                        jpeg = jpeg,
                        altText = current.altText.trim(),
                        imageWidth = outcome.size.width,
                        imageHeight = outcome.size.height,
                    ),
                )
                when (result) {
                    is PublishResult.Success -> {
                        val previous = renderedUri
                        store.markPublished(draftId, target, result.url, outcome.uri)
                        renderedUri = outcome.uri
                        if (previous != null && previous != outcome.uri) renderer.discard(previous)
                        finish("Posted to ${target.label} as ${publisher.accountLabel.orEmpty()}.", result.url)
                    }

                    is PublishResult.Failure -> {
                        renderer.discard(outcome.uri)
                        finish(result.message)
                    }
                }
            }
        }
    }

    private fun PostEditorState.withTarget(
        target: PublishTarget,
        change: (PublishTargetState) -> PublishTargetState,
    ): PostEditorState = copy(targets = targets.map { if (it.target == target) change(it) else it })

    /** Removes the post from the list and deletes the image made from it. */
    fun remove() {
        renderedUri?.let(renderer::discard)
        renderedUri = null
        store.remove(draftId)
        state = PostEditorState(isMissing = true)
    }
}
