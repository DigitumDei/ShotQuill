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
import kotlin.test.assertNull
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

    @Test
    fun regenerationPreservesAdvancedDraftStatus() {
        listOf(DraftStatus.PhotoEdited, DraftStatus.ReadyToShare).forEach { status ->
            val repository = FakeManualWorkflowRepository(
                sampleDraftWithVisionDescription().copy(status = status),
            )
            val pipeline = pipeline(
                repository = repository,
                settingsRepository = apiKeySettings(),
                aiProvider = FakeAiProvider(),
            )

            val result = pipeline.generateText(draftId, TargetPlatform.InstagramPortrait)

            val success = assertIs<PostTextGenerationResult.Success>(result)
            assertEquals(status, success.draft.status)
        }
    }

    @Test
    fun blankShortCaptionIsStoredAsNull() {
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription())
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = RecordingAiProvider(shortCaption = "  "),
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val success = assertIs<PostTextGenerationResult.Success>(result)
        assertEquals(null, success.captionResult.shortCaption)
    }

    @Test
    fun draftNotFoundFailsOnEntry() {
        val repository = FakeManualWorkflowRepository(sampleDraft()).also { it.clearAll() }
        val provider = RecordingAiProvider()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = provider,
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val failure = assertIs<PostTextGenerationResult.Failure>(result)
        assertEquals(PostTextGenerationError.DraftNotFound, failure.error)
        assertEquals(0, provider.totalCalls)
    }

    @Test
    fun draftNotFoundAfterVisionFailsWithoutSavingText() {
        val repository = FakeManualWorkflowRepository(sampleDraft()).also {
            it.deleteBeforeDraftGetNumber = 3
        }
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = RecordingAiProvider(),
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val failure = assertIs<PostTextGenerationResult.Failure>(result)
        assertEquals(PostTextGenerationError.DraftNotFound, failure.error)
        assertNull(repository.get(draftId))
    }

    @Test
    fun invalidDraftStatusFailsBeforeProviderCalls() {
        val repository = FakeManualWorkflowRepository(sampleDraft().copy(status = DraftStatus.Archived))
        val provider = RecordingAiProvider()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = provider,
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val failure = assertIs<PostTextGenerationResult.Failure>(result)
        val invalidStatus = assertIs<PostTextGenerationError.InvalidDraftStatus>(failure.error)
        assertEquals(DraftStatus.Archived, invalidStatus.status)
        assertEquals(0, provider.totalCalls)
    }

    @Test
    fun providerFailuresPropagateFromEachGenerationStage() {
        listOf(
            RecordingAiProvider.FailureStage.Vision,
            RecordingAiProvider.FailureStage.Caption,
            RecordingAiProvider.FailureStage.AltText,
        ).forEach { stage ->
            val repository = FakeManualWorkflowRepository(sampleDraft())
            val pipeline = pipeline(
                repository = repository,
                settingsRepository = apiKeySettings(),
                aiProvider = RecordingAiProvider(failureStage = stage),
            )

            val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

            val failure = assertIs<PostTextGenerationResult.Failure>(result)
            val providerError = assertIs<PostTextGenerationError.Provider>(failure.error)
            assertEquals(AiError.ProviderFailure(statusCode = null), providerError.error)
            assertEquals(null, repository.get(draftId)?.caption)
        }
    }

    @Test
    fun reuseVisionDescriptionFalseRunsVisionEvenWhenCachedDescriptionExists() {
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription())
        val provider = RecordingAiProvider()
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = provider,
        )

        val result = pipeline.generateText(
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            reuseVisionDescription = false,
        )

        assertIs<PostTextGenerationResult.Success>(result)
        assertEquals(1, provider.visionCalls)
        assertEquals("Recorded vision.", repository.get(draftId)?.visionDescription?.description)
    }

    @Test
    fun backwardClockLeavesUpdatedAtUnchanged() {
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription())
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = FakeAiProvider(),
            clock = MutableClock(1_699_999_999_000L),
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val success = assertIs<PostTextGenerationResult.Success>(result)
        assertEquals(1_700_000_000_000L, success.draft.updatedAt.toEpochMilliseconds())
    }

    @Test
    fun savesTextGenerationWithoutDroppingRowsAddedDuringProviderCalls() {
        val repository = FakeManualWorkflowRepository(sampleDraftWithVisionDescription())
        val provider = RecordingAiProvider(
            onCaption = {
                repository.saveCaptionResult(existingCaptionResult("caption-result-concurrent"))
                repository.savePromptHistoryEntry(existingPromptHistory("prompt-concurrent"))
                repository.get(draftId)?.let {
                    repository.save(
                        it.copy(
                            status = DraftStatus.PhotoEdited,
                            updatedAt = Instant.fromEpochMilliseconds(1_700_000_080_000L),
                        ),
                    )
                }
            },
        )
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = provider,
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        assertIs<PostTextGenerationResult.Success>(result)
        val stored = assertNotNull(repository.get(draftId))
        assertEquals(DraftStatus.PhotoEdited, stored.status)
        assertTrue(stored.captionResults.any { it.id.value == "caption-result-concurrent" })
        assertTrue(stored.promptHistory.any { it.id.value == "prompt-concurrent" })
        assertEquals(2, stored.captionResults.size)
        assertEquals(3, stored.promptHistory.size)
    }

    @Test
    fun usesVisionAssetForAltTextWhenSelectionChangesMidExecution() {
        val editedMediaAsset = MediaAsset(
            id = MediaAssetId("media-edited-mid"),
            type = MediaType.EditedPhoto,
            uri = "file://photo-edited-mid.jpg",
            mimeType = "image/jpeg",
            widthPx = 1080,
            heightPx = 1350,
            createdAtEpochMillis = 1_700_000_030_000L,
        )
        val draft = sampleDraft().copy(
            status = DraftStatus.PhotoEdited,
            selectedMediaAssetId = mediaAssetId,
            photoEditResults = listOf(
                PhotoEditResult(
                    id = PhotoEditResultId("photo-edit-result-mid"),
                    requestId = PhotoEditRequestId("photo-edit-request-mid"),
                    draftId = draftId,
                    editedMediaAsset = editedMediaAsset,
                    summary = "Mid-execution edit.",
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_030_000L,
                ),
            ),
        )
        val repository = object : FakeManualWorkflowRepository(draft) {
            private var getCount2 = 0
            override fun get(id: PostDraftId): PostDraft? {
                getCount2++
                val fetched = super.get(id) ?: return null
                if (getCount2 == 2) {
                    return fetched.copy(selectedMediaAssetId = editedMediaAsset.id)
                }
                return fetched
            }
        }
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = FakeAiProvider(),
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val success = assertIs<PostTextGenerationResult.Success>(result)
        assertEquals(mediaAssetId, success.visionDescription.mediaAssetId)
        assertEquals(mediaAssetId, success.altTextResult.mediaAssetId,
            "Alt-text asset must match vision asset even when selection changes mid-execution")
    }

    @Test
    fun usesEditedAssetForVisionAndAltTextWhenSelected() {
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
        val pipeline = pipeline(
            repository = repository,
            settingsRepository = apiKeySettings(),
            aiProvider = FakeAiProvider(),
        )

        val result = pipeline.generateText(draftId, TargetPlatform.InstagramFeedSquare)

        val success = assertIs<PostTextGenerationResult.Success>(result)
        assertEquals(editedMediaAssetId, success.visionDescription.mediaAssetId)
        assertEquals(editedMediaAssetId, success.altTextResult.mediaAssetId)
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

    private fun existingCaptionResult(id: String): CaptionResult =
        CaptionResult(
            id = CaptionResultId(id),
            requestId = CaptionRequestId("caption-request-existing"),
            draftId = draftId,
            targetPlatform = TargetPlatform.InstagramFeedSquare,
            caption = "Concurrent caption.",
            shortCaption = null,
            hashtags = emptyList(),
            modelName = "existing-model",
            createdAtEpochMillis = 1_700_000_075_000L,
        )

    private fun existingPromptHistory(id: String): PromptHistoryEntry =
        PromptHistoryEntry(
            id = PromptHistoryEntryId(id),
            draftId = draftId,
            operationType = AiOperationType.CaptionGeneration,
            prompt = "Concurrent prompt.",
            responseSummary = "Concurrent caption.",
            modelName = "existing-model",
            createdAtEpochMillis = 1_700_000_075_000L,
        )

    private class RecordingAiProvider(
        private val shortCaption: String = "Recorded short caption.",
        private val failureStage: FailureStage? = null,
        private val onCaption: () -> Unit = {},
    ) : AiProvider {
        enum class FailureStage {
            Vision,
            Caption,
            AltText,
        }

        var totalCalls = 0
        var visionCalls = 0

        override fun describeVision(request: VisionDescriptionRequest): AiProviderResult<VisionDescriptionOutput> {
            totalCalls += 1
            visionCalls += 1
            if (failureStage == FailureStage.Vision) {
                return AiProviderResult.Failure(AiError.ProviderFailure(statusCode = null))
            }
            return AiProviderResult.Success(VisionDescriptionOutput("Recorded vision.", "recording-model"))
        }

        override fun generateCaption(request: CaptionGenerationRequest): AiProviderResult<CaptionGenerationOutput> {
            totalCalls += 1
            onCaption()
            if (failureStage == FailureStage.Caption) {
                return AiProviderResult.Failure(AiError.ProviderFailure(statusCode = null))
            }
            return AiProviderResult.Success(
                CaptionGenerationOutput(
                    caption = "Recorded caption.",
                    shortCaption = shortCaption,
                    hashtags = listOf("#recorded"),
                    modelName = "recording-model",
                ),
            )
        }

        override fun generateAltText(request: AltTextGenerationRequest): AiProviderResult<AltTextGenerationOutput> {
            totalCalls += 1
            if (failureStage == FailureStage.AltText) {
                return AiProviderResult.Failure(AiError.ProviderFailure(statusCode = null))
            }
            return AiProviderResult.Success(AltTextGenerationOutput("Recorded alt text.", "recording-model"))
        }

        override fun editPhoto(request: PhotoEditGenerationRequest): AiProviderResult<PhotoEditOutput> =
            error("Not used")
    }

    private class FakeManualWorkflowRepository(initialDraft: PostDraft) : ManualWorkflowRepository {
        private val drafts = mutableMapOf(initialDraft.id to initialDraft)
        private val mediaAssets = mutableMapOf<MediaAssetId, MediaAsset>()
        var deleteBeforeDraftGetNumber: Int? = null
        private var draftGetCount = 0

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

        override fun get(id: PostDraftId): PostDraft? {
            draftGetCount += 1
            if (deleteBeforeDraftGetNumber == draftGetCount) {
                drafts.remove(id)
            }
            return drafts[id]
        }
        override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean = false
        override fun updateUpdatedAt(id: PostDraftId, updatedAt: Instant): Boolean {
            val draft = drafts[id] ?: return false
            drafts[id] = draft.copy(updatedAt = updatedAt)
            return true
        }
        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean = false

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

        override fun save(exportRecord: ExportRecord) = saveExportRecord(exportRecord)
        override fun get(id: ExportRecordId): ExportRecord? = null
        override fun listExportRecordsForDraft(id: PostDraftId): List<ExportRecord> = emptyList()
        override fun saveExportRecord(exportRecord: ExportRecord) = Unit
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
            mediaAssets[editedMediaAsset.id] = editedMediaAsset
            drafts[draftId] = draft.copy(
                status = targetStatus,
                updatedAt = updatedAt,
                selectedMediaAssetId = editedMediaAsset.id,
                photoEditRequests = draft.photoEditRequests + editRequest,
                photoEditResults = draft.photoEditResults + editResult,
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
}
