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
import com.digitumdei.shotquill.shared.social.PublishTarget
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.workflow.NewPostCreator
import kotlinx.datetime.Instant
import com.digitumdei.shotquill.shared.social.PhotoTag
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
    fun removedPostsKeepTheirPhotosUntilTheyAreCleared() {
        val kept = newPost("kept")
        val gone = newPost("gone")
        val alsoGone = newPost("also-gone")
        store.markShared(alsoGone, "file:///edited/also-gone.jpg")
        assertTrue(store.removedPostPhotos().isEmpty())

        store.remove(gone)
        store.remove(alsoGone)

        assertEquals(setOf("file:///photos/gone.jpg", "file:///photos/also-gone.jpg"), store.removedPostPhotos().toSet())
        assertEquals(listOf(kept), store.listPosts().map { it.id })
        // Removing a post is still only hiding it.
        assertNotNull(drafts.get(gone))

        val cleared = store.clearRemovedPosts()

        assertEquals(setOf("file:///photos/gone.jpg", "file:///photos/also-gone.jpg"), cleared.toSet())
        assertNull(drafts.get(gone))
        assertNull(drafts.get(alsoGone))
        assertNotNull(store.load(kept))
        assertTrue(store.removedPostPhotos().isEmpty())
        assertTrue(store.clearRemovedPosts().isEmpty())
    }

    @Test
    fun peopleTaggedOnThePhotoAndCollaboratorsAreKeptWithThePost() {
        val id = newPost("tagged")
        store.saveBlueskyText(id, "Short one")
        val tags = listOf(PhotoTag("some_friend", 0.2f, 0.8f), PhotoTag("another.one"))

        store.saveInstagramExtras(id, tags, "@some_friend another.one")

        val post = assertNotNull(store.load(id))
        assertEquals(tags, post.photoTags)
        assertEquals("@some_friend another.one", post.collaborators)
        // Nothing else stored beside the post is disturbed.
        assertEquals("Short one", post.blueskyText)

        store.saveInstagramExtras(id, emptyList(), "")
        val cleared = assertNotNull(store.load(id))
        assertTrue(cleared.photoTags.isEmpty())
        assertEquals("", cleared.collaborators)
        assertTrue(assertNotNull(store.load(newPost("plain"))).photoTags.isEmpty())
    }

    @Test
    fun aShareOrPostThatFinishesAfterThePostWasRemovedDoesNotBringItBack() {
        val id = newPost("late")
        store.remove(id)

        store.markPublished(id, PublishTarget.Bluesky, "https://bsky.app/profile/x/post/1", "file:///edited/late.jpg")
        store.markShared(id, "file:///edited/late.jpg")

        assertTrue(store.listPosts().isEmpty())
        assertNull(store.load(id))
        assertNull(treatments.get(id))
        assertEquals(listOf("file:///photos/late.jpg"), store.removedPostPhotos())
        // Nor is anything recorded for a post that never existed.
        store.markShared(PostDraftId("never"), "file:///edited/never.jpg")
        assertNull(treatments.get(PostDraftId("never")))
    }

    @Test
    fun changesToOnePostFromSeveralThreadsAtOnceAllSurvive() {
        val id = newPost("busy")
        val rounds = 400
        val start = java.util.concurrent.CountDownLatch(1)
        val workers = listOf<(Int) -> Unit>(
            { n -> store.saveBlueskyText(id, "text $n") },
            { n -> store.saveTags(id, PublishTarget.Instagram, "#tag$n") },
            { n -> store.saveInstagramExtras(id, listOf(PhotoTag("friend$n")), "collab$n") },
            { n -> store.saveTreatment(id, PhotoTreatment(zoom = 1f + (n % 10) / 10f)) },
            { n -> store.markPublished(id, PublishTarget.Bluesky, "https://example.test/$n", "file:///edited/$n.jpg") },
        ).map { work ->
            kotlin.concurrent.thread {
                start.await()
                repeat(rounds) { work(it) }
            }
        }
        start.countDown()
        workers.forEach { it.join(60_000) }

        // Every thread's last write is there: none wrote back a copy another had since changed.
        val last = rounds - 1
        val post = assertNotNull(store.load(id))
        assertEquals("text $last", post.blueskyText)
        assertEquals("#tag$last", post.tags[PublishTarget.Instagram])
        assertEquals(listOf(PhotoTag("friend$last")), post.photoTags)
        assertEquals("collab$last", post.collaborators)
        assertEquals("https://example.test/$last", post.published[PublishTarget.Bluesky])
        assertEquals("file:///edited/$last.jpg", post.renderedUri)
        assertTrue(post.shared)
    }

    @Test
    fun savesThatArriveAfterAPostWasRemovedAreDropped() {
        val id = newPost("gone")
        store.saveCaption(id, "Written before")
        store.remove(id)
        val savesBefore = drafts.saveCount

        store.saveCaption(id, "Written after")
        store.saveAltText(id, "Described after")
        store.saveTreatment(id, PhotoTreatment(zoom = 2f))
        store.saveBlueskyText(id, "After")
        store.saveTags(id, PublishTarget.Bluesky, "#after")
        store.saveInstagramExtras(id, listOf(PhotoTag("after")), "after")

        assertEquals(savesBefore, drafts.saveCount)
        assertNull(treatments.get(id))
        assertEquals("Written before", drafts.get(id)?.caption)
        // And nothing is created for a post that never existed.
        store.saveTreatment(PostDraftId("never"), PhotoTreatment())
        assertNull(treatments.get(PostDraftId("never")))
    }

    @Test
    fun whatIsCountedAsRemovedIsWhatClearingWouldFree() {
        val listed = newPost("listed", uri = "file:///photos/shared.jpg")
        val removedSharingAPhoto = newPost("twin", uri = "file:///photos/shared.jpg")
        val removed = newPost("removed")
        store.remove(removedSharingAPhoto)
        store.remove(removed)
        // An image left beside a removed post by some other route is counted, since clearing deletes it too.
        treatments.save(removed, StoredTreatment(PhotoTreatment(), renderedUri = "file:///edited/removed.jpg"))

        assertEquals(
            setOf("file:///photos/removed.jpg", "file:///edited/removed.jpg"),
            store.removedPostPhotos().toSet(),
        )
        assertNotNull(store.load(listed))
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
    fun blueskyTextAndWhereAPostWentAreKeptThroughOtherChanges() {
        val id = newPost("one")

        store.saveBlueskyText(id, "Short one.\nSecond line \\ with a backslash.")
        store.saveBlueskyText(id, "Short one.\nSecond line \\ with a backslash.")
        store.markPublished(id, PublishTarget.Bluesky, "https://bsky.app/profile/doctrine/post/1", "file:///edited/one.jpg")
        store.saveTreatment(id, PhotoTreatment(cropShape = CropShape.Square))
        store.markShared(id, "file:///edited/two.jpg")

        val post = assertNotNull(store.load(id))
        assertEquals("Short one.\nSecond line \\ with a backslash.", post.blueskyText)
        assertEquals(mapOf(PublishTarget.Bluesky to "https://bsky.app/profile/doctrine/post/1"), post.published)
        assertEquals("file:///edited/two.jpg", post.renderedUri)
        assertEquals(CropShape.Square, post.treatment?.cropShape)
        assertTrue(post.shared)
    }

    @Test
    fun aNewPostHasNoBlueskyTextAndHasNotBeenPublished() {
        val post = assertNotNull(store.load(newPost("one")))

        assertEquals("", post.blueskyText)
        assertTrue(post.published.isEmpty())
    }

    @Test
    fun unknownPublishTargetsInStoredDataAreIgnored() {
        val decoded = StoredTreatmentCodec.decode("published.myspace=https://x\npublished.bluesky=\npublished.bluesky=https://y")

        assertEquals(mapOf(PublishTarget.Bluesky to "https://y"), decoded.published)
    }

    @Test
    fun hashtagsAreKeptPerServiceAndAnEmptySetIsRemembered() {
        val id = newPost("one")
        assertTrue(assertNotNull(store.load(id)).tags.isEmpty())

        store.saveTags(id, PublishTarget.Bluesky, "#craftbeer #bskybeer")
        store.saveTags(id, PublishTarget.Bluesky, "#craftbeer #bskybeer")
        store.saveTags(id, PublishTarget.Instagram, "")
        store.saveTreatment(id, PhotoTreatment(cropShape = CropShape.Square))

        assertEquals(
            mapOf(PublishTarget.Bluesky to "#craftbeer #bskybeer", PublishTarget.Instagram to ""),
            store.load(id)?.tags,
        )
        assertEquals(
            mapOf(PublishTarget.Bluesky to "#a\n#b"),
            StoredTreatmentCodec.decode("tags.bluesky=#a\\n#b\ntags.myspace=#x").tags,
        )
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

    override fun delete(id: PostDraftId): List<String> =
        drafts.remove(id)?.mediaItems?.map { it.mediaAsset.uri }.orEmpty()

    override fun listPostDrafts(): List<DraftSummary> =
        drafts.values
            .sortedByDescending { it.updatedAt }
            .map { draft ->
                DraftSummary(
                    id = draft.id,
                    status = draft.status,
                    captionText = draft.caption,
                    createdAtEpochMillis = draft.createdAt.toEpochMilliseconds(),
                    updatedAtEpochMillis = draft.updatedAt.toEpochMilliseconds(),
                    primaryMediaUri = draft.mediaItems.minByOrNull { it.order }?.mediaAsset?.uri,
                )
            }
}
