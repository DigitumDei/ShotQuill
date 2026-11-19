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
import com.digitumdei.shotquill.shared.social.AccountLookup
import com.digitumdei.shotquill.shared.social.BlueskyTextLimit
import com.digitumdei.shotquill.shared.social.MentionCheck
import com.digitumdei.shotquill.shared.social.PostPublisher
import com.digitumdei.shotquill.shared.social.PostToPublish
import com.digitumdei.shotquill.shared.social.PublishResult
import com.digitumdei.shotquill.shared.social.PublishTarget
import com.digitumdei.shotquill.shared.social.blueskyHandlesToLookUp
import com.digitumdei.shotquill.shared.social.blueskyLength
import com.digitumdei.shotquill.shared.social.blueskyMentionChecks
import com.digitumdei.shotquill.shared.social.blueskyTextFor
import com.digitumdei.shotquill.shared.social.instagramMentionChecks
import com.digitumdei.shotquill.shared.social.AccountSuggestion
import com.digitumdei.shotquill.shared.social.MentionStatus
import com.digitumdei.shotquill.shared.social.completeMention
import com.digitumdei.shotquill.shared.social.mentionBeingTyped
import com.digitumdei.shotquill.shared.social.mentionNamesFor
import com.digitumdei.shotquill.shared.social.rememberMentions
import com.digitumdei.shotquill.shared.social.suggestMentions
import com.digitumdei.shotquill.shared.social.InstagramPhotoTagLimit
import com.digitumdei.shotquill.shared.social.PhotoTag
import com.digitumdei.shotquill.shared.social.collaboratorsProblem
import com.digitumdei.shotquill.shared.social.instagramNameOrNull
import com.digitumdei.shotquill.shared.social.parseCollaborators
import com.digitumdei.shotquill.shared.social.withTags
import kotlin.concurrent.Volatile

/** One service the post can be published to directly, and what has happened there so far. */
data class PublishTargetState(
    val target: PublishTarget,
    /** The account that will be posted as, such as `@name.bsky.social`. */
    val account: String,
    /** A link to the published post, or null when it has not been published there. */
    val postedUrl: String? = null,
    val isPublishing: Boolean = false,
)

