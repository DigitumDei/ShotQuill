package com.digitumdei.shotquill.shared.post

import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.social.PhotoTag
import com.digitumdei.shotquill.shared.social.PublishTarget
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import kotlinx.datetime.Instant

/** One post as the home screen shows it. */
data class PostSummary(
    val id: PostDraftId,
    val originalUri: String?,
    val thumbnailUri: String?,
    val caption: String?,
    val shared: Boolean,
    val updatedAtEpochMillis: Long,
)

/** One post as the editor works on it. */
data class EditablePost(
    val id: PostDraftId,
    val originalUri: String,
    val caption: String,
    val altText: String,
    /** Null until the photo has been framed at least once. */
    val treatment: PhotoTreatment?,
    val renderedUri: String?,
    val shared: Boolean,
    /** Written for Bluesky only; empty means the caption is used there too. */
    val blueskyText: String = "",
    /** A link to the post on each service it was published to directly. */
    val published: Map<PublishTarget, String> = emptyMap(),
    /** Hashtags set on this post, per service. A service that is absent uses the usual tags from settings. */
    val tags: Map<PublishTarget, String> = emptyMap(),
    /** People tagged on the photo, for Instagram. */
    val photoTags: List<PhotoTag> = emptyList(),
    /** The Instagram collaborators box, as typed. */
    val collaborators: String = "",
)

/** The few things the app needs to keep about a post: the photo, the words and how the photo is treated. */
interface PostEditorStore {
    fun listPosts(): List<PostSummary>
    fun load(id: PostDraftId): EditablePost?
    fun saveCaption(id: PostDraftId, caption: String)
    fun saveAltText(id: PostDraftId, altText: String)
    fun saveTreatment(id: PostDraftId, treatment: PhotoTreatment)
    fun saveBlueskyText(id: PostDraftId, text: String)
    fun saveTags(id: PostDraftId, target: PublishTarget, tags: String)
    fun markShared(id: PostDraftId, renderedUri: String)

    /** Records that the post went out to [target] and where it can be seen. */
    fun markPublished(id: PostDraftId, target: PublishTarget, url: String, renderedUri: String)
    fun remove(id: PostDraftId)

    /** Keeps who is tagged on the photo and who is invited as a collaborator, for Instagram. */
    fun saveInstagramExtras(id: PostDraftId, photoTags: List<PhotoTag>, collaborators: String) {}

    /**
     * The original photos of posts that were removed from the list. Removing a
     * post only hides it, so these stay on the phone until they are cleared.
     */
    fun removedPostPhotos(): List<String> = emptyList()

    /**
     * Deletes every removed post's record for good and returns the addresses
     * of the photos that went with them, for the caller to delete.
     */
    fun clearRemovedPosts(): List<String> = emptyList()
}

