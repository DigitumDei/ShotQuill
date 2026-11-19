package com.digitumdei.shotquill.shared.storage

import app.cash.sqldelight.db.QueryResult
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
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Takes a database as version 1 of the app laid it out, with posts in it, to
 * the current layout, and checks every post came through. Run both with the
 * links between tables enforced, as they are on a phone, and without.
 */
class DatabaseMigrationTest {
    companion object {
        init {
            Class.forName("org.sqlite.JDBC")
        }
    }

    private val created = VersionOneFixture.Created

    /** A version 1 database file holding [statements], migrated, and open. */
    private fun migrated(enforceLinks: Boolean, statements: List<String> = VersionOneFixture.posts): JdbcSqliteDriver {
        val file = File.createTempFile("shotquill-v1-", ".db").apply { deleteOnExit() }
        VersionOneFixture.copyTo(file)
        val properties = Properties().apply { if (enforceLinks) put("foreign_keys", "true") }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", properties)
        statements.forEach { driver.execute(null, it, 0) }
        assertEquals(if (enforceLinks) 1L else 0L, driver.long("PRAGMA foreign_keys"))

        // On a phone the whole migration runs inside one transaction.
        ShotQuillDatabase(driver).transaction {
            ShotQuillDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = ShotQuillDatabase.Schema.version)
        }
        return driver
    }

    private fun JdbcSqliteDriver.strings(sql: String): List<String> =
        executeQuery(null, sql, { cursor ->
            val found = mutableListOf<String>()
            while (cursor.next().value) found += cursor.getString(0).orEmpty()
            QueryResult.Value(found)
        }, 0).value

    private fun JdbcSqliteDriver.long(sql: String): Long =
        executeQuery(null, sql, { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: -1L)
        }, 0).value

    @Test
    fun theCurrentLayoutIsVersionTwo() {
        assertEquals(2L, ShotQuillDatabase.Schema.version)
    }

    @Test
    fun aPostMadeByTheAppAsItIsNowComesThroughWhole() {
        for (enforceLinks in listOf(true, false)) {
            val driver = migrated(enforceLinks)
            val post = assertNotNull(SqlDelightPostDraftRepository(driver).get(PostDraftId("draft-phone")))

            assertEquals(
                PostDraft(
                    id = PostDraftId("draft-phone"),
                    format = PostFormat.SingleImage,
                    status = DraftStatus.Shared,
                    mediaItems = listOf(
                        PostMediaItem(
                            mediaAsset = MediaAsset(
                                id = MediaAssetId("media-phone"),
                                uri = "file:///photos/phone.jpg",
                                mimeType = "image/jpeg",
                                widthPx = 4000,
                                heightPx = 3000,
                                createdAtEpochMillis = created,
                            ),
                            order = 0,
                        ),
                    ),
                    caption = "First pour of the witbier.",
                    altText = "A glass of beer on a bar.",
                    createdAt = Instant.fromEpochMilliseconds(created),
                    updatedAt = Instant.fromEpochMilliseconds(created + 90_000),
                ),
                post,
                "links enforced: $enforceLinks",
            )
            driver.close()
        }
    }

    @Test
    fun everyPostIsStillListedInTheSameOrderWithItsPhoto() {
        for (enforceLinks in listOf(true, false)) {
            val driver = migrated(enforceLinks)
            val listed = SqlDelightPostDraftRepository(driver).listPostDrafts()

            assertEquals(
                listOf(
                    Triple("draft-phone", DraftStatus.Shared, "file:///photos/phone.jpg"),
                    Triple("draft-new", DraftStatus.PhotoAdded, "file:///photos/new.jpg"),
                    Triple("draft-removed", DraftStatus.Archived, "file:///photos/removed.jpg"),
                    Triple("draft-old", DraftStatus.PhotoAdded, "file:///photos/old.jpg"),
                    Triple("draft-typed", DraftStatus.PhotoAdded, "file:///photos/typed.jpg"),
                    Triple("draft-only-typed", DraftStatus.PhotoAdded, "file:///photos/only-typed.jpg"),
                ),
                listed.map { Triple(it.id.value, it.status, it.primaryMediaUri) },
                "links enforced: $enforceLinks",
            )
            driver.close()
        }
    }

    @Test
    fun aPostWithNothingTypedAndARemovedPostKeepTheirState() {
        val driver = migrated(enforceLinks = true)
        val repository = SqlDelightPostDraftRepository(driver)

        val untouched = assertNotNull(repository.get(PostDraftId("draft-new")))
        assertNull(untouched.caption)
        assertNull(untouched.altText)
        // A photo whose type and size were never read is still that photo.
        assertEquals(
            MediaAsset(MediaAssetId("media-new"), "file:///photos/new.jpg", null, null, null, created),
            untouched.mediaItems.single().mediaAsset,
        )

        val removed = assertNotNull(repository.get(PostDraftId("draft-removed")))
        assertEquals(DraftStatus.Archived, removed.status)
        assertEquals("Gone", removed.caption)
        assertNull(removed.altText)
        driver.close()
    }

    @Test
    fun aPostFromTheOldFlowKeepsItsWordsAndBecomesAnOrdinaryPost() {
        for (enforceLinks in listOf(true, false)) {
            val driver = migrated(enforceLinks)
            val repository = SqlDelightPostDraftRepository(driver)

            val old = assertNotNull(repository.get(PostDraftId("draft-old")))
            assertEquals(DraftStatus.PhotoAdded, old.status)
            assertEquals("A generated caption", old.caption)
            // No alt text was typed by hand, so the last one written for it is kept.
            assertEquals("The latest description", old.altText)
            assertEquals("file:///photos/old.jpg", old.mediaItems.single().mediaAsset.uri)

            driver.close()
        }
    }

    @Test
    fun whatWasTypedOverGeneratedTextIsWhatIsKept() {
        for (enforceLinks in listOf(true, false)) {
            val driver = migrated(enforceLinks)
            val repository = SqlDelightPostDraftRepository(driver)

            // The old flow left the generated caption and alt text in place and
            // kept what was typed over them beside it. What was typed is what was meant.
            val typed = assertNotNull(repository.get(PostDraftId("draft-typed")))
            assertEquals("Typed by hand", typed.caption)
            assertEquals("Described by hand", typed.altText)

            // With nothing generated, what was typed was the only caption there was.
            assertEquals("Only ever typed", repository.get(PostDraftId("draft-only-typed"))?.caption)
            driver.close()
        }
    }

    @Test
    fun onlyTheTablesInUseAreLeftAndOnlyPhotosAPostUses() {
        for (enforceLinks in listOf(true, false)) {
            val driver = migrated(enforceLinks)

            assertEquals(
                listOf("media_assets", "post_draft_media_items", "post_drafts"),
                driver.strings("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name"),
                "links enforced: $enforceLinks",
            )
            // The brand's logo and the image the old flow generated belonged to no post.
            assertEquals(
                listOf("media-new", "media-old", "media-only-typed", "media-phone", "media-removed", "media-typed"),
                driver.strings("SELECT id FROM media_assets ORDER BY id"),
            )
            assertEquals(6L, driver.long("SELECT COUNT(*) FROM post_draft_media_items"))
            driver.close()
        }
    }

    @Test
    fun theMigratedDatabaseWorksLikeAFreshOne() {
        val driver = migrated(enforceLinks = true)
        val repository = SqlDelightPostDraftRepository(driver)
        val phone = assertNotNull(repository.get(PostDraftId("draft-phone")))

        repository.save(phone.copy(caption = "Edited after the update", altText = null))
        repository.save(
            phone.copy(
                id = PostDraftId("draft-after"),
                mediaItems = listOf(
                    PostMediaItem(phone.mediaItems.single().mediaAsset.copy(id = MediaAssetId("media-after")), 0),
                ),
            ),
        )

        assertEquals("Edited after the update", repository.get(PostDraftId("draft-phone"))?.caption)
        assertNull(repository.get(PostDraftId("draft-phone"))?.altText)
        assertEquals(7, repository.listPostDrafts().size)
        // Deleting still frees the photo, and the links between the new tables hold.
        assertEquals(listOf("file:///photos/removed.jpg"), repository.delete(PostDraftId("draft-removed")))
        assertEquals(0L, driver.long("SELECT COUNT(*) FROM post_draft_media_items WHERE draft_id = 'draft-removed'"))
        assertTrue(driver.strings("PRAGMA foreign_key_check").isEmpty())
        driver.close()
    }

    @Test
    fun anEmptyDatabaseMigratesToo() {
        for (enforceLinks in listOf(true, false)) {
            val driver = migrated(enforceLinks, statements = emptyList())

            assertTrue(SqlDelightPostDraftRepository(driver).listPostDrafts().isEmpty())
            driver.close()
        }
    }

    @Test
    fun rowsThatPointAtNothingDoNotStopTheMigrationAndAreNotCarriedOver() {
        val driver = migrated(
            enforceLinks = false,
            statements = VersionOneFixture.posts + VersionOneFixture.rowsThatPointAtNothing,
        )
        val repository = SqlDelightPostDraftRepository(driver)

        // A post without a photo cannot be shown, opened or removed, so it is not kept;
        // every post that is kept can be opened.
        val listed = repository.listPostDrafts()
        assertEquals(
            listOf("draft-phone", "draft-new", "draft-removed", "draft-old", "draft-typed", "draft-only-typed"),
            listed.map { it.id.value },
        )
        listed.forEach { assertEquals(1, assertNotNull(repository.get(it.id)).mediaItems.size) }
        assertNull(repository.get(PostDraftId("draft-photo-missing")))
        assertNull(repository.get(PostDraftId("draft-no-photo")))
        // Nor is a photo that belonged to no post, or a link with nothing at one end.
        assertEquals(6L, driver.long("SELECT COUNT(*) FROM media_assets"))
        assertEquals(6L, driver.long("SELECT COUNT(*) FROM post_draft_media_items"))
        assertTrue(driver.strings("PRAGMA foreign_key_check").isEmpty())
        driver.close()
    }
}
