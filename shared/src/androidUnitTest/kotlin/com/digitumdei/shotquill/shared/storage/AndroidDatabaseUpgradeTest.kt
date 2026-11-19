package com.digitumdei.shotquill.shared.storage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * The upgrade as a phone does it: the app's own database file, left by the
 * last version, opened by the app's own code. Android notices the older
 * layout, runs the migration inside a transaction with the links between
 * tables enforced, and the posts are read back through the repository.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidDatabaseUpgradeTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val name = "shotquill.db"

    /** Leaves a version 1 database, with posts in it, where the app keeps its own. */
    private fun leaveAVersionOneDatabase() {
        val file = context.getDatabasePath(name)
        VersionOneFixture.copyTo(file)
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.setForeignKeyConstraintsEnabled(true)
            VersionOneFixture.posts.forEach { database.execSQL(it) }
            database.version = 1
        }
    }

    private fun versionOnDisk(): Int =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            .use { it.version }

    @Test
    fun openingADatabaseFromTheLastVersionKeepsEveryPost() {
        leaveAVersionOneDatabase()
        assertEquals(1, versionOnDisk())

        val driver = AndroidDatabaseDriverFactory(context).create(name)
        val repository = SqlDelightPostDraftRepository(driver)

        assertEquals(
            listOf("draft-phone", "draft-new", "draft-removed", "draft-old", "draft-typed", "draft-only-typed"),
            repository.listPostDrafts().map { it.id.value },
        )
        val phone = assertNotNull(repository.get(PostDraftId("draft-phone")))
        assertEquals(DraftStatus.Shared, phone.status)
        assertEquals("First pour of the witbier.", phone.caption)
        assertEquals("A glass of beer on a bar.", phone.altText)
        assertEquals("file:///photos/phone.jpg", phone.mediaItems.single().mediaAsset.uri)
        assertEquals("The latest description", repository.get(PostDraftId("draft-old"))?.altText)
        assertEquals("Typed by hand", repository.get(PostDraftId("draft-typed"))?.caption)
        driver.close()
        assertEquals(2, versionOnDisk())

        // The next start finds the new layout and changes nothing.
        val reopened = AndroidDatabaseDriverFactory(context).create(name)
        val again = SqlDelightPostDraftRepository(reopened)
        assertEquals(phone, again.get(PostDraftId("draft-phone")))
        assertEquals(6, again.listPostDrafts().size)
        reopened.close()
    }

    @Test
    fun aFreshInstallStartsInTheCurrentLayout() {
        val driver = AndroidDatabaseDriverFactory(context).create(name)
        val repository = SqlDelightPostDraftRepository(driver)
        val post = PostDraft(
            id = PostDraftId("draft-1"),
            format = PostFormat.SingleImage,
            status = DraftStatus.PhotoAdded,
            mediaItems = listOf(
                PostMediaItem(MediaAsset(MediaAssetId("media-1"), "file:///photos/1.jpg", "image/jpeg", 4000, 3000, 1L), 0),
            ),
            caption = "Hello",
            createdAt = Instant.fromEpochMilliseconds(1L),
            updatedAt = Instant.fromEpochMilliseconds(2L),
        )

        repository.save(post)

        assertEquals(post, repository.get(post.id))
        assertNull(repository.get(PostDraftId("missing")))
        // The links between tables are enforced on a phone: deleting a post takes its media rows with it.
        assertEquals(listOf("file:///photos/1.jpg"), repository.delete(post.id))
        driver.close()
        assertEquals(2, versionOnDisk())
    }
}
