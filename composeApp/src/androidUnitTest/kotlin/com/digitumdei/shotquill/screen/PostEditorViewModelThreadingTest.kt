package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.shared.domain.PixelSize
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.post.EditablePost
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.social.PublishResult
import com.digitumdei.shotquill.shared.social.PublishTarget
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The editor's state is changed from the main thread by typing and from
 * background threads by loading, saving, sharing and posting. These tests do
 * that for real, with threads, and check that nothing typed is lost.
 */
class PostEditorViewModelThreadingTest {
    private val draftId = PostDraftId("draft-1")
    private val photoUri = "file:///photos/one.jpg"
    private val store = FakeStore().apply {
        post = EditablePost(PostDraftId("draft-1"), "file:///photos/one.jpg", "", "", null, null, shared = false)
    }
    private val renderer = FakeRenderer().apply { sizes["file:///photos/one.jpg"] = PixelSize(4000, 3000) }
    private val bluesky = FakePublisher()

    private fun loadedViewModel() = PostEditorViewModel(
        draftId = draftId,
        store = store,
        settingsRepository = InMemoryLocalSettingsRepository(),
        renderer = renderer,
        clipboardWriter = RecordingClipboard(),
        shareLauncher = RecordingShareLauncher(),
        publishers = listOf(bluesky),
    ).also { it.load() }

    @Test
    fun changesFromTwoThreadsAtOnceNeverOverwriteEachOther() {
        val vm = loadedViewModel()
        val rounds = 20_000
        val start = CountDownLatch(1)

        val typist = thread {
            start.await()
            repeat(rounds) { vm.updateCaption("caption $it") }
        }
        val background = thread {
            start.await()
            repeat(rounds) { vm.updateAltText("alt $it") }
        }
        val tagger = thread {
            start.await()
            repeat(rounds) { vm.updateTags(PublishTarget.Bluesky, "#tag$it") }
        }
        start.countDown()
        listOf(typist, background, tagger).forEach { it.join(30_000) }

        // Each thread's last change is still there. Without the lock, a change
        // that started from an older copy of the state puts the others back.
        assertEquals("caption ${rounds - 1}", vm.state.caption)
        assertEquals("alt ${rounds - 1}", vm.state.altText)
        assertEquals("#tag${rounds - 1}", vm.state.tagsFor(PublishTarget.Bluesky))
    }

    @Test
    fun whatIsTypedWhileAPostIsOnItsWayIsKeptAndCanBeSaved() {
        val vm = loadedViewModel()
        vm.updateCaption("Before posting")
        val posting = CountDownLatch(1)
        val typed = CountDownLatch(1)
        bluesky.whilePublishing = {
            posting.countDown()
            typed.await(10, TimeUnit.SECONDS)
        }

        val poster = thread { vm.publish(PublishTarget.Bluesky) }
        assertTrue(posting.await(10, TimeUnit.SECONDS), "the post never started")

        // The post is in flight. Typing and saving carry on meanwhile.
        assertTrue(vm.state.isPublishing)
        vm.updateCaption("Typed while posting")
        vm.persistCaption()
        assertEquals("Typed while posting", store.savedCaptions.last())
        // A second post or a share cannot start on top of the first.
        vm.publish(PublishTarget.Bluesky)
        vm.share()
        assertEquals(1, bluesky.posts.size)
        assertFalse(vm.state.isSharing)

        typed.countDown()
        poster.join(10_000)

        assertFalse(vm.state.isPublishing)
        assertEquals("Typed while posting", vm.state.caption)
        assertEquals("Before posting", bluesky.posts.single().text)
        assertEquals("Posted to Bluesky as @doctrine.bsky.social.", vm.state.message)
        assertTrue(bluesky.result is PublishResult.Success)
    }
}
