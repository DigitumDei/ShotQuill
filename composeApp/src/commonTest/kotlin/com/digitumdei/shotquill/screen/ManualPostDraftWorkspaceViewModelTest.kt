package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.AltTextResultId
import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PhotoEditResultId
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.domain.PostMediaItem
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualPostDraftWorkspaceViewModelTest {
    private val draftId = PostDraftId("draft-1")
    private val mediaAssetId = MediaAssetId("media-1")

    @Test
    fun loadsDraftWorkspaceFromStoredDraft() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals(DraftStatus.PhotoAdded, viewModel.state.draftStatus)
        assertEquals(null, viewModel.state.generatedCaption)
    }

    @Test
    fun exposesStateWithOnlyOriginalMedia() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals(null, viewModel.state.editedPhotoUri)
        assertEquals(null, viewModel.state.generatedAltText)
        assertTrue(viewModel.state.actions.canGeneratePostText)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
        assertFalse(viewModel.state.actions.canCopyCaption)
        assertFalse(viewModel.state.actions.canCopyAltText)
        assertFalse(viewModel.state.actions.canShareOrExport)
    }

    @Test
    fun exposesStateWithGeneratedText() {
        val repository = FakePostDraftRepository(sampleDraftWithGeneratedText())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertEquals("Morning focus, freshly brewed.", viewModel.state.generatedCaption)
        assertEquals("Coffee cup beside an open notebook.", viewModel.state.generatedAltText)
        assertEquals(TargetPlatform.InstagramFeedSquare, viewModel.state.targetPlatform)
        assertTrue(viewModel.state.actions.canCopyCaption)
        assertTrue(viewModel.state.actions.canCopyAltText)
        assertTrue(viewModel.state.actions.canShareOrExport)
    }

    @Test
    fun exposesStateWithEditedMedia() {
        val repository = FakePostDraftRepository(sampleDraftWithEditedMedia())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals("file://photo-edited.jpg", viewModel.state.editedPhotoUri)
        assertEquals(DraftStatus.PhotoEdited, viewModel.state.draftStatus)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun updatesStoredDraftWhenGeneratingTextWithFakeAiProvider() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_100_000L),
        )
        viewModel.load()

        viewModel.generatePostText()

        val stored = repository.get(draftId)
        assertEquals(DraftStatus.TextGenerated, stored?.status)
        assertEquals("Ready for instagram_feed_square: photo.jpg", viewModel.state.generatedCaption)
        assertEquals("Photo prepared for instagram_feed_square.", viewModel.state.generatedAltText)
        assertEquals(2, viewModel.state.promptHistory.size)
        assertTrue(viewModel.state.actions.canShareOrExport)
    }

    @Test
    fun updatesStoredDraftWhenEditingPhotoWithFakeAiProvider() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        val stored = repository.get(draftId)
        assertEquals(DraftStatus.PhotoEdited, stored?.status)
        assertEquals("file://photo.jpg#edited-1700000200000", viewModel.state.editedPhotoUri)
        assertEquals(1, viewModel.state.promptHistory.size)
        assertTrue(viewModel.state.actions.canViewPromptHistory)
    }

    @Test
    fun disablesMutationAndShareActionsForArchivedDraft() {
        val repository = FakePostDraftRepository(sampleDraftWithGeneratedText().copy(status = DraftStatus.Archived))
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertFalse(viewModel.state.actions.canGeneratePostText)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
        assertTrue(viewModel.state.actions.canCopyCaption)
        assertTrue(viewModel.state.actions.canCopyAltText)
        assertFalse(viewModel.state.actions.canShareOrExport)
    }

    private fun sampleDraft(): PostDraft =
        PostDraft(
            id = draftId,
            format = PostFormat.SingleImage,
            status = DraftStatus.PhotoAdded,
            mediaItems = listOf(PostMediaItem(sampleMediaAsset(), order = 0)),
            caption = null,
            targetPlatforms = emptySet(),
            brandProfile = null,
            visionDescription = null,
            captionRequests = emptyList(),
            captionResults = emptyList(),
            altTextResults = emptyList(),
            photoEditRequests = emptyList(),
            photoEditResults = emptyList(),
            promptHistory = emptyList(),
            exportRecords = emptyList(),
            createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        )

    private fun sampleDraftWithGeneratedText(): PostDraft =
        sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            caption = CaptionDraft(
                text = "Morning focus, freshly brewed.",
                hashtags = listOf("#coffee", "#work"),
            ),
            targetPlatforms = setOf(TargetPlatform.InstagramFeedSquare),
            captionResults = listOf(
                CaptionResult(
                    id = CaptionResultId("caption-result-1"),
                    requestId = CaptionRequestId("caption-request-1"),
                    draftId = draftId,
                    targetPlatform = TargetPlatform.InstagramFeedSquare,
                    caption = "Morning focus, freshly brewed.",
                    hashtags = listOf("#coffee", "#work"),
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_010_000L,
                ),
            ),
            altTextResults = listOf(
                AltTextResult(
                    id = AltTextResultId("alt-text-1"),
                    draftId = draftId,
                    mediaAssetId = mediaAssetId,
                    altText = "Coffee cup beside an open notebook.",
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_020_000L,
                ),
            ),
        )

    private fun sampleDraftWithEditedMedia(): PostDraft =
        sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            photoEditResults = listOf(
                PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-1"),
                    requestId = PhotoEditRequestId("photo-edit-request-1"),
                    draftId = draftId,
                    editedMediaAsset = sampleMediaAsset().copy(
                        id = MediaAssetId("media-edited-1"),
                        type = MediaType.EditedPhoto,
                        uri = "file://photo-edited.jpg",
                    ),
                    summary = "Adjusted brightness.",
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_030_000L,
                ),
            ),
        )

    private fun sampleMediaAsset(): MediaAsset =
        MediaAsset(
            id = mediaAssetId,
            type = MediaType.Photo,
            uri = "file://photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 1080,
            heightPx = 1080,
            createdAtEpochMillis = 1_700_000_000_000L,
        )

    private class FakePostDraftRepository(initialDraft: PostDraft) : PostDraftRepository {
        private val drafts = mutableMapOf(initialDraft.id to initialDraft)

        override fun save(postDraft: PostDraft) {
            drafts[postDraft.id] = postDraft
        }

        override fun get(id: PostDraftId): PostDraft? = drafts[id]

        override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean {
            val current = drafts[id] ?: return false
            drafts[id] = current.copy(status = status, updatedAt = updatedAt)
            return true
        }

        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean = false
    }

    private class FixedClock(private val now: Long) : EpochClock {
        override fun nowMillis(): Long = now
    }
}
