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
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import com.digitumdei.shotquill.shared.domain.FinalPostContent
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
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
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.VisionDescriptionId
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import com.digitumdei.shotquill.shared.storage.UpdateSelectionResult
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VisionDescriptionAnalyzerTest {
    private val draftId = PostDraftId("draft-1")
    private val mediaAssetId = MediaAssetId("media-1")

    @Test
    fun storesProviderVisionDescriptionAndPromptHistory() {
        val repository = FakeManualWorkflowRepository(sampleDraft())
        val analyzer = VisionDescriptionAnalyzer(
            repository = repository,
            aiProvider = FakeAiProvider(modelName = "fake-vision"),
            imageSource = fakeImageSource(),
            clock = FixedClock(1_700_000_100_000L),
        )

        val result = analyzer.analyzePrimaryPhoto(draftId)

        val success = assertIs<VisionDescriptionAnalysisResult.Success>(result)
        assertEquals(false, success.cacheHit)
        assertTrue(success.visionDescription.description.startsWith("Fake vision for media-1"))
        val stored = repository.get(draftId)
        assertEquals(success.visionDescription, stored?.visionDescriptions?.firstOrNull())
        assertEquals(1, stored?.promptHistory?.size)
        val promptHistory = stored?.promptHistory?.single()
        assertEquals(AiOperationType.VisionDescription, promptHistory?.operationType)
        assertEquals(success.visionDescription.description, promptHistory?.responseSummary)
        assertTrue(promptHistory?.prompt?.contains("Visible text or logos") == true)
        assertEquals("fake", promptHistory?.provider)
        assertEquals(mediaAssetId, promptHistory?.mediaAssetId)
        assertEquals("fileName=photo.jpg, mimeType=image/jpeg", promptHistory?.requestSettings)
        assertEquals(success.visionDescription.id.value, promptHistory?.resultReference)
    }

    @Test
    fun reusesCachedVisionDescriptionWithoutCallingProvider() {
        val cached = VisionDescription(
            id = VisionDescriptionId("vision-description-cached"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            description = "Cached concise description.",
            modelName = "cached-model",
            createdAtEpochMillis = 1_700_000_050_000L,
        )
        val repository = FakeManualWorkflowRepository(sampleDraft().copy(visionDescriptions = listOf(cached)))
        val provider = RecordingAiProvider()
        val analyzer = VisionDescriptionAnalyzer(
            repository = repository,
            aiProvider = provider,
            imageSource = fakeImageSource(),
            clock = FixedClock(1_700_000_100_000L),
        )

        val result = analyzer.analyzePrimaryPhoto(draftId)

        val success = assertIs<VisionDescriptionAnalysisResult.Success>(result)
        assertEquals(true, success.cacheHit)
        assertEquals(cached, success.visionDescription)
        assertEquals(0, provider.visionRequests.size)
        assertEquals(emptyList(), repository.get(draftId)?.promptHistory)
    }

    @Test
    fun canBypassCacheForFreshVisionDescription() {
        val cached = VisionDescription(
            id = VisionDescriptionId("vision-description-cached"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            description = "Cached concise description.",
            modelName = "cached-model",
            createdAtEpochMillis = 1_700_000_050_000L,
        )
        val repository = FakeManualWorkflowRepository(sampleDraft().copy(visionDescriptions = listOf(cached)))
        val provider = RecordingAiProvider()
        val analyzer = VisionDescriptionAnalyzer(
            repository = repository,
            aiProvider = provider,
            imageSource = fakeImageSource(),
            clock = FixedClock(1_700_000_100_000L),
        )

        val result = analyzer.analyzePrimaryPhoto(draftId, reuseCached = false)

        val success = assertIs<VisionDescriptionAnalysisResult.Success>(result)
        assertEquals(false, success.cacheHit)
        assertEquals("Recorded provider description.", success.visionDescription.description)
        assertEquals(1, provider.visionRequests.size)
        assertEquals(1, repository.get(draftId)?.promptHistory?.size)
    }

    @Test
    fun analyzesEditedAssetWhenSelected() {
        val editedMediaAssetId = MediaAssetId("media-edited-1")
        val editedMediaAsset = MediaAsset(
            id = editedMediaAssetId,
            type = MediaType.EditedPhoto,
            uri = "file://photo-edited.jpg",
            mimeType = "image/jpeg",
            widthPx = 1080,
            heightPx = 1350,
            createdAtEpochMillis = 1_700_000_030_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            selectedMediaAssetId = editedMediaAssetId,
            photoEditResults = listOf(
                PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-1"),
                    requestId = PhotoEditRequestId("photo-edit-request-1"),
                    draftId = draftId,
                    editedMediaAsset = editedMediaAsset,
                    summary = "Adjusted brightness.",
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_030_000L,
                ),
            ),
        )
        val repository = FakeManualWorkflowRepository(draft)
        val analyzer = VisionDescriptionAnalyzer(
            repository = repository,
            aiProvider = FakeAiProvider(modelName = "fake-vision"),
            imageSource = fakeImageSource(),
            clock = FixedClock(1_700_000_100_000L),
        )

        val result = analyzer.analyzePrimaryPhoto(draftId)

        val success = assertIs<VisionDescriptionAnalysisResult.Success>(result)
        assertEquals(editedMediaAssetId, success.visionDescription.mediaAssetId)
        assertTrue(success.visionDescription.description.startsWith("Fake vision for media-edited-1"))
    }

    @Test
    fun imageLoadFailureReturnsFailureWithMessage() {
        val repository = FakeManualWorkflowRepository(sampleDraft())
        val analyzer = VisionDescriptionAnalyzer(
            repository = repository,
            aiProvider = FakeAiProvider(modelName = "fake-vision"),
            imageSource = VisionImageSource { SourceImageResult.Failure("File not found") },
            clock = FixedClock(1_700_000_100_000L),
        )

        val result = analyzer.analyzePrimaryPhoto(draftId)

        val failure = assertIs<VisionDescriptionAnalysisResult.Failure>(result)
        val error = assertIs<VisionDescriptionAnalysisError.ImageLoadFailure>(failure.error)
        assertEquals("File not found", error.message)
    }

    @Test
    fun providerFailurePersistsPromptHistoryEntryError() {
        val repository = FakeManualWorkflowRepository(sampleDraft())
        val failingProvider = object : AiProvider {
            override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> =
                AiProviderResult.Failure(AiError.QuotaExceeded())
            override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> =
                error("Not used")
            override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
                error("Not used")
            override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
                error("Not used")
        }
        val analyzer = VisionDescriptionAnalyzer(
            repository = repository,
            aiProvider = failingProvider,
            imageSource = fakeImageSource(),
            clock = FixedClock(1_700_000_100_000L),
        )

        val result = analyzer.analyzePrimaryPhoto(draftId)

        val failure = assertIs<VisionDescriptionAnalysisResult.Failure>(result)
        val error = assertIs<VisionDescriptionAnalysisError.Provider>(failure.error)
        assertIs<AiError.QuotaExceeded>(error.error)
        assertEquals(null, repository.get(draftId)?.visionDescriptions?.firstOrNull(), "No vision description must be persisted on vision provider failure")

        val promptHistory = repository.get(draftId)?.promptHistory
        assertEquals(1, promptHistory?.size, "A prompt history entry must be persisted on vision provider failure")
        val entry = promptHistory?.single()
        assertEquals(AiOperationType.VisionDescription, entry?.operationType)
        assertEquals("unknown", entry?.provider)
        assertEquals(mediaAssetId, entry?.mediaAssetId)
        assertEquals("fileName=photo.jpg, mimeType=image/jpeg", entry?.requestSettings)
        assertEquals(null, entry?.resultReference, "resultReference must be null for a failure entry")
        assertEquals("The OpenAI account quota has been reached.", entry?.errorMessage)
        assertEquals(null, entry?.responseSummary, "responseSummary must be null for a failure entry")
        assertEquals(null, entry?.modelName, "modelName must be null for a failure entry")
        assertTrue(entry?.prompt?.contains("Visible text or logos") == true)
        assertTrue(entry?.isFailure == true)
    }

    @Test
    fun promptHistoryRequestSettingsDoesNotContainSecrets() {
        val repository = FakeManualWorkflowRepository(sampleDraft())
        val analyzer = VisionDescriptionAnalyzer(
            repository = repository,
            aiProvider = FakeAiProvider(modelName = "fake-vision"),
            imageSource = fakeImageSource(),
            clock = FixedClock(1_700_000_100_000L),
        )

        val result = analyzer.analyzePrimaryPhoto(draftId)

        val success = assertIs<VisionDescriptionAnalysisResult.Success>(result)
        val promptHistory = repository.get(draftId)?.promptHistory?.single()
        val requestSettings = promptHistory?.requestSettings
        assertNotNull(requestSettings)
        assertFalse(requestSettings.contains("sk-"), "requestSettings must not expose an OpenAI API key pattern")
        assertFalse(requestSettings.contains("image-bytes"), "requestSettings must not expose raw image data")
    }

    private fun sampleDraft(): PostDraft =
        PostDraft(
            id = draftId,
            format = PostFormat.SingleImage,
            status = DraftStatus.PhotoAdded,
            mediaItems = listOf(
                PostMediaItem(
                    mediaAsset = MediaAsset(
                        id = mediaAssetId,
                        type = MediaType.Photo,
                        uri = "file://photo.jpg",
                        mimeType = "image/jpeg",
                        widthPx = 1080,
                        heightPx = 1350,
                        createdAtEpochMillis = 1_700_000_000_000L,
                    ),
                    order = 0,
                ),
            ),
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

    private fun fakeImageSource(): VisionImageSource =
        VisionImageSource {
            SourceImageResult.Success(
                AiImageInput(
                    bytes = "image-bytes".encodeToByteArray(),
                    mimeType = it.mimeType ?: "image/jpeg",
                    fileName = "${it.id.value}.jpg",
                ),
            )
        }

    private class RecordingAiProvider : AiProvider {
        val visionRequests = mutableListOf<VisionDescriptionRequest>()

        override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> {
            visionRequests += request
            return AiProviderResult.Success(
                VisionDescriptionOutput(
                    description = "Recorded provider description.",
                    modelName = "recording-model",
                ),
            )
        }

        override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> =
            error("Not used")

        override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> =
            error("Not used")

        override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
            error("Not used")
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

        override fun save(brandProfile: BrandProfile) = Unit
        override fun get(id: BrandProfileId): BrandProfile? = null

        override fun save(postDraft: PostDraft) {
            drafts[postDraft.id] = postDraft
            postDraft.mediaItems.forEach { mediaAssets[it.mediaAsset.id] = it.mediaAsset }
            postDraft.photoEditResults.forEach { mediaAssets[it.editedMediaAsset.id] = it.editedMediaAsset }
        }

        override fun get(id: PostDraftId): PostDraft? = drafts[id]
        override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean = false
        override fun updateUpdatedAt(id: PostDraftId, updatedAt: Instant): Boolean {
            val draft = drafts[id] ?: return false
            drafts[id] = draft.copy(updatedAt = updatedAt)
            return true
        }
        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean = false

        override fun updateSelectedMediaAsset(id: PostDraftId, mediaAssetId: MediaAssetId?, updatedAt: Instant): UpdateSelectionResult {
            val draft = drafts[id] ?: return UpdateSelectionResult.DraftNotFound
            drafts[id] = draft.copy(selectedMediaAssetId = mediaAssetId, updatedAt = updatedAt)
            return UpdateSelectionResult.Success
        }

        override fun save(visionDescription: VisionDescription) = saveVisionDescription(visionDescription)
        override fun saveVisionDescription(visionDescription: VisionDescription) {
            val draft = drafts.getValue(visionDescription.draftId)
            drafts[visionDescription.draftId] = draft.copy(visionDescriptions = draft.visionDescriptions + visionDescription)
        }

        override fun get(id: VisionDescriptionId): VisionDescription? =
            drafts.values.flatMap { it.visionDescriptions }.firstOrNull { it.id == id }

        override fun listVisionDescriptionsForDraft(id: PostDraftId): List<VisionDescription> =
            drafts[id]?.visionDescriptions.orEmpty()

        override fun save(captionRequest: CaptionRequest) = saveCaptionRequest(captionRequest)
        override fun getCaptionRequest(id: CaptionRequestId): CaptionRequest? = null
        override fun listCaptionRequestsForDraft(id: PostDraftId): List<CaptionRequest> = emptyList()
        override fun saveCaptionRequest(captionRequest: CaptionRequest) = Unit
        override fun save(captionResult: CaptionResult) = saveCaptionResult(captionResult)
        override fun getCaptionResult(id: CaptionResultId): CaptionResult? = null
        override fun listCaptionResultsForDraft(id: PostDraftId): List<CaptionResult> = emptyList()
        override fun saveCaptionResult(captionResult: CaptionResult) = Unit

        override fun save(altTextResult: AltTextResult) = saveAltTextResult(altTextResult)
        override fun get(id: AltTextResultId): AltTextResult? = null
        override fun listAltTextResultsForDraft(id: PostDraftId): List<AltTextResult> = emptyList()
        override fun saveAltTextResult(altTextResult: AltTextResult) = Unit

        override fun save(photoEditRequest: PhotoEditRequest) = savePhotoEditRequest(photoEditRequest)
        override fun getPhotoEditRequest(id: PhotoEditRequestId): PhotoEditRequest? = drafts.values
            .flatMap { it.photoEditRequests }
            .firstOrNull { it.id == id }

        override fun listPhotoEditRequestsForDraft(id: PostDraftId): List<PhotoEditRequest> =
            drafts[id]?.photoEditRequests.orEmpty()

        override fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest) {
            val draft = drafts.getValue(photoEditRequest.draftId)
            drafts[photoEditRequest.draftId] = draft.copy(photoEditRequests = draft.photoEditRequests + photoEditRequest)
        }

        override fun save(photoEditResult: PhotoEditResult) = savePhotoEditResult(photoEditResult)
        override fun getPhotoEditResult(id: PhotoEditResultId): PhotoEditResult? = drafts.values
            .flatMap { it.photoEditResults }
            .firstOrNull { it.id == id }

        override fun listPhotoEditResultsForDraft(id: PostDraftId): List<PhotoEditResult> =
            drafts[id]?.photoEditResults.orEmpty()

        override fun savePhotoEditResult(photoEditResult: PhotoEditResult) {
            mediaAssets[photoEditResult.editedMediaAsset.id] = photoEditResult.editedMediaAsset
            val draft = drafts.getValue(photoEditResult.draftId)
            drafts[photoEditResult.draftId] = draft.copy(photoEditResults = draft.photoEditResults + photoEditResult)
        }

        override fun save(promptHistoryEntry: PromptHistoryEntry) = savePromptHistoryEntry(promptHistoryEntry)
        override fun get(id: PromptHistoryEntryId): PromptHistoryEntry? =
            drafts.values.flatMap { it.promptHistory }.firstOrNull { it.id == id }

        override fun listPromptHistoryForDraft(id: PostDraftId): List<PromptHistoryEntry> =
            drafts[id]?.promptHistory.orEmpty()

        override fun listPromptHistoryForMediaAsset(id: MediaAssetId): List<PromptHistoryEntry> =
            drafts.values.flatMap { it.promptHistory }.filter { it.mediaAssetId == id }

        override fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry) {
            val draft = drafts.getValue(promptHistoryEntry.draftId)
            drafts[promptHistoryEntry.draftId] = draft.copy(promptHistory = draft.promptHistory + promptHistoryEntry)
        }

        override fun save(exportRecord: ExportRecord) = saveExportRecord(exportRecord)
        override fun get(id: ExportRecordId): ExportRecord? = null
        override fun listExportRecordsForDraft(id: PostDraftId): List<ExportRecord> = emptyList()
        override fun saveExportRecord(exportRecord: ExportRecord) = Unit
        override fun saveFinalPostContent(finalPostContent: FinalPostContent) {
            val draft = drafts.getValue(finalPostContent.draftId)
            drafts[finalPostContent.draftId] = draft.copy(finalPostContent = finalPostContent)
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
        ): PostDraft? {
            val draft = drafts[draftId] ?: return null
            val draftWithRecords = draft.copy(
                photoEditRequests = draft.photoEditRequests + editRequest,
                photoEditResults = draft.photoEditResults + editResult,
                promptHistory = draft.promptHistory + promptHistoryEntry,
            )
            val candidate = if (targetStatus != draft.status) {
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
            val draft = drafts[draftId] ?: return null
            drafts[draftId] = draft.copy(
                updatedAt = updatedAt,
                photoEditRequests = draft.photoEditRequests + editRequest,
                promptHistory = draft.promptHistory + promptHistoryEntry,
            )
            return drafts[draftId]
        }
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
        ): PostDraft? {
            val draft = drafts[draftId] ?: return null
            val storedStatus = postTextGenerationStatus(draft.status, status) ?: return null
            val storedUpdatedAt = if (updatedAt >= draft.updatedAt) updatedAt else draft.updatedAt
            drafts[draftId] = draft.copy(
                status = storedStatus,
                caption = caption,
                targetPlatforms = draft.targetPlatforms + targetPlatform,
                brandProfile = brandProfile ?: draft.brandProfile,
                captionRequests = draft.captionRequests + captionRequest,
                captionResults = draft.captionResults + captionResult,
                altTextResults = draft.altTextResults + altTextResult,
                promptHistory = draft.promptHistory + promptHistoryEntries,
                updatedAt = storedUpdatedAt,
            )
            return drafts[draftId]
        }

        override fun clearAll() {
            drafts.clear()
            mediaAssets.clear()
        }

        private fun postTextGenerationStatus(current: DraftStatus, requested: DraftStatus): DraftStatus? =
            when {
                current == requested -> current
                current == DraftStatus.TextGenerated -> DraftStatus.TextGenerated
                current == DraftStatus.PhotoEdited -> DraftStatus.PhotoEdited
                current == DraftStatus.ReadyToShare -> DraftStatus.ReadyToShare
                current.canTransitionTo(requested) -> requested
                else -> null
            }
    }

    private class FixedClock(private val now: Long) : EpochClock {
        override fun nowMillis(): Long = now
    }
}
