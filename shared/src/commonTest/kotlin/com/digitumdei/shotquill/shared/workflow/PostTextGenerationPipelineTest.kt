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
import com.digitumdei.shotquill.shared.domain.CaptionRequest
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
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
import com.digitumdei.shotquill.shared.settings.ActiveBrandProfileStore
import com.digitumdei.shotquill.shared.settings.InMemoryLocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.InMemoryBrandProfileRepository
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostTextGenerationPipelineTest {
    private val draftId = PostDraftId("draft-1")
    private val mediaAssetId = MediaAssetId("media-1")

    @Test
    fun generatesCaptionAltTextPromptHistoryAndVisionDescriptionWithFakeProvider() {
        val repository = FakeManualWorkflowRepository(sampleDraft())
        val settingsRepository = apiKeySettings()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = settingsRepository,
            aiProvider = FakeAiProvider(modelName = "fake-text"),
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val success = assertIs<PostTextGenerationResult.Success>(result)
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(DraftStatus.TextGenerated, stored.status)
        assertEquals(success.captionResult.caption, stored.caption?.text)
        assertEquals(success.captionResult.hashtags, stored.caption?.hashtags)
        assertTrue(success.captionResult.caption.startsWith("Fake instagram_feed_square caption"))
        assertTrue(success.captionResult.shortCaption?.startsWith("Fake short caption") == true)
        assertTrue(success.altTextResult.altText.startsWith("Fake alt text for media-1"))
        assertEquals(success.visionDescription, stored.visionDescription)
        assertEquals(1, stored.captionRequests.size)
        assertEquals(1, stored.captionResults.size)
        assertEquals(1, stored.altTextResults.size)
        assertEquals(
            listOf(
                AiOperationType.VisionDescription,
                AiOperationType.CaptionGeneration,
                AiOperationType.AltTextGeneration,
            ),
            stored.promptHistory.map { it.operationType },
        )
    }

    @Test
    fun missingApiKeyFailsBeforeAnyProviderCall() {
        val repository = FakeManualWorkflowRepository(sampleDraft())
        val provider = RecordingAiProvider()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = InMemoryLocalSettingsRepository(),
            aiProvider = provider,
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val failure = assertIs<PostTextGenerationResult.Failure>(result)
        val providerError = assertIs<PostTextGenerationError.Provider>(failure.error)
        assertEquals(AiError.MissingApiKey, providerError.error)
        assertEquals(0, provider.totalCalls)
        assertEquals(DraftStatus.PhotoAdded, repository.get(draftId)?.status)
    }

    @Test
    fun missingBrandProfileUsesNeutralPromptFallback() {
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription())
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = FakeAiProvider(),
        )

        val result = pipeline.generateText(draftId, TargetPlatform.BlueskyPost)

        val success = assertIs<PostTextGenerationResult.Success>(result)
        assertEquals(null, success.captionRequest.brandProfileId)
        assertEquals(null, success.captionRequest.tone)
        assertTrue(success.captionRequest.prompt.contains("No active brand profile is configured"))
    }

    @Test
    fun regenerationAppendsNewResultsAndPromptHistoryEntries() {
        val clock = MutableClock(1_700_000_100_000L)
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription())
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = FakeAiProvider(),
            clock = clock,
        )

        assertIs<PostTextGenerationResult.Success>(
            pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare),
        )
        clock.now = 1_700_000_200_000L
        assertIs<PostTextGenerationResult.Success>(
            pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare),
        )

        val stored = assertNotNull(repository.get(draftId))
        assertEquals(2, stored.captionResults.size)
        assertEquals(2, stored.altTextResults.size)
        assertEquals(4, stored.promptHistory.size)
        assertEquals(
            listOf("caption-result-1700000100000-0", "caption-result-1700000200000-1"),
            stored.captionResults.map { it.id.value },
        )
    }

    @Test
    fun updatesDraftStatusAndTimestampAfterGeneration() {
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription())
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = FakeAiProvider(),
            clock = MutableClock(1_700_000_500_000L),
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramPortrait)

        val success = assertIs<PostTextGenerationResult.Success>(result)
        assertEquals(DraftStatus.TextGenerated, success.draft.status)
        assertEquals(1_700_000_500_000L, success.draft.updatedAt.toEpochMilliseconds())
        assertEquals(setOf(TargetPlatform.InstagramPortrait), success.draft.targetPlatforms)
    }

    private fun pipeline(
        repository: ManualWorkflowRepository,
        settingsRepository: InMemoryLocalSettingsRepository,
        aiProvider: AiProvider,
        clock: EpochClock = MutableClock(1_700_000_100_000L),
    ): PostTextGenerationPipeline {
        val profileRepository = InMemoryBrandProfileRepository()
        return PostTextGenerationPipeline(
            repository = repository,
            aiProvider = aiProvider,
            imageSource = fakeImageSource(),
            activeBrandProfileStore = ActiveBrandProfileStore(settingsRepository, profileRepository),
            settingsRepository = settingsRepository,
            clock = clock,
        )
    }

    private fun apiKeySettings(): InMemoryLocalSettingsRepository =
        InMemoryLocalSettingsRepository().also { it.saveOpenAiApiKey("sk-test") }

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

    private fun fakeImageSource(): VisionImageSource =
        VisionImageSource {
            AiImageInput(
                bytes = "image-bytes".encodeToByteArray(),
                mimeType = it.mimeType ?: "image/jpeg",
                fileName = "${it.id.value}.jpg",
            )
        }

    private class MutableClock(var now: Long) : EpochClock {
        override fun nowMillis(): Long = now
    }

    private class RecordingAiProvider : AiProvider {
        var totalCalls = 0

        override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> {
            totalCalls += 1
            return AiProviderResult.Success(VisionDescriptionOutput("Recorded vision.", "recording-model"))
        }

        override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> {
            totalCalls += 1
            return AiProviderResult.Success(
                CaptionGenerationOutput(
                    caption = "Recorded caption.",
                    shortCaption = "Recorded short caption.",
                    hashtags = listOf("#recorded"),
                    modelName = "recording-model",
                ),
            )
        }

        override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> {
            totalCalls += 1
            return AiProviderResult.Success(AltTextGenerationOutput("Recorded alt text.", "recording-model"))
        }

        override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
            error("Not used")
    }

    private class FakeManualWorkflowRepository(initialDraft: PostDraft) : ManualWorkflowRepository {
        private val drafts = mutableMapOf(initialDraft.id to initialDraft)

        override fun save(mediaAsset: MediaAsset) = Unit
        override fun get(id: MediaAssetId): MediaAsset? = drafts.values
            .flatMap { draft -> draft.mediaItems.map { it.mediaAsset } }
            .firstOrNull { it.id == id }

        override fun save(brandProfile: BrandProfile) = Unit
        override fun get(id: BrandProfileId): BrandProfile? = null

        override fun save(postDraft: PostDraft) {
            drafts[postDraft.id] = postDraft
        }

        override fun get(id: PostDraftId): PostDraft? = drafts[id]
        override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean = false
        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean = false

        override fun save(visionDescription: VisionDescription) = saveVisionDescription(visionDescription)
        override fun saveVisionDescription(visionDescription: VisionDescription) = save(
            drafts.getValue(visionDescription.draftId).copy(visionDescription = visionDescription),
        )

        override fun get(id: VisionDescriptionId): VisionDescription? =
            drafts.values.mapNotNull { it.visionDescription }.firstOrNull { it.id == id }

        override fun listVisionDescriptionsForDraft(id: PostDraftId): List<VisionDescription> =
            drafts[id]?.visionDescription?.let(::listOf).orEmpty()

        override fun save(captionRequest: CaptionRequest) = saveCaptionRequest(captionRequest)
        override fun getCaptionRequest(id: CaptionRequestId): CaptionRequest? = drafts.values
            .flatMap { it.captionRequests }
            .firstOrNull { it.id == id }

        override fun listCaptionRequestsForDraft(id: PostDraftId): List<CaptionRequest> =
            drafts[id]?.captionRequests.orEmpty()

        override fun saveCaptionRequest(captionRequest: CaptionRequest) = save(
            drafts.getValue(captionRequest.draftId).let { it.copy(captionRequests = it.captionRequests + captionRequest) },
        )

        override fun save(captionResult: CaptionResult) = saveCaptionResult(captionResult)
        override fun getCaptionResult(id: CaptionResultId): CaptionResult? = drafts.values
            .flatMap { it.captionResults }
            .firstOrNull { it.id == id }

        override fun listCaptionResultsForDraft(id: PostDraftId): List<CaptionResult> =
            drafts[id]?.captionResults.orEmpty()

        override fun saveCaptionResult(captionResult: CaptionResult) = save(
            drafts.getValue(captionResult.draftId).let { it.copy(captionResults = it.captionResults + captionResult) },
        )

        override fun save(altTextResult: AltTextResult) = saveAltTextResult(altTextResult)
        override fun get(id: AltTextResultId): AltTextResult? = drafts.values
            .flatMap { it.altTextResults }
            .firstOrNull { it.id == id }

        override fun listAltTextResultsForDraft(id: PostDraftId): List<AltTextResult> =
            drafts[id]?.altTextResults.orEmpty()

        override fun saveAltTextResult(altTextResult: AltTextResult) = save(
            drafts.getValue(altTextResult.draftId).let { it.copy(altTextResults = it.altTextResults + altTextResult) },
        )

        override fun save(photoEditRequest: PhotoEditRequest) = savePhotoEditRequest(photoEditRequest)
        override fun getPhotoEditRequest(id: PhotoEditRequestId): PhotoEditRequest? = null
        override fun listPhotoEditRequestsForDraft(id: PostDraftId): List<PhotoEditRequest> = emptyList()
        override fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest) = Unit
        override fun save(photoEditResult: PhotoEditResult) = savePhotoEditResult(photoEditResult)
        override fun getPhotoEditResult(id: PhotoEditResultId): PhotoEditResult? = null
        override fun listPhotoEditResultsForDraft(id: PostDraftId): List<PhotoEditResult> = emptyList()
        override fun savePhotoEditResult(photoEditResult: PhotoEditResult) = Unit

        override fun save(promptHistoryEntry: PromptHistoryEntry) = savePromptHistoryEntry(promptHistoryEntry)
        override fun get(id: PromptHistoryEntryId): PromptHistoryEntry? = drafts.values
            .flatMap { it.promptHistory }
            .firstOrNull { it.id == id }

        override fun listPromptHistoryForDraft(id: PostDraftId): List<PromptHistoryEntry> =
            drafts[id]?.promptHistory.orEmpty()

        override fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry) = save(
            drafts.getValue(promptHistoryEntry.draftId).let { it.copy(promptHistory = it.promptHistory + promptHistoryEntry) },
        )

        override fun save(exportRecord: ExportRecord) = saveExportRecord(exportRecord)
        override fun get(id: ExportRecordId): ExportRecord? = null
        override fun listExportRecordsForDraft(id: PostDraftId): List<ExportRecord> = emptyList()
        override fun saveExportRecord(exportRecord: ExportRecord) = Unit
        override fun clearAll() = drafts.clear()
    }
}
