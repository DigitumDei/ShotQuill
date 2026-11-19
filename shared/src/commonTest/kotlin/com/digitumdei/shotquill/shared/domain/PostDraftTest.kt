package com.digitumdei.shotquill.shared.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PostDraftTest {
    private val createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    private val updatedAt = Instant.fromEpochMilliseconds(1_700_000_060_000L)

    private fun photo(id: String = "media-1") = MediaAsset(
        id = MediaAssetId(id),
        uri = "file:///photos/$id.jpg",
        mimeType = "image/jpeg",
        widthPx = 4000,
        heightPx = 3000,
        createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    )

    private fun draft(
        format: PostFormat = PostFormat.SingleImage,
        mediaItems: List<PostMediaItem> = listOf(PostMediaItem(photo(), order = 0)),
        updatedAt: Instant = this.updatedAt,
    ) = PostDraft(
        id = PostDraftId("draft-1"),
        format = format,
        status = DraftStatus.PhotoAdded,
        mediaItems = mediaItems,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun aNewDraftHasItsPhotoAndNoWords() {
        val draft = draft()

        assertEquals("file:///photos/media-1.jpg", draft.mediaItems.single().mediaAsset.uri)
        assertNull(draft.caption)
        assertNull(draft.altText)
    }

    @Test
    fun aStoryOrCarouselMayHaveSeveralPhotos() {
        val items = listOf(PostMediaItem(photo("one"), order = 0), PostMediaItem(photo("two"), order = 1))

        assertEquals(2, draft(format = PostFormat.Story, mediaItems = items).mediaItems.size)
        assertEquals(2, draft(format = PostFormat.Carousel, mediaItems = items).mediaItems.size)
    }

    @Test
    fun aDraftMustHaveAPhoto() {
        assertFailsWith<IllegalArgumentException> { draft(mediaItems = emptyList()) }
    }

    @Test
    fun aSingleImagePostHasExactlyOnePhoto() {
        val items = listOf(PostMediaItem(photo("one"), order = 0), PostMediaItem(photo("two"), order = 1))

        assertFailsWith<IllegalArgumentException> { draft(format = PostFormat.SingleImage, mediaItems = items) }
    }

    @Test
    fun twoPhotosCannotShareAPlaceInTheOrder() {
        val items = listOf(PostMediaItem(photo("one"), order = 0), PostMediaItem(photo("two"), order = 0))

        assertFailsWith<IllegalArgumentException> { draft(format = PostFormat.Carousel, mediaItems = items) }
        assertFailsWith<IllegalArgumentException> { PostMediaItem(photo(), order = -1) }
    }

    @Test
    fun aDraftCannotBeChangedBeforeItWasCreated() {
        assertFailsWith<IllegalArgumentException> {
            draft(updatedAt = Instant.fromEpochMilliseconds(createdAt.toEpochMilliseconds() - 1))
        }
    }

    @Test
    fun aPhotoCannotHaveAnImpossibleSizeOrDate() {
        assertFailsWith<IllegalArgumentException> { photo().copy(widthPx = 0) }
        assertFailsWith<IllegalArgumentException> { photo().copy(heightPx = -1) }
        assertFailsWith<IllegalArgumentException> { photo().copy(createdAtEpochMillis = -1) }
        // A photo whose size could not be read is still a photo.
        assertNull(photo().copy(widthPx = null, heightPx = null).widthPx)
    }

    @Test
    fun aStatusIsStoredUnderAFixedName() {
        // These names are what is in the database: changing one orphans every stored post.
        assertEquals(
            listOf("photo_added", "shared", "archived"),
            DraftStatus.entries.map { it.wireValue },
        )
        DraftStatus.entries.forEach { assertEquals(it, DraftStatus.fromWireValue(it.wireValue)) }
        assertNull(DraftStatus.fromWireValue("text_generated"))
        assertEquals(listOf("SingleImage", "Carousel", "Story"), PostFormat.entries.map { it.name })
    }
}