/** The boxes in the editor where a mention can be typed, and the service each one is written for. */
enum class MentionField(val target: PublishTarget) {
    Caption(PublishTarget.Instagram),
    InstagramTags(PublishTarget.Instagram),
    BlueskyText(PublishTarget.Bluesky),
    BlueskyTags(PublishTarget.Bluesky),
}

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
    /** The hashtags for this post on each service, as typed. */
    val tags: Map<PublishTarget, String> = emptyMap(),
    /** People tagged on the photo, for Instagram. */
    val photoTags: List<PhotoTag> = emptyList(),
    /** The tag that the next tap on the photo will move, if any. */
    val placingTag: String? = null,
    /** The Instagram collaborators box, as typed. */
    val collaborators: String = "",
) {
    fun tagsFor(target: PublishTarget): String = tags[target].orEmpty()

    val hasInstagram: Boolean get() = targets.any { it.target == PublishTarget.Instagram }

    /** Why the collaborators as typed cannot be sent, or null when they can. */
    val collaboratorsProblem: String? get() = collaboratorsProblem(collaborators)

    /** The caption with Instagram's hashtags: what is shared or posted to Instagram. */
    val instagramPostText: String get() = withTags(caption, tagsFor(PublishTarget.Instagram))

    val isPublishing: Boolean get() = targets.any { it.isPublishing }
    val hasBluesky: Boolean get() = targets.any { it.target == PublishTarget.Bluesky }

    /** What would be posted to Bluesky right now, and how long Bluesky counts it as. */
    val blueskyPostText: String
        get() = withTags(blueskyTextFor(caption, blueskyText), tagsFor(PublishTarget.Bluesky))
    val blueskyLength: Int get() = blueskyLength(blueskyPostText)
    val blueskyTooLong: Boolean get() = blueskyLength > BlueskyTextLimit

    val captionStats: CaptionStats get() = captionStats(instagramPostText)
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

    // Typing arrives on the main thread while loading, saving, sharing and
    // posting run on background threads, and every change has the shape "take
    // the state, alter one part, put it back". Making each of those one step
    // under a lock means no change can be overwritten by another that started
    // from an older copy, whichever thread it came from.
    private val stateLock = Any()

    private inline fun update(change: (PostEditorState) -> PostEditorState) {
        synchronized(stateLock) { state = change(state) }
    }

    /** Makes [change] only if [allowed] still holds at that moment, and says whether it did. */
    private inline fun updateIf(
        allowed: (PostEditorState) -> Boolean,
        change: (PostEditorState) -> PostEditorState,
    ): Boolean = synchronized(stateLock) {
        val current = state
        if (allowed(current)) {
            state = change(current)
            true
        } else {
            false
        }
    }

    // What Bluesky has said so far about each handle mentioned, by handle. Kept
    // apart from [state] because it is written from a background thread while
    // typing carries on, and the two must never overwrite each other.
    private var blueskyLookups: Map<String, AccountLookup> by mutableStateOf(emptyMap())

    // The same for Instagram names, where Meta will answer at all.
    private var instagramLookups: Map<String, AccountLookup> by mutableStateOf(emptyMap())

    // What Bluesky suggested for each unfinished name asked about so far.
    private var liveSuggestions: Map<String, List<AccountSuggestion>> by mutableStateOf(emptyMap())

    // Accounts mentioned in earlier posts on each service, most recent first.
    private var usedBefore: Map<PublishTarget, List<String>> by mutableStateOf(emptyMap())
    private val mentionLock = Any()

    @Volatile
    private var renderedUri: String? = null

    // What the store holds, or would fall back to, for each service's tags.
    // Tags are only written once they differ, so a post whose tags were never
    // touched keeps following the usual tags in settings.
    private var savedTags: Map<PublishTarget, String> = emptyMap()
    private val tagSaveLock = Any()

    fun load() {
        val post = store.load(draftId)
        if (post == null) {
            update { PostEditorState(isMissing = true, message = "This post could not be found.") }
            return
        }
        val settings = settingsRepository.readSettings()
        val logo = settings.logo
        val imageSize = renderer.orientedSize(post.originalUri)
        renderedUri = post.renderedUri
        val loaded = PostEditorState(
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
            tags = PublishTarget.entries.associateWith { target ->
                post.tags[target] ?: settings.usualTagsFor(target)
            },
            photoTags = post.photoTags,
            collaborators = post.collaborators,
            message = if (imageSize == null) "The photo could not be read." else null,
        )
        update { loaded }
        savedTags = loaded.tags
        synchronized(mentionLock) {
            usedBefore = PublishTarget.entries.associateWith { settings.mentionHistoryFor(it) }
        }
    }

    fun updateCaption(text: String) {
        if (!state.isLoaded) return
        update { it.copy(caption = text) }
    }

    fun addSnippet(snippet: CaptionSnippet) {
        if (!state.isLoaded) return
        update { it.copy(caption = appendSnippet(it.caption, snippet.text)) }
    }

    /** Adds a phrase that was spoken rather than typed. */
    fun addDictation(spoken: String) {
        if (!state.isLoaded) return
        update { it.copy(caption = appendDictation(it.caption, spoken)) }
    }

    fun persistCaption() {
        if (!state.isLoaded) return
        store.saveCaption(draftId, state.caption)
    }

    fun updateBlueskyText(text: String) {
        if (!state.isLoaded) return
        update { it.copy(blueskyText = text) }
    }

    fun persistBlueskyText() {
        if (!state.isLoaded) return
        store.saveBlueskyText(draftId, state.blueskyText)
    }

    fun updateTags(target: PublishTarget, tags: String) {
        if (!state.isLoaded) return
        update { it.copy(tags = it.tags + (target to tags)) }
    }

    fun persistTags() {
        if (!state.isLoaded) return
        // One reading of the tags serves both the writing and the record of
        // what was written. Typing can carry on while this runs, and anything
        // typed meanwhile must still count as unsaved.
        // An autosave and a post can both get here at once; the record of what
        // has been written must not be updated by two of them together.
        synchronized(tagSaveLock) {
            val tags = state.tags
            tags.forEach { (target, text) ->
                if (savedTags[target] != text) store.saveTags(draftId, target, text)
            }
            savedTags = tags
        }
    }

    /**
     * Tags [typed] on the photo, in the middle to begin with, and makes it the
     * tag the next tap on the photo will move. Returns false when [typed] is
     * not an Instagram name.
     */
    fun addPhotoTag(typed: String): Boolean {
        if (!state.isLoaded) return false
        val name = instagramNameOrNull(typed)
        if (name == null) {
            update { it.copy(message = "That is not an Instagram name.") }
            return false
        }
        update { current ->
            when {
                current.photoTags.any { it.username == name } -> current.copy(placingTag = name)
                current.photoTags.size >= InstagramPhotoTagLimit ->
                    current.copy(message = "Instagram allows up to $InstagramPhotoTagLimit people on a photo.")

                else -> current.copy(photoTags = current.photoTags + PhotoTag(name), placingTag = name)
            }
        }
        return true
    }

    fun removePhotoTag(username: String) {
        update { current ->
            current.copy(
                photoTags = current.photoTags.filterNot { it.username == username },
                placingTag = current.placingTag.takeIf { it != username },
            )
        }
    }

    /** Chooses which tag the next tap on the photo moves; choosing it again lets go of it. */
    fun selectPhotoTag(username: String) {
        update { current ->
            if (current.photoTags.none { it.username == username }) {
                current
            } else {
                current.copy(placingTag = if (current.placingTag == username) null else username)
            }
        }
    }

    /** A tap on the photo: the chosen tag moves there. Without a chosen tag nothing happens. */
    fun placePhotoTag(xFraction: Float, yFraction: Float) {
        update { current ->
            val name = current.placingTag
            if (name == null) {
                current
            } else {
                current.copy(
                    photoTags = current.photoTags.map {
                        if (it.username == name) it.copy(x = xFraction.coerceIn(0f, 1f), y = yFraction.coerceIn(0f, 1f)) else it
                    },
                    placingTag = null,
                )
            }
        }
    }

    fun updateCollaborators(text: String) {
        if (!state.isLoaded) return
        update { it.copy(collaborators = text) }
    }

    fun persistInstagramExtras() {
        if (!state.isLoaded) return
        val current = state
        store.saveInstagramExtras(draftId, current.photoTags, current.collaborators)
    }

    /** Blocking. Writes everything typed or dragged that may not have been saved yet. */
    fun persistAll() {
        persistInstagramExtras()
        persistCaption()
        persistAltText()
        persistBlueskyText()
        persistTags()
        persistTreatment()
    }

    /**
     * What is known about each account mentioned in the words going to
     * [target]. Bluesky mentions are checked against Bluesky itself; Instagram
     * offers no lookup, so its names are only checked for how they are written.
     */
    fun mentionChecks(target: PublishTarget): List<MentionCheck> = when {
        !state.isLoaded -> emptyList()
        target == PublishTarget.Instagram -> instagramMentionChecks(state.instagramPostText, instagramLookups)
        state.hasBluesky -> blueskyMentionChecks(state.blueskyPostText, blueskyLookups)
        else -> emptyList()
    }

    /**
     * Blocking. Asks Bluesky about every mentioned handle it has not answered
     * for yet. Only the handle is sent, without any sign-in.
     */
    fun checkMentions() {
        if (!state.isLoaded) return
        checkInstagramMentions()
        if (!state.hasBluesky) return
        val bluesky = publishers.firstOrNull { it.target == PublishTarget.Bluesky } ?: return
        blueskyHandlesToLookUp(state.blueskyPostText).forEach { handle ->
            val known = blueskyLookups[handle]
            if (known != null && known != AccountLookup.Unreachable) return@forEach
            val answer = try {
                bluesky.findAccount(handle)
            } catch (_: Exception) {
                AccountLookup.Unreachable
            }
            blueskyLookups = blueskyLookups + (handle to answer)
        }
    }

    /**
     * Asks Instagram about each mentioned name it has not been asked about,
     * when Instagram is set up. It can only ever confirm a name; one it does
     * not confirm stays as it was.
     */
    private fun checkInstagramMentions() {
        if (state.targets.none { it.target == PublishTarget.Instagram }) return
        val instagram = publishers.firstOrNull { it.target == PublishTarget.Instagram } ?: return
        instagramMentionChecks(state.instagramPostText)
            .filter { it.status == MentionStatus.CannotCheck }
            .map { it.name.lowercase() }
            .filter { it !in instagramLookups }
            .forEach { name ->
                val answer = try {
                    instagram.findAccount(name)
                } catch (_: Exception) {
                    AccountLookup.CannotCheck
                }
                instagramLookups = instagramLookups + (name to answer)
            }
    }

    private fun textOf(field: MentionField): String = when (field) {
        MentionField.Caption -> state.caption
        MentionField.InstagramTags -> state.tagsFor(PublishTarget.Instagram)
        MentionField.BlueskyText -> state.blueskyText
        MentionField.BlueskyTags -> state.tagsFor(PublishTarget.Bluesky)
    }

    /**
     * Accounts to offer for the mention being typed at the end of [field]:
     * ones mentioned before, and for Bluesky what Bluesky itself suggests.
     * Empty when the box does not end in an unfinished mention.
     */
    fun suggestionsFor(field: MentionField): List<AccountSuggestion> {
        if (!state.isLoaded) return emptyList()
        if (field.target == PublishTarget.Bluesky && !state.hasBluesky) return emptyList()
        val typed = mentionBeingTyped(textOf(field)) ?: return emptyList()
        val fromService = if (field.target == PublishTarget.Bluesky) {
            liveSuggestions[typed.lowercase()].orEmpty()
        } else {
            emptyList()
        }
        return suggestMentions(typed, usedBefore[field.target].orEmpty(), fromService)
    }

    /** Finishes the mention being typed in [field] with [suggestion]. */
    fun applySuggestion(field: MentionField, suggestion: AccountSuggestion) {
        if (!state.isLoaded) return
        when (field) {
            MentionField.Caption -> update { it.copy(caption = completeMention(it.caption, suggestion.handle)) }
            MentionField.BlueskyText -> update { it.copy(blueskyText = completeMention(it.blueskyText, suggestion.handle)) }
            MentionField.InstagramTags, MentionField.BlueskyTags -> update {
                val tags = completeMention(it.tagsFor(field.target), suggestion.handle)
                it.copy(tags = it.tags + (field.target to tags))
            }
        }
    }

    /**
     * Blocking. Asks Bluesky for accounts matching each unfinished mention in
     * the Bluesky boxes. Only what was typed after the @ is sent.
     */
    fun fetchSuggestions() {
        if (!state.isLoaded || !state.hasBluesky) return
        val bluesky = publishers.firstOrNull { it.target == PublishTarget.Bluesky } ?: return
        listOf(MentionField.BlueskyText, MentionField.BlueskyTags)
            .mapNotNull { mentionBeingTyped(textOf(it))?.lowercase() }
            .filter { it.length >= MinTypedForSuggestions && it !in liveSuggestions }
            .distinct()
            .forEach { typed ->
                val found = try {
                    bluesky.suggestAccounts(typed)
                } catch (_: Exception) {
                    emptyList()
                }
                liveSuggestions = liveSuggestions + (typed to found)
            }
    }

    /**
     * Blocking. Adds the accounts each service says this account has
     * mentioned before to the ones remembered on the phone.
     */
    fun loadMentionHistory() {
        if (!state.isLoaded) return
        publishers.filter { it.isConfigured }.forEach { publisher ->
            val names = try {
                publisher.pastMentions()
            } catch (_: Exception) {
                emptyList()
            }
            if (names.isNotEmpty()) {
                synchronized(mentionLock) {
                    val known = usedBefore[publisher.target].orEmpty()
                    usedBefore = usedBefore + (publisher.target to (known + names).distinctBy { it.lowercase() })
                }
            }
        }
    }

    /** Remembers who was mentioned in [text], so they are offered next time. */
    private fun rememberMentionsUsed(target: PublishTarget, text: String) {
        val names = mentionNamesFor(target, text)
        if (names.isEmpty()) return
        runCatching {
            settingsRepository.saveSettings(settingsRepository.readSettings().withMentionsUsed(target, names))
        }
        synchronized(mentionLock) {
            usedBefore = usedBefore + (target to rememberMentions(usedBefore[target].orEmpty(), names))
        }
    }

    fun updateAltText(text: String) {
        if (!state.isLoaded) return
        update { it.copy(altText = text) }
    }

    fun persistAltText() {
        if (!state.isLoaded) return
        store.saveAltText(draftId, state.altText)
    }

    fun selectAltTextModel(model: String) {
        if (!state.isLoaded || model !in state.altTextModels) return
        update { it.copy(altTextModel = model) }
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
        if (writer == null || sourceUri == null) return
        if (!updateIf({ it.canWriteAltText }) { it.copy(isWritingAltText = true, message = null) }) return
        val jpeg = renderer.encodeForDescription(sourceUri, current.treatment)
        if (jpeg == null) {
            update { it.copy(isWritingAltText = false, message = "The photo could not be prepared for describing.") }
            return
        }
        when (val result = writer.write(jpeg, current.caption, current.altTextModel)) {
            is AiResult.Success -> {
                // Only if the post is still open: it may have been removed while the description was on its way.
                val stillOpen = updateIf({ it.isLoaded }) { it.copy(isWritingAltText = false, altText = result.text) }
                if (stillOpen) store.saveAltText(draftId, result.text)
            }

            is AiResult.Failure -> {
                updateIf({ it.isLoaded }) { it.copy(isWritingAltText = false, message = result.message) }
            }
        }
    }

    /**
     * Puts the caption on the clipboard exactly as typed, without any
     * service's hashtags: the starting point for a version edited by hand,
     * such as the Bluesky text.
     */
    fun copyCaption() {
        val text = state.caption.trim()
        if (text.isEmpty()) return
        val message = try {
            clipboardWriter.copy("caption", text)
            "Caption copied."
        } catch (_: Exception) {
            "The caption could not be copied."
        }
        update { it.copy(message = message) }
    }

    fun copyAltText() {
        val text = state.altText.trim()
        if (text.isEmpty()) return
        val message = try {
            clipboardWriter.copy("alt text", text)
            "Alt text copied."
        } catch (_: Exception) {
            "The alt text could not be copied."
        }
        update { it.copy(message = message) }
    }

    /**
     * Changes how the photo is framed or treated. Not while it is being shared
     * or posted: the image going out was made from the framing as it was, and
     * that image is what gets recorded for the post.
     */
    private inline fun changeTreatment(change: (PhotoTreatment) -> PhotoTreatment) {
        updateIf({ it.isLoaded && !it.isPublishing && !it.isSharing }) { it.copy(treatment = change(it.treatment)) }
    }

    fun selectShape(shape: CropShape) {
        if (!state.isLoaded || state.treatment.cropShape == shape) return
        changeTreatment { it.withShape(shape) }
    }

    /** A drag and pinch on the photo, in fractions of the visible crop. */
    fun panZoom(panXFraction: Float, panYFraction: Float, zoomChange: Float) {
        val size = state.imageSize ?: return
        changeTreatment {
            it.panZoomed(
                panXFraction = panXFraction,
                panYFraction = panYFraction,
                zoomChange = zoomChange,
                imageWidth = size.width,
                imageHeight = size.height,
            )
        }
    }

    fun setLookEnabled(enabled: Boolean) {
        changeTreatment { it.copy(lookEnabled = enabled) }
    }

    fun setLogoEnabled(enabled: Boolean) {
        changeTreatment { it.copy(logoEnabled = enabled) }
    }

    fun setLogoCorner(corner: LogoCorner) {
        changeTreatment { it.copy(logoCorner = corner) }
    }

    fun persistTreatment() {
        if (!state.isLoaded) return
        store.saveTreatment(draftId, state.treatment)
    }

    fun dismissMessage() {
        update { it.copy(message = null) }
    }

    /**
     * Writes the finished image, puts the caption on the clipboard and opens
     * the share sheet. Nothing is posted by the app itself.
     */
    fun share() {
        val current = state
        val sourceUri = current.originalUri
        if (sourceUri == null) return
        if (!updateIf({ it.canShare }) { it.copy(isSharing = true, message = null) }) return
        store.saveCaption(draftId, current.caption)
        store.saveAltText(draftId, current.altText)
        persistTags()
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
                update { it.copy(isSharing = false, message = outcome.message) }
            }

            is RenderOutcome.Success -> {
                val caption = current.instagramPostText
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
                    rememberMentionsUsed(PublishTarget.Instagram, caption)
                    renderedUri = outcome.uri
                    if (previous != null && previous != outcome.uri) renderer.discard(previous)
                    update { it.copy(isSharing = false, shared = true, message = "Image ready.$clipboardNote") }
                } else {
                    renderer.discard(outcome.uri)
                    update {
                        it.copy(
                            isSharing = false,
                            message = result.errorMessage ?: "The share sheet could not be opened.",
                        )
                    }
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
            PublishTarget.Instagram -> current.instagramPostText
            PublishTarget.Bluesky -> current.blueskyPostText
        }
        publisher.problemWith(text)?.let { problem ->
            update { it.copy(message = problem) }
            return
        }
        val forInstagram = target == PublishTarget.Instagram
        if (forInstagram) {
            current.collaboratorsProblem?.let { problem ->
                update { it.copy(message = problem) }
                return
            }
        }
        // Only one post or share runs at a time; a second tap that slipped in is dropped here.
        val started = updateIf({ it.canShare }) { latest ->
            latest.withTarget(target) { it.copy(isPublishing = true) }.copy(message = null)
        }
        if (!started) return
        store.saveCaption(draftId, current.caption)
        store.saveAltText(draftId, current.altText)
        store.saveBlueskyText(draftId, current.blueskyText)
        store.saveInstagramExtras(draftId, current.photoTags, current.collaborators)
        persistTags()
        store.saveTreatment(draftId, current.treatment)

        fun finish(message: String, postedUrl: String? = null) {
            update { latest ->
                latest.withTarget(target) {
                    it.copy(isPublishing = false, postedUrl = postedUrl ?: it.postedUrl)
                }.copy(message = message, shared = latest.shared || postedUrl != null)
            }
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
                        userTags = if (forInstagram) current.photoTags else emptyList(),
                        collaborators = if (forInstagram) {
                            parseCollaborators(current.collaborators).mapNotNull(::instagramNameOrNull)
                        } else {
                            emptyList()
                        },
                    ),
                )
                when (result) {
                    is PublishResult.Success -> {
                        val previous = renderedUri
                        store.markPublished(draftId, target, result.url, outcome.uri)
                        rememberMentionsUsed(target, text)
                        renderedUri = outcome.uri
                        if (previous != null && previous != outcome.uri) renderer.discard(previous)
                        finish(
                            "Posted to ${target.label} as ${publisher.accountLabel.orEmpty()}." +
                                result.note?.let { " $it" }.orEmpty(),
                            result.url,
                        )
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

    private companion object {
        const val MinTypedForSuggestions = 2
    }

    /**
     * Removes the post from the list and deletes the image made from it. Not
     * while it is being shared, posted or described: that would finish
     * afterwards and write to a post that is gone.
     */
    fun remove() {
        val removing = updateIf({ it.isLoaded && !it.isPublishing && !it.isSharing && !it.isWritingAltText }) {
            PostEditorState(isMissing = true)
        }
        if (!removing) {
            update { if (it.isLoaded) it.copy(message = "Wait for this to finish before removing the post.") else it }
            return
        }
        renderedUri?.let(renderer::discard)
        renderedUri = null
        store.remove(draftId)
    }
}