class DraftBackedPostEditorStore(
    private val drafts: PostDraftRepository,
    private val treatments: PhotoTreatmentStore,
    private val clock: EpochClock = EpochClock.Default,
) : PostEditorStore {
    // Each change here reads what is stored for a post, alters part of it and
    // writes the whole thing back. Saving while typing, sharing and posting can
    // reach the store from different threads at once, so every operation runs
    // under one lock: none can write back a copy that another has since changed.
    // Nothing in here waits on the network, so the lock is only ever held briefly.
    private val lock = Any()

    override fun listPosts(): List<PostSummary> = synchronized(lock) {
        drafts.listPostDrafts()
            .filter { it.status != DraftStatus.Archived }
            .map { summary ->
                PostSummary(
                    id = summary.id,
                    originalUri = summary.primaryMediaUri,
                    thumbnailUri = treatments.get(summary.id)?.renderedUri ?: summary.primaryMediaUri,
                    caption = summary.captionText?.takeIf { it.isNotBlank() },
                    shared = summary.status == DraftStatus.Shared,
                    updatedAtEpochMillis = summary.updatedAtEpochMillis,
                )
            }
    }

    override fun removedPostPhotos(): List<String> = synchronized(lock) {
        val all = drafts.listPostDrafts()
        // A photo a listed post still uses is not freed by clearing, so it is not counted.
        val stillUsed = all.filter { it.status != DraftStatus.Archived }.mapNotNull { it.primaryMediaUri }.toSet()
        all.filter { it.status == DraftStatus.Archived }
            .flatMap { removed -> listOfNotNull(removed.primaryMediaUri, treatments.get(removed.id)?.renderedUri) }
            .filter { it !in stillUsed }
            .distinct()
    }

    override fun clearRemovedPosts(): List<String> = synchronized(lock) {
        drafts.listPostDrafts()
            .filter { it.status == DraftStatus.Archived }
            .flatMap { removed ->
                val rendered = treatments.get(removed.id)?.renderedUri
                treatments.remove(removed.id)
                drafts.delete(removed.id) + listOfNotNull(rendered)
            }
            .distinct()
    }

    override fun load(id: PostDraftId): EditablePost? {
        synchronized(lock) {
            val draft = drafts.get(id)?.takeIf { it.status != DraftStatus.Archived } ?: return null
            val original = draft.mediaItems.minBy { it.order }.mediaAsset
            val stored = treatments.get(id)
            return EditablePost(
                id = draft.id,
                originalUri = original.uri,
                caption = draft.caption.orEmpty(),
                altText = draft.altText.orEmpty(),
                treatment = stored?.treatment,
                renderedUri = stored?.renderedUri,
                shared = draft.status == DraftStatus.Shared,
                blueskyText = stored?.blueskyText.orEmpty(),
                published = stored?.published.orEmpty(),
                tags = stored?.tags.orEmpty(),
                photoTags = stored?.photoTags.orEmpty(),
                collaborators = stored?.collaborators.orEmpty(),
            )
        }
    }

    override fun saveInstagramExtras(id: PostDraftId, photoTags: List<PhotoTag>, collaborators: String) {
        synchronized(lock) {
            if (listed(id) == null) return
            val existing = stored(id)
            if (existing.photoTags == photoTags && existing.collaborators == collaborators) return
            treatments.save(id, existing.copy(photoTags = photoTags, collaborators = collaborators))
        }
    }

    override fun saveCaption(id: PostDraftId, caption: String) {
        synchronized(lock) {
            val draft = listed(id) ?: return
            if (draft.caption.orEmpty() == caption) return
            drafts.save(draft.copy(caption = caption.takeIf { it.isNotBlank() }, updatedAt = nextUpdatedAt(draft)))
        }
    }

    override fun saveAltText(id: PostDraftId, altText: String) {
        synchronized(lock) {
            val draft = listed(id) ?: return
            if (draft.altText.orEmpty() == altText) return
            drafts.save(draft.copy(altText = altText.takeIf { it.isNotBlank() }, updatedAt = nextUpdatedAt(draft)))
        }
    }

    override fun saveTreatment(id: PostDraftId, treatment: PhotoTreatment) {
        synchronized(lock) {
            if (listed(id) == null) return
            treatments.save(id, stored(id).copy(treatment = treatment))
        }
    }

    override fun saveBlueskyText(id: PostDraftId, text: String) {
        synchronized(lock) {
            if (listed(id) == null) return
            val existing = stored(id)
            if (existing.blueskyText == text) return
            treatments.save(id, existing.copy(blueskyText = text))
        }
    }

    override fun saveTags(id: PostDraftId, target: PublishTarget, tags: String) {
        synchronized(lock) {
            if (listed(id) == null) return
            val existing = stored(id)
            if (existing.tags[target] == tags) return
            treatments.save(id, existing.copy(tags = existing.tags + (target to tags)))
        }
    }

    override fun markPublished(id: PostDraftId, target: PublishTarget, url: String, renderedUri: String) {
        synchronized(lock) {
            if (!keepAsShared(id, renderedUri)) return
            val existing = stored(id)
            treatments.save(id, existing.copy(published = existing.published + (target to url)))
        }
    }

    private fun stored(id: PostDraftId): StoredTreatment = treatments.get(id) ?: StoredTreatment(PhotoTreatment())

    /**
     * The draft, when it is still on the list. A save that arrives after the
     * post was removed, such as alt text that was being written at the time,
     * finds nothing here and is dropped rather than written onto a removed post.
     */
    private fun listed(id: PostDraftId): PostDraft? = drafts.get(id)?.takeIf { it.status != DraftStatus.Archived }

    override fun markShared(id: PostDraftId, renderedUri: String) {
        synchronized(lock) {
            keepAsShared(id, renderedUri)
        }
    }

    /**
     * Records the finished image and marks the post shared, and says whether
     * it did. A post that was removed in the meantime stays removed: nothing
     * is recorded for it, so a share or a post that finishes late cannot bring
     * it back to the list.
     */
    private fun keepAsShared(id: PostDraftId, renderedUri: String): Boolean {
        val draft = drafts.get(id)?.takeIf { it.status != DraftStatus.Archived } ?: return false
        treatments.save(id, stored(id).copy(renderedUri = renderedUri))
        drafts.save(draft.copy(status = DraftStatus.Shared, updatedAt = nextUpdatedAt(draft)))
        return true
    }

    override fun remove(id: PostDraftId) {
        synchronized(lock) {
            val draft = drafts.get(id)
            if (draft != null && draft.status != DraftStatus.Archived) {
                drafts.save(draft.copy(status = DraftStatus.Archived, updatedAt = nextUpdatedAt(draft)))
            }
            treatments.remove(id)
        }
    }

    private fun nextUpdatedAt(draft: PostDraft): Instant {
        val now = Instant.fromEpochMilliseconds(clock.nowMillis())
        return if (now >= draft.updatedAt) now else draft.updatedAt
    }
}
