package com.digitumdei.shotquill.shared.post

import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.DraftSummary
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.settings.SnippetCodec
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.storage.UpdateSelectionResult
import com.digitumdei.shotquill.shared.workflow.NewPostCreator
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostEditorStoreTest {
    private val clock = SteppingClock()
    private val drafts = InMemoryPostDraftRepository()
    private val treatments = InMemoryPhotoTreatmentStore()
    private val store = DraftBackedPostEditorStore(drafts, treatments, clock)

    private fun newPost(id: String, uri: String = "file:///photos/$id.jpg"): PostDraftId {
        val draftId = PostDraftId(id)
        NewPostCreator(drafts, clock).createDraftFromMedia(
            draftId = draftId,
            mediaAssetId = MediaAssetId("media-$id"),
            format = PostFormat.SingleImage,
            uri = uri,
            mimeType = "image/jpeg",
            widthPx = 4000,
            heightPx = 3000,
        )
        return draftId
    }

    @Test
    fun aNewPostOpensWithItsPhotoAndNothingElse() {
        val id = newPost("one")

        val post = assertNotNull(store.load(id))

        assertEquals("file:///photos/one.jpg", post.originalUri)
        assertEquals("", post.caption)
        assertEquals("", post.altText)
        assertNull(post.treatment)
        assertNull(post.renderedUri)
        assertFalse(post.shared)
    }

    @Test
    fun loadingAnUnknownPostGivesNothing() {
        assertNull(store.load(PostDraftId("missing")))
    }

    @Test
    fun captionIsKeptAndShownInTheList() {
        val id = newPost("one")

        store.saveCaption(id, "First pour of the witbier.")

        assertEquals("First pour of the witbier.", store.load(id)?.caption)
        assertEquals("First pour of the witbier.", store.listPosts().single().caption)
    }

    @Test
    fun clearingTheCaptionRemovesIt() {
        val id = newPost("one")
        store.saveCaption(id, "Something")

        store.saveCaption(id, "   ")

        assertEquals("", store.load(id)?.caption)
        assertNull(store.listPosts().single().caption)
    }

    @Test
    fun savingAnUnchangedCaptionDoesNotTouchTheDraft() {
        val id = newPost("one")
        store.saveCaption(id, "Same")
        val savesBefore = drafts.saveCount

        store.saveCaption(id, "Same")
        store.saveCaption(PostDraftId("missing"), "Ignored")

        assertEquals(savesBefore, drafts.saveCount)
    }

    @Test
    fun altTextIsKeptBesideTheCaption() {
        val id = newPost("one")
        store.saveCaption(id, "First pour.")

        store.saveAltText(id, "A glass of beer on a bar.")
        store.saveCaption(id, "First pour, edited.")

        val post = assertNotNull(store.load(id))
        assertEquals("A glass of beer on a bar.", post.altText)
        assertEquals("First pour, edited.", post.caption)
    }

    @Test
    fun clearingOrRepeatingAltTextBehavesLikeTheCaption() {
        val id = newPost("one")
        store.saveAltText(id, "Something")
        val savesBefore = drafts.saveCount

        store.saveAltText(id, "Something")
        store.saveAltText(PostDraftId("missing"), "Ignored")
        assertEquals(savesBefore, drafts.saveCount)

        store.saveAltText(id, "  ")
        assertEquals("", store.load(id)?.altText)
    }

    @Test
    fun treatmentIsKeptPerPost() {
        val first = newPost("one")
        val second = newPost("two")
        val treatment = PhotoTreatment(cropShape = CropShape.Square, zoom = 2f, logoCorner = LogoCorner.TopStart)

        store.saveTreatment(first, treatment)

        assertEquals(treatment, store.load(first)?.treatment)
        assertNull(store.load(second)?.treatment)
    }

    @Test
    fun sharingMarksThePostAndRemembersTheImage() {
        val id = newPost("one")
        store.saveTreatment(id, PhotoTreatment(cropShape = CropShape.Story))

        store.markShared(id, "file:///edited/one.jpg")

        val post = assertNotNull(store.load(id))
        assertTrue(post.shared)
        assertEquals("file:///edited/one.jpg", post.renderedUri)
        assertEquals(CropShape.Story, post.treatment?.cropShape)
        val summary = store.listPosts().single()
        assertTrue(summary.shared)
        assertEquals("file:///edited/one.jpg", summary.thumbnailUri)
        assertEquals("file:///photos/one.jpg", summary.originalUri)
    }

    @Test
    fun sharingAgainKeepsThePostSharedAndSwapsTheImage() {
        val id = newPost("one")
        store.markShared(id, "file:///edited/first.jpg")

        store.markShared(id, "file:///edited/second.jpg")
        store.saveTreatment(id, PhotoTreatment(cropShape = CropShape.Square))

        val post = assertNotNull(store.load(id))
        assertTrue(post.shared)
        assertEquals("file:///edited/second.jpg", post.renderedUri)
    }

    @Test
    fun sharingAPostThatNoLongerExistsOnlyRecordsTheImage() {
        store.markShared(PostDraftId("missing"), "file:///edited/orphan.jpg")

        assertTrue(store.listPosts().isEmpty())
    }

    @Test
    fun aPostWithoutAnImageYetUsesTheOriginalAsItsThumbnail() {
        newPost("one")

        assertEquals("file:///photos/one.jpg", store.listPosts().single().thumbnailUri)
    }

    @Test
    fun removingAPostHidesItAndForgetsItsTreatment() {
        val keep = newPost("keep")
        val gone = newPost("gone")
        store.saveTreatment(gone, PhotoTreatment())

        store.remove(gone)
        store.remove(gone)
        store.remove(PostDraftId("missing"))

        assertEquals(listOf(keep), store.listPosts().map { it.id })
        assertNull(store.load(gone))
        assertNull(treatments.get(gone))
        assertEquals(DraftStatus.Archived, drafts.get(gone)?.status)
    }

    @Test
    fun updatedAtNeverMovesBackwards() {
        val id = newPost("one")
        val created = assertNotNull(drafts.get(id)).updatedAt
        clock.now = 0L

        store.saveCaption(id, "Written with a clock that went backwards")

        assertEquals(created, drafts.get(id)?.updatedAt)
    }

    @Test
    fun storedTreatmentSurvivesEncoding() {
        val stored = StoredTreatment(
            treatment = PhotoTreatment(
                cropShape = CropShape.Story,
                zoom = 2.5f,
                centerX = 0.25f,
                centerY = 0.75f,
                lookEnabled = false,
                logoEnabled = false,
                logoCorner = LogoCorner.TopEnd,
            ),
            renderedUri = "file:///data/user/0/app/files/media/edited/draft=1.jpg",
        )

        assertEquals(stored, StoredTreatmentCodec.decode(StoredTreatmentCodec.encode(stored)))
        assertEquals(
            StoredTreatment(PhotoTreatment()),
            StoredTreatmentCodec.decode(StoredTreatmentCodec.encode(StoredTreatment(PhotoTreatment()))),
        )
    }

    @Test
    fun damagedTreatmentFallsBackToDefaults() {
        val decoded = StoredTreatmentCodec.decode(
            "shape=hexagon\nzoom=NaN\ncx=7\ncy=oops\nlook=maybe\nnonsense\ncorner=middle\nrendered=",
        )

        assertEquals(PhotoTreatment().copy(centerX = 1f), decoded.treatment)
        assertNull(decoded.renderedUri)
        assertEquals(StoredTreatment(PhotoTreatment()), StoredTreatmentCodec.decode(""))
    }

    @Test
    fun snippetsSurviveEncodingIncludingLineBreaksAndBackslashes() {
        val snippets = listOf(
            CaptionSnippet(name = "18+", text = "Not for persons under the age of 18."),
            CaptionSnippet(name = "Tags", text = "#one\n#two\\n literal"),
            CaptionSnippet(name = "", text = ""),
        )

        assertEquals(snippets, SnippetCodec.decode(SnippetCodec.encode(snippets)))
        assertEquals(emptyList(), SnippetCodec.decode(SnippetCodec.encode(emptyList())))
        assertEquals(listOf(CaptionSnippet("a", "b")), SnippetCodec.decode("a\nb\ndangling"))
    }
}

