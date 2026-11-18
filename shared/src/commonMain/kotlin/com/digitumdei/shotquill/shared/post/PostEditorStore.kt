package com.digitumdei.shotquill.shared.post

import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
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
    /** Null until the photo has been framed at least once. */
    val treatment: PhotoTreatment?,
    val renderedUri: String?,
    val shared: Boolean,
)

/** The few things the app needs to keep about a post: the photo, the words and how the photo is treated. */
interface PostEditorStore {
    fun listPosts(): List<PostSummary>
    fun load(id: PostDraftId): EditablePost?
    fun saveCaption(id: PostDraftId, caption: String)
    fun saveTreatment(id: PostDraftId, treatment: PhotoTreatment)
    fun markShared(id: PostDraftId, renderedUri: String)
    fun remove(id: PostDraftId)
}

class DraftBackedPostEditorStore(
    private val drafts: PostDraftRepository,
    private val treatments: PhotoTreatmentStore,
    private val clock: EpochClock = EpochClock.Default,
) : PostEditorStore {
    override fun listPosts(): List<PostSummary> =
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

    override fun load(id: PostDraftId): EditablePost? {
        val draft = drafts.get(id)?.takeIf { it.status != DraftStatus.Archived } ?: return null
        val original = draft.mediaItems.minBy { it.order }.mediaAsset
        val stored = treatments.get(id)
        return EditablePost(
            id = draft.id,
            originalUri = original.uri,
            caption = draft.caption?.text.orEmpty(),
            treatment = stored?.treatment,
            renderedUri = stored?.renderedUri,
            shared = draft.status == DraftStatus.Shared,
        )
    }

    override fun saveCaption(id: PostDraftId, caption: String) {
        val draft = drafts.get(id) ?: return
        if (draft.caption?.text.orEmpty() == caption) return
        drafts.save(
            draft.copy(
                caption = if (caption.isBlank()) null else CaptionDraft(text = caption, hashtags = emptyList()),
                updatedAt = nextUpdatedAt(draft),
            ),
        )
    }

    override fun saveTreatment(id: PostDraftId, treatment: PhotoTreatment) {
        val existing = treatments.get(id)
        treatments.save(id, StoredTreatment(treatment = treatment, renderedUri = existing?.renderedUri))
    }

    override fun markShared(id: PostDraftId, renderedUri: String) {
        val existing = treatments.get(id)
        treatments.save(
            id,
            StoredTreatment(treatment = existing?.treatment ?: PhotoTreatment(), renderedUri = renderedUri),
        )
        val draft = drafts.get(id) ?: return
        // Saved directly rather than through updateStatus: the status ladder was
        // built for the old multi-step workflow and has no rung from "photo
        // added" straight to "shared".
        drafts.save(draft.copy(status = DraftStatus.Shared, updatedAt = nextUpdatedAt(draft)))
    }

    override fun remove(id: PostDraftId) {
        val draft = drafts.get(id)
        if (draft != null && draft.status != DraftStatus.Archived) {
            drafts.save(draft.copy(status = DraftStatus.Archived, updatedAt = nextUpdatedAt(draft)))
        }
        treatments.remove(id)
    }

    private fun nextUpdatedAt(draft: PostDraft): Instant {
        val now = Instant.fromEpochMilliseconds(clock.nowMillis())
        return if (now >= draft.updatedAt) now else draft.updatedAt
    }
}
