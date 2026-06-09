package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.ai.AiError
import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.ai.AiProvider
import com.digitumdei.shotquill.shared.ai.AiProviderResult
import com.digitumdei.shotquill.shared.ai.AltTextGenerationOutput
import com.digitumdei.shotquill.shared.ai.AltTextGenerationRequest
import com.digitumdei.shotquill.shared.ai.CaptionGenerationOutput
import com.digitumdei.shotquill.shared.ai.CaptionGenerationRequest
import com.digitumdei.shotquill.shared.ai.FakeAiProvider
import com.digitumdei.shotquill.shared.ai.PhotoEditGenerationRequest
import com.digitumdei.shotquill.shared.ai.PhotoEditOutput
import com.digitumdei.shotquill.shared.ai.VisionDescriptionOutput
import com.digitumdei.shotquill.shared.ai.VisionDescriptionRequest
import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PhotoEditPromptAssembler
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PhotoEditResultId
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
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhotoEditExecutionPipelineTest {

    private val draftId = PostDraftId("draft-edit-1")
    private val mediaAssetId = MediaAssetId("media-edit-1")
    private val requestId = PhotoEditRequestId("edit-req-1")
    private val resultId = PhotoEditResultId("edit-result-1")
    private val baseEpoch = 1_700_000_000_000L
    private val editedMediaAssetId = MediaAssetId("media-edited-existing")

    @Test
    fun `happy path with fake provider preserves original media and links edited image to draft`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        val expectedPrompt = expectedAssembledPrompt()
        assertEquals(expectedPrompt, success.assembledPrompt, "assembledPrompt must match the expected assembled prompt")
        assertEquals(expectedPrompt, success.photoEditRequest.prompt, "photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, success.promptHistoryEntry.prompt, "promptHistoryEntry.prompt must match the assembled prompt")
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(DraftStatus.PhotoEdited, stored.status)

        val originalAsset = repository.get(mediaAssetId)
        assertNotNull(originalAsset, "Original media asset must still exist")
        assertEquals(MediaType.Photo, originalAsset.type)

        val editedAsset = success.photoEditResult.editedMediaAsset
        assertEquals(MediaType.EditedPhoto, editedAsset.type)
        assertTrue(stored.photoEditResults.any { it.editedMediaAsset.id == editedAsset.id })
        assertEquals(1, stored.photoEditRequests.size)
        assertEquals(1, stored.photoEditResults.size)
        assertTrue(stored.promptHistory.any { it.operationType == AiOperationType.PhotoEdit && it.responseSummary != null })

        assertEquals(1, stored.mediaItems.size, "Original mediaItems must not be modified")
        val draftOriginalAsset = stored.mediaItems.first().mediaAsset
        assertEquals(mediaAssetId, draftOriginalAsset.id)
        val latestResult = stored.photoEditResults.maxByOrNull { it.createdAtEpochMillis }
        assertNotNull(latestResult, "Edited asset must be linked via photoEditResults")
        assertEquals(editedAsset.id, latestResult.editedMediaAsset.id)
    }

    @Test
    fun `failed edit stores error history via FailurePersisted`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val failingProvider = object : AiProvider {
            override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> =
                AiProviderResult.Success(VisionDescriptionOutput("Recorded vision.", "recording-model"))
            override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> =
                error("Not used")
            override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
                error("Not used")
            override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
                AiProviderResult.Failure(AiError.RateLimited())
        }
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = failingProvider,
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        val persisted = assertIs<PhotoEditExecutionError.FailurePersisted>(failure.error)
        val expectedPrompt = expectedAssembledPrompt()
        assertEquals(expectedPrompt, persisted.assembledPrompt, "FailurePersisted.assembledPrompt must match expected")
        assertEquals(expectedPrompt, persisted.photoEditRequest.prompt, "FailurePersisted.photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, persisted.promptHistoryEntry.prompt, "FailurePersisted.promptHistoryEntry.prompt must match the assembled prompt")
        assertIs<PhotoEditExecutionError.Provider>(persisted.cause)
        assertEquals(AiError.RateLimited(), (persisted.cause as PhotoEditExecutionError.Provider).error)
        assertEquals(draftId, persisted.photoEditRequest.draftId)
        assertNotNull(persisted.assembledPrompt)
        assertEquals(AiOperationType.PhotoEdit, persisted.promptHistoryEntry.operationType)
        assertNotNull(persisted.promptHistoryEntry.responseSummary)

        val stored = assertNotNull(repository.get(draftId))
        assertEquals(1, stored.photoEditRequests.size)
        assertEquals(0, stored.photoEditResults.size)
        assertEquals(1, stored.promptHistory.size)
        assertNotNull(stored.promptHistory.first().responseSummary)
        assertEquals(DraftStatus.PhotoAdded, stored.status)
        val persistedUpdatedAt = persisted.updatedDraft.updatedAt
        assertTrue(persistedUpdatedAt > Instant.fromEpochMilliseconds(baseEpoch), "FailurePersisted.updatedDraft.updatedAt must advance on failure")
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "stored draft updatedAt must advance on failure")
        assertEquals(stored.updatedAt, persistedUpdatedAt, "stored and FailurePersisted updatedAt must match")
    }

    @Test
    fun `retry appends another history entry after a failed edit`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val failingProvider = object : AiProvider {
            var callCount = 0
            override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> =
                AiProviderResult.Success(VisionDescriptionOutput("Recorded vision.", "recording-model"))
            override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> =
                error("Not used")
            override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
                error("Not used")
            override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> {
                callCount += 1
                if (callCount == 1) return AiProviderResult.Failure(AiError.RateLimited())
                return AiProviderResult.Success(
                    PhotoEditOutput(
                        imageBytes = "fake-edit-success".encodeToByteArray(),
                        mimeType = "image/jpeg",
                        summary = "Fake success edit",
                        modelName = "recording-model",
                    ),
                )
            }
        }
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = failingProvider,
            clock = clock,
        )

        val expectedPrompt = expectedAssembledPrompt()
        pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        clock.now = 1_700_000_200_000L
        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        assertEquals(expectedPrompt, success.assembledPrompt, "retry success.assembledPrompt must match expected")
        assertEquals(expectedPrompt, success.photoEditRequest.prompt, "retry success.photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, success.promptHistoryEntry.prompt, "retry success.promptHistoryEntry.prompt must match the assembled prompt")
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(2, stored.photoEditRequests.size)
        assertEquals(1, stored.photoEditResults.size)
        assertEquals(2, stored.promptHistory.size)
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "updatedAt must advance after retry")
        val historyFailure = stored.promptHistory.first()
        assertNotNull(historyFailure.responseSummary)
        assertEquals(expectedPrompt, historyFailure.prompt, "stored failure promptHistoryEntry.prompt must match the assembled prompt")
        val historySuccess = stored.promptHistory.last()
        assertNotNull(historySuccess.responseSummary)
        assertEquals(expectedPrompt, historySuccess.prompt, "stored success promptHistoryEntry.prompt must match the assembled prompt")
        assertEquals(DraftStatus.PhotoEdited, stored.status)
        assertEquals(1, stored.mediaItems.size, "Original mediaItems must not be modified after retry success")
        assertEquals(
            MediaType.Photo,
            stored.mediaItems.first().mediaAsset.type,
            "Original photo must remain the only media item",
        )
        val latestResult = stored.photoEditResults.maxByOrNull { it.createdAtEpochMillis }
        assertNotNull(latestResult, "Edited asset must be linked via photoEditResults on retry")
    }

    @Test
    fun `source image load failure returns FailurePersisted`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = PhotoEditExecutionPipeline(
            repository = repository,
            aiProvider = FakeAiProvider(),
            settingsRepository = settingsRepository,
            imageSource = PhotoEditImageSource { _ -> SourceImageResult.Failure("File not found") },
            mediaSaver = PhotoEditMediaSaver { _, _, _, _, _ -> SaveEditedImageResult.Success(sampleMediaAsset()) },
            visionImageSource = VisionImageSource { SourceImageResult.Success(sampleAiImageInput()) },
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        val persisted = assertIs<PhotoEditExecutionError.FailurePersisted>(failure.error)
        val expectedPrompt = expectedAssembledPrompt()
        assertEquals(expectedPrompt, persisted.assembledPrompt, "FailedToLoadSourceImage persisted.assembledPrompt must match expected")
        assertEquals(expectedPrompt, persisted.photoEditRequest.prompt, "FailedToLoadSourceImage persisted.photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, persisted.promptHistoryEntry.prompt, "FailedToLoadSourceImage persisted.promptHistoryEntry.prompt must match the assembled prompt")
        assertIs<PhotoEditExecutionError.FailedToLoadSourceImage>(persisted.cause)
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(1, stored.photoEditRequests.size)
        assertEquals(1, stored.promptHistory.size)
        assertTrue(persisted.updatedDraft.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "source image failure updatedDraft.updatedAt must advance")
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "source image failure stored updatedAt must advance")
    }

    @Test
    fun `vision image source load failure returns FailurePersisted`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = PhotoEditExecutionPipeline(
            repository = repository,
            aiProvider = FakeAiProvider(),
            settingsRepository = settingsRepository,
            imageSource = PhotoEditImageSource { _ -> SourceImageResult.Success(sampleAiImageInput()) },
            mediaSaver = PhotoEditMediaSaver { _, _, _, _, _ -> SaveEditedImageResult.Success(sampleMediaAsset()) },
            visionImageSource = VisionImageSource { SourceImageResult.Failure("Corrupt vision image file") },
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = false,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        val persisted = assertIs<PhotoEditExecutionError.FailurePersisted>(failure.error)
        assertIs<PhotoEditExecutionError.FailedToLoadSourceImage>(persisted.cause)
        assertEquals("Corrupt vision image file", (persisted.cause as PhotoEditExecutionError.FailedToLoadSourceImage).message)
        val expectedPrompt = expectedAssembledPrompt(subjectDescription = null)
        assertEquals(expectedPrompt, persisted.assembledPrompt, "vision load failure persisted.assembledPrompt must match expected")
        assertEquals(expectedPrompt, persisted.photoEditRequest.prompt, "vision load failure persisted.photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, persisted.promptHistoryEntry.prompt, "vision load failure persisted.promptHistoryEntry.prompt must match the assembled prompt")
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(1, stored.photoEditRequests.size, "Edit request must be persisted on vision load failure")
        assertEquals(1, stored.promptHistory.size, "Prompt history must be persisted on vision load failure")
        assertTrue(persisted.updatedDraft.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "vision load failure updatedDraft.updatedAt must advance")
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "vision load failure stored updatedAt must advance")
    }

    @Test
    fun `media saver failure returns FailurePersisted`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = PhotoEditExecutionPipeline(
            repository = repository,
            aiProvider = FakeAiProvider(),
            settingsRepository = settingsRepository,
            imageSource = PhotoEditImageSource { _ -> SourceImageResult.Success(sampleAiImageInput()) },
            mediaSaver = PhotoEditMediaSaver { _, _, _, _, _ -> SaveEditedImageResult.Failure("Disk full") },
            visionImageSource = VisionImageSource { SourceImageResult.Success(sampleAiImageInput()) },
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        val persisted = assertIs<PhotoEditExecutionError.FailurePersisted>(failure.error)
        val expectedPrompt = expectedAssembledPrompt()
        assertEquals(expectedPrompt, persisted.assembledPrompt, "media saver failure persisted.assembledPrompt must match expected")
        assertEquals(expectedPrompt, persisted.photoEditRequest.prompt, "media saver failure persisted.photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, persisted.promptHistoryEntry.prompt, "media saver failure persisted.promptHistoryEntry.prompt must match the assembled prompt")
        assertIs<PhotoEditExecutionError.FailedToSaveEditedImage>(persisted.cause)
        assertTrue(persisted.updatedDraft.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "media saver failure updatedDraft.updatedAt must advance")
        val stored = assertNotNull(repository.get(draftId))
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "media saver failure stored updatedAt must advance")
    }

    @Test
    fun `editing an already PhotoEdited draft preserves PhotoEdited status`() {
        val clock = MutableClock(1_700_000_100_000L)
        val existingRequest = samplePhotoEditRequest()
        val existingResult = samplePhotoEditResult()
        val draft = sampleDraftWithVisionDescription().copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = listOf(existingRequest),
            photoEditResults = listOf(existingResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        val expectedPrompt = expectedAssembledPrompt()
        assertEquals(expectedPrompt, success.assembledPrompt, "PhotoEdited preserves status: assembledPrompt must match expected")
        assertEquals(expectedPrompt, success.photoEditRequest.prompt, "PhotoEdited preserves status: photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, success.promptHistoryEntry.prompt, "PhotoEdited preserves status: promptHistoryEntry.prompt must match the assembled prompt")
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(DraftStatus.PhotoEdited, stored.status)
        assertEquals(2, stored.photoEditRequests.size)
        assertEquals(2, stored.photoEditResults.size)
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "updatedAt must advance even when status is unchanged")
        assertEquals(1, stored.mediaItems.size, "Original mediaItems must not be modified for subsequent edits")
        assertEquals(
            MediaType.Photo,
            stored.mediaItems.first().mediaAsset.type,
            "Original photo must remain the only media item",
        )
        assertEquals(2, stored.photoEditResults.size, "Both edits must be recorded in photoEditResults")
    }

    @Test
    fun `editing a ReadyToShare draft preserves ReadyToShare status`() {
        val clock = MutableClock(1_700_000_100_000L)
        val existingRequest = samplePhotoEditRequest()
        val existingResult = samplePhotoEditResult()
        val draft = sampleDraftWithVisionDescription().copy(
            status = DraftStatus.ReadyToShare,
            photoEditRequests = listOf(existingRequest),
            photoEditResults = listOf(existingResult),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        val expectedPrompt = expectedAssembledPrompt()
        assertEquals(expectedPrompt, success.assembledPrompt, "ReadyToShare preserves status: assembledPrompt must match expected")
        assertEquals(expectedPrompt, success.photoEditRequest.prompt, "ReadyToShare preserves status: photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, success.promptHistoryEntry.prompt, "ReadyToShare preserves status: promptHistoryEntry.prompt must match the assembled prompt")
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(DraftStatus.ReadyToShare, stored.status)
        assertEquals(2, stored.photoEditRequests.size)
        assertEquals(2, stored.photoEditResults.size)
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "updatedAt must advance even when status is unchanged")
        assertEquals(1, stored.mediaItems.size, "Original mediaItems must not be modified when editing a ReadyToShare draft")
        assertEquals(
            MediaType.Photo,
            stored.mediaItems.first().mediaAsset.type,
            "Original photo must remain the only media item",
        )
        val latestResult = stored.photoEditResults.maxByOrNull { it.createdAtEpochMillis }
        assertNotNull(latestResult, "Edited asset must be linked via photoEditResults")
    }

    @Test
    fun `missing API key fails early without persisting anything`() {
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = InMemoryLocalSettingsRepository(),
            aiProvider = FakeAiProvider(),
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        assertIs<PhotoEditExecutionError.Provider>(failure.error)
        assertEquals(AiError.MissingApiKey, (failure.error as PhotoEditExecutionError.Provider).error)
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(0, stored.photoEditRequests.size)
        assertEquals(0, stored.promptHistory.size)
    }

    @Test
    fun `draft not found fails early without persisting anything`() {
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription()).also { it.clearAll() }
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(),
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        assertIs<PhotoEditExecutionError.DraftNotFound>(failure.error)
    }

    @Test
    fun `PhotoEditImageSource load returns Success with AiImageInput`() {
        val source = PhotoEditImageSource { _ -> SourceImageResult.Success(sampleAiImageInput()) }
        val asset = sampleMediaAsset()
        val result = source.load(asset)
        val success = assertIs<SourceImageResult.Success>(result)
        assertEquals("image/jpeg", success.image.mimeType)
        assertEquals("test-image.jpg", success.image.fileName)
        assertTrue(success.image.bytes.isNotEmpty())
    }

    @Test
    fun `PhotoEditImageSource load returns Failure with message`() {
        val source = PhotoEditImageSource { _ -> SourceImageResult.Failure("File not found") }
        val asset = sampleMediaAsset()
        val result = source.load(asset)
        val failure = assertIs<SourceImageResult.Failure>(result)
        assertEquals("File not found", failure.message)
    }

    @Test
    fun `PhotoEditMediaSaver save returns Success with new MediaAsset`() {
        val original = sampleMediaAsset()
        val newAsset = original.copy(
            id = MediaAssetId("media-edited-1"),
            type = MediaType.EditedPhoto,
            uri = "file://edited/result.jpg",
            createdAtEpochMillis = baseEpoch + 1000,
        )
        val saver = PhotoEditMediaSaver { _, _, original, id, createdAt -> SaveEditedImageResult.Success(newAsset) }
        val result = saver.save(
            bytes = byteArrayOf(0, 1, 2),
            mimeType = "image/jpeg",
            originalMediaAsset = original,
            mediaAssetId = MediaAssetId("media-edited-1"),
            createdAtEpochMillis = baseEpoch + 1000,
        )
        val success = assertIs<SaveEditedImageResult.Success>(result)
        assertEquals(MediaType.EditedPhoto, success.mediaAsset.type)
        assertEquals("file://edited/result.jpg", success.mediaAsset.uri)
    }

    @Test
    fun `PhotoEditMediaSaver save returns Failure with message`() {
        val saver = PhotoEditMediaSaver { _, _, _, _, _ -> SaveEditedImageResult.Failure("Disk full") }
        val result = saver.save(
            bytes = byteArrayOf(0),
            mimeType = "image/jpeg",
            originalMediaAsset = sampleMediaAsset(),
            mediaAssetId = MediaAssetId("media-edited-1"),
            createdAtEpochMillis = baseEpoch,
        )
        val failure = assertIs<SaveEditedImageResult.Failure>(result)
        assertEquals("Disk full", failure.message)
    }

    @Test
    fun `PhotoEditExecutionResult Success holds all required fields`() {
        val editRequest = samplePhotoEditRequest()
        val editResult = samplePhotoEditResult()
        val promptHistory = samplePromptHistoryEntry()
        val draft = sampleDraft()
        val result = PhotoEditExecutionResult.Success(
            photoEditRequest = editRequest,
            photoEditResult = editResult,
            assembledPrompt = "Edit this image: Improve lighting",
            promptHistoryEntry = promptHistory,
            updatedDraft = draft,
        )
        assertEquals(editRequest, result.photoEditRequest)
        assertEquals(editResult, result.photoEditResult)
        assertEquals("Edit this image: Improve lighting", result.assembledPrompt)
        assertEquals(promptHistory, result.promptHistoryEntry)
        assertEquals(draft, result.updatedDraft)
    }

    @Test
    fun `PhotoEditExecutionResult Failure holds PhotoEditExecutionError`() {
        val result = PhotoEditExecutionResult.Failure(PhotoEditExecutionError.DraftNotFound)
        assertIs<PhotoEditExecutionResult.Failure>(result)
        assertIs<PhotoEditExecutionError.DraftNotFound>(result.error)
    }

    @Test
    fun `DraftNotFound error is a data object`() {
        val e1 = PhotoEditExecutionError.DraftNotFound
        val e2 = PhotoEditExecutionError.DraftNotFound
        assertEquals(e1, e2)
    }

    @Test
    fun `SourceMediaNotFound error is a data object`() {
        val e1 = PhotoEditExecutionError.SourceMediaNotFound
        val e2 = PhotoEditExecutionError.SourceMediaNotFound
        assertEquals(e1, e2)
    }

    @Test
    fun `InvalidDraftStatus error carries status`() {
        val err = PhotoEditExecutionError.InvalidDraftStatus(DraftStatus.Archived)
        assertEquals(DraftStatus.Archived, err.status)
    }

    @Test
    fun `Provider error wraps any AiError variant including MissingApiKey`() {
        val exactMissing = PhotoEditExecutionError.Provider(AiError.MissingApiKey)
        val exactMissingAgain = PhotoEditExecutionError.Provider(AiError.MissingApiKey)
        assertEquals(exactMissing, exactMissingAgain)
        assertIs<AiError.MissingApiKey>((exactMissing as PhotoEditExecutionError.Provider).error)

        val rateLimited = PhotoEditExecutionError.Provider(AiError.RateLimited())
        assertIs<AiError.RateLimited>((rateLimited as PhotoEditExecutionError.Provider).error)

        val invalidKey = PhotoEditExecutionError.Provider(AiError.InvalidApiKey())
        assertIs<AiError.InvalidApiKey>((invalidKey as PhotoEditExecutionError.Provider).error)
    }

    @Test
    fun `FailedToLoadSourceImage error carries message`() {
        val err = PhotoEditExecutionError.FailedToLoadSourceImage("corrupt file")
        assertEquals("corrupt file", err.message)
    }

    @Test
    fun `FailedToSaveEditedImage error carries message`() {
        val err = PhotoEditExecutionError.FailedToSaveEditedImage("write denied")
        assertEquals("write denied", err.message)
    }

    @Test
    fun `FailurePersisted holds request prompt history draft and cause`() {
        val cause = PhotoEditExecutionError.Provider(AiError.NetworkFailure())
        val request = samplePhotoEditRequest()
        val promptHistory = samplePromptHistoryEntry()
        val draft = sampleDraft()
        val err = PhotoEditExecutionError.FailurePersisted(
            photoEditRequest = request,
            assembledPrompt = "Edit this image",
            promptHistoryEntry = promptHistory,
            updatedDraft = draft,
            cause = cause,
        )
        assertEquals(request, err.photoEditRequest)
        assertEquals("Edit this image", err.assembledPrompt)
        assertEquals(promptHistory, err.promptHistoryEntry)
        assertEquals(draft, err.updatedDraft)
        assertEquals(cause, err.cause)
    }

    @Test
    fun `FailurePersisted can wrap any PhotoEditExecutionError cause`() {
        val causes = listOf<PhotoEditExecutionError>(
            PhotoEditExecutionError.DraftNotFound,
            PhotoEditExecutionError.SourceMediaNotFound,
            PhotoEditExecutionError.InvalidDraftStatus(DraftStatus.Archived),
            PhotoEditExecutionError.Provider(AiError.MissingApiKey),
            PhotoEditExecutionError.Provider(AiError.QuotaExceeded()),
            PhotoEditExecutionError.FailedToLoadSourceImage("not found"),
            PhotoEditExecutionError.FailedToSaveEditedImage("no space"),
        )
        for (cause in causes) {
            val wrapped = PhotoEditExecutionError.FailurePersisted(
                photoEditRequest = samplePhotoEditRequest(),
                assembledPrompt = "prompt",
                promptHistoryEntry = samplePromptHistoryEntry(),
                updatedDraft = sampleDraft(),
                cause = cause,
            )
            assertEquals(cause, wrapped.cause)
        }
    }

    @Test
    fun `FailurePersisted is itself a PhotoEditExecutionError`() {
        val err: PhotoEditExecutionError = PhotoEditExecutionError.FailurePersisted(
            photoEditRequest = samplePhotoEditRequest(),
            assembledPrompt = "prompt",
            promptHistoryEntry = samplePromptHistoryEntry(),
            updatedDraft = sampleDraft(),
            cause = PhotoEditExecutionError.Provider(AiError.MissingApiKey),
        )
        assertIs<PhotoEditExecutionError>(err)
    }

    @Test
    fun `SourceImageResult Success and Failure are sealed subtypes`() {
        assertIs<SourceImageResult>(SourceImageResult.Success(sampleAiImageInput()))
        assertIs<SourceImageResult>(SourceImageResult.Failure("err"))
    }

    @Test
    fun `SaveEditedImageResult Success and Failure are sealed subtypes`() {
        assertIs<SaveEditedImageResult>(SaveEditedImageResult.Success(sampleMediaAsset()))
        assertIs<SaveEditedImageResult>(SaveEditedImageResult.Failure("err"))
    }

    @Test
    fun `re-edit uses selected edited asset from photoEditResults as source`() {
        val clock = MutableClock(1_700_000_100_000L)
        val existingEditedAsset = MediaAsset(
            id = editedMediaAssetId,
            type = MediaType.EditedPhoto,
            uri = "file://edited/existing.jpg",
            mimeType = "image/jpeg",
            widthPx = 1920,
            heightPx = 1080,
            createdAtEpochMillis = baseEpoch + 1000,
        )
        val existingResult = PhotoEditResult(
            id = PhotoEditResultId("edit-result-existing"),
            requestId = PhotoEditRequestId("edit-req-existing"),
            draftId = draftId,
            editedMediaAsset = existingEditedAsset,
            summary = "Previous edit.",
            modelName = "dall-e-3",
            createdAtEpochMillis = baseEpoch + 1000,
        )
        val existingRequest = PhotoEditRequest(
            id = PhotoEditRequestId("edit-req-existing"),
            draftId = draftId,
            sourceMediaAssetId = mediaAssetId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.Standard,
            prompt = "First edit",
            userRefinement = null,
            subjectDescription = "A handmade ceramic mug beside a notebook.",
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            maskRegion = null,
            createdAtEpochMillis = baseEpoch + 1000,
        )
        val draftWithSelection = sampleDraftWithVisionDescription().copy(
            status = DraftStatus.PhotoEdited,
            selectedMediaAssetId = editedMediaAssetId,
            photoEditRequests = listOf(existingRequest),
            photoEditResults = listOf(existingResult),
        )
        var capturedSourceId: MediaAssetId? = null
        val capturingImageSource = PhotoEditImageSource { asset ->
            capturedSourceId = asset.id
            SourceImageResult.Success(sampleAiImageInput())
        }
        val repository = FakeManualWorkflowRepository(draftWithSelection)
        val pipeline = PhotoEditExecutionPipeline(
            repository = repository,
            aiProvider = FakeAiProvider(),
            settingsRepository = apiKeySettings(),
            imageSource = capturingImageSource,
            mediaSaver = PhotoEditMediaSaver { _, _, original, id, createdAt ->
                SaveEditedImageResult.Success(
                    MediaAsset(
                        id = id,
                        type = MediaType.EditedPhoto,
                        uri = "file://edited/re-edit.jpg",
                        mimeType = "image/jpeg",
                        widthPx = original.widthPx,
                        heightPx = original.heightPx,
                        createdAtEpochMillis = createdAt,
                    ),
                )
            },
            visionImageSource = VisionImageSource { SourceImageResult.Success(sampleAiImageInput()) },
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.BackgroundAdjustment,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.Standard,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        val expectedPrompt = expectedAssembledPrompt(
            intent = EditIntent.BackgroundAdjustment,
            qualityTier = QualityTier.Standard,
        )
        assertEquals(expectedPrompt, success.assembledPrompt, "re-edit success.assembledPrompt must match expected")
        assertEquals(expectedPrompt, success.photoEditRequest.prompt, "re-edit success.photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, success.promptHistoryEntry.prompt, "re-edit success.promptHistoryEntry.prompt must match the assembled prompt")
        assertEquals(editedMediaAssetId, capturedSourceId, "Pipeline must use the selected edited asset as source")
        assertEquals(editedMediaAssetId, success.photoEditRequest.sourceMediaAssetId, "Edit request source must be the selected edited asset")
    }

    @Test
    fun `draft exists but vision asset not found returns SourceMediaNotFound`() {
        val clock = MutableClock(1_700_000_100_000L)
        val orphanVision = VisionDescription(
            id = VisionDescriptionId("vision-orphan"),
            draftId = draftId,
            mediaAssetId = MediaAssetId("media-nonexistent"),
            description = "A description for an asset that no longer exists.",
            modelName = "cached-model",
            createdAtEpochMillis = 1_700_000_050_000L,
        )
        val draft = sampleDraft().copy(visionDescription = orphanVision)
        val repository = FakeManualWorkflowRepository(draft)
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.Standard,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        assertIs<PhotoEditExecutionError.SourceMediaNotFound>(failure.error)
    }

    @Test
    fun `source asset matches vision asset when selection changes mid-execution`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription().copy(
            status = DraftStatus.PhotoEdited,
            selectedMediaAssetId = mediaAssetId,
        )
        var capturedSource: MediaAssetId? = null
        val capturingImageSource = PhotoEditImageSource { asset ->
            capturedSource = asset.id
            SourceImageResult.Success(sampleAiImageInput())
        }
        val repository = object : FakeManualWorkflowRepository(draft) {
            private var getCount = 0
            override fun get(id: PostDraftId): PostDraft? {
                getCount++
                val fetched = super.get(id) ?: return null
                if (getCount == 2) {
                    return fetched.copy(selectedMediaAssetId = MediaAssetId("nonexistent"))
                }
                return fetched
            }
        }
        val pipeline = PhotoEditExecutionPipeline(
            repository = repository,
            aiProvider = FakeAiProvider(),
            settingsRepository = apiKeySettings(),
            imageSource = capturingImageSource,
            mediaSaver = PhotoEditMediaSaver { _, _, original, id, createdAt ->
                SaveEditedImageResult.Success(
                    MediaAsset(
                        id = id,
                        type = MediaType.EditedPhoto,
                        uri = "file://edited/output.jpg",
                        mimeType = "image/jpeg",
                        widthPx = original.widthPx,
                        heightPx = original.heightPx,
                        createdAtEpochMillis = createdAt,
                    ),
                )
            },
            visionImageSource = VisionImageSource { SourceImageResult.Success(sampleAiImageInput()) },
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.Standard,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        val expectedPrompt = expectedAssembledPrompt(
            qualityTier = QualityTier.Standard,
        )
        assertEquals(expectedPrompt, success.assembledPrompt, "selection-change success.assembledPrompt must match expected")
        assertEquals(expectedPrompt, success.photoEditRequest.prompt, "selection-change success.photoEditRequest.prompt must match the assembled prompt")
        assertEquals(expectedPrompt, success.promptHistoryEntry.prompt, "selection-change success.promptHistoryEntry.prompt must match the assembled prompt")
        assertEquals(mediaAssetId, capturedSource, "Source asset must match the vision asset, not the changed selection")
    }

    @Test
    fun `failed edit persists request and history entries in repository and advances updatedAt`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val failingProvider = object : AiProvider {
            override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> =
                AiProviderResult.Success(VisionDescriptionOutput("Recorded vision.", "recording-model"))
            override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> =
                error("Not used")
            override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
                error("Not used")
            override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
                AiProviderResult.Failure(AiError.RateLimited())
        }
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = failingProvider,
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        val persisted = assertIs<PhotoEditExecutionError.FailurePersisted>(failure.error)
        val expectedPrompt = expectedAssembledPrompt()

        val stored = assertNotNull(repository.get(draftId))
        assertEquals(1, stored.photoEditRequests.size)
        assertEquals(expectedPrompt, stored.photoEditRequests.first().prompt,
            "Stored request prompt must match assembled prompt on failure")
        assertEquals(1, stored.promptHistory.size)
        assertEquals(expectedPrompt, stored.promptHistory.first().prompt,
            "Stored prompt history entry prompt must match assembled prompt on failure")
        assertNotNull(stored.promptHistory.first().responseSummary,
            "Failure prompt history entry must have a response summary")
        assertEquals(AiOperationType.PhotoEdit, stored.promptHistory.first().operationType)

        val baseInstant = Instant.fromEpochMilliseconds(baseEpoch)
        assertTrue(stored.updatedAt > baseInstant,
            "Stored draft updatedAt must advance beyond base on failure")
        assertEquals(stored.updatedAt, persisted.updatedDraft.updatedAt,
            "Stored and FailurePersisted updatedAt must match")

        assertEquals(0, stored.photoEditResults.size,
            "No results must be stored on failure")
    }

    @Test
    fun `successful edit persists selectedMediaAssetId atomically with results in repository`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        val editedAssetId = success.photoEditResult.editedMediaAsset.id
        val stored = assertNotNull(repository.get(draftId))

        assertEquals(editedAssetId, stored.selectedMediaAssetId,
            "selectedMediaAssetId must be atomically set to the newly edited asset in the stored draft")
        assertTrue(stored.photoEditResults.any { it.editedMediaAsset.id == editedAssetId },
            "Edited asset must be present in stored photoEditResults")
        assertEquals(1, stored.photoEditResults.size)
        assertEquals(1, stored.photoEditRequests.size)
        assertEquals(1, stored.promptHistory.size)
        assertEquals(DraftStatus.PhotoEdited, stored.status)

        assertEquals(editedAssetId, success.updatedDraft.selectedMediaAssetId,
            "Returned updatedDraft must also have selectedMediaAssetId set to the edited asset")
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch),
            "Stored draft updatedAt must advance on success")
    }

    @Test
    fun `rehydrated draft selectedMediaAssetId is used as source for downstream pipeline edit`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        val firstResult = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )
        val firstSuccess = assertIs<PhotoEditExecutionResult.Success>(firstResult)
        val firstEditedAssetId = firstSuccess.photoEditResult.editedMediaAsset.id

        val rehydratedDraft = assertNotNull(repository.get(draftId))
        assertEquals(firstEditedAssetId, rehydratedDraft.selectedMediaAssetId,
            "Rehydrated draft must have selectedMediaAssetId set to the first edited asset")

        clock.now = 1_700_000_200_000L
        var capturedSourceId: MediaAssetId? = null
        val capturingImageSource = PhotoEditImageSource { asset ->
            capturedSourceId = asset.id
            SourceImageResult.Success(sampleAiImageInput())
        }
        val secondPipeline = PhotoEditExecutionPipeline(
            repository = repository,
            aiProvider = FakeAiProvider(),
            settingsRepository = settingsRepository,
            imageSource = capturingImageSource,
            mediaSaver = PhotoEditMediaSaver { _, _, original, id, createdAt ->
                SaveEditedImageResult.Success(
                    MediaAsset(
                        id = id,
                        type = MediaType.EditedPhoto,
                        uri = "file://edited/re-edit.jpg",
                        mimeType = "image/jpeg",
                        widthPx = original.widthPx,
                        heightPx = original.heightPx,
                        createdAtEpochMillis = createdAt,
                    ),
                )
            },
            visionImageSource = VisionImageSource { SourceImageResult.Success(sampleAiImageInput()) },
            clock = clock,
        )

        val secondResult = secondPipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val secondSuccess = assertIs<PhotoEditExecutionResult.Success>(secondResult)

        assertEquals(firstEditedAssetId, capturedSourceId,
            "Downstream pipeline must use the selected edited asset from the rehydrated draft as source")
        assertEquals(firstEditedAssetId, secondSuccess.photoEditRequest.sourceMediaAssetId,
            "Second edit request source must be the first edited asset")

        val finalStored = assertNotNull(repository.get(draftId))
        assertEquals(2, finalStored.photoEditResults.size,
            "Both edits must be recorded in photoEditResults")
        assertEquals(2, finalStored.photoEditRequests.size,
            "Both requests must be recorded in photoEditRequests")
        assertEquals(2, finalStored.promptHistory.size,
            "Both prompt history entries must be recorded")
        assertEquals(DraftStatus.PhotoEdited, finalStored.status)
        assertTrue(finalStored.updatedAt > Instant.fromEpochMilliseconds(1_700_000_100_000L),
            "updatedAt must advance on second edit")
    }

    @Test
    fun `vision imageSource load failure with reuse=true and no cached vision returns FailurePersisted`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraft()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = PhotoEditExecutionPipeline(
            repository = repository,
            aiProvider = FakeAiProvider(),
            settingsRepository = settingsRepository,
            imageSource = PhotoEditImageSource { _ -> SourceImageResult.Success(sampleAiImageInput()) },
            mediaSaver = PhotoEditMediaSaver { _, _, _, _, _ -> SaveEditedImageResult.Success(sampleMediaAsset()) },
            visionImageSource = VisionImageSource { SourceImageResult.Failure("Vision image file corrupt") },
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        val persisted = assertIs<PhotoEditExecutionError.FailurePersisted>(failure.error)
        assertIs<PhotoEditExecutionError.FailedToLoadSourceImage>(persisted.cause)
        assertEquals("Vision image file corrupt", (persisted.cause as PhotoEditExecutionError.FailedToLoadSourceImage).message)
        val expectedPrompt = expectedAssembledPrompt(subjectDescription = null)
        assertEquals(expectedPrompt, persisted.assembledPrompt, "vision load failure with reuse=true no cache persisted.assembledPrompt must match expected")
        assertEquals(expectedPrompt, persisted.photoEditRequest.prompt, "vision load failure with reuse=true no cache persisted.photoEditRequest.prompt must match")
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(1, stored.photoEditRequests.size, "Edit request must be persisted on vision load failure when cache miss")
        assertEquals(1, stored.promptHistory.size, "Prompt history must be persisted on vision load failure when cache miss")
        assertTrue(persisted.updatedDraft.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "vision load failure with reuse=true no cache updatedDraft.updatedAt must advance")
        assertTrue(stored.updatedAt > Instant.fromEpochMilliseconds(baseEpoch), "vision load failure with reuse=true no cache stored updatedAt must advance")
    }

    @Test
    fun `vision-analysis provider failure before image edit returns Failure without persistence`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraft()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val failingVisionProvider = object : AiProvider {
            override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> =
                AiProviderResult.Failure(AiError.RateLimited())
            override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> =
                error("Not used")
            override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
                error("Not used")
            override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
                error("Not used")
        }
        val pipeline = PhotoEditExecutionPipeline(
            repository = repository,
            aiProvider = failingVisionProvider,
            settingsRepository = settingsRepository,
            imageSource = PhotoEditImageSource { _ -> SourceImageResult.Success(sampleAiImageInput()) },
            mediaSaver = PhotoEditMediaSaver { _, _, _, _, _ -> SaveEditedImageResult.Success(sampleMediaAsset()) },
            visionImageSource = VisionImageSource { SourceImageResult.Success(sampleAiImageInput()) },
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = false,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        assertIs<PhotoEditExecutionError.Provider>(failure.error)
        assertEquals(AiError.RateLimited(), (failure.error as PhotoEditExecutionError.Provider).error)
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(0, stored.photoEditRequests.size, "No edit request must be persisted on vision-analysis failure")
        assertEquals(0, stored.promptHistory.size, "No prompt history must be persisted on vision-analysis failure")
        assertEquals(0, stored.photoEditResults.size, "No edit result must be persisted on vision-analysis failure")
    }

    @Test
    fun `reuseVisionDescription equals false forces re-analysis even with cached vision`() {
        val clock = MutableClock(1_700_000_100_000L)
        val draft = sampleDraftWithVisionDescription()
        val repository = FakeManualWorkflowRepository(draft)
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = false,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        val expectedPrompt = expectedAssembledPrompt()
        assertEquals(expectedPrompt, success.assembledPrompt, "reuseVisionDescription=false assembledPrompt must match expected")
        assertEquals(expectedPrompt, success.photoEditRequest.prompt, "reuseVisionDescription=false photoEditRequest.prompt must match")
        assertEquals(expectedPrompt, success.promptHistoryEntry.prompt, "reuseVisionDescription=false promptHistoryEntry.prompt must match")
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(DraftStatus.PhotoEdited, stored.status)
        assertEquals(1, stored.photoEditRequests.size)
        assertEquals(1, stored.photoEditResults.size)
        assertEquals(2, stored.promptHistory.size, "Must have both a fresh vision analysis AND edit entry")
        val visionHistoryEntries = stored.promptHistory.filter { it.operationType == AiOperationType.VisionDescription }
        assertEquals(1, visionHistoryEntries.size, "A fresh vision description must be recorded")
        assertTrue(stored.visionDescription?.description?.startsWith("Fake vision for media-edit-1") == true, "Vision must be freshly analyzed, not from cache")
    }

    @Test
    fun `pipeline always produces PhotoEditRequest with null maskRegion`() {
        val clock = MutableClock(1_700_000_100_000L)
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription())
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val success = assertIs<PhotoEditExecutionResult.Success>(result)
        assertEquals(null, success.photoEditRequest.maskRegion, "Pipeline must always produce null maskRegion")
        val stored = assertNotNull(repository.get(draftId))
        val storedRequest = stored.photoEditRequests.single()
        assertEquals(null, storedRequest.maskRegion, "Stored request maskRegion must be null")
    }

    @Test
    fun `selectedMediaAssetId is preserved after failure-persisted edit`() {
        val clock = MutableClock(1_700_000_100_000L)
        val preselectedAssetId = MediaAssetId("media-edited-existing")
        val existingEditedAsset = MediaAsset(
            id = preselectedAssetId,
            type = MediaType.EditedPhoto,
            uri = "file://edited/existing.jpg",
            mimeType = "image/jpeg",
            widthPx = 1920,
            heightPx = 1080,
            createdAtEpochMillis = baseEpoch + 1000,
        )
        val existingResult = PhotoEditResult(
            id = PhotoEditResultId("edit-result-existing"),
            requestId = PhotoEditRequestId("edit-req-existing"),
            draftId = draftId,
            editedMediaAsset = existingEditedAsset,
            summary = "Previous edit.",
            modelName = "dall-e-3",
            createdAtEpochMillis = baseEpoch + 1000,
        )
        val currentDraft = sampleDraftWithVisionDescription().copy(
            status = DraftStatus.PhotoEdited,
            selectedMediaAssetId = preselectedAssetId,
            photoEditResults = listOf(existingResult),
        )
        val repository = FakeManualWorkflowRepository(currentDraft)
        val settingsRepository = apiKeySettings()
        val failingProvider = object : AiProvider {
            override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> =
                AiProviderResult.Success(VisionDescriptionOutput("Recorded vision.", "recording-model"))
            override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> =
                error("Not used")
            override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
                error("Not used")
            override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
                AiProviderResult.Failure(AiError.RateLimited())
        }
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = failingProvider,
            clock = clock,
        )

        val result = pipeline.execute(
            draftId = draftId,
            intent = EditIntent.ImproveLighting,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            userRefinement = null,
            reuseVisionDescription = true,
        )

        val failure = assertIs<PhotoEditExecutionResult.Failure>(result)
        val persisted = assertIs<PhotoEditExecutionError.FailurePersisted>(failure.error)
        assertEquals(preselectedAssetId, persisted.updatedDraft.selectedMediaAssetId, "FailurePersisted updatedDraft must preserve existing selectedMediaAssetId")
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(preselectedAssetId, stored.selectedMediaAssetId, "Stored draft selectedMediaAssetId must be preserved after failure")
        assertEquals(1, stored.photoEditResults.size, "Existing edit result must be preserved; no new result added on failure")
    }

    @Test
    fun `PhotoEditExecutionResult Success and Failure are sealed subtypes`() {
        assertIs<PhotoEditExecutionResult>(PhotoEditExecutionResult.Success(
            photoEditRequest = samplePhotoEditRequest(),
            photoEditResult = samplePhotoEditResult(),
            assembledPrompt = "",
            promptHistoryEntry = samplePromptHistoryEntry(),
            updatedDraft = sampleDraft(),
        ))
        assertIs<PhotoEditExecutionResult>(PhotoEditExecutionResult.Failure(
            PhotoEditExecutionError.DraftNotFound,
        ))
    }

    private fun pipeline(
        repository: ManualWorkflowRepository,
        settingsRepository: InMemoryLocalSettingsRepository,
        aiProvider: AiProvider,
        clock: EpochClock = MutableClock(1_700_000_100_000L),
    ): PhotoEditExecutionPipeline = PhotoEditExecutionPipeline(
        repository = repository,
        aiProvider = aiProvider,
        settingsRepository = settingsRepository,
        imageSource = PhotoEditImageSource { _ -> SourceImageResult.Success(sampleAiImageInput()) },
        mediaSaver = PhotoEditMediaSaver { _, _, original, id, createdAt ->
            SaveEditedImageResult.Success(
                MediaAsset(
                    id = id,
                    type = MediaType.EditedPhoto,
                    uri = "file://edited/output.jpg",
                    mimeType = "image/jpeg",
                    widthPx = original.widthPx,
                    heightPx = original.heightPx,
                    createdAtEpochMillis = createdAt,
                ),
            )
        },
        visionImageSource = VisionImageSource { SourceImageResult.Success(sampleAiImageInput()) },
        clock = clock,
    )

    private fun apiKeySettings(): InMemoryLocalSettingsRepository =
        InMemoryLocalSettingsRepository().also { it.saveOpenAiApiKey("sk-test") }

    private fun sampleDraft(): PostDraft = PostDraft(
        id = draftId,
        format = PostFormat.SingleImage,
        status = DraftStatus.PhotoAdded,
        mediaItems = listOf(PostMediaItem(mediaAsset = sampleMediaAsset(), order = 0)),
        caption = null,
        targetPlatforms = setOf(TargetPlatform.InstagramFeedSquare),
        brandProfile = null,
        visionDescription = null,
        captionRequests = emptyList(),
        captionResults = emptyList(),
        altTextResults = emptyList(),
        photoEditRequests = emptyList(),
        photoEditResults = emptyList(),
        promptHistory = emptyList(),
        exportRecords = emptyList(),
        createdAt = Instant.fromEpochMilliseconds(baseEpoch),
        updatedAt = Instant.fromEpochMilliseconds(baseEpoch),
    )

    private fun sampleDraftWithVisionDescription(): PostDraft {
        val visionDescription = VisionDescription(
            id = VisionDescriptionId("vision-description-cached"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            description = "A handmade ceramic mug beside a notebook.",
            modelName = "cached-model",
            createdAtEpochMillis = 1_700_000_050_000L,
        )
        return sampleDraft().copy(visionDescription = visionDescription)
    }

    private fun sampleMediaAsset(): MediaAsset = MediaAsset(
        id = mediaAssetId,
        type = MediaType.Photo,
        uri = "file://test/photo.jpg",
        mimeType = "image/jpeg",
        widthPx = 1920,
        heightPx = 1080,
        createdAtEpochMillis = baseEpoch,
    )

    private fun sampleEditedMediaAsset(): MediaAsset = MediaAsset(
        id = MediaAssetId("media-edited-result"),
        type = MediaType.EditedPhoto,
        uri = "file://edited/output.jpg",
        mimeType = "image/jpeg",
        widthPx = 1920,
        heightPx = 1080,
        createdAtEpochMillis = baseEpoch + 2000,
    )

    private fun sampleAiImageInput(): AiImageInput = AiImageInput(
        bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
        mimeType = "image/jpeg",
        fileName = "test-image.jpg",
    )

    private fun samplePhotoEditRequest(): PhotoEditRequest = PhotoEditRequest(
        id = requestId,
        draftId = draftId,
        sourceMediaAssetId = mediaAssetId,
        intent = EditIntent.ImproveLighting,
        realismLevel = RealismLevel.Photoreal,
        qualityTier = QualityTier.High,
        prompt = "Make the image brighter",
        userRefinement = null,
        subjectDescription = null,
        targetPlatform = TargetPlatform.InstagramFeedSquare,
        maskRegion = null,
        createdAtEpochMillis = baseEpoch,
    )

    private fun samplePhotoEditResult(): PhotoEditResult = PhotoEditResult(
        id = resultId,
        requestId = requestId,
        draftId = draftId,
        editedMediaAsset = sampleEditedMediaAsset(),
        summary = "Brightened the image",
        modelName = "dall-e-3",
        createdAtEpochMillis = baseEpoch + 2000,
    )

    private fun samplePromptHistoryEntry(): PromptHistoryEntry = PromptHistoryEntry(
        id = PromptHistoryEntryId("prompt-edit-1"),
        draftId = draftId,
        operationType = AiOperationType.PhotoEdit,
        prompt = "Edit this image: Improve lighting",
        responseSummary = "Brightened the image",
        modelName = "dall-e-3",
        createdAtEpochMillis = baseEpoch + 2000,
    )

    private fun expectedAssembledPrompt(
        intent: EditIntent = EditIntent.ImproveLighting,
        realismLevel: RealismLevel = RealismLevel.Photoreal,
        qualityTier: QualityTier = QualityTier.High,
        targetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
        subjectDescription: String? = "A handmade ceramic mug beside a notebook.",
        userRefinement: String? = null,
    ): String = PhotoEditPromptAssembler.buildPrompt(
        intent = intent,
        realismLevel = realismLevel,
        qualityTier = qualityTier,
        targetPlatform = targetPlatform,
        maskRegion = null,
        subjectDescription = subjectDescription,
        userRefinement = userRefinement,
    )

    private class MutableClock(var now: Long) : EpochClock {
        override fun nowMillis(): Long = now
    }

    private class FakeManualWorkflowRepository(initialDraft: PostDraft) : ManualWorkflowRepository {
        private val drafts = mutableMapOf(initialDraft.id to initialDraft)
        private val mediaAssets = mutableMapOf<MediaAssetId, MediaAsset>()

        init {
            initialDraft.mediaItems.forEach { mediaAssets[it.mediaAsset.id] = it.mediaAsset }
            initialDraft.photoEditResults.forEach { mediaAssets[it.editedMediaAsset.id] = it.editedMediaAsset }
        }

        override fun save(mediaAsset: MediaAsset) {
            mediaAssets[mediaAsset.id] = mediaAsset
        }

        override fun get(id: MediaAssetId): MediaAsset? = mediaAssets[id]

        override fun save(brandProfile: com.digitumdei.shotquill.shared.domain.BrandProfile) = Unit
        override fun get(id: com.digitumdei.shotquill.shared.domain.BrandProfileId): com.digitumdei.shotquill.shared.domain.BrandProfile? = null

        override fun save(postDraft: PostDraft) {
            postDraft.mediaItems.forEach { mediaAssets[it.mediaAsset.id] = it.mediaAsset }
            postDraft.photoEditResults.forEach { mediaAssets[it.editedMediaAsset.id] = it.editedMediaAsset }
            drafts[postDraft.id] = postDraft
        }

        override fun get(id: PostDraftId): PostDraft? = drafts[id]

        override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean {
            val draft = drafts[id] ?: return false
            if (!draft.status.canTransitionTo(status)) return false
            drafts[id] = draft.copy(status = status, updatedAt = updatedAt)
            return true
        }

        override fun updateUpdatedAt(id: PostDraftId, updatedAt: Instant): Boolean {
            val draft = drafts[id] ?: return false
            drafts[id] = draft.copy(updatedAt = updatedAt)
            return true
        }
        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean {
            val draft = drafts[id] ?: return false
            val items = mediaItems.mapIndexedNotNull { index, mid ->
                mediaAssets[mid]?.let { PostMediaItem(it, index) }
            }
            if (items.size != mediaItems.size) return false
            drafts[id] = draft.copy(mediaItems = items)
            return true
        }

        override fun updateSelectedMediaAsset(id: PostDraftId, mediaAssetId: MediaAssetId?, updatedAt: Instant): Boolean {
            val draft = drafts[id] ?: return false
            drafts[id] = draft.copy(selectedMediaAssetId = mediaAssetId, updatedAt = updatedAt)
            return true
        }

        override fun save(visionDescription: VisionDescription) = saveVisionDescription(visionDescription)
        override fun saveVisionDescription(visionDescription: VisionDescription) = save(
            drafts.getValue(visionDescription.draftId).copy(visionDescription = visionDescription),
        )
        override fun get(id: VisionDescriptionId): VisionDescription? =
            drafts.values.mapNotNull { it.visionDescription }.firstOrNull { it.id == id }
        override fun listVisionDescriptionsForDraft(id: PostDraftId): List<VisionDescription> =
            drafts[id]?.visionDescription?.let(::listOf).orEmpty()

        override fun save(captionRequest: com.digitumdei.shotquill.shared.domain.CaptionRequest) = saveCaptionRequest(captionRequest)
        override fun getCaptionRequest(id: com.digitumdei.shotquill.shared.domain.CaptionRequestId): com.digitumdei.shotquill.shared.domain.CaptionRequest? = null
        override fun listCaptionRequestsForDraft(id: PostDraftId): List<com.digitumdei.shotquill.shared.domain.CaptionRequest> = emptyList()
        override fun saveCaptionRequest(captionRequest: com.digitumdei.shotquill.shared.domain.CaptionRequest) = Unit

        override fun save(captionResult: com.digitumdei.shotquill.shared.domain.CaptionResult) = saveCaptionResult(captionResult)
        override fun getCaptionResult(id: com.digitumdei.shotquill.shared.domain.CaptionResultId): com.digitumdei.shotquill.shared.domain.CaptionResult? = null
        override fun listCaptionResultsForDraft(id: PostDraftId): List<com.digitumdei.shotquill.shared.domain.CaptionResult> = emptyList()
        override fun saveCaptionResult(captionResult: com.digitumdei.shotquill.shared.domain.CaptionResult) = Unit

        override fun save(altTextResult: com.digitumdei.shotquill.shared.domain.AltTextResult) = saveAltTextResult(altTextResult)
        override fun get(id: com.digitumdei.shotquill.shared.domain.AltTextResultId): com.digitumdei.shotquill.shared.domain.AltTextResult? = null
        override fun listAltTextResultsForDraft(id: PostDraftId): List<com.digitumdei.shotquill.shared.domain.AltTextResult> = emptyList()
        override fun saveAltTextResult(altTextResult: com.digitumdei.shotquill.shared.domain.AltTextResult) = Unit

        override fun save(photoEditRequest: PhotoEditRequest) = savePhotoEditRequest(photoEditRequest)
        override fun getPhotoEditRequest(id: PhotoEditRequestId): PhotoEditRequest? = drafts.values
            .flatMap { it.photoEditRequests }
            .firstOrNull { it.id == id }
        override fun listPhotoEditRequestsForDraft(id: PostDraftId): List<PhotoEditRequest> =
            drafts[id]?.photoEditRequests.orEmpty()
        override fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest) = save(
            drafts.getValue(photoEditRequest.draftId).let { it.copy(photoEditRequests = it.photoEditRequests + photoEditRequest) },
        )

        override fun save(photoEditResult: PhotoEditResult) = savePhotoEditResult(photoEditResult)
        override fun getPhotoEditResult(id: PhotoEditResultId): PhotoEditResult? = drafts.values
            .flatMap { it.photoEditResults }
            .firstOrNull { it.id == id }
        override fun listPhotoEditResultsForDraft(id: PostDraftId): List<PhotoEditResult> =
            drafts[id]?.photoEditResults.orEmpty()
        override fun savePhotoEditResult(photoEditResult: PhotoEditResult) {
            mediaAssets[photoEditResult.editedMediaAsset.id] = photoEditResult.editedMediaAsset
            save(
                drafts.getValue(photoEditResult.draftId).let { it.copy(photoEditResults = it.photoEditResults + photoEditResult) },
            )
        }

        override fun save(promptHistoryEntry: PromptHistoryEntry) = savePromptHistoryEntry(promptHistoryEntry)
        override fun get(id: PromptHistoryEntryId): PromptHistoryEntry? = drafts.values
            .flatMap { it.promptHistory }
            .firstOrNull { it.id == id }
        override fun listPromptHistoryForDraft(id: PostDraftId): List<PromptHistoryEntry> =
            drafts[id]?.promptHistory.orEmpty()
        override fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry) = save(
            drafts.getValue(promptHistoryEntry.draftId).let { it.copy(promptHistory = it.promptHistory + promptHistoryEntry) },
        )

        override fun save(exportRecord: com.digitumdei.shotquill.shared.domain.ExportRecord) = saveExportRecord(exportRecord)
        override fun get(id: com.digitumdei.shotquill.shared.domain.ExportRecordId): com.digitumdei.shotquill.shared.domain.ExportRecord? = null
        override fun listExportRecordsForDraft(id: PostDraftId): List<com.digitumdei.shotquill.shared.domain.ExportRecord> = emptyList()
        override fun saveExportRecord(exportRecord: com.digitumdei.shotquill.shared.domain.ExportRecord) = Unit

        override fun savePhotoEditSuccess(
            draftId: PostDraftId,
            editedMediaAsset: MediaAsset,
            editRequest: PhotoEditRequest,
            editResult: PhotoEditResult,
            promptHistoryEntry: PromptHistoryEntry,
            targetStatus: DraftStatus,
            updatedAt: Instant,
        ): PostDraft? {
            val currentDraft = drafts[draftId] ?: return null
            val draftWithRecords = currentDraft.copy(
                photoEditRequests = currentDraft.photoEditRequests + editRequest,
                photoEditResults = currentDraft.photoEditResults + editResult,
                promptHistory = currentDraft.promptHistory + promptHistoryEntry,
            )
            val candidate = if (targetStatus != currentDraft.status) {
                draftWithRecords.transitionTo(targetStatus, updatedAt).copy(
                    selectedMediaAssetId = editedMediaAsset.id,
                )
            } else {
                draftWithRecords.copy(updatedAt = updatedAt, selectedMediaAssetId = editedMediaAsset.id)
            }
            mediaAssets[editedMediaAsset.id] = editedMediaAsset
            drafts[draftId] = candidate
            return candidate
        }

        override fun savePhotoEditFailure(
            draftId: PostDraftId,
            editRequest: PhotoEditRequest,
            promptHistoryEntry: PromptHistoryEntry,
            updatedAt: Instant,
        ): PostDraft? {
            val currentDraft = drafts[draftId] ?: return null
            drafts[draftId] = currentDraft.copy(
                updatedAt = updatedAt,
                photoEditRequests = currentDraft.photoEditRequests + editRequest,
                promptHistory = currentDraft.promptHistory + promptHistoryEntry,
            )
            return drafts[draftId]
        }

        override fun recordPostTextGeneration(
            draftId: PostDraftId,
            status: DraftStatus,
            caption: com.digitumdei.shotquill.shared.domain.CaptionDraft,
            targetPlatform: TargetPlatform,
            brandProfile: com.digitumdei.shotquill.shared.domain.BrandProfile?,
            captionRequest: com.digitumdei.shotquill.shared.domain.CaptionRequest,
            captionResult: com.digitumdei.shotquill.shared.domain.CaptionResult,
            altTextResult: com.digitumdei.shotquill.shared.domain.AltTextResult,
            promptHistoryEntries: List<PromptHistoryEntry>,
            updatedAt: Instant,
        ): PostDraft? = null

        override fun clearAll() {
            drafts.clear()
            mediaAssets.clear()
        }
    }
}