private class SteppingClock(var now: Long = 1_700_000_000_000L) : EpochClock {
    override fun nowMillis(): Long = now.also { now += 1_000L }
}

private class InMemoryPostDraftRepository : PostDraftRepository {
    private val drafts = linkedMapOf<PostDraftId, PostDraft>()
    var saveCount = 0
        private set

    override fun save(postDraft: PostDraft) {
        saveCount++
        drafts[postDraft.id] = postDraft
    }

    override fun get(id: PostDraftId): PostDraft? = drafts[id]

    override fun listPostDrafts(): List<DraftSummary> =
        drafts.values
            .sortedByDescending { it.updatedAt }
            .map { draft ->
                DraftSummary(
                    id = draft.id,
                    status = draft.status,
                    captionText = draft.caption?.text,
                    createdAtEpochMillis = draft.createdAt.toEpochMilliseconds(),
                    updatedAtEpochMillis = draft.updatedAt.toEpochMilliseconds(),
                    primaryMediaUri = draft.mediaItems.minByOrNull { it.order }?.mediaAsset?.uri,
                )
            }

    override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean = false

    override fun updateUpdatedAt(id: PostDraftId, updatedAt: Instant): Boolean = false

    override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean = false

    override fun updateSelectedMediaAsset(
        id: PostDraftId,
        mediaAssetId: MediaAssetId?,
        updatedAt: Instant,
    ): UpdateSelectionResult = UpdateSelectionResult.DraftNotFound
}
