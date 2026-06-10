package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.AltTextResultId
import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.ai.AiError
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.ExportStatus
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType

import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PhotoEditResultId
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.domain.PostMediaItem
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.VisionDescriptionId
import com.digitumdei.shotquill.shared.domain.VisionDescriptionPromptFactory
import com.digitumdei.shotquill.shared.domain.primaryMediaAsset
import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.CaptionRequest
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import com.digitumdei.shotquill.shared.domain.FinalPostContent
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.storage.UpdateSelectionResult
import com.digitumdei.shotquill.shared.workflow.AnalyzeVision
import com.digitumdei.shotquill.shared.workflow.PhotoEditExecutionError
import com.digitumdei.shotquill.shared.workflow.PhotoEditExecutionResult
import com.digitumdei.shotquill.shared.workflow.PhotoEditExecutor
import com.digitumdei.shotquill.shared.workflow.PostTextGenerationError
import com.digitumdei.shotquill.shared.workflow.PostTextGenerationResult
import com.digitumdei.shotquill.shared.workflow.PostTextGenerator
import com.digitumdei.shotquill.shared.workflow.VisionDescriptionAnalysisError
import com.digitumdei.shotquill.shared.workflow.VisionDescriptionAnalysisResult
import com.digitumdei.shotquill.shared.ai.FakeAiProvider
import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.workflow.AnalyzeVisionWorkflow
import com.digitumdei.shotquill.shared.workflow.VisionImageSource
import com.digitumdei.shotquill.shared.workflow.SourceImageResult
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertEquals(null, viewModel.state.visionDescription)
        assertEquals(null, viewModel.state.generatedCaption)
    }

    @Test
    fun exposesStateWithOnlyOriginalMedia() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = RecordingPhotoEditExecutor(),
        )

        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals(null, viewModel.state.editedPhotoUri)
        assertEquals(null, viewModel.state.visionDescription)
        assertEquals(null, viewModel.state.generatedAltText)
        assertTrue(viewModel.state.actions.canAnalyzeVision)
        assertTrue(viewModel.state.actions.canGeneratePostText)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
        assertFalse(viewModel.state.actions.canCopyCaption)
        assertFalse(viewModel.state.actions.canCopyAltText)
        assertFalse(viewModel.state.actions.canShareOrExport)
    }

    @Test
    fun exposesStateWithGeneratedText() {
        val repository = FakePostDraftRepository(sampleDraftWithGeneratedText())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = RecordingPhotoEditExecutor(),
        )

        viewModel.load()

        assertEquals("Morning focus, freshly brewed.", viewModel.state.generatedCaption)
        assertEquals("Coffee cup beside an open notebook.", viewModel.state.generatedAltText)
        assertEquals(TargetPlatform.InstagramFeedSquare, viewModel.state.targetPlatform)
        assertTrue(viewModel.state.actions.canCopyCaption)
        assertTrue(viewModel.state.actions.canCopyAltText)
        assertTrue(viewModel.state.actions.canShareOrExport)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun exposesStateWithEditedMedia() {
        val repository = FakePostDraftRepository(sampleDraftWithEditedMedia())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = RecordingPhotoEditExecutor(),
        )

        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals("file://photo-edited.jpg", viewModel.state.editedPhotoUri)
        assertEquals(DraftStatus.PhotoEdited, viewModel.state.draftStatus)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun selectEditedPhotoSetsActivePhotoToLatestEditedAsset() {
        val repository = FakePostDraftRepository(sampleDraftWithEditedMedia())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri, "Active photo defaults to original when no selection made")
        assertTrue(viewModel.state.actions.canSelectEditedPhoto)

        viewModel.selectEditedPhoto()

        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        assertEquals("Using edited photo", viewModel.state.statusMessage)
        assertTrue(viewModel.state.actions.canSelectOriginalPhoto, "Should be able to switch back to original")
        assertFalse(viewModel.state.actions.canSelectEditedPhoto, "Edited already selected, should not be selectable")
    }

    @Test
    fun selectOriginalPhotoClearsSelectionAndRestoresActivePhoto() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(
            selectedMediaAssetId = editedMediaId,
        )
        val repository = FakePostDraftRepository(draft)
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        assertTrue(viewModel.state.actions.canSelectOriginalPhoto)

        viewModel.selectOriginalPhoto()

        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)
        assertEquals("Using original photo", viewModel.state.statusMessage)
        assertFalse(viewModel.state.actions.canSelectOriginalPhoto, "Original is active, should not be selectable")
        assertTrue(viewModel.state.actions.canSelectEditedPhoto, "Should be able to select edited")
    }

    @Test
    fun selectEditedPhotoHandlesMissingDraft() {
        val repository = FakePostDraftRepository()
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.selectEditedPhoto()

        assertEquals("Draft not found", viewModel.state.statusMessage)
    }

    @Test
    fun selectEditedPhotoHandlesNoEditedResults() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.selectEditedPhoto()

        assertEquals("No edited photo available", viewModel.state.statusMessage)
    }

    @Test
    fun selectOriginalPhotoHandlesMissingDraft() {
        val repository = FakePostDraftRepository()
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.selectOriginalPhoto()

        assertEquals("Draft not found", viewModel.state.statusMessage)
    }

    @Test
    fun selectionUsesIdComparisonWhenUrisAreIdentical() {
        val sameUri = "file://photo.jpg"
        val editedAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-uri-test"),
            type = MediaType.EditedPhoto,
            uri = sameUri,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-uri-test"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.ImproveLighting,
                    realismLevel = RealismLevel.Photoreal,
                    qualityTier = QualityTier.Standard,
                    prompt = "Brighten the image",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.InstagramFeedSquare,
                    createdAtEpochMillis = 1_700_000_025_000L,
                ),
            ),
            photoEditResults = listOf(
                PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-uri-test"),
                    requestId = PhotoEditRequestId("photo-edit-request-uri-test"),
                    draftId = draftId,
                    editedMediaAsset = editedAsset,
                    summary = "Adjusted brightness.",
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_030_000L,
                ),
            ),
        )
        val repository = FakePostDraftRepository(draft)
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertEquals(sameUri, viewModel.state.originalPhotoUri)
        assertEquals(sameUri, viewModel.state.editedPhotoUri, "Both photos share the same URI to test ID-based logic")
        assertEquals(sameUri, viewModel.state.activePhotoUri, "Defaults to original when no selection made")
        assertTrue(viewModel.state.actions.canSelectEditedPhoto, "Edited differs by ID, should be selectable despite same URI")

        viewModel.selectEditedPhoto()

        assertTrue(viewModel.state.actions.canSelectOriginalPhoto, "Active is edited, should be able to switch back to original")
        assertFalse(viewModel.state.actions.canSelectEditedPhoto, "Edited is active, should not be selectable")
    }

    @Test
    fun activePhotoUriDerivesFromOriginalWhenNoSelectionMade() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertNull(viewModel.state.editedPhotoUri)
    }

    @Test
    fun activePhotoUriDerivesFromEditedAfterSelectEditedPhoto() {
        val repository = FakePostDraftRepository(sampleDraftWithEditedMedia())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()
        viewModel.selectEditedPhoto()

        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        val stored = repository.get(draftId)
        assertEquals(MediaAssetId("media-edited-1"), stored?.selectedMediaAssetId)
    }

    @Test
    fun activePhotoUriRevertsToOriginalAfterSelectOriginalPhoto() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(selectedMediaAssetId = editedMediaId)
        val repository = FakePostDraftRepository(draft)
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        viewModel.selectOriginalPhoto()

        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)
        val stored = repository.get(draftId)
        assertNull(stored?.selectedMediaAssetId)
    }

    @Test
    fun canSelectEditedPhotoIsFalseWhenNoEditedResultsExist() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertFalse(viewModel.state.actions.canSelectEditedPhoto)
    }

    @Test
    fun canSelectOriginalPhotoIsFalseWhenOriginalIsActive() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertFalse(viewModel.state.actions.canSelectOriginalPhoto)
    }

    @Test
    fun selectionActionFlagsAreFalseForArchivedDraft() {
        val repository = FakePostDraftRepository(
            sampleDraftWithEditedMedia().copy(status = DraftStatus.Archived),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertFalse(viewModel.state.actions.canSelectEditedPhoto)
        assertFalse(viewModel.state.actions.canSelectOriginalPhoto)
    }

    @Test
    fun selectEditedPhotoPersistsSelectedMediaAssetIdInRepository() {
        val repository = FakePostDraftRepository(sampleDraftWithEditedMedia())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertNull(repository.get(draftId)?.selectedMediaAssetId)

        viewModel.selectEditedPhoto()

        val stored = repository.get(draftId)
        assertEquals(MediaAssetId("media-edited-1"), stored?.selectedMediaAssetId)
    }

    @Test
    fun selectOriginalPhotoPersistsNullSelectedMediaAssetIdInRepository() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(selectedMediaAssetId = editedMediaId)
        val repository = FakePostDraftRepository(draft)
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertEquals(editedMediaId, repository.get(draftId)?.selectedMediaAssetId)

        viewModel.selectOriginalPhoto()

        assertNull(repository.get(draftId)?.selectedMediaAssetId)
    }

    @Test
    fun activePhotoUriCanSwitchBackAndForthBetweenOriginalAndEdited() {
        val repository = FakePostDraftRepository(sampleDraftWithEditedMedia())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)

        viewModel.selectEditedPhoto()
        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        assertEquals(MediaAssetId("media-edited-1"), repository.get(draftId)?.selectedMediaAssetId)

        viewModel.selectOriginalPhoto()
        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)
        assertNull(repository.get(draftId)?.selectedMediaAssetId)

        viewModel.selectEditedPhoto()
        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        assertEquals(MediaAssetId("media-edited-1"), repository.get(draftId)?.selectedMediaAssetId)

        viewModel.selectOriginalPhoto()
        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)
        assertNull(repository.get(draftId)?.selectedMediaAssetId)
    }

    @Test
    fun selectEditedPhotoHandlesRepositoryUpdateReturningDraftNotFound() {
        val repository = FakePostDraftRepository(
            sampleDraftWithEditedMedia(),
            simulatedUpdateSelectionResult = UpdateSelectionResult.DraftNotFound,
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.selectEditedPhoto()

        assertEquals("Draft not found", viewModel.state.statusMessage)
        assertNull(viewModel.state.activePhotoUri)
    }

    @Test
    fun selectEditedPhotoHandlesAssetNotOwnedByDraftAndPreservesWorkspace() {
        val draft = sampleDraftWithEditedMedia()
        val repository = FakePostDraftRepository(
            draft,
            simulatedUpdateSelectionResult = UpdateSelectionResult.AssetNotOwnedByDraft,
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Softer contrast")
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.BlueskyPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.High)
        viewModel.togglePromptHistory()

        viewModel.selectEditedPhoto()

        assertEquals("Selected asset is not part of this draft", viewModel.state.statusMessage)
        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals("file://photo-edited.jpg", viewModel.state.editedPhotoUri)
        assertEquals(EditIntent.RemoveObject, viewModel.state.photoEditForm.selectedIntent)
        assertEquals("Softer contrast", viewModel.state.photoEditForm.userRefinementText)
        assertEquals(TargetPlatform.BlueskyPost, viewModel.state.photoEditForm.selectedTargetPlatform)
        assertEquals(QualityTier.High, viewModel.state.photoEditForm.selectedQualityTier)
        assertTrue(viewModel.state.isPromptHistoryVisible)
    }

    @Test
    fun selectOriginalPhotoHandlesRepositoryUpdateReturningDraftNotFound() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(selectedMediaAssetId = editedMediaId)
        val repository = FakePostDraftRepository(
            draft,
            simulatedUpdateSelectionResult = UpdateSelectionResult.DraftNotFound,
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.selectOriginalPhoto()

        assertEquals("Draft not found", viewModel.state.statusMessage)
        assertNull(viewModel.state.activePhotoUri)
    }

    @Test
    fun selectOriginalPhotoHandlesAssetNotOwnedByDraftAndPreservesWorkspace() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(selectedMediaAssetId = editedMediaId)
        val repository = FakePostDraftRepository(
            draft,
            simulatedUpdateSelectionResult = UpdateSelectionResult.AssetNotOwnedByDraft,
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.StyleTransfer)
        viewModel.updatePhotoEditRefinement("Vintage film look")
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.FacebookPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.Draft)
        viewModel.togglePromptHistory()

        viewModel.selectOriginalPhoto()

        assertEquals("Selected asset is not part of this draft", viewModel.state.statusMessage)
        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals(EditIntent.StyleTransfer, viewModel.state.photoEditForm.selectedIntent)
        assertEquals("Vintage film look", viewModel.state.photoEditForm.userRefinementText)
        assertEquals(TargetPlatform.FacebookPost, viewModel.state.photoEditForm.selectedTargetPlatform)
        assertEquals(QualityTier.Draft, viewModel.state.photoEditForm.selectedQualityTier)
        assertTrue(viewModel.state.isPromptHistoryVisible)
    }

    @Test
    fun reportsDraftNotFoundWhenLoadingMissingDraft() {
        val repository = FakePostDraftRepository()
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertEquals("Draft not found", viewModel.state.statusMessage)
        assertNull(viewModel.state.originalPhotoUri)
        assertFalse(viewModel.state.actions.canAnalyzeVision)
        assertFalse(viewModel.state.actions.canGeneratePostText)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
        assertFalse(viewModel.state.actions.canCopyCaption)
        assertFalse(viewModel.state.actions.canCopyAltText)
        assertFalse(viewModel.state.actions.canShareOrExport)
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
        assertEquals(
            "Generate a manual post caption for instagram_feed_square from file://photo.jpg.",
            viewModel.state.promptHistory[0].prompt,
        )
        assertEquals(
            "Generate accessible alt text for file://photo.jpg.",
            viewModel.state.promptHistory[1].prompt,
        )
        assertTrue(viewModel.state.actions.canShareOrExport)
    }

    @Test
    fun usesConfiguredPostTextGeneratorWhenGeneratingText() {
        val generated = sampleDraftWithGeneratedText().copy(
            caption = CaptionDraft("Pipeline caption.", listOf("#pipeline")),
            targetPlatforms = setOf(TargetPlatform.BlueskyPost),
        )
        val repository = FakePostDraftRepository(sampleDraft())
        val generator = RecordingPostTextGenerator(generated)
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            postTextGenerator = generator,
            defaultTargetPlatform = TargetPlatform.BlueskyPost,
        )
        viewModel.load()

        viewModel.generatePostText()

        assertEquals(listOf(TargetPlatform.BlueskyPost), generator.targetPlatforms)
        assertEquals("Pipeline caption.", viewModel.state.generatedCaption)
        assertEquals("Generated caption and alt text", viewModel.state.statusMessage)
    }

    @Test
    fun updatesStoredDraftWhenAnalyzingVisionWithFakeAiProvider() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = FakeAnalyzeVision(repository, FixedClock(1_700_000_090_000L)),
            clock = FixedClock(1_700_000_090_000L),
        )
        viewModel.load()

        viewModel.analyzeVisionDescription()

        val stored = repository.get(draftId)
        assertEquals(
            "Photo shows photo.jpg prepared for social content.",
            viewModel.state.visionDescription,
        )
        assertEquals(viewModel.state.visionDescription, stored?.visionDescriptions?.firstOrNull()?.description)
        assertEquals(DraftStatus.PhotoAdded, stored?.status)
        assertEquals(1, viewModel.state.promptHistory.size)
        assertEquals(
            "vision_description",
            viewModel.state.promptHistory.single().operationType.wireValue,
        )
        assertTrue(viewModel.state.promptHistory.single().prompt.contains("Visible text or logos"))
        assertEquals("Analyzed photo", viewModel.state.statusMessage)
    }

    @Test
    fun reusesCachedVisionDescriptionInWorkspace() {
        val repository = FakePostDraftRepository(
            sampleDraft().copy(
                visionDescriptions = listOf(VisionDescription(
                    id = VisionDescriptionId("vision-description-1"),
                    draftId = draftId,
                    mediaAssetId = mediaAssetId,
                    description = "Cached workspace description.",
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_080_000L,
                )),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = FakeAnalyzeVision(repository, FixedClock(1_700_000_090_000L)),
            clock = FixedClock(1_700_000_090_000L),
        )
        viewModel.load()

        viewModel.analyzeVisionDescription()

        assertEquals("Cached workspace description.", viewModel.state.visionDescription)
        assertEquals("Reused cached vision description", viewModel.state.statusMessage)
        assertEquals(emptyList(), viewModel.state.promptHistory)
    }

    @Test
    fun reusesCachedVisionDescriptionForActiveMediaAssetAcrossAssetSwitch() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val originalPhotoDescription = VisionDescription(
            id = VisionDescriptionId("vision-description-original"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            description = "Description for the original photo.",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_080_000L,
        )
        val editedPhotoDescription = VisionDescription(
            id = VisionDescriptionId("vision-description-edited"),
            draftId = draftId,
            mediaAssetId = editedMediaId,
            description = "Description for the edited photo.",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_090_000L,
        )
        val repository = FakePostDraftRepository(
            sampleDraftWithEditedMedia().copy(
                selectedMediaAssetId = editedMediaId,
                visionDescriptions = listOf(originalPhotoDescription),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = object : AnalyzeVision {
                override fun analyzePrimaryPhoto(
                    draftId: PostDraftId,
                    reuseCached: Boolean,
                ): VisionDescriptionAnalysisResult {
                    return VisionDescriptionAnalysisResult.Success(
                        editedPhotoDescription,
                        cacheHit = true,
                    )
                }
            },
            clock = FixedClock(1_700_000_090_000L),
        )
        viewModel.load()

        viewModel.analyzeVisionDescription()

        assertEquals("Description for the edited photo.", viewModel.state.visionDescription)
        assertEquals("Reused cached vision description", viewModel.state.statusMessage)
    }

    @Test
    fun failsClosedWhenNoAnalyzeVisionConfigured() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.analyzeVisionDescription()

        assertEquals("Vision analysis not available", viewModel.state.statusMessage)
        assertNull(viewModel.state.visionDescription)
    }

    @Test
    fun preservesWorkspaceStateWhenAnalyzeVisionFailsWithProviderError() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = object : AnalyzeVision {
                override fun analyzePrimaryPhoto(
                    draftId: PostDraftId,
                    reuseCached: Boolean,
                ): VisionDescriptionAnalysisResult {
                    return VisionDescriptionAnalysisResult.Failure(
                        VisionDescriptionAnalysisError.Provider(AiError.RateLimited()),
                    )
                }
            },
            photoEditExecutor = RecordingPhotoEditExecutor(),
        )
        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Softer contrast")
        viewModel.togglePromptHistory()

        viewModel.analyzeVisionDescription()

        val draft = repository.get(draftId)
        assertEquals("Unable to analyze photo: The AI provider is rate limited. Try again later.", viewModel.state.statusMessage)
        assertNotNull(draft, "Draft should still exist in repository")
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri, "Original photo URI preserved")
        assertTrue(viewModel.state.actions.canAnalyzeVision, "Can retry vision analysis")
        assertTrue(viewModel.state.actions.canGeneratePostText, "Can still generate text")
        assertTrue(viewModel.state.actions.canEditPhotoWithAi, "Can still edit photo")
        assertTrue(viewModel.state.isPromptHistoryVisible, "Prompt history visibility preserved")
        assertEquals(EditIntent.RemoveObject, viewModel.state.photoEditForm.selectedIntent, "Edit intent preserved")
        assertEquals("Softer contrast", viewModel.state.photoEditForm.userRefinementText, "Refinement text preserved")
    }

    @Test
    fun preservesWorkspaceStateWhenAnalyzeVisionFailsWithImageLoadFailure() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = object : AnalyzeVision {
                override fun analyzePrimaryPhoto(
                    draftId: PostDraftId,
                    reuseCached: Boolean,
                ): VisionDescriptionAnalysisResult {
                    return VisionDescriptionAnalysisResult.Failure(
                        VisionDescriptionAnalysisError.ImageLoadFailure("Corrupted image file"),
                    )
                }
            },
            photoEditExecutor = RecordingPhotoEditExecutor(),
        )
        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Softer contrast")
        viewModel.togglePromptHistory()

        viewModel.analyzeVisionDescription()

        val draft = repository.get(draftId)
        assertEquals("Corrupted image file", viewModel.state.statusMessage)
        assertNotNull(draft, "Draft should still exist in repository")
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri, "Original photo URI preserved")
        assertTrue(viewModel.state.actions.canAnalyzeVision, "Can retry vision analysis")
        assertTrue(viewModel.state.actions.canGeneratePostText, "Can still generate text")
        assertTrue(viewModel.state.actions.canEditPhotoWithAi, "Can still edit photo")
        assertTrue(viewModel.state.isPromptHistoryVisible, "Prompt history visibility preserved")
        assertEquals(EditIntent.RemoveObject, viewModel.state.photoEditForm.selectedIntent, "Edit intent preserved")
        assertEquals("Softer contrast", viewModel.state.photoEditForm.userRefinementText, "Refinement text preserved")
    }

    @Test
    fun showsUnloadedStateWhenAnalyzeVisionFailsWithDraftNotFound() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = object : AnalyzeVision {
                override fun analyzePrimaryPhoto(
                    draftId: PostDraftId,
                    reuseCached: Boolean,
                ): VisionDescriptionAnalysisResult {
                    return VisionDescriptionAnalysisResult.Failure(
                        VisionDescriptionAnalysisError.DraftNotFound,
                    )
                }
            },
        )
        viewModel.load()

        viewModel.analyzeVisionDescription()

        assertEquals("Draft not found", viewModel.state.statusMessage)
        assertNull(viewModel.state.draftStatus, "Draft status should be null in unloaded state")
        assertNull(viewModel.state.visionDescription, "Vision description should be null in unloaded state")
        assertNull(viewModel.state.originalPhotoUri, "Original photo URI should be null in unloaded state")
        assertFalse(viewModel.state.actions.canAnalyzeVision, "Cannot analyze vision in unloaded state")
        assertFalse(viewModel.state.actions.canGeneratePostText, "Cannot generate text in unloaded state")
        assertFalse(viewModel.state.actions.canEditPhotoWithAi, "Cannot edit photo in unloaded state")
    }

    @Test
    fun analyzeVisionWithInjectedAnalyzerStoresDescriptionWithCorrectMediaAssetId() {
        val clock = FixedClock(1_700_000_090_000L)
        val repository = FakeManualWorkflowRepository(sampleDraft())
        val imageSource = VisionImageSource { _ ->
            SourceImageResult.Success(
                AiImageInput(
                    bytes = byteArrayOf(0, 1, 2),
                    mimeType = "image/jpeg",
                    fileName = "test.jpg",
                ),
            )
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = AnalyzeVisionWorkflow(
                repository = repository,
                aiProvider = FakeAiProvider(),
                imageSource = imageSource,
                clock = clock,
            ),
            clock = clock,
        )
        viewModel.load()

        viewModel.analyzeVisionDescription()

        val stored = repository.get(draftId)
        assertNotNull(stored?.visionDescriptions?.firstOrNull())
        assertTrue(
            stored?.visionDescriptions?.firstOrNull()?.description?.startsWith("Fake vision for media-1:") ?: false,
            "Description should come from injected FakeAiProvider",
        )
        assertEquals(mediaAssetId, stored?.visionDescriptions?.firstOrNull()?.mediaAssetId)
        assertEquals("Analyzed photo", viewModel.state.statusMessage)
        assertTrue(viewModel.state.promptHistory.isNotEmpty())
    }

    @Test
    fun selectEditedPhotoHidesOriginalPhotoVisionDescription() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val originalDescription = VisionDescription(
            id = VisionDescriptionId("vision-description-original"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            description = "Original photo description for regression test.",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_080_000L,
        )
        val repository = FakePostDraftRepository(
            sampleDraftWithEditedMedia().copy(visionDescriptions = listOf(originalDescription)),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertEquals("Original photo description for regression test.", viewModel.state.visionDescription)
        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)

        viewModel.selectEditedPhoto()

        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        assertEquals("Using edited photo", viewModel.state.statusMessage)
        assertNull(viewModel.state.visionDescription, "Vision description hidden when active asset differs")
        assertEquals(editedMediaId, repository.get(draftId)?.selectedMediaAssetId)
    }

    @Test
    fun doesNotReuseCachedVisionDescriptionWhenMediaAssetIdDoesNotMatch() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val originalDescription = VisionDescription(
            id = VisionDescriptionId("vision-description-original"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            description = "Legacy cached description for original photo.",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_080_000L,
        )
        val clock = FixedClock(1_700_000_090_000L)
        val repository = FakeManualWorkflowRepository(
            sampleDraftWithEditedMedia().copy(visionDescriptions = listOf(originalDescription)),
        )
        val imageSource = VisionImageSource { _ ->
            SourceImageResult.Success(
                AiImageInput(
                    bytes = byteArrayOf(0, 1, 2),
                    mimeType = "image/jpeg",
                    fileName = "test.jpg",
                ),
            )
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = AnalyzeVisionWorkflow(
                repository = repository,
                aiProvider = FakeAiProvider(),
                imageSource = imageSource,
                clock = clock,
            ),
            clock = clock,
        )
        viewModel.load()

        assertEquals("Legacy cached description for original photo.", viewModel.state.visionDescription)

        viewModel.selectEditedPhoto()
        assertNull(viewModel.state.visionDescription)

        viewModel.analyzeVisionDescription()

        val stored = repository.get(draftId)
        assertTrue(
            viewModel.state.visionDescription?.startsWith("Fake vision for media-edited-1:") ?: false,
            "Description should come from injected FakeAiProvider for edited photo",
        )
        val newestStored = stored?.visionDescriptions?.maxByOrNull { it.createdAtEpochMillis }
        assertNotEquals("Legacy cached description for original photo.", newestStored?.description)
        assertEquals(editedMediaId, newestStored?.mediaAssetId)
        assertEquals("Analyzed photo", viewModel.state.statusMessage)
    }

    @Test
    fun reusesCachedVisionDescriptionWithRealAnalyzerForEditedPhotoAfterAssetSwitch() {
        val editedMediaId = MediaAssetId("media-edited-1")
        val editedDescription = VisionDescription(
            id = VisionDescriptionId("vision-description-edited-cached"),
            draftId = draftId,
            mediaAssetId = editedMediaId,
            description = "Cached description for the edited photo via real analyzer.",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_080_000L,
        )
        val clock = FixedClock(1_700_000_090_000L)
        val repository = FakeManualWorkflowRepository(
            sampleDraftWithEditedMedia().copy(selectedMediaAssetId = editedMediaId),
        )
        repository.saveVisionDescription(editedDescription)
        val imageSource = VisionImageSource { _ ->
            SourceImageResult.Success(
                AiImageInput(
                    bytes = byteArrayOf(0, 1, 2),
                    mimeType = "image/jpeg",
                    fileName = "test.jpg",
                ),
            )
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = AnalyzeVisionWorkflow(
                repository = repository,
                aiProvider = FakeAiProvider(),
                imageSource = imageSource,
                clock = clock,
            ),
            clock = clock,
        )
        viewModel.load()

        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri)
        assertEquals(
            "Cached description for the edited photo via real analyzer.",
            viewModel.state.visionDescription,
            "Cached vision description for the active edited photo is shown after load",
        )

        viewModel.analyzeVisionDescription()

        assertEquals("Cached description for the edited photo via real analyzer.", viewModel.state.visionDescription)
        assertEquals("Reused cached vision description", viewModel.state.statusMessage)
        assertTrue(viewModel.state.promptHistory.isEmpty(), "No prompt history entry for cache hit")
    }

    @Test
    fun preservesExistingVisionDescriptionOnAnalyzeFailure() {
        val existingDescription = VisionDescription(
            id = VisionDescriptionId("vision-description-existing"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            description = "Existing vision description to preserve on failure.",
            modelName = "fake",
            createdAtEpochMillis = 1_700_000_080_000L,
        )
        val repository = FakePostDraftRepository(
            sampleDraft().copy(visionDescriptions = listOf(existingDescription)),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = object : AnalyzeVision {
                override fun analyzePrimaryPhoto(
                    draftId: PostDraftId,
                    reuseCached: Boolean,
                ): VisionDescriptionAnalysisResult {
                    return VisionDescriptionAnalysisResult.Failure(
                        VisionDescriptionAnalysisError.Provider(AiError.RateLimited()),
                    )
                }
            },
            photoEditExecutor = RecordingPhotoEditExecutor(),
        )
        viewModel.load()

        assertEquals("Existing vision description to preserve on failure.", viewModel.state.visionDescription)

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Softer contrast")
        viewModel.togglePromptHistory()

        viewModel.analyzeVisionDescription()

        assertEquals("Unable to analyze photo: The AI provider is rate limited. Try again later.", viewModel.state.statusMessage)
        assertEquals("Existing vision description to preserve on failure.", viewModel.state.visionDescription, "Existing vision description preserved in state")
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertTrue(viewModel.state.actions.canAnalyzeVision, "Can retry vision analysis")
        assertTrue(viewModel.state.actions.canGeneratePostText, "Can still generate text")
        assertTrue(viewModel.state.actions.canEditPhotoWithAi, "Can still edit photo")
        assertTrue(viewModel.state.isPromptHistoryVisible, "Prompt history visibility preserved")
        assertEquals(EditIntent.RemoveObject, viewModel.state.photoEditForm.selectedIntent, "Edit intent preserved")
        assertEquals("Softer contrast", viewModel.state.photoEditForm.userRefinementText, "Refinement text preserved")
    }

    @Test
    fun usesConfiguredDefaultPlatformWhenDraftHasNoTargetPlatform() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_100_000L),
            defaultTargetPlatform = TargetPlatform.BlueskyPost,
        )
        viewModel.load()

        viewModel.generatePostText()

        val stored = repository.get(draftId)
        assertEquals(TargetPlatform.BlueskyPost, viewModel.state.targetPlatform)
        assertEquals(setOf(TargetPlatform.BlueskyPost), stored?.targetPlatforms)
        assertEquals("Ready for bluesky_post: photo.jpg", viewModel.state.generatedCaption)
    }

    @Test
    fun keepsPhotoEditedStatusWhenGeneratingTextAfterPhotoEdit() {
        val repository = FakePostDraftRepository(sampleDraftWithEditedMedia())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_400_000L),
        )
        viewModel.load()

        viewModel.generatePostText()

        val stored = repository.get(draftId)
        assertEquals(DraftStatus.PhotoEdited, stored?.status)
        assertEquals("Ready for instagram_feed_square: photo.jpg", viewModel.state.generatedCaption)
        assertEquals("Generated caption and alt text", viewModel.state.statusMessage)
    }

    @Test
    fun supportsGenerateThenEditWorkflow() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = IncrementingClock(1_700_000_500_000L),
        )
        viewModel.load()

        viewModel.generatePostText()
        viewModel.editPhotoWithAi()

        val stored = repository.get(draftId)
        assertEquals(DraftStatus.PhotoEdited, stored?.status)
        assertEquals("Ready for instagram_feed_square: photo.jpg", viewModel.state.generatedCaption)
        assertEquals("file://executor-edited.jpg", viewModel.state.editedPhotoUri)
        assertEquals(PhotoEditFormOperationState.Idle, viewModel.state.photoEditForm.operationState)
    }

    @Test
    fun rejectsLegacyDraftStatusWithoutThrowing() {
        val repository = FakePostDraftRepository(sampleDraft().copy(status = DraftStatus.Draft))
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.InvalidDraftStatus(DraftStatus.Draft),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            analyzeVision = FakeAnalyzeVision(repository),
            photoEditExecutor = executor,
        )
        viewModel.load()

        assertFalse(viewModel.state.actions.canGeneratePostText)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
        assertFalse(viewModel.state.actions.canAnalyzeVision)

        viewModel.analyzeVisionDescription()
        assertEquals("Cannot analyze vision while status is draft", viewModel.state.statusMessage)
        assertEquals(DraftStatus.Draft, repository.get(draftId)?.status)

        viewModel.generatePostText()
        assertEquals("Cannot generate text while status is draft", viewModel.state.statusMessage)
        assertEquals(DraftStatus.Draft, repository.get(draftId)?.status)

        viewModel.editPhotoWithAi()
        assertEquals("Cannot edit photo while status is draft", viewModel.state.statusMessage)
        assertEquals(DraftStatus.Draft, repository.get(draftId)?.status)
    }

    @Test
    fun updatesStoredDraftWhenEditingPhotoWithExecutor() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        val stored = repository.get(draftId)
        assertEquals(DraftStatus.PhotoEdited, stored?.status)
        assertEquals("file://executor-edited.jpg", viewModel.state.editedPhotoUri)
        assertEquals(1, viewModel.state.promptHistory.size)
        assertTrue(viewModel.state.actions.canViewPromptHistory)
        val request = stored?.photoEditRequests?.single()
        assertEquals(TargetPlatform.InstagramFeedSquare, request?.targetPlatform)
        assertEquals(RealismLevel.Photoreal, request?.realismLevel)
        assertEquals(QualityTier.Standard, request?.qualityTier)
        assertEquals(null, request?.subjectDescription)
        assertEquals(null, request?.userRefinement)
        assertEquals(null, request?.maskRegion)
        assertEquals("Edited photo created", viewModel.state.statusMessage)
    }

    @Test
    fun editPhotoWithAiAutoSelectsEditedResultOverExplicitOriginalSelection() {
        val existingEditedId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(selectedMediaAssetId = existingEditedId)
        val repository = FakePostDraftRepository(draft)
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri,
            "Active photo starts as previously selected edited result")
        assertEquals("file://photo-edited.jpg", viewModel.state.editedPhotoUri)
        assertFalse(viewModel.state.actions.canSelectEditedPhoto,
            "Edited is already active, should not be selectable")
        assertTrue(viewModel.state.actions.canSelectOriginalPhoto,
            "Original should be selectable when edited is active")

        viewModel.selectOriginalPhoto()

        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri,
            "Active photo reverts to original after explicit selection")
        assertEquals("Using original photo", viewModel.state.statusMessage)
        assertFalse(viewModel.state.actions.canSelectOriginalPhoto,
            "Original already active, should not be selectable")
        assertTrue(viewModel.state.actions.canSelectEditedPhoto,
            "Edited should be selectable when original is active")
        assertEquals(null, repository.get(draftId)?.selectedMediaAssetId,
            "Repository must persist cleared selection")

        viewModel.editPhotoWithAi()

        assertEquals("file://executor-edited.jpg", viewModel.state.activePhotoUri,
            "Active photo must auto-select the new edited result despite explicit original choice")
        assertEquals("file://executor-edited.jpg", viewModel.state.editedPhotoUri)
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals("Edited photo created", viewModel.state.statusMessage)
        assertTrue(viewModel.state.actions.canSelectOriginalPhoto,
            "Original should be selectable after auto-selecting new edited result")
        assertFalse(viewModel.state.actions.canSelectEditedPhoto,
            "New edited result already active, should not be re-selectable")
        val stored = repository.get(draftId)
        assertEquals(MediaAssetId("media-edited-executor"), stored?.selectedMediaAssetId,
            "Repository must persist auto-selected newest edited asset")
    }

    @Test
    fun editPhotoWithAiAutoSelectsEditedResultWhenNoPriorSelectionExists() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.activePhotoUri)
        assertNull(viewModel.state.editedPhotoUri)

        viewModel.editPhotoWithAi()

        assertEquals("file://executor-edited.jpg", viewModel.state.activePhotoUri,
            "Active photo must auto-select the new edited result on first edit")
        assertEquals("file://executor-edited.jpg", viewModel.state.editedPhotoUri)
        assertTrue(viewModel.state.actions.canSelectOriginalPhoto)
        assertFalse(viewModel.state.actions.canSelectEditedPhoto)
    }

    @Test
    fun reEditAutoSelectsNewestResultOverPreviouslySelectedEditedAsset() {
        val oldEditedId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(selectedMediaAssetId = oldEditedId)
        val repository = FakePostDraftRepository(draft)
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri,
            "Active photo starts as previously edited when it was selected")

        viewModel.editPhotoWithAi()

        assertEquals("file://executor-edited.jpg", viewModel.state.activePhotoUri,
            "Active photo must auto-select the newest edited result")
        assertNotEquals(oldEditedId, repository.get(draftId)?.selectedMediaAssetId,
            "Old edited asset must be replaced by new auto-selection")
        assertEquals(MediaAssetId("media-edited-executor"), repository.get(draftId)?.selectedMediaAssetId,
            "Repository must persist the new edited asset as selected")
    }

    @Test
    fun usesPreferredTargetPlatformAndVisionContextWhenEditingPhotoWithAi() {
        val draft = sampleDraft().copy(
            targetPlatforms = setOf(TargetPlatform.BlueskyPost),
            visionDescriptions = listOf(VisionDescription(
                id = VisionDescriptionId("vision-description-1"),
                draftId = draftId,
                mediaAssetId = mediaAssetId,
                description = "A coffee cup on a wooden table.",
                modelName = "fake",
                createdAtEpochMillis = 1_700_000_010_000L,
            )),
        )
        val repository = FakePostDraftRepository(draft)
        val resultDraft = draft.copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-1"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.ImproveLighting,
                    realismLevel = RealismLevel.Photoreal,
                    qualityTier = QualityTier.Standard,
                    prompt = "assembled prompt",
                    userRefinement = null,
                    subjectDescription = "A coffee cup on a wooden table.",
                    targetPlatform = TargetPlatform.BlueskyPost,
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
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
                    summary = "Edit result.",
                    modelName = "executor-model",
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
        )
        val executor = RecordingPhotoEditExecutor(
            resultDraft = resultDraft,
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        val request = executor.capturedIntent
        assertEquals(EditIntent.ImproveLighting, request)
        assertEquals(TargetPlatform.BlueskyPost, executor.capturedTargetPlatform)
        assertTrue(executor.capturedReuseVisionDescription ?: false)
    }

    @Test
    fun usesConfiguredRealismAndQualityDefaultsWhenEditingPhotoWithAi() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
            defaultRealismLevel = RealismLevel.Polished,
            defaultQualityTier = QualityTier.High,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(RealismLevel.Polished, executor.capturedRealismLevel)
        assertEquals(QualityTier.High, executor.capturedQualityTier)
        assertEquals(TargetPlatform.InstagramFeedSquare, executor.capturedTargetPlatform)
    }

    @Test
    fun rejectsTerminalDraftMutationsWithoutThrowing() {
        listOf(DraftStatus.Archived, DraftStatus.Shared).forEach { terminalStatus ->
            val executor = RecordingPhotoEditExecutor(
                result = PhotoEditExecutionResult.Failure(
                    PhotoEditExecutionError.InvalidDraftStatus(terminalStatus),
                ),
            )
            val repository = FakePostDraftRepository(sampleDraftWithGeneratedText().copy(status = terminalStatus))
            val viewModel = ManualPostDraftWorkspaceViewModel(
                draftId = draftId,
                postDraftRepository = repository,
                photoEditExecutor = executor,
            )
            viewModel.load()

            viewModel.generatePostText()
            assertEquals("Cannot generate text while status is ${terminalStatus.wireValue}", viewModel.state.statusMessage)
            assertEquals(terminalStatus, repository.get(draftId)?.status)

            viewModel.editPhotoWithAi()
            assertEquals("Cannot edit photo while status is ${terminalStatus.wireValue}", viewModel.state.statusMessage)
            assertEquals(terminalStatus, repository.get(draftId)?.status)
        }
    }

    @Test
    fun rejectsTerminalDraftPhotoSelectionsWithoutThrowing() {
        listOf(DraftStatus.Archived, DraftStatus.Shared).forEach { terminalStatus ->
            val repository = FakePostDraftRepository(sampleDraftWithEditedMedia().copy(status = terminalStatus))
            val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
            viewModel.load()

            viewModel.selectEditedPhoto()
            assertEquals(
                "Cannot select edited photo while status is ${terminalStatus.wireValue}",
                viewModel.state.statusMessage,
            )
            assertNull(repository.get(draftId)?.selectedMediaAssetId)

            viewModel.selectOriginalPhoto()
            assertEquals(
                "Cannot select original photo while status is ${terminalStatus.wireValue}",
                viewModel.state.statusMessage,
            )
            assertNull(repository.get(draftId)?.selectedMediaAssetId)
        }
    }

    @Test
    fun selectEditedPhotoOnArchivedDraftPreservesDraftIntegrity() {
        val originalUpdatedAt = Instant.fromEpochMilliseconds(1_700_000_100_000L)
        val selectedAssetId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(
            status = DraftStatus.Archived,
            selectedMediaAssetId = selectedAssetId,
            updatedAt = originalUpdatedAt,
        )
        val repository = FakePostDraftRepository(draft)
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        val activeUriBefore = viewModel.state.activePhotoUri

        viewModel.selectEditedPhoto()

        assertEquals(
            "Cannot select edited photo while status is archived",
            viewModel.state.statusMessage,
        )
        assertEquals(activeUriBefore, viewModel.state.activePhotoUri)
        assertEquals(selectedAssetId, repository.get(draftId)?.selectedMediaAssetId)
        assertEquals(originalUpdatedAt, repository.get(draftId)?.updatedAt)
        assertFalse(viewModel.state.actions.canSelectEditedPhoto)
        assertFalse(viewModel.state.actions.canSelectOriginalPhoto)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun selectOriginalPhotoOnArchivedDraftPreservesDraftIntegrity() {
        val originalUpdatedAt = Instant.fromEpochMilliseconds(1_700_000_100_000L)
        val selectedAssetId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(
            status = DraftStatus.Archived,
            selectedMediaAssetId = selectedAssetId,
            updatedAt = originalUpdatedAt,
        )
        val repository = FakePostDraftRepository(draft)
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        val activeUriBefore = viewModel.state.activePhotoUri

        viewModel.selectOriginalPhoto()

        assertEquals(
            "Cannot select original photo while status is archived",
            viewModel.state.statusMessage,
        )
        assertEquals(activeUriBefore, viewModel.state.activePhotoUri)
        assertEquals(selectedAssetId, repository.get(draftId)?.selectedMediaAssetId)
        assertEquals(originalUpdatedAt, repository.get(draftId)?.updatedAt)
        assertFalse(viewModel.state.actions.canSelectEditedPhoto)
        assertFalse(viewModel.state.actions.canSelectOriginalPhoto)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun selectEditedPhotoOnSharedDraftPreservesDraftIntegrity() {
        val originalUpdatedAt = Instant.fromEpochMilliseconds(1_700_000_100_000L)
        val selectedAssetId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(
            status = DraftStatus.Shared,
            selectedMediaAssetId = selectedAssetId,
            updatedAt = originalUpdatedAt,
        )
        val repository = FakePostDraftRepository(draft)
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        val activeUriBefore = viewModel.state.activePhotoUri

        viewModel.selectEditedPhoto()

        assertEquals(
            "Cannot select edited photo while status is shared",
            viewModel.state.statusMessage,
        )
        assertEquals(activeUriBefore, viewModel.state.activePhotoUri)
        assertEquals(selectedAssetId, repository.get(draftId)?.selectedMediaAssetId)
        assertEquals(originalUpdatedAt, repository.get(draftId)?.updatedAt)
        assertFalse(viewModel.state.actions.canSelectEditedPhoto)
        assertFalse(viewModel.state.actions.canSelectOriginalPhoto)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun selectOriginalPhotoOnSharedDraftPreservesDraftIntegrity() {
        val originalUpdatedAt = Instant.fromEpochMilliseconds(1_700_000_100_000L)
        val selectedAssetId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(
            status = DraftStatus.Shared,
            selectedMediaAssetId = selectedAssetId,
            updatedAt = originalUpdatedAt,
        )
        val repository = FakePostDraftRepository(draft)
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        val activeUriBefore = viewModel.state.activePhotoUri

        viewModel.selectOriginalPhoto()

        assertEquals(
            "Cannot select original photo while status is shared",
            viewModel.state.statusMessage,
        )
        assertEquals(activeUriBefore, viewModel.state.activePhotoUri)
        assertEquals(selectedAssetId, repository.get(draftId)?.selectedMediaAssetId)
        assertEquals(originalUpdatedAt, repository.get(draftId)?.updatedAt)
        assertFalse(viewModel.state.actions.canSelectEditedPhoto)
        assertFalse(viewModel.state.actions.canSelectOriginalPhoto)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun handlesDraftDisappearingBeforeActions() {
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.DraftNotFound,
            ),
        )
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()
        repository.delete(draftId)

        viewModel.generatePostText()
        assertEquals("Draft not found", viewModel.state.statusMessage)

        viewModel.editPhotoWithAi()
        assertEquals("Draft not found", viewModel.state.statusMessage)
    }

    @Test
    fun persistsPendingExportAndMovesDraftReadyToShare() {
        val repository = FakePostDraftRepository(sampleDraftWithGeneratedText())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_600_000L),
        )
        viewModel.load()

        viewModel.markShareOrExportStarted()

        val stored = repository.get(draftId)
        assertEquals(DraftStatus.ReadyToShare, stored?.status)
        assertEquals(1, stored?.exportRecords?.size)
        assertEquals(ExportStatus.Pending, stored?.exportRecords?.single()?.status)
        assertEquals(TargetPlatform.InstagramFeedSquare, stored?.exportRecords?.single()?.targetPlatform)
        assertEquals("Share/export ready", viewModel.state.statusMessage)
    }

    @Test
    fun updatesStatusMessagesForCopyActions() {
        val repository = FakePostDraftRepository(sampleDraftWithGeneratedText())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.markCaptionCopied()
        assertEquals("Caption copied", viewModel.state.statusMessage)

        viewModel.markAltTextCopied()
        assertEquals("Alt text copied", viewModel.state.statusMessage)
    }

    @Test
    fun togglesPromptHistoryVisibility() {
        val repository = FakePostDraftRepository(sampleDraftWithGeneratedText())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        assertFalse(viewModel.state.isPromptHistoryVisible)
        viewModel.togglePromptHistory()
        assertTrue(viewModel.state.isPromptHistoryVisible)
        viewModel.togglePromptHistory()
        assertFalse(viewModel.state.isPromptHistoryVisible)
    }

    @Test
    fun usesDeterministicTargetPlatformWhenMultipleTargetsExist() {
        val repository = FakePostDraftRepository(
            sampleDraftWithGeneratedText().copy(
                targetPlatforms = setOf(TargetPlatform.InstagramFeedSquare, TargetPlatform.BlueskyPost),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertEquals(TargetPlatform.BlueskyPost, viewModel.state.targetPlatform)
    }

    @Test
    fun doesNotFallbackToNonPhotoForOriginalPhoto() {
        val repository = FakePostDraftRepository(
            sampleDraft().copy(
                mediaItems = listOf(
                    PostMediaItem(
                        mediaAsset = sampleMediaAsset().copy(type = MediaType.EditedPhoto),
                        order = 0,
                    ),
                ),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertNull(viewModel.state.originalPhotoUri)
    }

    @Test
    fun disablesMutationAndShareActionsForArchivedDraft() {
        val repository = FakePostDraftRepository(sampleDraftWithGeneratedText().copy(status = DraftStatus.Archived))
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertFalse(viewModel.state.actions.canGeneratePostText)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
        assertFalse(viewModel.state.actions.canAnalyzeVision)
        assertTrue(viewModel.state.actions.canCopyCaption)
        assertTrue(viewModel.state.actions.canCopyAltText)
        assertFalse(viewModel.state.actions.canShareOrExport)
    }

    @Test
    fun reportsPipelineNotAvailableWhenNoPhotoEditExecutorConfigured() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

        viewModel.load()

        assertFalse(viewModel.state.actions.canEditPhotoWithAi)

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Softer contrast")
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.BlueskyPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.High)
        viewModel.togglePromptHistory()

        viewModel.editPhotoWithAi()

        assertEquals("Photo edit execution pipeline not available", viewModel.state.statusMessage)
        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertEquals(DraftStatus.PhotoAdded, viewModel.state.draftStatus)
        assertNull(viewModel.state.generatedCaption)
        assertNull(viewModel.state.generatedAltText)
        assertEquals(EditIntent.RemoveObject, viewModel.state.photoEditForm.selectedIntent)
        assertEquals("Softer contrast", viewModel.state.photoEditForm.userRefinementText)
        assertEquals(TargetPlatform.BlueskyPost, viewModel.state.photoEditForm.selectedTargetPlatform)
        assertEquals(QualityTier.High, viewModel.state.photoEditForm.selectedQualityTier)
        assertTrue(viewModel.state.isPromptHistoryVisible)
    }

    @Test
    fun preservesWorkspaceStateWhenPostTextGenerationFailsWithImageLoadFailure() {
        val generated = sampleDraftWithGeneratedText()
        val repository = FakePostDraftRepository(generated)
        val generator = FailurePostTextGenerator(PostTextGenerationError.ImageLoadFailure("Corrupted image file"))
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            postTextGenerator = generator,
            defaultTargetPlatform = TargetPlatform.InstagramFeedSquare,
            photoEditExecutor = RecordingPhotoEditExecutor(),
        )

        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Softer contrast")
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.BlueskyPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.High)
        viewModel.togglePromptHistory()

        viewModel.generatePostText()

        assertEquals("Corrupted image file", viewModel.state.statusMessage)
        assertEquals(generated.caption?.text, viewModel.state.generatedCaption, "Caption preserved from draft state")
        assertEquals(generated.status, viewModel.state.draftStatus, "Draft status preserved")
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri, "Original photo URI preserved")
        assertTrue(viewModel.state.actions.canGeneratePostText, "Can retry generation")
        assertTrue(viewModel.state.actions.canEditPhotoWithAi, "Can still edit photo")
        assertTrue(viewModel.state.isPromptHistoryVisible, "Prompt history visibility preserved")
        assertEquals(EditIntent.RemoveObject, viewModel.state.photoEditForm.selectedIntent, "Edit intent preserved")
        assertEquals("Softer contrast", viewModel.state.photoEditForm.userRefinementText, "Refinement text preserved")
        assertEquals(RealismLevel.Photoreal, viewModel.state.photoEditForm.selectedRealismLevel, "Realism level preserved")
        assertEquals(TargetPlatform.BlueskyPost, viewModel.state.photoEditForm.selectedTargetPlatform, "Target platform preserved")
        assertEquals(QualityTier.High, viewModel.state.photoEditForm.selectedQualityTier, "Quality tier preserved")
        assertEquals(PhotoEditFormOperationState.Idle, viewModel.state.photoEditForm.operationState, "Operation state idle")
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
            visionDescriptions = emptyList(),
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
                    shortCaption = null,
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
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-1"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.ImproveLighting,
                    realismLevel = RealismLevel.Photoreal,
                    qualityTier = QualityTier.Standard,
                    prompt = "Brighten the image",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.InstagramFeedSquare,
                    createdAtEpochMillis = 1_700_000_025_000L,
                ),
            ),
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

    private class FakePostDraftRepository(
        initialDraft: PostDraft? = null,
        private val simulatedUpdateSelectionResult: UpdateSelectionResult? = null,
    ) : PostDraftRepository {
        private val drafts: MutableMap<PostDraftId, PostDraft> =
            initialDraft?.let { mutableMapOf(it.id to it) } ?: mutableMapOf()

        override fun save(postDraft: PostDraft) {
            drafts[postDraft.id] = postDraft
        }

        override fun get(id: PostDraftId): PostDraft? = drafts[id]

        fun delete(id: PostDraftId) {
            drafts.remove(id)
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
            if (simulatedUpdateSelectionResult != null) return simulatedUpdateSelectionResult
            val current = drafts[id] ?: return UpdateSelectionResult.DraftNotFound
            drafts[id] = current.copy(selectedMediaAssetId = mediaAssetId, updatedAt = updatedAt)
            return UpdateSelectionResult.Success
        }
    }

    private class FixedClock(private val now: Long) : EpochClock {
        override fun nowMillis(): Long = now
    }

    private class IncrementingClock(private var now: Long) : EpochClock {
        override fun nowMillis(): Long = now++
    }

    @Test
    fun exposesPhotoEditFormDefaultsAfterLoad() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        val form = viewModel.state.photoEditForm
        assertEquals(EditIntent.ImproveLighting, form.selectedIntent)
        assertEquals("", form.userRefinementText)
        assertEquals(RealismLevel.Photoreal, form.selectedRealismLevel)
        assertEquals(TargetPlatform.InstagramFeedSquare, form.selectedTargetPlatform)
        assertEquals(QualityTier.Standard, form.selectedQualityTier)
        assertEquals(QualityTier.Standard.modelMappingNote, form.qualityTierModelNotes)
        assertEquals(QualityTier.Standard.costNote, form.qualityTierCostNotes)
        assertEquals(null, form.latestRequestId)
        assertEquals(null, form.latestResultId)
        assertEquals(null, form.latestModelName)
        assertEquals(null, form.latestSummary)
        assertEquals(PhotoEditFormOperationState.Idle, form.operationState)
    }

    @Test
    fun loadsPhotoEditFormFromLatestRequest() {
        val request = PhotoEditRequest(
            id = PhotoEditRequestId("photo-edit-request-1"),
            draftId = draftId,
            sourceMediaAssetId = mediaAssetId,
            intent = EditIntent.RemoveObject,
            realismLevel = RealismLevel.Polished,
            qualityTier = QualityTier.High,
            prompt = "Remove the coffee cup",
            userRefinement = "Be careful with shadows",
            subjectDescription = null,
            targetPlatform = TargetPlatform.BlueskyPost,
            createdAtEpochMillis = 1_700_000_025_000L,
        )
        val result = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-1"),
            requestId = request.id,
            draftId = draftId,
            editedMediaAsset = sampleMediaAsset().copy(
                id = MediaAssetId("media-edited-1"),
                type = MediaType.EditedPhoto,
                uri = "file://photo-edited.jpg",
            ),
            summary = "Removed the coffee cup.",
            modelName = "fake-test-model",
            createdAtEpochMillis = 1_700_000_030_000L,
        )
        val repository = FakePostDraftRepository(
            sampleDraft().copy(
                status = DraftStatus.PhotoEdited,
                photoEditRequests = listOf(request),
                photoEditResults = listOf(result),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        val form = viewModel.state.photoEditForm
        assertEquals(EditIntent.RemoveObject, form.selectedIntent)
        assertEquals("Be careful with shadows", form.userRefinementText)
        assertEquals(RealismLevel.Polished, form.selectedRealismLevel)
        assertEquals(TargetPlatform.BlueskyPost, form.selectedTargetPlatform)
        assertEquals(QualityTier.High, form.selectedQualityTier)
        assertEquals(QualityTier.High.modelMappingNote, form.qualityTierModelNotes)
        assertEquals(QualityTier.High.costNote, form.qualityTierCostNotes)
        assertEquals(request.id, form.latestRequestId)
        assertEquals(result.id, form.latestResultId)
        assertEquals("fake-test-model", form.latestModelName)
        assertEquals("Removed the coffee cup.", form.latestSummary)
        assertEquals(PhotoEditFormOperationState.Idle, form.operationState)
    }

    @Test
    fun updatesPhotoEditFormIntent() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.AddLogoOverlay)

        assertEquals(EditIntent.AddLogoOverlay, viewModel.state.photoEditForm.selectedIntent)
    }

    @Test
    fun updatesPhotoEditFormRefinement() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.updatePhotoEditRefinement("Make it warmer")

        assertEquals("Make it warmer", viewModel.state.photoEditForm.userRefinementText)
    }

    @Test
    fun updatesPhotoEditFormRealism() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.updatePhotoEditRealism(RealismLevel.Stylized)

        assertEquals(RealismLevel.Stylized, viewModel.state.photoEditForm.selectedRealismLevel)
    }

    @Test
    fun updatesPhotoEditFormTargetPlatform() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.FacebookPost)

        assertEquals(TargetPlatform.FacebookPost, viewModel.state.photoEditForm.selectedTargetPlatform)
    }

    @Test
    fun updatesPhotoEditFormQualityTierWithNotes() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.updatePhotoEditQualityTier(QualityTier.High)

        val form = viewModel.state.photoEditForm
        assertEquals(QualityTier.High, form.selectedQualityTier)
        assertEquals(QualityTier.High.modelMappingNote, form.qualityTierModelNotes)
        assertEquals(QualityTier.High.costNote, form.qualityTierCostNotes)
    }

    @Test
    fun usesPhotoEditFormValuesWhenEditingPhoto() {
        val repository = FakePostDraftRepository(
            sampleDraft().copy(
                targetPlatforms = setOf(TargetPlatform.BlueskyPost),
            ),
        )
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Remove the cup")
        viewModel.updatePhotoEditRealism(RealismLevel.Polished)
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.FacebookPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.High)
        viewModel.editPhotoWithAi()

        assertEquals(EditIntent.RemoveObject, executor.capturedIntent)
        assertEquals(RealismLevel.Polished, executor.capturedRealismLevel)
        assertEquals(QualityTier.High, executor.capturedQualityTier)
        assertEquals(TargetPlatform.FacebookPost, executor.capturedTargetPlatform)
    }

    @Test
    fun usesPhotoEditFormDefaultsWhenRefinementIsBlank() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertNull(executor.capturedUserRefinement)
    }

    @Test
    fun preservesRawRefinementInMemoryButTrimsInStoredRequest() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.updatePhotoEditRefinement("  Remove the cup  ")

        assertEquals("  Remove the cup  ", viewModel.state.photoEditForm.userRefinementText)

        viewModel.editPhotoWithAi()

        assertEquals("Remove the cup", executor.capturedUserRefinement)
    }

    @Test
    fun preservesErrorOperationStateWhenExecutorThrows() {
        val repository = FakePostDraftRepository(sampleDraft())
        val throwingExecutor = object : PhotoEditExecutor {
            override fun execute(
                draftId: PostDraftId,
                intent: EditIntent,
                realismLevel: RealismLevel,
                qualityTier: QualityTier,
                targetPlatform: TargetPlatform,
                userRefinement: String?,
                reuseVisionDescription: Boolean,
            ): PhotoEditExecutionResult {
                throw RuntimeException("Provider failure")
            }
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = throwingExecutor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Photo edit failed: Provider failure", viewModel.state.statusMessage)
    }

    @Test
    fun showsFallbackMessageWhenExecutorThrowsNullMessageException() {
        val repository = FakePostDraftRepository(sampleDraft())
        val throwingExecutor = object : PhotoEditExecutor {
            override fun execute(
                draftId: PostDraftId,
                intent: EditIntent,
                realismLevel: RealismLevel,
                qualityTier: QualityTier,
                targetPlatform: TargetPlatform,
                userRefinement: String?,
                reuseVisionDescription: Boolean,
            ): PhotoEditExecutionResult {
                throw RuntimeException()
            }
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = throwingExecutor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Photo edit failed: Unknown error", viewModel.state.statusMessage)
    }

    @Test
    fun preservesPhotoEditFormValuesWhenExecutorThrows() {
        val repository = FakePostDraftRepository(sampleDraft())
        val throwingExecutor = object : PhotoEditExecutor {
            override fun execute(
                draftId: PostDraftId,
                intent: EditIntent,
                realismLevel: RealismLevel,
                qualityTier: QualityTier,
                targetPlatform: TargetPlatform,
                userRefinement: String?,
                reuseVisionDescription: Boolean,
            ): PhotoEditExecutionResult {
                throw RuntimeException("Provider failure")
            }
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = throwingExecutor,
        )
        viewModel.load()
        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Keep the mug handle intact")
        viewModel.updatePhotoEditRealism(RealismLevel.Polished)
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.FacebookPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.High)

        viewModel.editPhotoWithAi()

        val form = viewModel.state.photoEditForm
        assertEquals(EditIntent.RemoveObject, form.selectedIntent)
        assertEquals("Keep the mug handle intact", form.userRefinementText)
        assertEquals(RealismLevel.Polished, form.selectedRealismLevel)
        assertEquals(TargetPlatform.FacebookPost, form.selectedTargetPlatform)
        assertEquals(QualityTier.High, form.selectedQualityTier)
        assertEquals(PhotoEditFormOperationState.Error, form.operationState)
    }

    @Test
    fun exposesLoadingStateWhileEditingPhoto() {
        val repository = FakePostDraftRepository(sampleDraft())
        val capturedStates = mutableListOf<PhotoEditFormOperationState>()
        val viewModelRef = arrayOfNulls<ManualPostDraftWorkspaceViewModel>(1)
        val capturingExecutor = object : PhotoEditExecutor {
            override fun execute(
                draftId: PostDraftId,
                intent: EditIntent,
                realismLevel: RealismLevel,
                qualityTier: QualityTier,
                targetPlatform: TargetPlatform,
                userRefinement: String?,
                reuseVisionDescription: Boolean,
            ): PhotoEditExecutionResult {
                capturedStates.add(viewModelRef[0]!!.state.photoEditForm.operationState)
                val request = PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-executor"),
                    draftId = draftId,
                    sourceMediaAssetId = MediaAssetId("media-1"),
                    intent = intent,
                    realismLevel = realismLevel,
                    qualityTier = qualityTier,
                    prompt = "assembled prompt",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = targetPlatform,
                    createdAtEpochMillis = 1_700_000_200_000L,
                )
                val result = PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-executor"),
                    requestId = request.id,
                    draftId = draftId,
                    editedMediaAsset = MediaAsset(
                        id = MediaAssetId("media-edited-executor"),
                        type = MediaType.EditedPhoto,
                        uri = "file://executor-edited.jpg",
                        mimeType = "image/jpeg",
                        widthPx = 1080,
                        heightPx = 1080,
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    summary = "Executor result.",
                    modelName = "executor-model",
                    createdAtEpochMillis = 1_700_000_200_000L,
                )
                val draft = PostDraft(
                    id = draftId,
                    format = PostFormat.SingleImage,
                    status = DraftStatus.PhotoEdited,
                    mediaItems = listOf(PostMediaItem(MediaAsset(
                        id = MediaAssetId("media-1"),
                        type = MediaType.Photo,
                        uri = "file://photo.jpg",
                        mimeType = "image/jpeg",
                        widthPx = 1080,
                        heightPx = 1080,
                        createdAtEpochMillis = 0L,
                    ), order = 0)),
                    caption = null,
                    targetPlatforms = emptySet(),
                    brandProfile = null,
                    visionDescriptions = emptyList(),
                    captionRequests = emptyList(),
                    captionResults = emptyList(),
                    altTextResults = emptyList(),
                    photoEditRequests = listOf(request),
                    photoEditResults = listOf(result),
                    promptHistory = emptyList(),
                    exportRecords = emptyList(),
                    createdAt = Instant.fromEpochMilliseconds(0L),
                    updatedAt = Instant.fromEpochMilliseconds(0L),
                )
                repository.save(draft)
                return PhotoEditExecutionResult.Success(
                    photoEditRequest = request,
                    photoEditResult = result,
                    assembledPrompt = "assembled prompt",
                    promptHistoryEntry = com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                        id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-executor"),
                        draftId = draftId,
                        operationType = AiOperationType.PhotoEdit,
                        prompt = "assembled prompt",
                        responseSummary = "Executor result.",
                        modelName = "executor-model",
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    updatedDraft = draft,
                )
            }
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = capturingExecutor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModelRef[0] = viewModel
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(1, capturedStates.size)
        assertEquals(PhotoEditFormOperationState.Loading, capturedStates.single())
        assertEquals(PhotoEditFormOperationState.Idle, viewModel.state.photoEditForm.operationState)
    }

    @Test
    fun ignoresPhotoEditFormUpdatesWhileEditingPhotoIsLoading() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModelRef = arrayOfNulls<ManualPostDraftWorkspaceViewModel>(1)
        val executor = object : PhotoEditExecutor {
            override fun execute(
                draftId: PostDraftId,
                intent: EditIntent,
                realismLevel: RealismLevel,
                qualityTier: QualityTier,
                targetPlatform: TargetPlatform,
                userRefinement: String?,
                reuseVisionDescription: Boolean,
            ): PhotoEditExecutionResult {
                val vm = viewModelRef[0]!!
                vm.updatePhotoEditIntent(EditIntent.AddLogoOverlay)
                vm.updatePhotoEditRefinement("This should be ignored")
                vm.updatePhotoEditRealism(RealismLevel.Stylized)
                vm.updatePhotoEditTargetPlatform(TargetPlatform.FacebookPost)
                vm.updatePhotoEditQualityTier(QualityTier.Standard)
                val request = PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-executor"),
                    draftId = draftId,
                    sourceMediaAssetId = MediaAssetId("media-1"),
                    intent = EditIntent.RemoveObject,
                    realismLevel = RealismLevel.Polished,
                    qualityTier = QualityTier.High,
                    prompt = "assembled prompt",
                    userRefinement = "Remove the cup",
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.BlueskyPost,
                    createdAtEpochMillis = 1_700_000_200_000L,
                )
                val result = PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-executor"),
                    requestId = request.id,
                    draftId = draftId,
                    editedMediaAsset = MediaAsset(
                        id = MediaAssetId("media-edited-executor"),
                        type = MediaType.EditedPhoto,
                        uri = "file://executor-edited.jpg",
                        mimeType = "image/jpeg",
                        widthPx = 1080,
                        heightPx = 1080,
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    summary = "Executor result.",
                    modelName = "executor-model",
                    createdAtEpochMillis = 1_700_000_200_000L,
                )
                val draft = PostDraft(
                    id = draftId,
                    format = PostFormat.SingleImage,
                    status = DraftStatus.PhotoEdited,
                    mediaItems = listOf(PostMediaItem(MediaAsset(
                        id = MediaAssetId("media-1"),
                        type = MediaType.Photo,
                        uri = "file://photo.jpg",
                        mimeType = "image/jpeg",
                        widthPx = 1080,
                        heightPx = 1080,
                        createdAtEpochMillis = 0L,
                    ), order = 0)),
                    caption = null,
                    targetPlatforms = emptySet(),
                    brandProfile = null,
                    visionDescriptions = emptyList(),
                    captionRequests = emptyList(),
                    captionResults = emptyList(),
                    altTextResults = emptyList(),
                    photoEditRequests = listOf(request),
                    photoEditResults = listOf(result),
                    promptHistory = emptyList(),
                    exportRecords = emptyList(),
                    createdAt = Instant.fromEpochMilliseconds(0L),
                    updatedAt = Instant.fromEpochMilliseconds(0L),
                )
                repository.save(draft)
                return PhotoEditExecutionResult.Success(
                    photoEditRequest = request,
                    photoEditResult = result,
                    assembledPrompt = "assembled prompt",
                    promptHistoryEntry = com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                        id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-executor"),
                        draftId = draftId,
                        operationType = AiOperationType.PhotoEdit,
                        prompt = "assembled prompt",
                        responseSummary = "Executor result.",
                        modelName = "executor-model",
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    updatedDraft = draft,
                )
            }
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModelRef[0] = viewModel
        viewModel.load()
        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Remove the cup")
        viewModel.updatePhotoEditRealism(RealismLevel.Polished)
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.BlueskyPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.High)

        viewModel.editPhotoWithAi()

        val request = repository.get(draftId)?.photoEditRequests?.single()
        val form = viewModel.state.photoEditForm
        assertEquals(EditIntent.RemoveObject, request?.intent)
        assertEquals("Remove the cup", request?.userRefinement)
        assertEquals(RealismLevel.Polished, request?.realismLevel)
        assertEquals(TargetPlatform.BlueskyPost, request?.targetPlatform)
        assertEquals(QualityTier.High, request?.qualityTier)
        assertEquals(EditIntent.RemoveObject, form.selectedIntent)
        assertEquals("Remove the cup", form.userRefinementText)
        assertEquals(RealismLevel.Polished, form.selectedRealismLevel)
        assertEquals(TargetPlatform.BlueskyPost, form.selectedTargetPlatform)
        assertEquals(QualityTier.High, form.selectedQualityTier)
    }

    @Test
    fun reRunningEditAppendsSecondRequestResultAndHistoryEntry() {
        val repository = FakePostDraftRepository(sampleDraft())
        var callCount = 0
        val executor = object : PhotoEditExecutor {
            override fun execute(
                draftId: PostDraftId, intent: EditIntent, realismLevel: RealismLevel,
                qualityTier: QualityTier, targetPlatform: TargetPlatform,
                userRefinement: String?, reuseVisionDescription: Boolean,
            ): PhotoEditExecutionResult {
                callCount++
                val idSuffix = "call$callCount"
                val request = PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-$idSuffix"),
                    draftId = draftId, sourceMediaAssetId = MediaAssetId("media-1"),
                    intent = intent, realismLevel = realismLevel, qualityTier = qualityTier,
                    prompt = "assembled prompt", userRefinement = null, subjectDescription = null,
                    targetPlatform = targetPlatform, createdAtEpochMillis = 1_700_000_200_000L + callCount,
                )
                val result = PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-$idSuffix"),
                    requestId = request.id, draftId = draftId,
                    editedMediaAsset = MediaAsset(
                        id = MediaAssetId("media-edited-$idSuffix"),
                        type = MediaType.EditedPhoto, uri = "file://edited-$idSuffix.jpg",
                        mimeType = "image/jpeg", widthPx = 1080, heightPx = 1080,
                        createdAtEpochMillis = 1_700_000_200_000L + callCount,
                    ),
                    summary = "Result $callCount.", modelName = "model",
                    createdAtEpochMillis = 1_700_000_200_000L + callCount,
                )
                val existingDraft = repository.get(draftId) ?: sampleDraft()
                val updatedDraft = existingDraft.copy(
                    status = DraftStatus.PhotoEdited,
                    photoEditRequests = existingDraft.photoEditRequests + request,
                    photoEditResults = existingDraft.photoEditResults + result,
                    promptHistory = existingDraft.promptHistory + com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                        id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-$idSuffix"),
                        draftId = draftId, operationType = AiOperationType.PhotoEdit,
                        prompt = "assembled", responseSummary = "Result $callCount.",
                        modelName = "model", createdAtEpochMillis = 1_700_000_200_000L + callCount,
                    ),
                )
                repository.save(updatedDraft)
                return PhotoEditExecutionResult.Success(request, result, "assembled",
                    updatedDraft.promptHistory.last(), updatedDraft)
            }
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId, postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = IncrementingClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()
        val storedAfterFirst = repository.get(draftId)
        assertEquals(1, storedAfterFirst?.photoEditRequests?.size)
        assertEquals(1, storedAfterFirst?.photoEditResults?.size)
        assertEquals(1, viewModel.state.promptHistory.size)

        viewModel.editPhotoWithAi()
        val storedAfterSecond = repository.get(draftId)
        assertEquals(2, storedAfterSecond?.photoEditRequests?.size)
        assertEquals(2, storedAfterSecond?.photoEditResults?.size)
        assertEquals(2, viewModel.state.promptHistory.size)
        assertNotEquals(
            storedAfterSecond?.photoEditRequests?.first()?.id,
            storedAfterSecond?.photoEditRequests?.last()?.id,
        )
        assertEquals(DraftStatus.PhotoEdited, storedAfterSecond?.status)
    }

    @Test
    fun rehydratesPhotoEditFormFromMostRecentRequestAfterSecondEdit() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = IncrementingClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Remove the mug")
        viewModel.updatePhotoEditRealism(RealismLevel.Polished)
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.BlueskyPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.High)
        viewModel.editPhotoWithAi()

        viewModel.updatePhotoEditIntent(EditIntent.BackgroundAdjustment)
        viewModel.updatePhotoEditRefinement("Warm the background")
        viewModel.updatePhotoEditRealism(RealismLevel.Stylized)
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.FacebookPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.Standard)
        viewModel.editPhotoWithAi()

        val stored = repository.get(draftId)
        val latestRequest = stored?.photoEditRequests?.last()
        val latestResult = stored?.photoEditResults?.last()
        val form = viewModel.state.photoEditForm
        assertEquals(EditIntent.BackgroundAdjustment, form.selectedIntent)
        assertEquals("Warm the background", form.userRefinementText)
        assertEquals(RealismLevel.Stylized, form.selectedRealismLevel)
        assertEquals(TargetPlatform.FacebookPost, form.selectedTargetPlatform)
        assertEquals(QualityTier.Standard, form.selectedQualityTier)
        assertEquals(latestRequest?.id, form.latestRequestId)
        assertEquals(latestResult?.id, form.latestResultId)
        assertEquals(latestResult?.summary, form.latestSummary)
    }

    @Test
    fun preservesOriginalMediaAssetDuringPhotoEdit() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertNull(viewModel.state.editedPhotoUri)

        viewModel.editPhotoWithAi()

        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri)
        assertNotNull(viewModel.state.editedPhotoUri)
        val stored = repository.get(draftId)
        val originalMedia = stored?.mediaItems?.firstOrNull { it.mediaAsset.type == MediaType.Photo }
        assertNotNull(originalMedia)
        assertEquals("file://photo.jpg", originalMedia.mediaAsset.uri)
    }

    @Test
    fun exposesLatestEditedImageStateAfterEdit() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            repository = repository,
            defaultMediaAsset = sampleMediaAsset(),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        val form = viewModel.state.photoEditForm
        assertNotNull(form.latestRequestId)
        assertNotNull(form.latestResultId)
        assertNotNull(form.latestModelName)
        assertNotNull(form.latestSummary)
        val editedUri = viewModel.state.editedPhotoUri
        assertNotNull(editedUri)
        assertEquals("file://executor-edited.jpg", editedUri)
    }

    @Test
    fun exposesErrorOperationStateWhenDraftMissingDuringEdit() {
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.DraftNotFound,
            ),
        )
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()
        repository.delete(draftId)

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Draft not found", viewModel.state.statusMessage)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun exposesErrorOperationStateWhenEditStatusIsInvalid() {
        val repository = FakePostDraftRepository(sampleDraft().copy(status = DraftStatus.Draft))
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.InvalidDraftStatus(DraftStatus.Draft),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Cannot edit photo while status is draft", viewModel.state.statusMessage)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun exposesErrorOperationStateWhenPipelineReturnsDraftNotFound() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.DraftNotFound,
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Draft not found", viewModel.state.statusMessage)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun exposesRecoverableErrorForSourceMediaNotFound() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.SourceMediaNotFound,
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("The source photo for this draft is no longer available", viewModel.state.statusMessage)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun usesConfiguredPhotoEditExecutorWhenEditingPhoto() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor()
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(TargetPlatform.InstagramFeedSquare, executor.capturedTargetPlatform)
        assertEquals(EditIntent.ImproveLighting, executor.capturedIntent)
        assertEquals(RealismLevel.Photoreal, executor.capturedRealismLevel)
        assertEquals(QualityTier.Standard, executor.capturedQualityTier)
    }

    @Test
    fun usesUpdatedDraftFromSuccessfulPipelineResult() {
        val editedDraft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-pipeline"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.ImproveLighting,
                    realismLevel = RealismLevel.Photoreal,
                    qualityTier = QualityTier.Standard,
                    prompt = "Pipeline prompt",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.InstagramFeedSquare,
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
            photoEditResults = listOf(
                PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-pipeline"),
                    requestId = PhotoEditRequestId("photo-edit-request-pipeline"),
                    draftId = draftId,
                    editedMediaAsset = sampleMediaAsset().copy(
                        id = MediaAssetId("media-edited-pipeline"),
                        type = MediaType.EditedPhoto,
                        uri = "file://pipeline-edited.jpg",
                    ),
                    summary = "Pipeline edit summary.",
                    modelName = "pipeline-model",
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
        )
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(resultDraft = editedDraft)
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals("file://pipeline-edited.jpg", viewModel.state.editedPhotoUri)
        assertEquals("Edited photo created", viewModel.state.statusMessage)
        assertEquals(PhotoEditFormOperationState.Idle, viewModel.state.photoEditForm.operationState)
        assertEquals("Pipeline edit summary.", viewModel.state.photoEditForm.latestSummary)
        assertEquals("pipeline-model", viewModel.state.photoEditForm.latestModelName)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun exposesErrorStateWhenPipelineReturnsProviderFailure() {
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.Provider(AiError.RateLimited()),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Unable to edit photo: The AI provider is rate limited. Try again later.", viewModel.state.statusMessage)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun exposesErrorStateWhenPipelineReturnsFailurePersisted() {
        val persistedDraft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-failed"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.ImproveLighting,
                    realismLevel = RealismLevel.Photoreal,
                    qualityTier = QualityTier.Standard,
                    prompt = "Failing request",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.InstagramFeedSquare,
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
        )
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.FailurePersisted(
                    photoEditRequest = persistedDraft.photoEditRequests.first(),
                    assembledPrompt = "assembled",
                    promptHistoryEntry = com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                        id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-failed"),
                        draftId = draftId,
                        operationType = AiOperationType.PhotoEdit,
                        prompt = "assembled",
                        responseSummary = "Provider error",
                        modelName = null,
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    updatedDraft = persistedDraft,
                    cause = PhotoEditExecutionError.Provider(AiError.RateLimited()),
                ),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Unable to edit photo: The AI provider is rate limited. Try again later.", viewModel.state.statusMessage)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun failurePersistedRebuildsDraftStateIncludingEditedUriAndRequestForm() {
        val persistedDraft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-failed"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.RemoveObject,
                    realismLevel = RealismLevel.Polished,
                    qualityTier = QualityTier.High,
                    prompt = "Remove object",
                    userRefinement = "Be careful",
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.BlueskyPost,
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
        )
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.FailurePersisted(
                    photoEditRequest = persistedDraft.photoEditRequests.first(),
                    assembledPrompt = "assembled",
                    promptHistoryEntry = com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                        id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-failed"),
                        draftId = draftId,
                        operationType = AiOperationType.PhotoEdit,
                        prompt = "assembled",
                        responseSummary = "Provider error",
                        modelName = null,
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    updatedDraft = persistedDraft,
                    cause = PhotoEditExecutionError.Provider(AiError.RateLimited()),
                ),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        val form = viewModel.state.photoEditForm
        assertEquals(EditIntent.RemoveObject, form.selectedIntent)
        assertEquals("Be careful", form.userRefinementText)
        assertEquals(RealismLevel.Polished, form.selectedRealismLevel)
        assertEquals(TargetPlatform.BlueskyPost, form.selectedTargetPlatform)
        assertEquals(QualityTier.High, form.selectedQualityTier)
        assertEquals(PhotoEditFormOperationState.Error, form.operationState)
    }

    @Test
    fun disablesEditFormDuringPipelineExecution() {
        val repository = FakePostDraftRepository(sampleDraft())
        val capturedStates = mutableListOf<PhotoEditFormOperationState>()
        val viewModelRef = arrayOfNulls<ManualPostDraftWorkspaceViewModel>(1)
        val executor = object : PhotoEditExecutor {
            override fun execute(
                draftId: PostDraftId,
                intent: EditIntent,
                realismLevel: RealismLevel,
                qualityTier: QualityTier,
                targetPlatform: TargetPlatform,
                userRefinement: String?,
                reuseVisionDescription: Boolean,
            ): PhotoEditExecutionResult {
                capturedStates.add(viewModelRef[0]!!.state.photoEditForm.operationState)
                return PhotoEditExecutionResult.Failure(PhotoEditExecutionError.DraftNotFound)
            }
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModelRef[0] = viewModel
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(1, capturedStates.size)
        assertEquals(PhotoEditFormOperationState.Loading, capturedStates.single())
    }

    @Test
    fun failurePersistedWithFailedToLoadSourceImageShowsFormError() {
        val persistedDraft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-load-fail"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.ImproveLighting,
                    realismLevel = RealismLevel.Photoreal,
                    qualityTier = QualityTier.Standard,
                    prompt = "Failing request",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.InstagramFeedSquare,
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
        )
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.FailurePersisted(
                    photoEditRequest = persistedDraft.photoEditRequests.first(),
                    assembledPrompt = "assembled",
                    promptHistoryEntry = com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                        id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-load-fail"),
                        draftId = draftId,
                        operationType = AiOperationType.PhotoEdit,
                        prompt = "assembled",
                        responseSummary = "Source image unreadable",
                        modelName = null,
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    updatedDraft = persistedDraft,
                    cause = PhotoEditExecutionError.FailedToLoadSourceImage("Source image unreadable"),
                ),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Photo edit failed: Unable to load the source photo", viewModel.state.statusMessage)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun failurePersistedWithFailedToSaveEditedImageShowsFormError() {
        val persistedDraft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-save-fail"),
                    draftId = draftId,
                    sourceMediaAssetId = mediaAssetId,
                    intent = EditIntent.BackgroundAdjustment,
                    realismLevel = RealismLevel.Polished,
                    qualityTier = QualityTier.High,
                    prompt = "Add a sunset background",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.BlueskyPost,
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
        )
        val repository = FakePostDraftRepository(sampleDraft())
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.FailurePersisted(
                    photoEditRequest = persistedDraft.photoEditRequests.first(),
                    assembledPrompt = "Add a sunset background",
                    promptHistoryEntry = com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                        id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-save-fail"),
                        draftId = draftId,
                        operationType = AiOperationType.PhotoEdit,
                        prompt = "Add a sunset background",
                        responseSummary = "Insufficient storage",
                        modelName = null,
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    updatedDraft = persistedDraft,
                    cause = PhotoEditExecutionError.FailedToSaveEditedImage("Insufficient storage"),
                ),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Photo edit failed: Unable to save the edited photo", viewModel.state.statusMessage)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun preservesSelectedMediaAssetIdAfterFailurePersistedEdit() {
        val preselectedId = MediaAssetId("media-edited-1")
        val draft = sampleDraftWithEditedMedia().copy(
            selectedMediaAssetId = preselectedId,
        )
        val persistedDraft = draft.copy(
            photoEditRequests = draft.photoEditRequests + listOf(
                PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-failed"),
                    draftId = draftId,
                    sourceMediaAssetId = preselectedId,
                    intent = EditIntent.ImproveLighting,
                    realismLevel = RealismLevel.Photoreal,
                    qualityTier = QualityTier.Standard,
                    prompt = "Failing request",
                    userRefinement = null,
                    subjectDescription = null,
                    targetPlatform = TargetPlatform.InstagramFeedSquare,
                    createdAtEpochMillis = 1_700_000_200_000L,
                ),
            ),
        )
        val repository = FakePostDraftRepository(draft)
        val executor = RecordingPhotoEditExecutor(
            result = PhotoEditExecutionResult.Failure(
                PhotoEditExecutionError.FailurePersisted(
                    photoEditRequest = persistedDraft.photoEditRequests.last(),
                    assembledPrompt = "Failing request",
                    promptHistoryEntry = com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                        id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-failed"),
                        draftId = draftId,
                        operationType = AiOperationType.PhotoEdit,
                        prompt = "Failing request",
                        responseSummary = "Provider error",
                        modelName = null,
                        createdAtEpochMillis = 1_700_000_200_000L,
                    ),
                    updatedDraft = persistedDraft,
                    cause = PhotoEditExecutionError.Provider(AiError.RateLimited()),
                ),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            photoEditExecutor = executor,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("file://photo-edited.jpg", viewModel.state.activePhotoUri, "Active photo must reflect the preselected asset's URI")
        assertEquals("file://photo.jpg", viewModel.state.originalPhotoUri, "Original photo URI must be preserved")
        assertTrue(viewModel.state.actions.canSelectOriginalPhoto, "Original should still be selectable when preselected edited is active")
        assertTrue(viewModel.state.actions.canEditPhotoWithAi, "Should still be able to re-edit after failure")
    }

    private class RecordingPhotoEditExecutor(
        private val resultDraft: PostDraft? = null,
        private val result: PhotoEditExecutionResult? = null,
        private val defaultDraft: PostDraft? = null,
        private val defaultMediaAsset: MediaAsset? = null,
        private val repository: PostDraftRepository? = null,
    ) : PhotoEditExecutor {
        var capturedIntent: EditIntent? = null
        var capturedRealismLevel: RealismLevel? = null
        var capturedQualityTier: QualityTier? = null
        var capturedTargetPlatform: TargetPlatform? = null
        var capturedUserRefinement: String? = null
        var capturedReuseVisionDescription: Boolean? = null
        private var executionSequence = 0L

        private fun defaultMediaAsset(): MediaAsset =
            defaultMediaAsset ?: MediaAsset(
                id = MediaAssetId("media-1"),
                type = MediaType.Photo,
                uri = "file://photo.jpg",
                mimeType = "image/jpeg",
                widthPx = 1080,
                heightPx = 1080,
                createdAtEpochMillis = 0L,
            )

        private fun defaultMediaAssetId(): MediaAssetId = defaultMediaAsset().id

        override fun execute(
            draftId: PostDraftId,
            intent: EditIntent,
            realismLevel: RealismLevel,
            qualityTier: QualityTier,
            targetPlatform: TargetPlatform,
            userRefinement: String?,
            reuseVisionDescription: Boolean,
        ): PhotoEditExecutionResult {
            capturedIntent = intent
            capturedRealismLevel = realismLevel
            capturedQualityTier = qualityTier
            capturedTargetPlatform = targetPlatform
            capturedUserRefinement = userRefinement
            capturedReuseVisionDescription = reuseVisionDescription

            if (result != null) {
                result.let { r ->
                    if (r is PhotoEditExecutionResult.Success) {
                        repository?.save(r.updatedDraft)
                    }
                }
                return result
            }

            // Strictly increasing timestamps so the most recent request/result wins
            // maxByOrNull-based projections even when executions happen within one millisecond.
            val now = EpochClock.Default.nowMillis() + executionSequence++
            val createdRequest = resultDraft?.photoEditRequests?.lastOrNull()
                ?: PhotoEditRequest(
                    id = PhotoEditRequestId("photo-edit-request-executor"),
                    draftId = draftId,
                    sourceMediaAssetId = defaultMediaAssetId(),
                    intent = intent,
                    realismLevel = realismLevel,
                    qualityTier = qualityTier,
                    prompt = "assembled prompt",
                    userRefinement = userRefinement?.trim()?.takeIf { it.isNotEmpty() },
                    subjectDescription = null,
                    targetPlatform = targetPlatform,
                    createdAtEpochMillis = now,
                )
            val createdResult = resultDraft?.photoEditResults?.lastOrNull()
                ?: PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-executor"),
                    requestId = createdRequest.id,
                    draftId = draftId,
                    editedMediaAsset = defaultMediaAsset().copy(
                        id = MediaAssetId("media-edited-executor"),
                        type = MediaType.EditedPhoto,
                        uri = "file://executor-edited.jpg",
                    ),
                    summary = "Executor result.",
                    modelName = "executor-model",
                    createdAtEpochMillis = now,
                )
            val createdEntry = com.digitumdei.shotquill.shared.domain.PromptHistoryEntry(
                id = com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId("prompt-executor"),
                draftId = draftId,
                operationType = AiOperationType.PhotoEdit,
                prompt = "assembled prompt",
                responseSummary = "Executor result.",
                modelName = "executor-model",
                createdAtEpochMillis = now,
            )
            val baseDraft = resultDraft ?: defaultDraft ?: repository?.get(draftId) ?: PostDraft(
                id = draftId,
                format = PostFormat.SingleImage,
                status = DraftStatus.PhotoAdded,
                mediaItems = listOf(PostMediaItem(mediaAsset = defaultMediaAsset(), order = 0)),
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
                createdAt = Instant.fromEpochMilliseconds(0L),
                updatedAt = Instant.fromEpochMilliseconds(0L),
            )
            val updatedDraft = baseDraft.copy(
                status = if (baseDraft.status.canTransitionTo(DraftStatus.PhotoEdited)) DraftStatus.PhotoEdited else baseDraft.status,
                selectedMediaAssetId = createdResult.editedMediaAsset.id,
                photoEditRequests = baseDraft.photoEditRequests + createdRequest,
                photoEditResults = baseDraft.photoEditResults + createdResult,
                promptHistory = baseDraft.promptHistory + createdEntry,
            )
            val successResult = PhotoEditExecutionResult.Success(
                photoEditRequest = createdRequest,
                photoEditResult = createdResult,
                assembledPrompt = "assembled prompt",
                promptHistoryEntry = createdEntry,
                updatedDraft = updatedDraft,
            )
            repository?.save(updatedDraft)
            return successResult
        }
    }

    private class RecordingPostTextGenerator(
        private val draft: PostDraft,
    ) : PostTextGenerator {
        val targetPlatforms = mutableListOf<TargetPlatform>()

        override fun generateText(
            draftId: PostDraftId,
            targetPlatform: TargetPlatform,
            reuseVisionDescription: Boolean,
        ): PostTextGenerationResult {
            targetPlatforms += targetPlatform
            return PostTextGenerationResult.Success(
                draft = draft,
                visionDescription = VisionDescription(
                    id = VisionDescriptionId("vision-description-generated"),
                    draftId = draftId,
                    mediaAssetId = MediaAssetId("media-1"),
                    description = "Generated vision.",
                    modelName = "pipeline",
                    createdAtEpochMillis = 1_700_000_100_000L,
                ),
                captionRequest = com.digitumdei.shotquill.shared.domain.CaptionRequest(
                    id = CaptionRequestId("caption-request-generated"),
                    draftId = draftId,
                    targetPlatform = targetPlatform,
                    prompt = "Pipeline prompt.",
                    tone = null,
                    brandProfileId = null,
                    createdAtEpochMillis = 1_700_000_100_000L,
                ),
                captionResult = draft.captionResults.first(),
                altTextResult = draft.altTextResults.first(),
                promptHistoryEntries = emptyList(),
            )
        }
    }

    private class FailurePostTextGenerator(
        private val error: PostTextGenerationError,
    ) : PostTextGenerator {
        override fun generateText(
            draftId: PostDraftId,
            targetPlatform: TargetPlatform,
            reuseVisionDescription: Boolean,
        ): PostTextGenerationResult {
            return PostTextGenerationResult.Failure(error)
        }
    }

    private class FakeAnalyzeVision(
        private val repository: PostDraftRepository,
        private val clock: EpochClock = EpochClock.Default,
    ) : AnalyzeVision {
        override fun analyzePrimaryPhoto(draftId: PostDraftId, reuseCached: Boolean): VisionDescriptionAnalysisResult {
            val draft = repository.get(draftId) ?: return VisionDescriptionAnalysisResult.Failure(
                VisionDescriptionAnalysisError.DraftNotFound,
            )
            val mediaAsset = try {
                draft.primaryMediaAsset()
            } catch (_: IllegalStateException) {
                return VisionDescriptionAnalysisResult.Failure(
                    VisionDescriptionAnalysisError.ImageLoadFailure("No primary media asset"),
                )
            }
            if (reuseCached) {
                draft.visionDescriptions.firstOrNull()?.takeIf { it.mediaAssetId == mediaAsset.id }?.let {
                    return VisionDescriptionAnalysisResult.Success(it, cacheHit = true)
                }
            }
            val now = clock.nowMillis()
            val description = "Photo shows ${mediaAsset.uri.substringAfterLast('/')} prepared for social content."
            val prompt = VisionDescriptionPromptFactory.buildPrompt(mediaAsset)
            val visionDescription = VisionDescription(
                id = VisionDescriptionId("vision-description-$now"),
                draftId = draftId,
                mediaAssetId = mediaAsset.id,
                description = description,
                modelName = "fake-manual-draft-ai",
                createdAtEpochMillis = now,
            )
            val promptHistoryEntry = PromptHistoryEntry(
                id = PromptHistoryEntryId("prompt-vision-description-$now"),
                draftId = draftId,
                operationType = AiOperationType.VisionDescription,
                prompt = prompt,
                responseSummary = description,
                modelName = "fake-manual-draft-ai",
                createdAtEpochMillis = now,
            )
            val updated = draft.copy(
                visionDescriptions = listOf(visionDescription),
                promptHistory = draft.promptHistory + promptHistoryEntry,
            )
            repository.save(updated)
            return VisionDescriptionAnalysisResult.Success(visionDescription, cacheHit = false)
        }
    }

    private class FakeManualWorkflowRepository(
        initialDraft: PostDraft? = null,
    ) : ManualWorkflowRepository {
        private val drafts: MutableMap<PostDraftId, PostDraft> =
            initialDraft?.let { mutableMapOf(it.id to it) } ?: mutableMapOf()
        private val storedVisionDescriptions = mutableListOf<VisionDescription>()
        private val storedPromptHistory = mutableListOf<PromptHistoryEntry>()

        init {
            initialDraft?.visionDescriptions?.let { storedVisionDescriptions.addAll(it) }
            initialDraft?.promptHistory?.let { storedPromptHistory += it }
        }

        override fun save(postDraft: PostDraft) { drafts[postDraft.id] = postDraft }
        override fun get(id: PostDraftId): PostDraft? = drafts[id]
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
            drafts[finalPostContent.draftId] = drafts[finalPostContent.draftId]!!.copy(finalPostContent = finalPostContent)
        }
        override fun getFinalPostContent(draftId: PostDraftId): FinalPostContent? =
            drafts[draftId]?.finalPostContent
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
}
