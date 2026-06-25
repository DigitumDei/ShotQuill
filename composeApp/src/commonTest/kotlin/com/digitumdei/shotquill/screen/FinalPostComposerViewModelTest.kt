package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.share.ShareResult
import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.AltTextResultId
import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.CaptionRequest
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.DraftSummary
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import com.digitumdei.shotquill.shared.domain.ExportStatus
import com.digitumdei.shotquill.shared.domain.FinalPostContent
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.domain.PostMediaItem
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.VisionDescriptionId
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import com.digitumdei.shotquill.shared.storage.UpdateSelectionResult
import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PhotoEditResultId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinalPostComposerViewModelTest {

    private val draftId = PostDraftId("draft-1")
    private val mediaAssetId = MediaAssetId("media-1")

    @Test
    fun `load with multiple caption and alt text results picks most recent`() {
        val olderCaptionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Older caption",
            shortCaption = "Older short",
            hashtags = listOf("#old"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val newerCaptionResult = CaptionResult(
            id = CaptionResultId("caption-result-2"),
            requestId = CaptionRequestId("caption-request-2"),
            draftId = draftId,
            targetPlatform = TargetPlatform.FacebookPost,
            caption = "Newer caption",
            shortCaption = "Newer short",
            hashtags = listOf("#new"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val olderAltTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Older alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_015_000L,
        )
        val newerAltTextResult = AltTextResult(
            id = AltTextResultId("alt-text-2"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Newer alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_025_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(olderCaptionResult, newerCaptionResult),
            altTextResults = listOf(olderAltTextResult, newerAltTextResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()

        with(viewModel.state) {
            assertEquals("Newer caption", caption)
            assertEquals("Newer short", shortCaption)
            assertEquals(listOf("#new"), hashtags)
            assertEquals("Newer alt text", altText)
            assertEquals(TargetPlatform.FacebookPost, targetPlatform)
            assertTrue(isLoaded)
        }
    }

    @Test
    fun `load with persisted FinalPostContent overrides shows manual text`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Generated caption",
            shortCaption = "Generated short",
            hashtags = listOf("#generated"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Generated alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val finalPostContent = FinalPostContent(
            draftId = draftId,
            editedCaption = "Manual caption",
            editedAltText = "Manual alt text",
            updatedAtEpochMillis = 1_700_000_030_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
            finalPostContent = finalPostContent,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()

        with(viewModel.state) {
            assertEquals("Manual caption", caption)
            assertEquals("Manual alt text", altText)
            assertNull(shortCaption)
            assertEquals(listOf("#generated"), hashtags)
            assertTrue(isLoaded)
        }
    }

    @Test
    fun `load with no generated content yields empty state without crashing`() {
        val repository = FakeManualWorkflowRepository(sampleDraft())
        val viewModel = createViewModel(repository)

        viewModel.load()

        with(viewModel.state) {
            assertNull(caption)
            assertNull(shortCaption)
            assertNull(altText)
            assertEquals(emptyList<String>(), hashtags)
            assertEquals(TargetPlatform.InstagramFeedSquare, targetPlatform)
            assertTrue(isLoaded)
            assertNull(statusMessage)
            assertFalse(actions.canShare)
        }
    }

    @Test
    fun `load with missing draft sets error state`() {
        val repository = FakeManualWorkflowRepository(initialDraft = null)
        val viewModel = createViewModel(repository)

        viewModel.load()

        with(viewModel.state) {
            assertFalse(isLoaded)
            assertEquals("Draft not found", statusMessage)
            assertNull(caption)
            assertNull(altText)
            assertNull(selectedPhotoUri)
            assertFalse(actions.canShare)
            assertFalse(actions.canSelectEdited)
        }
    }

    @Test
    fun `edited photo URI and selection flag reflect selectedMediaAssetId and latest PhotoEditResult`() {
        val editedMediaAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-1"),
            type = MediaType.EditedPhoto,
            uri = "file://photo-edited.jpg",
        )
        val photoEditResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-1"),
            requestId = PhotoEditRequestId("photo-edit-request-1"),
            draftId = draftId,
            editedMediaAsset = editedMediaAsset,
            summary = "Improved lighting",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_030_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            selectedMediaAssetId = editedMediaAsset.id,
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-1"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.ImproveLighting,
                    realismLevel = RealismLevel.Photoreal,
                    qualityTier = QualityTier.Standard,
                    prompt = "Improve lighting",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.InstagramFeedSquare,
                    createdAtEpochMillis = 1_700_000_025_000L,
                ),
            ),
            photoEditResults = listOf(photoEditResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()

        with(viewModel.state) {
            assertEquals("file://photo-edited.jpg", editedPhotoUri)
            assertEquals("file://photo-edited.jpg", selectedPhotoUri)
            assertEquals("file://photo.jpg", originalPhotoUri)
            assertTrue(isShowingEdited)
            assertTrue(actions.canSelectEdited)
            assertFalse(actions.canShare)
        }
    }

    // ===== Manual edit tests =====

    @Test
    fun `updateCaption updates state without synchronously touching repository`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Generated caption",
            shortCaption = "Short",
            hashtags = listOf("#gen"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Generated alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        viewModel.updateCaption("Manual caption")

        with(viewModel.state) {
            assertEquals("Manual caption", caption)
            assertTrue(actions.canShare)
        }
        assertEquals(0, repository.finalPostContentGetCount)
        assertEquals(0, repository.finalPostContentSaveCount)

        viewModel.persistFinalPostContent()

        assertEquals(1, repository.finalPostContentGetCount)
        assertEquals(1, repository.finalPostContentSaveCount)
        assertEquals("Manual caption", repository.lastSavedFinalPostContent?.editedCaption)
        assertNull(repository.lastSavedFinalPostContent?.editedAltText)

        val reloadedViewModel = createViewModel(repository)
        reloadedViewModel.load()

        assertEquals("Generated alt text", reloadedViewModel.state.altText)
    }

    @Test
    fun `blank caption stays non-shareable and is normalized away on persistence`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Generated caption",
            shortCaption = "Generated short",
            hashtags = listOf("#gen"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val editedMediaAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-1"),
            type = MediaType.EditedPhoto,
            uri = "file://photo-edited.jpg",
        )
        val photoEditResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-1"),
            requestId = PhotoEditRequestId("photo-edit-request-1"),
            draftId = draftId,
            editedMediaAsset = editedMediaAsset,
            summary = "Improved",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_030_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
            photoEditResults = listOf(photoEditResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val shareLauncher = FakePostShareLauncher(success = true)
        val viewModel = createViewModel(
            repository = repository,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        viewModel.updateCaption("")

        assertEquals("", viewModel.state.caption)
        assertFalse(viewModel.state.actions.canShare)

        viewModel.selectEditedPhoto()

        assertEquals("", viewModel.state.caption)
        assertFalse(viewModel.state.actions.canShare)

        viewModel.load()

        assertEquals("", viewModel.state.caption)
        assertFalse(viewModel.state.actions.canShare)

        viewModel.selectOriginalPhoto()

        assertEquals("", viewModel.state.caption)
        assertFalse(viewModel.state.actions.canShare)

        viewModel.shareOrExport()

        assertEquals("Cannot open share sheet: caption and photo are required", viewModel.state.statusMessage)
        assertNull(shareLauncher.lastText)
        assertNull(shareLauncher.lastImageUri)

        viewModel.persistFinalPostContent()

        assertEquals(1, repository.finalPostContentSaveCount)
        assertNull(repository.lastSavedFinalPostContent?.editedCaption)

        val reloadedViewModel = createViewModel(repository)
        reloadedViewModel.load()

        with(reloadedViewModel.state) {
            assertEquals("Generated caption", caption)
            assertTrue(actions.canShare)
        }
    }

    @Test
    fun `updateAltText updates state without synchronously touching repository`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Generated caption",
            shortCaption = "Short",
            hashtags = listOf("#gen"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Generated alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        viewModel.updateAltText("Manual alt text")

        assertEquals("Manual alt text", viewModel.state.altText)
        assertEquals(0, repository.finalPostContentGetCount)
        assertEquals(0, repository.finalPostContentSaveCount)

        viewModel.persistFinalPostContent()

        assertEquals(1, repository.finalPostContentGetCount)
        assertEquals(1, repository.finalPostContentSaveCount)
        assertNull(repository.lastSavedFinalPostContent?.editedCaption)
        assertEquals("Manual alt text", repository.lastSavedFinalPostContent?.editedAltText)

        val reloadedViewModel = createViewModel(repository)
        reloadedViewModel.load()

        assertEquals("Generated caption", reloadedViewModel.state.caption)
    }

    @Test
    fun `editing only alt text keeps generated caption out of editedCaption`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Generated caption",
            shortCaption = "Short",
            hashtags = listOf("#gen"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Generated alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        viewModel.updateAltText("Manual alt text")
        viewModel.persistFinalPostContent()

        assertEquals(1, repository.finalPostContentGetCount)
        assertEquals(1, repository.finalPostContentSaveCount)
        assertEquals("Manual alt text", repository.lastSavedFinalPostContent?.editedAltText)
        assertNull(repository.lastSavedFinalPostContent?.editedCaption)

        val reloadedViewModel = createViewModel(repository)
        reloadedViewModel.load()

        with(reloadedViewModel.state) {
            assertEquals("Generated caption", caption)
            assertEquals("Manual alt text", altText)
        }
    }

    @Test
    fun `editing only caption keeps generated alt text out of editedAltText`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Generated caption",
            shortCaption = "Short",
            hashtags = listOf("#gen"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Generated alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        viewModel.updateCaption("Manual caption")
        viewModel.persistFinalPostContent()

        assertEquals(1, repository.finalPostContentGetCount)
        assertEquals(1, repository.finalPostContentSaveCount)
        assertEquals("Manual caption", repository.lastSavedFinalPostContent?.editedCaption)
        assertNull(repository.lastSavedFinalPostContent?.editedAltText)

        val reloadedViewModel = createViewModel(repository)
        reloadedViewModel.load()

        with(reloadedViewModel.state) {
            assertEquals("Manual caption", caption)
            assertEquals("Generated alt text", altText)
        }
    }

    @Test
    fun `fresh viewmodel loaded after manual edit sees persisted edits`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Generated caption",
            shortCaption = "Short",
            hashtags = listOf("#gen"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Generated alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val firstVm = createViewModel(repository)

        firstVm.load()
        firstVm.updateCaption("Persistent caption")
        firstVm.updateAltText("Persistent alt text")
        firstVm.persistFinalPostContent()

        val secondVm = createViewModel(repository)
        secondVm.load()

        with(secondVm.state) {
            assertEquals("Persistent caption", caption)
            assertEquals("Persistent alt text", altText)
        }
    }

    @Test
    fun `editing caption does not clobber previously edited alt text`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Gen caption",
            shortCaption = null,
            hashtags = emptyList(),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Gen alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        viewModel.updateAltText("Manual alt text")
        viewModel.updateCaption("Manual caption")
        viewModel.persistFinalPostContent()

        assertEquals("Manual caption", repository.lastSavedFinalPostContent?.editedCaption)
        assertEquals("Manual alt text", repository.lastSavedFinalPostContent?.editedAltText)
    }

    @Test
    fun `repository refresh before persistence preserves pending manual edits`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Generated caption",
            shortCaption = "Short",
            hashtags = listOf("#gen"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Generated alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        viewModel.updateCaption("Manual caption")
        viewModel.updateAltText("Manual alt text")

        viewModel.load()

        with(viewModel.state) {
            assertEquals("Manual caption", caption)
            assertEquals("Manual alt text", altText)
            assertTrue(actions.canShare)
        }

        viewModel.persistFinalPostContent()

        assertEquals(1, repository.finalPostContentSaveCount)
        assertEquals("Manual caption", repository.lastSavedFinalPostContent?.editedCaption)
        assertEquals("Manual alt text", repository.lastSavedFinalPostContent?.editedAltText)
    }

    @Test
    fun `editing alt text does not clobber previously edited caption`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Gen caption",
            shortCaption = null,
            hashtags = emptyList(),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Gen alt text",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            altTextResults = listOf(altTextResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        viewModel.updateCaption("Manual caption")
        viewModel.updateAltText("Manual alt text")
        viewModel.persistFinalPostContent()

        assertEquals("Manual caption", repository.lastSavedFinalPostContent?.editedCaption)
        assertEquals("Manual alt text", repository.lastSavedFinalPostContent?.editedAltText)
    }

    // ===== Image switching tests =====

    @Test
    fun `selectOriginalPhoto calls updateSelectedMediaAsset with null and flips isShowingEdited`() {
        val editedMediaAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-1"),
            type = MediaType.EditedPhoto,
            uri = "file://photo-edited.jpg",
        )
        val photoEditResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-1"),
            requestId = PhotoEditRequestId("photo-edit-request-1"),
            draftId = draftId,
            editedMediaAsset = editedMediaAsset,
            summary = "Improved",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_030_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            selectedMediaAssetId = editedMediaAsset.id,
            photoEditResults = listOf(photoEditResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        assertTrue(viewModel.state.isShowingEdited)

        viewModel.selectOriginalPhoto()

        assertFalse(viewModel.state.isShowingEdited)
        assertEquals(viewModel.state.originalPhotoUri, viewModel.state.selectedPhotoUri)
        assertNull(repository.get(draftId)?.selectedMediaAssetId)
        assertEquals("Using original photo", viewModel.state.statusMessage)
    }

    @Test
    fun `selectEditedPhoto selects latest edited asset and flips isShowingEdited`() {
        val editedMediaAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-1"),
            type = MediaType.EditedPhoto,
            uri = "file://photo-edited.jpg",
        )
        val photoEditResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-1"),
            requestId = PhotoEditRequestId("photo-edit-request-1"),
            draftId = draftId,
            editedMediaAsset = editedMediaAsset,
            summary = "Improved",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_030_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            photoEditResults = listOf(photoEditResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        assertFalse(viewModel.state.isShowingEdited)

        viewModel.selectEditedPhoto()

        assertTrue(viewModel.state.isShowingEdited)
        assertEquals("file://photo-edited.jpg", viewModel.state.selectedPhotoUri)
        assertEquals(editedMediaAsset.id, repository.get(draftId)?.selectedMediaAssetId)
        assertEquals("Using edited photo", viewModel.state.statusMessage)
    }

    @Test
    fun `selectEditedPhoto sets error status when no edited asset exists`() {
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoAdded,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        val prevCaption = viewModel.state.caption

        viewModel.selectEditedPhoto()

        assertEquals("No edited photo available", viewModel.state.statusMessage)
        assertEquals(prevCaption, viewModel.state.caption)
    }

    // ===== Copy tests =====

    @Test
    fun `copyCaption invokes clipboard with state caption and sets status message`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption to copy",
            shortCaption = "Short",
            hashtags = emptyList(),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
        )
        val clipboard = FakeClipboardWriter()
        val viewModel = createViewModel(
            repository = FakeManualWorkflowRepository(draft),
            clipboardWriter = clipboard,
        )

        viewModel.load()
        viewModel.copyCaption()

        assertEquals("caption", clipboard.lastLabel)
        assertEquals("Caption to copy", clipboard.lastText)
        assertEquals("Caption copied", viewModel.state.statusMessage)
    }

    @Test
    fun `copyAltText invokes clipboard with state alt text and sets status message`() {
        val altTextResult = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Alt text to copy",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_020_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            altTextResults = listOf(altTextResult),
        )
        val clipboard = FakeClipboardWriter()
        val viewModel = createViewModel(
            repository = FakeManualWorkflowRepository(draft),
            clipboardWriter = clipboard,
        )

        viewModel.load()
        viewModel.copyAltText()

        assertEquals("alt text", clipboard.lastLabel)
        assertEquals("Alt text to copy", clipboard.lastText)
        assertEquals("Alt text copied", viewModel.state.statusMessage)
    }

    // ===== Share tests =====

    @Test
    fun `shareOrExport with success records an Exported handoff and transitions draft to Shared`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = listOf("#test"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.ReadyToShare,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val resolvedUri = "content://com.digitumdei.shotquill.fileprovider/once-resolved.jpg"
        val shareLauncher = FakePostShareLauncher(
            success = true,
            destinationUri = resolvedUri,
        )
        val viewModel = createViewModel(
            repository = repository,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals("Image shared — caption copied to clipboard. Paste it in your target app.", viewModel.state.statusMessage)
        val updatedDraft = repository.get(draftId)!!
        assertEquals(DraftStatus.Shared, updatedDraft.status)
        assertEquals(1, updatedDraft.exportRecords.size)
        val exportRecord = updatedDraft.exportRecords.first()
        assertEquals(ExportStatus.Exported, exportRecord.status)
        assertEquals(resolvedUri, exportRecord.destinationUri)
        assertEquals("file://photo.jpg", shareLauncher.lastImageUri)
        assertTrue(shareLauncher.lastText?.contains("Caption") == true)
        assertTrue(shareLauncher.lastText?.contains("#test") == true)
    }

    @Test
    fun `shareOrExport normalizes bare hashtags in the composed share payload`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = listOf("launch", "#ready"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.ReadyToShare,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val shareLauncher = FakePostShareLauncher(success = true)
        val viewModel = createViewModel(
            repository = repository,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals("Caption\n\n#launch #ready", shareLauncher.lastText)
    }

    @Test
    fun `shareOrExport on already Shared draft records another Exported handoff without changing status`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = listOf("#test"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val existingExport = ExportRecord(
            id = ExportRecordId("export-existing"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            status = ExportStatus.Exported,
            destinationUri = null,
            errorMessage = null,
            createdAtEpochMillis = 1_700_000_000_000L,
            completedAtEpochMillis = 1_700_000_000_500L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.Shared,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
            targetPlatforms = setOf(TargetPlatform.InstagramFeedSquare),
            exportRecords = listOf(existingExport),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val shareLauncher = FakePostShareLauncher(success = true)
        val viewModel = createViewModel(
            repository = repository,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals("Image shared — caption copied to clipboard. Paste it in your target app.", viewModel.state.statusMessage)
        val updatedDraft = repository.get(draftId)!!
        assertEquals(DraftStatus.Shared, updatedDraft.status)
        assertEquals(2, updatedDraft.exportRecords.size)
        assertEquals(ExportStatus.Exported, updatedDraft.exportRecords[0].status)
        assertEquals(ExportStatus.Exported, updatedDraft.exportRecords[1].status)
        assertEquals("file://photo.jpg", shareLauncher.lastImageUri)
        assertTrue(shareLauncher.lastText?.contains("Caption") == true)
        assertTrue(shareLauncher.lastText?.contains("#test") == true)
    }

    @Test
    fun `shareOrExport with failure creates Failed ExportRecord and sets error message`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = emptyList(),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.ReadyToShare,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val shareError = "User cancelled the share"
        val shareLauncher = FakePostShareLauncher(
            success = false,
            errorMessage = shareError,
        )
        val viewModel = createViewModel(
            repository = repository,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals(shareError, viewModel.state.statusMessage)
        assertTrue(viewModel.state.isLoaded)
        assertEquals("Caption", viewModel.state.caption)
        assertEquals(3, repository.getCount)
        val updatedDraft = repository.get(draftId)!!
        assertEquals(DraftStatus.ReadyToShare, updatedDraft.status)
        assertEquals(1, updatedDraft.exportRecords.size)
        val exportRecord = updatedDraft.exportRecords.first()
        assertEquals(ExportStatus.Failed, exportRecord.status)
        assertEquals(shareError, exportRecord.errorMessage)
    }

    @Test
    fun `shareOrExport failure from TextGenerated persists ReadyToShare status and Failed export record after refresh`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = listOf("#test"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.TextGenerated,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val shareLauncher = FakePostShareLauncher(success = false)
        val viewModel = createViewModel(
            repository = repository,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals("Unable to open share sheet", viewModel.state.statusMessage)
        assertTrue(viewModel.state.isLoaded)
        assertEquals("Caption", viewModel.state.caption)
        assertEquals(3, repository.getCount)
        val updatedDraft = repository.get(draftId)!!
        assertEquals(DraftStatus.ReadyToShare, updatedDraft.status)
        assertEquals(1, updatedDraft.exportRecords.size)
        val exportRecord = updatedDraft.exportRecords.first()
        assertEquals(ExportStatus.Failed, exportRecord.status)
        assertEquals("Unable to open share sheet", exportRecord.errorMessage)
    }

    @Test
    fun `shareOrExport failure on already Shared draft keeps draft Shared and records Failed export`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = listOf("#test"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val existingExport = ExportRecord(
            id = ExportRecordId("export-existing"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            status = ExportStatus.Exported,
            destinationUri = null,
            errorMessage = null,
            createdAtEpochMillis = 1_700_000_000_000L,
            completedAtEpochMillis = 1_700_000_000_500L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.Shared,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
            targetPlatforms = setOf(TargetPlatform.InstagramFeedSquare),
            exportRecords = listOf(existingExport),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val shareLauncher = FakePostShareLauncher(success = false)
        val viewModel = createViewModel(
            repository = repository,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals("Unable to open share sheet", viewModel.state.statusMessage)
        assertTrue(viewModel.state.isLoaded)
        assertEquals("Caption", viewModel.state.caption)
        assertEquals(3, repository.getCount)
        val updatedDraft = repository.get(draftId)!!
        assertEquals(DraftStatus.Shared, updatedDraft.status)
        assertEquals(2, updatedDraft.exportRecords.size)
        assertEquals(ExportStatus.Exported, updatedDraft.exportRecords[0].status)
        assertEquals(ExportStatus.Failed, updatedDraft.exportRecords[1].status)
        assertEquals("Unable to open share sheet", updatedDraft.exportRecords[1].errorMessage)
    }

    @Test
    fun `shareOrExport sets error when canShare is false`() {
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoAdded,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        assertFalse(viewModel.state.actions.canShare)

        viewModel.shareOrExport()

        assertEquals("Cannot open share sheet: caption and photo are required", viewModel.state.statusMessage)
    }

    @Test
    fun `shareOrExport on Archived draft stops before illegal transition`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = listOf("#test"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.Archived,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val shareLauncher = FakePostShareLauncher(success = true)
        val viewModel = createViewModel(
            repository = repository,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        val shareResult = runCatching { viewModel.shareOrExport() }

        assertTrue(shareResult.isSuccess)
        assertEquals("Cannot open share sheet while status is archived", viewModel.state.statusMessage)
        val updatedDraft = repository.get(draftId)!!
        assertEquals(DraftStatus.Archived, updatedDraft.status)
        assertTrue(updatedDraft.exportRecords.isEmpty())
        assertNull(shareLauncher.lastImageUri)
        assertNull(shareLauncher.lastText)
    }

    @Test
    fun `shareOrExport copies composed caption to clipboard before share`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "My post caption",
            shortCaption = null,
            hashtags = listOf("#photo", "#landscape"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.ReadyToShare,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val clipboard = FakeClipboardWriter()
        val viewModel = createViewModel(
            repository = FakeManualWorkflowRepository(draft),
            clipboardWriter = clipboard,
            postShareLauncher = FakePostShareLauncher(success = true),
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals("post caption", clipboard.lastLabel)
        assertEquals("My post caption\n\n#photo #landscape", clipboard.lastText)
    }

    @Test
    fun `shareOrExport clipboard failure does not persist pending export and shows error`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = emptyList(),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.ReadyToShare,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val shareLauncher = FakePostShareLauncher(success = true)
        val failingClipboard = FailingClipboardWriter()
        val viewModel = createViewModel(
            repository = repository,
            clipboardWriter = failingClipboard,
            postShareLauncher = shareLauncher,
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals("Failed to copy caption to clipboard", viewModel.state.statusMessage)
        assertNull(shareLauncher.lastImageUri)
        assertNull(shareLauncher.lastText)
        val persistedDraft = repository.get(draftId)!!
        assertEquals(DraftStatus.ReadyToShare, persistedDraft.status)
        assertTrue(persistedDraft.exportRecords.isEmpty())
    }

    @Test
    fun `copyCaption does nothing when caption is null`() {
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoAdded,
        )
        val clipboard = FakeClipboardWriter()
        val viewModel = createViewModel(
            repository = FakeManualWorkflowRepository(draft),
            clipboardWriter = clipboard,
        )

        viewModel.load()
        assertNull(viewModel.state.caption)

        viewModel.copyCaption()

        assertNull(clipboard.lastLabel)
        assertNull(clipboard.lastText)
        assertNull(viewModel.state.statusMessage)
    }

    @Test
    fun `copyAltText does nothing when alt text is null`() {
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoAdded,
        )
        val clipboard = FakeClipboardWriter()
        val viewModel = createViewModel(
            repository = FakeManualWorkflowRepository(draft),
            clipboardWriter = clipboard,
        )

        viewModel.load()
        assertNull(viewModel.state.altText)

        viewModel.copyAltText()

        assertNull(clipboard.lastLabel)
        assertNull(clipboard.lastText)
        assertNull(viewModel.state.statusMessage)
    }

    // ===== New: Share action copies caption to clipboard =====

    @Test
    fun `shareOrExport copies caption and hashtags to clipboard for pasting into target app`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Check out this photo!",
            shortCaption = null,
            hashtags = listOf("#sunset", "#travel"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.ReadyToShare,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val clipboard = FakeClipboardWriter()
        val viewModel = createViewModel(
            repository = FakeManualWorkflowRepository(draft),
            clipboardWriter = clipboard,
            postShareLauncher = FakePostShareLauncher(success = true),
        )

        viewModel.load()
        viewModel.shareOrExport()

        assertEquals("post caption", clipboard.lastLabel)
        assertEquals("Check out this photo!\n\n#sunset #travel", clipboard.lastText)
        assertEquals("Image shared — caption copied to clipboard. Paste it in your target app.", viewModel.state.statusMessage)
    }

    @Test
    fun `archive transitions draft to Archived and sets status message`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = listOf("#test"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.ReadyToShare,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        assertTrue(viewModel.state.actions.canArchive)
        viewModel.archive()

        assertEquals("Draft archived", viewModel.state.statusMessage)
        assertFalse(viewModel.state.actions.canArchive)
        val updatedDraft = repository.get(draftId)!!
        assertEquals(DraftStatus.Archived, updatedDraft.status)
    }

    @Test
    fun `archive on already Archived draft is a no-op`() {
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = CaptionRequestId("caption-request-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Caption",
            shortCaption = null,
            hashtags = listOf("#test"),
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_010_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.Archived,
            captionResults = listOf(captionResult),
            selectedMediaAssetId = mediaAssetId,
        )
        val repository = FakeManualWorkflowRepository(draft)
        val viewModel = createViewModel(repository)

        viewModel.load()
        assertFalse(viewModel.state.actions.canArchive)
        viewModel.archive()

        val getCountAfterActions = repository.getCount
        assertEquals(DraftStatus.Archived, repository.get(draftId)!!.status)
        assertEquals(2, getCountAfterActions)
    }

    @Test
    fun `archive on missing draft sets error state`() {
        val repository = FakeManualWorkflowRepository(initialDraft = null)
        val viewModel = createViewModel(repository)

        viewModel.archive()

        assertFalse(viewModel.state.isLoaded)
        assertEquals("Draft not found", viewModel.state.statusMessage)
    }

    @Test
    fun `canArchive is true for all non-archived statuses`() {
        val statusesToTest = DraftStatus.entries.filter { it != DraftStatus.Archived }
        for (status in statusesToTest) {
            val draft = sampleDraft().copy(status = status)
            val repository = FakeManualWorkflowRepository(draft)
            val viewModel = createViewModel(repository)
            viewModel.load()
            assertTrue(viewModel.state.actions.canArchive, "Expected canArchive for status $status")
        }
    }

    private class FailingClipboardWriter : ClipboardWriter {
        override fun copy(label: String, text: String) {
            throw RuntimeException("Clipboard unavailable")
        }
    }

    private class FakeClipboardWriter : ClipboardWriter {
        var lastLabel: String? = null
        var lastText: String? = null
        override fun copy(label: String, text: String) {
            lastLabel = label
            lastText = text
        }
    }

    private class FakePostShareLauncher(
        private val success: Boolean,
        private val destinationUri: String? = null,
        private val errorMessage: String? = null,
    ) : PostShareLauncher {
        var lastImageUri: String? = null
        var lastText: String? = null
        override fun share(imageUri: String?, text: String): ShareResult {
            lastImageUri = imageUri
            lastText = text
            return ShareResult(success = success, destinationUri = destinationUri, errorMessage = errorMessage)
        }
    }

    private fun createViewModel(
        repository: ManualWorkflowRepository,
        clipboardWriter: ClipboardWriter = ClipboardWriter { _, _ -> },
        postShareLauncher: PostShareLauncher = PostShareLauncher { _, _ -> ShareResult(success = true) },
        clock: EpochClock = FixedClock(1_700_000_000_000L),
    ): FinalPostComposerViewModel =
        FinalPostComposerViewModel(
            draftId = draftId,
            repository = repository,
            clipboardWriter = clipboardWriter,
            postShareLauncher = postShareLauncher,
            clock = clock,
        )

    private fun sampleDraft(): PostDraft =
        PostDraft(
            id = draftId,
            format = PostFormat.SingleImage,
            status = DraftStatus.PhotoAdded,
            mediaItems = listOf(PostMediaItem(sampleMediaAsset(), order = 0)),
            caption = null,
            targetPlatforms = emptySet(),
            brandProfile = null,
            visionDescriptions = emptyList(),
            captionRequests = emptyList(),
            captionResults = emptyList(),
            altTextResults = emptyList(),
            photoEditRequests = emptyList(),
            photoEditResults = emptyList(),
            promptHistory = emptyList(),
            exportRecords = emptyList(),
            finalPostContent = null,
            createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            updatedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
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

    private class FakeManualWorkflowRepository(
        initialDraft: PostDraft? = null,
    ) : ManualWorkflowRepository {
        private val drafts: MutableMap<PostDraftId, PostDraft> =
            initialDraft?.let { mutableMapOf(it.id to it) } ?: mutableMapOf()
        private val storedVisionDescriptions = mutableListOf<VisionDescription>()
        private val storedPromptHistory = mutableListOf<PromptHistoryEntry>()
        var getCount = 0
            private set
        var finalPostContentGetCount = 0
            private set
        var finalPostContentSaveCount = 0
            private set
        var lastSavedFinalPostContent: FinalPostContent? = null
            private set

        init {
            initialDraft?.visionDescriptions?.let { storedVisionDescriptions.addAll(it) }
            initialDraft?.promptHistory?.let { storedPromptHistory += it }
        }

        override fun save(postDraft: PostDraft) {
            val existing = drafts[postDraft.id]
            drafts[postDraft.id] = if (postDraft.finalPostContent == null && existing?.finalPostContent != null) {
                postDraft.copy(finalPostContent = existing.finalPostContent)
            } else {
                postDraft
            }
        }
        override fun get(id: PostDraftId): PostDraft? {
            getCount++
            return drafts[id]
        }
        override fun listPostDrafts(): List<DraftSummary> = drafts.values.map {
            DraftSummary(it.id, it.status, it.caption?.text, it.createdAt.toEpochMilliseconds(), it.updatedAt.toEpochMilliseconds(), null)
        }

        override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean {
            val current = drafts[id] ?: return false
            drafts[id] = current.copy(status = status, updatedAt = updatedAt)
            return true
        }
        override fun updateUpdatedAt(id: PostDraftId, updatedAt: Instant): Boolean {
            val current = drafts[id] ?: return false
            drafts[id] = current.copy(updatedAt = updatedAt)
            return true
        }
        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean = false
        override fun updateSelectedMediaAsset(id: PostDraftId, mediaAssetId: MediaAssetId?, updatedAt: Instant): UpdateSelectionResult {
            val current = drafts[id] ?: return UpdateSelectionResult.DraftNotFound
            drafts[id] = current.copy(selectedMediaAssetId = mediaAssetId, updatedAt = updatedAt)
            return UpdateSelectionResult.Success
        }

        override fun saveVisionDescription(visionDescription: VisionDescription) {
            storedVisionDescriptions += visionDescription
            drafts[visionDescription.draftId] = drafts[visionDescription.draftId]!!.copy(
                visionDescriptions = (drafts[visionDescription.draftId]!!.visionDescriptions) + visionDescription,
            )
        }
        override fun save(visionDescription: VisionDescription) { saveVisionDescription(visionDescription) }
        override fun get(id: VisionDescriptionId): VisionDescription? = storedVisionDescriptions.find { it.id == id }
        override fun listVisionDescriptionsForDraft(id: PostDraftId): List<VisionDescription> =
            storedVisionDescriptions.filter { it.draftId == id }

        override fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry) {
            storedPromptHistory += promptHistoryEntry
            drafts[promptHistoryEntry.draftId] = drafts[promptHistoryEntry.draftId]!!.copy(
                promptHistory = (drafts[promptHistoryEntry.draftId]!!.promptHistory) + promptHistoryEntry,
            )
        }
        override fun save(promptHistoryEntry: PromptHistoryEntry) { savePromptHistoryEntry(promptHistoryEntry) }
        override fun get(id: PromptHistoryEntryId): PromptHistoryEntry? = storedPromptHistory.find { it.id == id }
        override fun listPromptHistoryForDraft(id: PostDraftId): List<PromptHistoryEntry> =
            storedPromptHistory.filter { it.draftId == id }

        override fun listPromptHistoryForMediaAsset(id: MediaAssetId): List<PromptHistoryEntry> =
            storedPromptHistory.filter { it.mediaAssetId == id }

        override fun save(mediaAsset: MediaAsset) {}
        override fun get(id: MediaAssetId): MediaAsset? = null
        override fun save(brandProfile: BrandProfile) {}
        override fun get(id: BrandProfileId): BrandProfile? = null
        override fun save(captionRequest: CaptionRequest) {}
        override fun getCaptionRequest(id: CaptionRequestId): CaptionRequest? = null
        override fun listCaptionRequestsForDraft(id: PostDraftId): List<CaptionRequest> = emptyList()
        override fun save(captionResult: CaptionResult) {}
        override fun getCaptionResult(id: CaptionResultId): CaptionResult? = null
        override fun listCaptionResultsForDraft(id: PostDraftId): List<CaptionResult> = emptyList()
        override fun save(altTextResult: AltTextResult) {}
        override fun get(id: AltTextResultId): AltTextResult? = null
        override fun listAltTextResultsForDraft(id: PostDraftId): List<AltTextResult> = emptyList()
        override fun save(photoEditRequest: PhotoEditRequest) {}
        override fun getPhotoEditRequest(id: PhotoEditRequestId): PhotoEditRequest? = null
        override fun listPhotoEditRequestsForDraft(id: PostDraftId): List<PhotoEditRequest> = emptyList()
        override fun save(photoEditResult: PhotoEditResult) {}
        override fun getPhotoEditResult(id: PhotoEditResultId): PhotoEditResult? = null
        override fun listPhotoEditResultsForDraft(id: PostDraftId): List<PhotoEditResult> = emptyList()
        override fun save(exportRecord: ExportRecord) {}
        override fun get(id: ExportRecordId): ExportRecord? = null
        override fun listExportRecordsForDraft(id: PostDraftId): List<ExportRecord> = emptyList()
        override fun saveCaptionRequest(captionRequest: CaptionRequest) {}
        override fun saveCaptionResult(captionResult: CaptionResult) {}
        override fun saveAltTextResult(altTextResult: AltTextResult) {}
        override fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest) {}
        override fun savePhotoEditResult(photoEditResult: PhotoEditResult) {}
        override fun saveExportRecord(exportRecord: ExportRecord) {}
        override fun saveFinalPostContent(finalPostContent: FinalPostContent) {
            finalPostContentSaveCount++
            lastSavedFinalPostContent = finalPostContent
            drafts[finalPostContent.draftId] = drafts[finalPostContent.draftId]!!.copy(finalPostContent = finalPostContent)
        }
        override fun getFinalPostContent(draftId: PostDraftId): FinalPostContent? =
            run {
                finalPostContentGetCount++
                drafts[draftId]?.finalPostContent
            }
        override fun savePhotoEditSuccess(
            draftId: PostDraftId,
            editedMediaAsset: MediaAsset,
            editRequest: PhotoEditRequest,
            editResult: PhotoEditResult,
            promptHistoryEntry: PromptHistoryEntry,
            targetStatus: DraftStatus,
            updatedAt: Instant,
        ): PostDraft? = null
        override fun savePhotoEditFailure(
            draftId: PostDraftId,
            editRequest: PhotoEditRequest,
            promptHistoryEntry: PromptHistoryEntry,
            updatedAt: Instant,
        ): PostDraft? = null
        override fun recordPostTextGeneration(
            draftId: PostDraftId,
            status: DraftStatus,
            caption: CaptionDraft,
            targetPlatform: TargetPlatform,
            brandProfile: BrandProfile?,
            captionRequest: CaptionRequest,
            captionResult: CaptionResult,
            altTextResult: AltTextResult,
            promptHistoryEntries: List<PromptHistoryEntry>,
            updatedAt: Instant,
        ): PostDraft? = null
        override fun clearAll() {
            drafts.clear()
            storedVisionDescriptions.clear()
            storedPromptHistory.clear()
        }
    }

    private class FixedClock(private val now: Long) : EpochClock {
        override fun nowMillis(): Long = now
    }
}
