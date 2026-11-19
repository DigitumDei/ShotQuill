package com.digitumdei.shotquill.shared.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.digitumdei.shotquill.shared.db.ShotQuillDatabase
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.domain.PostMediaItem
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightPostDraftRepositoryTest {
    companion object {
        init {
            // Force sqlite-jdbc driver registration with this classloader's DriverManager
            // state. Without this, the first test in the class can fail with
            // "No suitable driver found for jdbc:sqlite:" depending on which test class
            // (e.g. a Robolectric-sandboxed one) initialized DriverManager first.
            Class.forName("org.sqlite.JDBC")
        }
    }

    private val createdAt = 1_700_000_000_000L

    private fun inMemoryDriver(enforceLinks: Boolean = true): JdbcSqliteDriver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            if (enforceLinks) it.execute(null, "PRAGMA foreign_keys = ON", 0)
            ShotQuillDatabase.Schema.create(it)
        }

    private fun photo(id: String, uri: String = "file:///photos/$id.jpg") = MediaAsset(
        id = MediaAssetId(id),
        uri = uri,
        mimeType = "image/jpeg",
        widthPx = 4000,
        heightPx = 3000,
        createdAtEpochMillis = createdAt,
    )

    private fun post(
        id: String,
        photo: MediaAsset = photo("media-$id"),
        updatedAt: Long = createdAt + 60_000,
    ) = PostDraft(
        id = PostDraftId(id),
        format = PostFormat.SingleImage,
        status = DraftStatus.PhotoAdded,
        mediaItems = listOf(PostMediaItem(mediaAsset = photo, order = 0)),
        caption = "First pour of the witbier.",
        altText = "A glass of beer on a bar.",
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    )

    @Test
    fun aPostIsReadBackExactlyAsItWasSaved() {
        val driver = inMemoryDriver()
        val repository = SqlDelightPostDraftRepository(driver)
        val full = post("one")
        val bare = post("two", photo = photo("media-two").copy(mimeType = null, widthPx = null, heightPx = null))
            .copy(caption = null, altText = null, status = DraftStatus.Archived)

        repository.save(full)
        repository.save(bare)

        assertEquals(full, repository.get(full.id))
        assertEquals(bare, repository.get(bare.id))
        assertNull(repository.get(PostDraftId("missing")))
        driver.close()
    }

    @Test
    fun savingAgainReplacesWhatWasStored() {
        val driver = inMemoryDriver()
        val repository = SqlDelightPostDraftRepository(driver)
        val first = post("one")
        repository.save(first)

        val changed = first.copy(
            status = DraftStatus.Shared,
            caption = "Edited",
            altText = null,
            updatedAt = Instant.fromEpochMilliseconds(createdAt + 120_000),
        )
        repository.save(changed)

        assertEquals(changed, repository.get(first.id))
        assertEquals(1, repository.listPostDrafts().size)
        driver.close()
    }

    @Test
    fun aPostWithSeveralPhotosKeepsThemInOrder() {
        val driver = inMemoryDriver()
        val repository = SqlDelightPostDraftRepository(driver)
        val carousel = post("one").copy(
            format = PostFormat.Carousel,
            mediaItems = listOf(
                PostMediaItem(photo("second"), order = 1),
                PostMediaItem(photo("first"), order = 0),
            ),
        )

        repository.save(carousel)

        val read = assertNotNull(repository.get(carousel.id))
        assertEquals(listOf("first", "second"), read.mediaItems.map { it.mediaAsset.id.value })
        // The list shows the first photo.
        assertEquals("file:///photos/first.jpg", repository.listPostDrafts().single().primaryMediaUri)
        driver.close()
    }

    @Test
    fun postsAreListedMostRecentlyChangedFirst() {
        val driver = inMemoryDriver()
        val repository = SqlDelightPostDraftRepository(driver)
        repository.save(post("older", updatedAt = createdAt + 1_000))
        repository.save(post("newest", updatedAt = createdAt + 3_000).copy(caption = null))
        repository.save(post("middle", updatedAt = createdAt + 2_000).copy(status = DraftStatus.Archived))

        val listed = repository.listPostDrafts()

        assertEquals(listOf("newest", "middle", "older"), listed.map { it.id.value })
        assertEquals(listOf(null, "First pour of the witbier.", "First pour of the witbier."), listed.map { it.captionText })
        assertEquals(DraftStatus.Archived, listed[1].status)
        assertEquals("file:///photos/media-newest.jpg", listed[0].primaryMediaUri)
        assertEquals(createdAt, listed[0].createdAtEpochMillis)
        assertEquals(createdAt + 3_000, listed[0].updatedAtEpochMillis)
        driver.close()
    }

    @Test
    fun deletingAPostRemovesItAndItsPhotoButLeavesOtherPostsAlone() {
        for (enforceLinks in listOf(true, false)) {
            val driver = inMemoryDriver(enforceLinks)
            val repository = SqlDelightPostDraftRepository(driver)
            val first = post("one")
            val second = post("two")
            repository.save(first)
            repository.save(second)

            val freed = repository.delete(first.id)

            assertEquals(listOf("file:///photos/media-one.jpg"), freed, "links enforced: $enforceLinks")
            assertNull(repository.get(first.id))
            assertEquals(listOf(second.id), repository.listPostDrafts().map { it.id })
            assertEquals(second, repository.get(second.id))
            assertTrue(repository.delete(first.id).isEmpty())
            assertTrue(repository.delete(PostDraftId("never-existed")).isEmpty())
            driver.close()
        }
    }

    @Test
    fun deletingAPostKeepsAPhotoAnotherPostStillUses() {
        for (enforceLinks in listOf(true, false)) {
            val driver = inMemoryDriver(enforceLinks)
            val repository = SqlDelightPostDraftRepository(driver)
            val shared = photo("shared")
            val first = post("one", photo = shared)
            val second = post("two", photo = shared)
            repository.save(first)
            repository.save(second)

            val freed = repository.delete(first.id)

            assertTrue(freed.isEmpty(), "a photo still in use must not be handed back for deletion: $freed")
            assertEquals(second, repository.get(second.id))
            // Once nothing uses it, it is handed back.
            assertEquals(listOf("file:///photos/shared.jpg"), repository.delete(second.id))
            driver.close()
        }
    }

    @Test
    fun aStatusTheAppDoesNotKnowIsRefusedRatherThanGuessed() {
        val driver = inMemoryDriver()
        val repository = SqlDelightPostDraftRepository(driver)
        repository.save(post("one"))
        driver.execute(null, "UPDATE post_drafts SET status = 'text_generated' WHERE id = 'one'", 0)

        assertFailsWith<IllegalStateException> { repository.get(PostDraftId("one")) }
        assertFailsWith<IllegalStateException> { repository.listPostDrafts() }
        driver.close()
    }
}
