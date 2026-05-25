package com.digitumdei.shotquill.shared.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualWorkflowModelsTest {
    private val createdAt = 1_700_000_000_000L
    private val updatedAt = 1_700_000_060_000L
    private val draftId = PostDraftId("draft-1")
    private val mediaAssetId = MediaAssetId("media-1")
    private val brandProfileId = BrandProfileId("brand-1")
    private val captionRequestId = CaptionRequestId("caption-request-1")
    private val photoEditRequestId = PhotoEditRequestId("photo-edit-request-1")

    @Test
    fun createsMediaAsset() {
        val asset = sampleMediaAsset()

        assertEquals(mediaAssetId, asset.id)
        assertEquals(MediaType.Photo, asset.type)
        assertEquals("file://photo.jpg", asset.uri)
    }

    @Test
    fun createsBrandProfile() {
        val profile = sampleBrandProfile()

        assertEquals(brandProfileId, profile.id)
        assertEquals("ShotQuill", profile.displayName)
        assertEquals(listOf("#photo", "#launch"), profile.defaultHashtags)
    }

    @Test
    fun createsVisionDescription() {
        val description = VisionDescription(
            id = VisionDescriptionId("vision-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            description = "A coffee cup on a desk near a notebook.",
            modelName = "vision-model",
            createdAtEpochMillis = createdAt,
        )

        assertEquals(draftId, description.draftId)
        assertEquals(mediaAssetId, description.mediaAssetId)
        assertEquals("vision-model", description.modelName)
    }

    @Test
    fun createsCaptionRequest() {
        val request = sampleCaptionRequest()

        assertEquals(captionRequestId, request.id)
        assertEquals(TargetPlatform.Instagram, request.targetPlatform)
        assertEquals(brandProfileId, request.brandProfileId)
    }

    @Test
    fun createsCaptionResult() {
        val result = CaptionResult(
            id = CaptionResultId("caption-result-1"),
            requestId = captionRequestId,
            draftId = draftId,
            targetPlatform = TargetPlatform.Instagram,
            caption = "Morning focus, freshly brewed.",
            hashtags = listOf("#coffee", "#work"),
            modelName = "caption-model",
            createdAtEpochMillis = createdAt,
        )

        assertEquals(captionRequestId, result.requestId)
        assertEquals("Morning focus, freshly brewed.", result.caption)
        assertEquals(listOf("#coffee", "#work"), result.hashtags)
    }

    @Test
    fun createsAltTextResult() {
        val result = AltTextResult(
            id = AltTextResultId("alt-text-1"),
            draftId = draftId,
            mediaAssetId = mediaAssetId,
            altText = "Coffee cup beside an open notebook.",
            modelName = "alt-text-model",
            createdAtEpochMillis = createdAt,
        )

        assertEquals(draftId, result.draftId)
        assertEquals(mediaAssetId, result.mediaAssetId)
        assertEquals("Coffee cup beside an open notebook.", result.altText)
    }

    @Test
    fun createsPhotoEditRequest() {
        val request = samplePhotoEditRequest()

        assertEquals(photoEditRequestId, request.id)
        assertEquals(EditIntent.ColorCorrect, request.intent)
        assertEquals(QualityTier.High, request.qualityTier)
    }

    @Test
    fun createsPhotoEditResult() {
        val result = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-1"),
            requestId = photoEditRequestId,
            draftId = draftId,
            editedMediaAsset = sampleMediaAsset().copy(
                id = MediaAssetId("media-2"),
                type = MediaType.EditedPhoto,
                uri = "file://photo-edited.jpg",
            ),
            summary = "Adjusted brightness and contrast.",
            modelName = "edit-model",
            createdAtEpochMillis = createdAt,
        )

        assertEquals(photoEditRequestId, result.requestId)
        assertEquals(MediaType.EditedPhoto, result.editedMediaAsset.type)
        assertEquals("Adjusted brightness and contrast.", result.summary)
    }

    @Test
    fun createsPromptHistoryEntry() {
        val entry = PromptHistoryEntry(
            id = PromptHistoryEntryId("prompt-1"),
            draftId = draftId,
            operationType = AiOperationType.CaptionGeneration,
            prompt = "Write a concise caption.",
            responseSummary = "Generated one caption.",
            modelName = "caption-model",
            createdAtEpochMillis = createdAt,
        )

        assertEquals(AiOperationType.CaptionGeneration, entry.operationType)
        assertEquals("Write a concise caption.", entry.prompt)
    }

    @Test
    fun createsExportRecord() {
        val record = ExportRecord(
            id = ExportRecordId("export-1"),
            draftId = draftId,
            targetPlatform = TargetPlatform.LinkedIn,
            status = ExportStatus.Exported,
            destinationUri = "content://share/export-1",
            errorMessage = null,
            createdAtEpochMillis = createdAt,
            completedAtEpochMillis = updatedAt,
        )

        assertEquals(TargetPlatform.LinkedIn, record.targetPlatform)
        assertEquals(ExportStatus.Exported, record.status)
        assertEquals(updatedAt, record.completedAtEpochMillis)
    }

    @Test
    fun createsPostDraftAsManualWorkflowRoot() {
        val draft = samplePostDraft()

        assertEquals(draftId, draft.id)
        assertEquals(PostFormat.SingleImage, draft.format)
        assertEquals(DraftStatus.PhotoAdded, draft.status)
        assertEquals(mediaAssetId, draft.mediaItems.single().mediaAsset.id)
        assertEquals(0, draft.mediaItems.single().order)
        assertEquals("Morning focus, freshly brewed.", draft.caption?.text)
        assertEquals(setOf(TargetPlatform.Instagram, TargetPlatform.LinkedIn), draft.targetPlatforms)
        assertTrue(draft.captionRequests.isNotEmpty())
        assertTrue(draft.photoEditRequests.isNotEmpty())
    }

    @Test
    fun createsStoryPostDraftWithMultipleMediaItems() {
        val story = samplePostDraft().copy(
            format = PostFormat.Story,
            mediaItems = listOf(
                PostMediaItem(mediaAsset = sampleMediaAsset(), order = 0),
                PostMediaItem(
                    mediaAsset = sampleMediaAsset().copy(
                        id = MediaAssetId("media-2"),
                        uri = "file://photo-2.jpg",
                    ),
                    order = 1,
                ),
            ),
        )

        assertEquals(PostFormat.Story, story.format)
        assertEquals(listOf(0, 1), story.mediaItems.map { it.order })
    }

    @Test
    fun returnsPrimaryMediaAssetByLowestMediaItemOrder() {
        val expectedPrimary = sampleMediaAsset().copy(
            id = MediaAssetId("media-2"),
            uri = "file://photo-2.jpg",
        )
        val story = samplePostDraft().copy(
            format = PostFormat.Story,
            mediaItems = listOf(
                PostMediaItem(mediaAsset = sampleMediaAsset(), order = 1),
                PostMediaItem(mediaAsset = expectedPrimary, order = 0),
            ),
        )

        assertEquals(expectedPrimary, story.primaryMediaAsset())
    }

    @Test
    fun rejectsSingleImagePostDraftWithMultipleMediaItems() {
        val failure = assertFailsWith<IllegalArgumentException> {
            samplePostDraft().copy(
                mediaItems = listOf(
                    PostMediaItem(mediaAsset = sampleMediaAsset(), order = 0),
                    PostMediaItem(
                        mediaAsset = sampleMediaAsset().copy(id = MediaAssetId("media-2")),
                        order = 1,
                    ),
                ),
            )
        }

        assertEquals("Single image post drafts must include exactly one media item", failure.message)
    }

    @Test
    fun rejectsPostDraftWithDuplicateMediaItemOrder() {
        val failure = assertFailsWith<IllegalArgumentException> {
            samplePostDraft().copy(
                format = PostFormat.Carousel,
                mediaItems = listOf(
                    PostMediaItem(mediaAsset = sampleMediaAsset(), order = 0),
                    PostMediaItem(
                        mediaAsset = sampleMediaAsset().copy(id = MediaAssetId("media-2")),
                        order = 0,
                    ),
                ),
            )
        }

        assertEquals("Post draft media item orders must be unique", failure.message)
    }

    @Test
    fun mapsEnumsToAndFromWireValues() {
        assertEquals(DraftStatus.ReadyToShare, DraftStatus.fromWireValue("ready_to_share"))
        assertEquals(TargetPlatform.Threads, TargetPlatform.fromWireValue("threads"))
        assertEquals(MediaType.EditedPhoto, MediaType.fromWireValue("edited_photo"))
        assertEquals(EditIntent.RemoveBackground, EditIntent.fromWireValue("remove_background"))
        assertEquals(RealismLevel.Polished, RealismLevel.fromWireValue("polished"))
        assertEquals(QualityTier.Standard, QualityTier.fromWireValue("standard"))
        assertEquals(AiOperationType.AltTextGeneration, AiOperationType.fromWireValue("alt_text_generation"))
        assertEquals(ExportStatus.Cancelled, ExportStatus.fromWireValue("cancelled"))
    }

    @Test
    fun returnsNullForUnknownWireValues() {
        assertEquals(null, DraftStatus.fromWireValue("unknown"))
        assertEquals(null, TargetPlatform.fromWireValue("unknown"))
        assertEquals(null, MediaType.fromWireValue("unknown"))
        assertEquals(null, EditIntent.fromWireValue("unknown"))
        assertEquals(null, RealismLevel.fromWireValue("unknown"))
        assertEquals(null, QualityTier.fromWireValue("unknown"))
        assertEquals(null, AiOperationType.fromWireValue("unknown"))
        assertEquals(null, ExportStatus.fromWireValue("unknown"))
    }

    @Test
    fun validatesDraftStatusTransitions() {
        assertTrue(DraftStatus.Draft.canTransitionTo(DraftStatus.PhotoAdded))
        assertTrue(DraftStatus.PhotoAdded.canTransitionTo(DraftStatus.TextGenerated))
        assertTrue(DraftStatus.ReadyToShare.canTransitionTo(DraftStatus.Shared))
        assertFalse(DraftStatus.Draft.canTransitionTo(DraftStatus.Shared))
        assertFalse(DraftStatus.Archived.canTransitionTo(DraftStatus.PhotoAdded))
    }

    @Test
    fun transitionsPostDraftWhenAllowed() {
        val transitioned = samplePostDraft().transitionTo(
            next = DraftStatus.TextGenerated,
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
        )

        assertEquals(DraftStatus.TextGenerated, transitioned.status)
        assertEquals(Instant.fromEpochMilliseconds(updatedAt), transitioned.updatedAt)
    }

    @Test
    fun rejectsInvalidPostDraftTransition() {
        val failure = assertFailsWith<IllegalArgumentException> {
            samplePostDraft().transitionTo(
                next = DraftStatus.Shared,
                updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            )
        }

        assertEquals("Cannot transition post draft from photo_added to shared", failure.message)
    }

    private fun samplePostDraft(): PostDraft = PostDraft(
        id = draftId,
        format = PostFormat.SingleImage,
        status = DraftStatus.PhotoAdded,
        mediaItems = listOf(PostMediaItem(mediaAsset = sampleMediaAsset(), order = 0)),
        caption = CaptionDraft(
            text = "Morning focus, freshly brewed.",
            hashtags = listOf("#coffee", "#work"),
        ),
        targetPlatforms = setOf(TargetPlatform.Instagram, TargetPlatform.LinkedIn),
        brandProfile = sampleBrandProfile(),
        visionDescription = null,
        captionRequests = listOf(sampleCaptionRequest()),
        captionResults = emptyList(),
        altTextResults = emptyList(),
        photoEditRequests = listOf(samplePhotoEditRequest()),
        photoEditResults = emptyList(),
        promptHistory = emptyList(),
        exportRecords = emptyList(),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun sampleMediaAsset(): MediaAsset = MediaAsset(
        id = mediaAssetId,
        type = MediaType.Photo,
        uri = "file://photo.jpg",
        mimeType = "image/jpeg",
        widthPx = 1080,
        heightPx = 1080,
        createdAtEpochMillis = createdAt,
    )

    private fun sampleBrandProfile(): BrandProfile = BrandProfile(
        id = brandProfileId,
        displayName = "ShotQuill",
        voice = "Warm and concise",
        audience = "Independent creators",
        defaultHashtags = listOf("#photo", "#launch"),
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = updatedAt,
    )

    private fun sampleCaptionRequest(): CaptionRequest = CaptionRequest(
        id = captionRequestId,
        draftId = draftId,
        targetPlatform = TargetPlatform.Instagram,
        prompt = "Write a caption for this image.",
        tone = "Friendly",
        brandProfileId = brandProfileId,
        createdAtEpochMillis = createdAt,
    )

    private fun samplePhotoEditRequest(): PhotoEditRequest = PhotoEditRequest(
        id = photoEditRequestId,
        draftId = draftId,
        sourceMediaAssetId = mediaAssetId,
        intent = EditIntent.ColorCorrect,
        realismLevel = RealismLevel.Natural,
        qualityTier = QualityTier.High,
        prompt = "Make the image brighter while keeping it realistic.",
        createdAtEpochMillis = createdAt,
    )
}
