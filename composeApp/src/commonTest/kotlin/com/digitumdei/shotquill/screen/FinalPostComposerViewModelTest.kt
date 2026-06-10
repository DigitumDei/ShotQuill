package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.share.PostShareLauncher
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
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
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
            targetPlatform = TargetPlatform.TikTok,
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
            assertEquals(TargetPlatform.TikTok, targetPlatform)
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
            assertEquals("Generated short", shortCaption)
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

    private fun createViewModel(
        repository: ManualWorkflowRepository,
        clipboardWriter: ClipboardWriter = ClipboardWriter { _, _ -> },
        postShareLauncher: PostShareLauncher = PostShareLauncher { _, _ -> true },
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

    private class FixedClock(private val now: Long) : EpochClock {
        override fun nowMillis(): Long = now
    }
}
