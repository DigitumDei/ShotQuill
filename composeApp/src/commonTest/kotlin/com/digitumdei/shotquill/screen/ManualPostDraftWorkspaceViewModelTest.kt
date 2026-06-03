package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.AltTextResultId
import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.ExportStatus
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PhotoEditPromptAssembler
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
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.VisionDescriptionId
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.workflow.PostTextGenerationResult
import com.digitumdei.shotquill.shared.workflow.PostTextGenerator
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)

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
            clock = FixedClock(1_700_000_090_000L),
        )
        viewModel.load()

        viewModel.analyzeVisionDescription()

        val stored = repository.get(draftId)
        assertEquals(
            "Photo shows photo.jpg prepared for social content.",
            viewModel.state.visionDescription,
        )
        assertEquals(viewModel.state.visionDescription, stored?.visionDescription?.description)
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
                visionDescription = VisionDescription(
                    id = VisionDescriptionId("vision-description-1"),
                    draftId = draftId,
                    mediaAssetId = mediaAssetId,
                    description = "Cached workspace description.",
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_080_000L,
                ),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_090_000L),
        )
        viewModel.load()

        viewModel.analyzeVisionDescription()

        assertEquals("Cached workspace description.", viewModel.state.visionDescription)
        assertEquals("Reused cached vision description", viewModel.state.statusMessage)
        assertEquals(emptyList(), viewModel.state.promptHistory)
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
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = IncrementingClock(1_700_000_500_000L),
        )
        viewModel.load()

        viewModel.generatePostText()
        viewModel.editPhotoWithAi()

        val stored = repository.get(draftId)
        assertEquals(DraftStatus.PhotoEdited, stored?.status)
        assertEquals("Ready for instagram_feed_square: photo.jpg", viewModel.state.generatedCaption)
        assertEquals("file://photo.jpg#edited-1700000500001", viewModel.state.editedPhotoUri)
        assertEquals(3, viewModel.state.promptHistory.size)
        val photoEditEntry = viewModel.state.promptHistory.last()
        assertEquals(AiOperationType.PhotoEdit, photoEditEntry.operationType)
        val photoEditRequest = stored?.photoEditRequests?.last()
        assertEquals(
            PhotoEditPromptAssembler.assemble(photoEditRequest!!),
            photoEditEntry.prompt,
        )
    }

    @Test
    fun rejectsLegacyDraftStatusWithoutThrowing() {
        val repository = FakePostDraftRepository(sampleDraft().copy(status = DraftStatus.Draft))
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
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
        val request = stored?.photoEditRequests?.single()
        assertEquals(TargetPlatform.InstagramFeedSquare, request?.targetPlatform)
        assertEquals(RealismLevel.Photoreal, request?.realismLevel)
        assertEquals(QualityTier.Standard, request?.qualityTier)
        assertEquals(null, request?.subjectDescription)
        assertEquals(null, request?.userRefinement)
        assertEquals(null, request?.maskRegion)
        val expectedPrompt = PhotoEditPromptAssembler.assemble(request!!)
        assertEquals(expectedPrompt, viewModel.state.promptHistory.single().prompt)
        assertEquals("Edited photo preview created", viewModel.state.statusMessage)
    }

    @Test
    fun usesPreferredTargetPlatformAndVisionContextWhenEditingPhotoWithAi() {
        val repository = FakePostDraftRepository(
            sampleDraft().copy(
                targetPlatforms = setOf(TargetPlatform.BlueskyPost),
                visionDescription = VisionDescription(
                    id = VisionDescriptionId("vision-description-1"),
                    draftId = draftId,
                    mediaAssetId = mediaAssetId,
                    description = "A coffee cup on a wooden table.",
                    modelName = "fake",
                    createdAtEpochMillis = 1_700_000_010_000L,
                ),
            ),
        )
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        val request = repository.get(draftId)?.photoEditRequests?.single()
        assertEquals(TargetPlatform.BlueskyPost, request?.targetPlatform)
        assertEquals("A coffee cup on a wooden table.", request?.subjectDescription)
        assertEquals(RealismLevel.Photoreal, request?.realismLevel)
        assertEquals(QualityTier.Standard, request?.qualityTier)
        assertEquals(null, request?.userRefinement)
        assertEquals(null, request?.maskRegion)
        assertEquals(
            PhotoEditPromptAssembler.assemble(request!!),
            viewModel.state.promptHistory.single().prompt,
        )
    }

    @Test
    fun usesConfiguredRealismAndQualityDefaultsWhenEditingPhotoWithAi() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_200_000L),
            defaultRealismLevel = RealismLevel.Polished,
            defaultQualityTier = QualityTier.High,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        val request = repository.get(draftId)?.photoEditRequests?.single()
        assertEquals(RealismLevel.Polished, request?.realismLevel)
        assertEquals(QualityTier.High, request?.qualityTier)
        assertEquals(TargetPlatform.InstagramFeedSquare, request?.targetPlatform)
        assertEquals(
            PhotoEditPromptAssembler.assemble(request!!),
            viewModel.state.promptHistory.single().prompt,
        )
    }

    @Test
    fun rejectsTerminalDraftMutationsWithoutThrowing() {
        listOf(DraftStatus.Archived, DraftStatus.Shared).forEach { terminalStatus ->
            val repository = FakePostDraftRepository(sampleDraftWithGeneratedText().copy(status = terminalStatus))
            val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
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
    fun handlesDraftDisappearingBeforeActions() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
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
                    maskRegion = null,
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

    private class FakePostDraftRepository(initialDraft: PostDraft? = null) : PostDraftRepository {
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

        override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean = false
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
        assertEquals(null, form.unsupportedModelWarning)
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
            maskRegion = null,
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
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.updatePhotoEditIntent(EditIntent.RemoveObject)
        viewModel.updatePhotoEditRefinement("Remove the cup")
        viewModel.updatePhotoEditRealism(RealismLevel.Polished)
        viewModel.updatePhotoEditTargetPlatform(TargetPlatform.FacebookPost)
        viewModel.updatePhotoEditQualityTier(QualityTier.High)
        viewModel.editPhotoWithAi()

        val request = repository.get(draftId)?.photoEditRequests?.single()
        assertEquals(EditIntent.RemoveObject, request?.intent)
        assertEquals(RealismLevel.Polished, request?.realismLevel)
        assertEquals(QualityTier.High, request?.qualityTier)
        assertEquals("Remove the cup", request?.userRefinement)
        assertEquals(TargetPlatform.FacebookPost, request?.targetPlatform)
    }

    @Test
    fun usesPhotoEditFormDefaultsWhenRefinementIsBlank() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        val request = repository.get(draftId)?.photoEditRequests?.single()
        assertEquals(null, request?.userRefinement)
    }

    @Test
    fun preservesRawRefinementInMemoryButTrimsInStoredRequest() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            clock = FixedClock(1_700_000_200_000L),
        )
        viewModel.load()

        viewModel.updatePhotoEditRefinement("  Remove the cup  ")

        assertEquals("  Remove the cup  ", viewModel.state.photoEditForm.userRefinementText)

        viewModel.editPhotoWithAi()

        val request = repository.get(draftId)?.photoEditRequests?.single()
        assertEquals("Remove the cup", request?.userRefinement)
    }

    @Test
    fun preservesErrorOperationStateWhenAiProviderThrows() {
        val repository = FakePostDraftRepository(sampleDraft())
        val throwingProvider = object : ManualDraftAiProvider {
            override fun analyzeVision(draft: PostDraft, nowEpochMillis: Long): GeneratedVisionDescription =
                error("unused")
            override fun generatePostText(draft: PostDraft, targetPlatform: TargetPlatform, nowEpochMillis: Long): GeneratedPostText =
                error("unused")
            override fun editPhoto(draft: PostDraft, formState: PhotoEditFormState, nowEpochMillis: Long): GeneratedPhotoEdit =
                throw RuntimeException("Provider failure")
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            aiProvider = throwingProvider,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Photo edit failed: Provider failure", viewModel.state.statusMessage)
    }

    @Test
    fun exposesLoadingStateWhileEditingPhoto() {
        val repository = FakePostDraftRepository(sampleDraft())
        val capturedStates = mutableListOf<PhotoEditFormOperationState>()
        val viewModelRef = arrayOfNulls<ManualPostDraftWorkspaceViewModel>(1)
        val capturingProvider = object : ManualDraftAiProvider {
            override fun analyzeVision(draft: PostDraft, nowEpochMillis: Long): GeneratedVisionDescription =
                error("unused")
            override fun generatePostText(draft: PostDraft, targetPlatform: TargetPlatform, nowEpochMillis: Long): GeneratedPostText =
                error("unused")
            override fun editPhoto(draft: PostDraft, formState: PhotoEditFormState, nowEpochMillis: Long): GeneratedPhotoEdit {
                capturedStates.add(viewModelRef[0]!!.state.photoEditForm.operationState)
                return FakeManualDraftAiProvider().editPhoto(draft, formState, nowEpochMillis)
            }
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            aiProvider = capturingProvider,
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
    fun exposesErrorOperationStateWhenDraftMissingDuringEdit() {
        val repository = FakePostDraftRepository(sampleDraft())
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
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
        val viewModel = ManualPostDraftWorkspaceViewModel(draftId, repository)
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Cannot edit photo while status is draft", viewModel.state.statusMessage)
        assertFalse(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun exposesErrorOperationStateWhenProviderReturnsBlankPrompt() {
        val repository = FakePostDraftRepository(sampleDraft())
        val blankPromptProvider = object : ManualDraftAiProvider {
            override fun analyzeVision(draft: PostDraft, nowEpochMillis: Long): GeneratedVisionDescription =
                error("unused")
            override fun generatePostText(draft: PostDraft, targetPlatform: TargetPlatform, nowEpochMillis: Long): GeneratedPostText =
                error("unused")
            override fun editPhoto(draft: PostDraft, formState: PhotoEditFormState, nowEpochMillis: Long): GeneratedPhotoEdit =
                GeneratedPhotoEdit(
                    editedMediaUri = "file://edited.jpg",
                    prompt = "",
                    summary = "",
                    modelName = "fake",
                )
        }
        val viewModel = ManualPostDraftWorkspaceViewModel(
            draftId = draftId,
            postDraftRepository = repository,
            aiProvider = blankPromptProvider,
        )
        viewModel.load()

        viewModel.editPhotoWithAi()

        assertEquals(PhotoEditFormOperationState.Error, viewModel.state.photoEditForm.operationState)
        assertEquals("Photo edit failed: prompt must not be blank", viewModel.state.statusMessage)
        assertTrue(viewModel.state.actions.canEditPhotoWithAi)
    }

    @Test
    fun fakeEditPhotoEncodesFormValuesInOutput() {
        val provider = FakeManualDraftAiProvider()
        val draft = sampleDraft()
        val formState = PhotoEditFormState(
            selectedIntent = EditIntent.BackgroundAdjustment,
            userRefinementText = "Make it warmer",
            selectedRealismLevel = RealismLevel.Polished,
            selectedTargetPlatform = TargetPlatform.BlueskyPost,
            selectedQualityTier = QualityTier.High,
            qualityTierModelNotes = QualityTier.High.modelMappingNote,
            qualityTierCostNotes = QualityTier.High.costNote,
            unsupportedModelWarning = null,
            latestRequestId = null,
            latestResultId = null,
            latestModelName = null,
            latestSummary = null,
            operationState = PhotoEditFormOperationState.Idle,
        )
        val result = provider.editPhoto(draft, formState, 1_700_000_200_000L)

        assertTrue(result.editedMediaUri.contains("background_adjustment"))
        assertTrue(result.editedMediaUri.contains("bluesky_post"))
        assertTrue(result.summary.contains("intent=background_adjustment"))
        assertTrue(result.summary.contains("platform=bluesky_post"))
        assertTrue(result.summary.contains("realism=polished"))
        assertTrue(result.summary.contains("quality=high"))
        assertTrue(result.summary.contains("refinement=Make it warmer"))
        assertTrue(result.prompt.contains("background_adjustment"))
        assertTrue(result.prompt.contains("bluesky_post"))
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
}
