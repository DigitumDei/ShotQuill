package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.ai.AiError
import com.digitumdei.shotquill.shared.ai.AiImageInput
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.AiOperationType
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
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
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
        val saver = PhotoEditMediaSaver { _, _, _, _ -> SaveEditedImageResult.Success(newAsset) }
        val result = saver.save(
            bytes = byteArrayOf(0, 1, 2),
            mimeType = "image/jpeg",
            originalMediaAsset = original,
            createdAtEpochMillis = baseEpoch + 1000,
        )
        val success = assertIs<SaveEditedImageResult.Success>(result)
        assertEquals(MediaType.EditedPhoto, success.mediaAsset.type)
        assertEquals("file://edited/result.jpg", success.mediaAsset.uri)
    }

    @Test
    fun `PhotoEditMediaSaver save returns Failure with message`() {
        val saver = PhotoEditMediaSaver { _, _, _, _ -> SaveEditedImageResult.Failure("Disk full") }
        val result = saver.save(
            bytes = byteArrayOf(0),
            mimeType = "image/jpeg",
            originalMediaAsset = sampleMediaAsset(),
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

    private fun sampleMediaAsset(): MediaAsset = MediaAsset(
        id = mediaAssetId,
        type = MediaType.Photo,
        uri = "file://test/photo.jpg",
        mimeType = "image/jpeg",
        widthPx = 1920,
        heightPx = 1080,
        createdAtEpochMillis = baseEpoch,
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
        editedMediaAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-result"),
            type = MediaType.EditedPhoto,
            uri = "file://edited/output.jpg",
            createdAtEpochMillis = baseEpoch + 2000,
        ),
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
}