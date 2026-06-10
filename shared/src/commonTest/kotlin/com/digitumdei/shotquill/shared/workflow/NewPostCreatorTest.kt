package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.storage.UpdateSelectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NewPostCreatorTest {
    private val savedMediaAssets = mutableMapOf<MediaAssetId, MediaAsset>()
    private val savedDrafts = mutableMapOf<PostDraftId, PostDraft>()

    private fun saveMediaAsset(mediaAsset: MediaAsset) {
        savedMediaAssets[mediaAsset.id] = mediaAsset
    }

    private val postDraftRepository = object : PostDraftRepository {
        override fun save(postDraft: PostDraft) {
            postDraft.mediaItems.forEach { saveMediaAsset(it.mediaAsset) }
            savedDrafts[postDraft.id] = postDraft
        }

        override fun get(id: PostDraftId): PostDraft? = savedDrafts[id]

        override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: kotlinx.datetime.Instant): Boolean =
            false

        override fun updateUpdatedAt(id: PostDraftId, updatedAt: kotlinx.datetime.Instant): Boolean =
            false

        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean =
            false

        override fun updateSelectedMediaAsset(id: PostDraftId, mediaAssetId: MediaAssetId?, updatedAt: kotlinx.datetime.Instant): UpdateSelectionResult =
            UpdateSelectionResult.DraftNotFound
    }

    private fun getMediaAsset(id: MediaAssetId): MediaAsset? = savedMediaAssets[id]

    @Test
    fun createsDraftFromMediaWithAllMetadata() {
        val creator = NewPostCreator(postDraftRepository)
        val draftId = PostDraftId("draft-test-1")
        val mediaAssetId = MediaAssetId("media-test-1")

        val draft = creator.createDraftFromMedia(
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            format = PostFormat.SingleImage,
            uri = "file://test/photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 1920,
            heightPx = 1080,
        )

        assertEquals(draftId, draft.id)
        assertEquals(PostFormat.SingleImage, draft.format)
        assertEquals(DraftStatus.PhotoAdded, draft.status)
        assertEquals(1, draft.mediaItems.size)
        assertEquals(mediaAssetId, draft.mediaItems[0].mediaAsset.id)
        assertEquals(0, draft.mediaItems[0].order)
        assertTrue(draft.createdAt.toEpochMilliseconds() > 0)
        assertEquals(draft.createdAt, draft.updatedAt)
    }

    @Test
    fun createsDraftFromMediaWithoutDimensions() {
        val creator = NewPostCreator(postDraftRepository)
        val draftId = PostDraftId("draft-test-2")
        val mediaAssetId = MediaAssetId("media-test-2")

        val draft = creator.createDraftFromMedia(
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            format = PostFormat.SingleImage,
            uri = "file://test/photo.png",
            mimeType = "image/png",
            widthPx = null,
            heightPx = null,
        )

        assertEquals(draftId, draft.id)
        assertEquals(1, draft.mediaItems.size)
        assertEquals(mediaAssetId, draft.mediaItems[0].mediaAsset.id)
    }

    @Test
    fun savesMediaAssetToRepository() {
        val creator = NewPostCreator(postDraftRepository)
        val draftId = PostDraftId("draft-test-3")
        val mediaAssetId = MediaAssetId("media-test-3")

        creator.createDraftFromMedia(
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            format = PostFormat.SingleImage,
            uri = "file://test/photo.webp",
            mimeType = "image/webp",
            widthPx = 400,
            heightPx = 300,
        )

        val saved = getMediaAsset(mediaAssetId)
        assertNotNull(saved)
        assertEquals(MediaType.Photo, saved.type)
        assertEquals("file://test/photo.webp", saved.uri)
        assertEquals("image/webp", saved.mimeType)
        assertEquals(400, saved.widthPx)
        assertEquals(300, saved.heightPx)
    }

    @Test
    fun savesPostDraftToRepository() {
        val creator = NewPostCreator(postDraftRepository)
        val draftId = PostDraftId("draft-test-4")
        val mediaAssetId = MediaAssetId("media-test-4")

        creator.createDraftFromMedia(
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            format = PostFormat.SingleImage,
            uri = "file://test/photo.jpg",
            mimeType = null,
            widthPx = null,
            heightPx = null,
        )

        val saved = postDraftRepository.get(draftId)
        assertNotNull(saved)
        assertEquals(DraftStatus.PhotoAdded, saved.status)
        assertEquals(PostFormat.SingleImage, saved.format)
        assertEquals(emptySet(), saved.targetPlatforms)
        assertEquals(null, saved.caption)
    }

    @Test
    fun createsCarouselPostDraftIfRequested() {
        val creator = NewPostCreator(postDraftRepository)
        val draftId = PostDraftId("draft-test-5")
        val mediaAssetId = MediaAssetId("media-test-5")

        val draft = creator.createDraftFromMedia(
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            format = PostFormat.Carousel,
            uri = "file://test/carousel.jpg",
            mimeType = "image/jpeg",
            widthPx = 1080,
            heightPx = 1080,
        )

        assertEquals(PostFormat.Carousel, draft.format)
        assertEquals(1, draft.mediaItems.size)
    }

    @Test
    fun usesInjectedClockForTimestamps() {
        val knownEpochMillis = 1_700_000_000_000L
        val clock = object : EpochClock {
            override fun nowMillis(): Long = knownEpochMillis
        }
        val creator = NewPostCreator(postDraftRepository, clock)
        val draftId = PostDraftId("draft-clock-1")
        val mediaAssetId = MediaAssetId("media-clock-1")

        val draft = creator.createDraftFromMedia(
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            format = PostFormat.SingleImage,
            uri = "file://test/clock-photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 800,
            heightPx = 600,
        )

        assertEquals(knownEpochMillis, draft.createdAt.toEpochMilliseconds())
        assertEquals(knownEpochMillis, draft.updatedAt.toEpochMilliseconds())
        assertEquals(knownEpochMillis, draft.mediaItems[0].mediaAsset.createdAtEpochMillis)

        val savedMedia = getMediaAsset(mediaAssetId)
        assertEquals(knownEpochMillis, savedMedia?.createdAtEpochMillis)
    }

    @Test
    fun propagatesRepositorySaveException() {
        val failingRepo = object : PostDraftRepository {
            override fun save(postDraft: PostDraft) {
                throw RuntimeException("SQLite disk full")
            }

            override fun get(id: PostDraftId): PostDraft? = null

            override fun updateStatus(
                id: PostDraftId,
                status: DraftStatus,
                updatedAt: kotlinx.datetime.Instant,
            ): Boolean = false

            override fun updateUpdatedAt(
                id: PostDraftId,
                updatedAt: kotlinx.datetime.Instant,
            ): Boolean = false

            override fun replaceMediaItems(
                id: PostDraftId,
                mediaItems: List<MediaAssetId>,
            ): Boolean = false

            override fun updateSelectedMediaAsset(
                id: PostDraftId,
                mediaAssetId: MediaAssetId?,
                updatedAt: kotlinx.datetime.Instant,
            ): UpdateSelectionResult = UpdateSelectionResult.DraftNotFound
        }
        val creator = NewPostCreator(failingRepo)

        assertFailsWith<RuntimeException> {
            creator.createDraftFromMedia(
                draftId = PostDraftId("draft-test-fail"),
                mediaAssetId = MediaAssetId("media-test-fail"),
                format = PostFormat.SingleImage,
                uri = "file://test/photo.jpg",
                mimeType = "image/jpeg",
                widthPx = 1920,
                heightPx = 1080,
            )
        }
    }
}
