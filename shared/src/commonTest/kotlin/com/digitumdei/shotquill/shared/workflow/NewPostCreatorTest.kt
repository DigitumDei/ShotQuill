package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.storage.MediaAssetRepository
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NewPostCreatorTest {
    private val savedMediaAssets = mutableMapOf<MediaAssetId, MediaAsset>()
    private val savedDrafts = mutableMapOf<PostDraftId, PostDraft>()

    private val mediaAssetRepository = object : MediaAssetRepository {
        override fun save(mediaAsset: MediaAsset) {
            savedMediaAssets[mediaAsset.id] = mediaAsset
        }

        override fun get(id: MediaAssetId): MediaAsset? = savedMediaAssets[id]
    }

    private val postDraftRepository = object : PostDraftRepository {
        override fun save(postDraft: PostDraft) {
            savedDrafts[postDraft.id] = postDraft
        }

        override fun get(id: PostDraftId): PostDraft? = savedDrafts[id]

        override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: kotlinx.datetime.Instant): Boolean =
            false

        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean =
            false
    }

    private val creator = NewPostCreator(mediaAssetRepository, postDraftRepository)

    @Test
    fun createsDraftFromMediaWithAllMetadata() {
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
        assertEquals(DraftStatus.Draft, draft.status)
        assertEquals(1, draft.mediaItems.size)
        assertEquals(mediaAssetId, draft.mediaItems[0].mediaAsset.id)
        assertEquals(0, draft.mediaItems[0].order)
        assertTrue(draft.createdAt.toEpochMilliseconds() > 0)
        assertEquals(draft.createdAt, draft.updatedAt)
    }

    @Test
    fun createsDraftFromMediaWithoutDimensions() {
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

        val saved = mediaAssetRepository.get(mediaAssetId)
        assertNotNull(saved)
        assertEquals(MediaType.Photo, saved.type)
        assertEquals("file://test/photo.webp", saved.uri)
        assertEquals("image/webp", saved.mimeType)
        assertEquals(400, saved.widthPx)
        assertEquals(300, saved.heightPx)
    }

    @Test
    fun savesPostDraftToRepository() {
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
        assertEquals(DraftStatus.Draft, saved.status)
        assertEquals(PostFormat.SingleImage, saved.format)
        assertEquals(emptySet(), saved.targetPlatforms)
        assertEquals(null, saved.caption)
    }

    @Test
    fun createsCarouselPostDraftIfRequested() {
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
}
