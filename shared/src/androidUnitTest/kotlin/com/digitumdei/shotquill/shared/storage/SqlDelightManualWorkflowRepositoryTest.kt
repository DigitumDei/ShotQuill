package com.digitumdei.shotquill.shared.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.digitumdei.shotquill.shared.db.ShotQuillDatabase
import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.AltTextResultId
import com.digitumdei.shotquill.shared.domain.BrandImageAsset
import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.CaptionRequest
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import com.digitumdei.shotquill.shared.domain.ExportStatus
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
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.VisionDescriptionId
import com.digitumdei.shotquill.shared.settings.SecretRedactor
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightManualWorkflowRepositoryTest {
    private val createdAt = 1_700_000_000_000L
    private val updatedAt = 1_700_000_060_000L

    @Test
    fun createsDatabase() {
        val driver = inMemoryDriver()

        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(sampleMediaAsset())

        assertNotNull(repository.get(MediaAssetId("media-1")))
        driver.close()
    }

    @Test
    fun insertsReadsAndUpdatesMediaAsset() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(sampleMediaAsset())
        repository.save(sampleMediaAsset().copy(widthPx = 2048, heightPx = 1536))

        val stored = repository.get(MediaAssetId("media-1"))
        assertEquals(2048, stored?.widthPx)
        assertEquals(1536, stored?.heightPx)
        driver.close()
    }

    @Test
    fun insertsReadsAndUpdatesBrandProfile() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(sampleBrandProfile())
        repository.save(
            sampleBrandProfile().copy(
                displayName = "ShotQuill Studio",
                defaultHashtags = listOf("#studio"),
                websiteOrSocialLinks = listOf("https://shotquill.example"),
                visualStyleNotes = "Use clean product-forward compositions.",
                productNamingNotes = "Preserve product names exactly.",
                imageAssets = listOf(sampleBrandImageAsset().copy(title = "Updated logo")),
            ),
        )

        val stored = repository.get(BrandProfileId("brand-1"))
        assertEquals("ShotQuill Studio", stored?.displayName)
        assertEquals(listOf("#studio"), stored?.defaultHashtags)
        assertEquals(listOf("https://shotquill.example"), stored?.websiteOrSocialLinks)
        assertEquals("Use clean product-forward compositions.", stored?.visualStyleNotes)
        assertEquals("Preserve product names exactly.", stored?.productNamingNotes)
        assertEquals("Updated logo", stored?.imageAssets?.single()?.title)
        driver.close()
    }

    @Test
    fun insertsReadsAndUpdatesPostDraft() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(samplePostDraft())
        repository.save(
            samplePostDraft().copy(
                status = DraftStatus.ReadyToShare,
                updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            ),
        )

        val stored = repository.get(PostDraftId("draft-1"))
        assertEquals(DraftStatus.ReadyToShare, stored?.status)
        assertEquals(Instant.fromEpochMilliseconds(updatedAt), stored?.updatedAt)
        assertEquals(setOf(TargetPlatform.InstagramFeedSquare, TargetPlatform.BlueskyPost), stored?.targetPlatforms)
        driver.close()
    }

    @Test
    fun readsPostDraftRelationships() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(samplePostDraft())

        val stored = repository.get(PostDraftId("draft-1"))
        assertNotNull(stored)
        assertEquals(MediaAssetId("media-1"), stored.mediaItems.single().mediaAsset.id)
        assertEquals(listOf("#coffee", "#work"), stored.caption?.hashtags)
        assertEquals(BrandProfileId("brand-1"), stored.brandProfile?.id)
        assertEquals(VisionDescriptionId("vision-1"), stored.visionDescription?.id)
        assertEquals(CaptionRequestId("caption-request-1"), stored.captionRequests.single().id)
        assertEquals(CaptionResultId("caption-result-1"), stored.captionResults.single().id)
        assertEquals(listOf("#coffee", "#work"), stored.captionResults.single().hashtags)
        assertEquals(AltTextResultId("alt-text-1"), stored.altTextResults.single().id)
        assertEquals(PhotoEditRequestId("photo-edit-request-1"), stored.photoEditRequests.single().id)
        assertEquals(PhotoEditResultId("photo-edit-result-1"), stored.photoEditResults.single().id)
        assertEquals(MediaAssetId("media-edited-1"), stored.photoEditResults.single().editedMediaAsset.id)
        assertEquals(PromptHistoryEntryId("prompt-1"), stored.promptHistory.single().id)
        assertEquals(ExportRecordId("export-1"), stored.exportRecords.single().id)
        driver.close()
    }

    @Test
    fun repositoryInterfacesLoadSavedRecordsIndividually() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(samplePostDraft())

        assertEquals(VisionDescriptionId("vision-1"), repository.get(VisionDescriptionId("vision-1"))?.id)
        assertEquals(listOf(VisionDescriptionId("vision-1")), repository.listVisionDescriptionsForDraft(PostDraftId("draft-1")).map { it.id })
        assertEquals(CaptionRequestId("caption-request-1"), repository.getCaptionRequest(CaptionRequestId("caption-request-1"))?.id)
        assertEquals(listOf(CaptionRequestId("caption-request-1")), repository.listCaptionRequestsForDraft(PostDraftId("draft-1")).map { it.id })
        assertEquals(CaptionResultId("caption-result-1"), repository.getCaptionResult(CaptionResultId("caption-result-1"))?.id)
        assertEquals(listOf(CaptionResultId("caption-result-1")), repository.listCaptionResultsForDraft(PostDraftId("draft-1")).map { it.id })
        assertEquals(AltTextResultId("alt-text-1"), repository.get(AltTextResultId("alt-text-1"))?.id)
        assertEquals(listOf(AltTextResultId("alt-text-1")), repository.listAltTextResultsForDraft(PostDraftId("draft-1")).map { it.id })
        assertEquals(PhotoEditRequestId("photo-edit-request-1"), repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-request-1"))?.id)
        assertEquals(listOf(PhotoEditRequestId("photo-edit-request-1")), repository.listPhotoEditRequestsForDraft(PostDraftId("draft-1")).map { it.id })
        assertEquals(PhotoEditResultId("photo-edit-result-1"), repository.getPhotoEditResult(PhotoEditResultId("photo-edit-result-1"))?.id)
        assertEquals(listOf(PhotoEditResultId("photo-edit-result-1")), repository.listPhotoEditResultsForDraft(PostDraftId("draft-1")).map { it.id })
        assertEquals(PromptHistoryEntryId("prompt-1"), repository.get(PromptHistoryEntryId("prompt-1"))?.id)
        assertEquals(listOf(PromptHistoryEntryId("prompt-1")), repository.listPromptHistoryForDraft(PostDraftId("draft-1")).map { it.id })
        assertEquals(ExportRecordId("export-1"), repository.get(ExportRecordId("export-1"))?.id)
        assertEquals(listOf(ExportRecordId("export-1")), repository.listExportRecordsForDraft(PostDraftId("draft-1")).map { it.id })
        driver.close()
    }

    @Test
    fun returnsNullOrEmptyForMissingRecords() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        assertNull(repository.get(MediaAssetId("missing-media")))
        assertNull(repository.get(BrandProfileId("missing-brand")))
        assertNull(repository.get(PostDraftId("missing-draft")))
        assertNull(repository.get(VisionDescriptionId("missing-vision")))
        assertNull(repository.getCaptionRequest(CaptionRequestId("missing-caption-request")))
        assertNull(repository.getCaptionResult(CaptionResultId("missing-caption-result")))
        assertNull(repository.get(AltTextResultId("missing-alt-text")))
        assertNull(repository.getPhotoEditRequest(PhotoEditRequestId("missing-photo-edit-request")))
        assertNull(repository.getPhotoEditResult(PhotoEditResultId("missing-photo-edit-result")))
        assertNull(repository.get(PromptHistoryEntryId("missing-prompt")))
        assertNull(repository.get(ExportRecordId("missing-export")))
        assertEquals(emptyList(), repository.listVisionDescriptionsForDraft(PostDraftId("missing-draft")))
        assertEquals(emptyList(), repository.listCaptionRequestsForDraft(PostDraftId("missing-draft")))
        assertEquals(emptyList(), repository.listCaptionResultsForDraft(PostDraftId("missing-draft")))
        assertEquals(emptyList(), repository.listAltTextResultsForDraft(PostDraftId("missing-draft")))
        assertEquals(emptyList(), repository.listPhotoEditRequestsForDraft(PostDraftId("missing-draft")))
        assertEquals(emptyList(), repository.listPhotoEditResultsForDraft(PostDraftId("missing-draft")))
        assertEquals(emptyList(), repository.listPromptHistoryForDraft(PostDraftId("missing-draft")))
        assertEquals(emptyList(), repository.listExportRecordsForDraft(PostDraftId("missing-draft")))
        driver.close()
    }

    @Test
    fun updatesDraftStatusAndLinkedMediaIds() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        val secondMedia = sampleMediaAsset().copy(id = MediaAssetId("media-2"), uri = "file://photo-2.jpg")

        repository.save(samplePostDraft().copy(format = PostFormat.Carousel))
        repository.save(secondMedia)

        assertTrue(
            repository.updateStatus(
                id = PostDraftId("draft-1"),
                status = DraftStatus.TextGenerated,
                updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            ),
        )
        assertTrue(repository.replaceMediaItems(PostDraftId("draft-1"), listOf(MediaAssetId("media-2"), MediaAssetId("media-1"))))
        assertFalse(repository.updateStatus(PostDraftId("missing-draft"), DraftStatus.TextGenerated, Instant.fromEpochMilliseconds(updatedAt)))
        assertFalse(repository.replaceMediaItems(PostDraftId("draft-1"), listOf(MediaAssetId("missing-media"))))
        assertFalse(repository.replaceMediaItems(PostDraftId("draft-1"), listOf(MediaAssetId("media-1"), MediaAssetId("media-1"))))

        val stored = repository.get(PostDraftId("draft-1"))
        assertEquals(DraftStatus.TextGenerated, stored?.status)
        assertEquals(listOf(MediaAssetId("media-2"), MediaAssetId("media-1")), stored?.mediaItems?.map { it.mediaAsset.id })
        driver.close()
    }

    @Test
    fun mapsDatabaseRowsToDomainModelsExplicitly() {
        val mappedMedia = ManualWorkflowStorageMapper.mediaAsset(
            id = "media-1",
            type = "photo",
            uri = "file://photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 1080L,
            heightPx = 1080L,
            createdAt = createdAt,
        )
        val mappedCaptionRequest = ManualWorkflowStorageMapper.captionRequest(
            requestId = "caption-request-1",
            draftId = "draft-1",
            targetPlatform = "instagram_feed_square",
            prompt = "Write a caption.",
            tone = "Warm",
            brandProfileId = "brand-1",
            createdAt = createdAt,
        )
        val mappedVision = ManualWorkflowStorageMapper.visionDescription(
            visionId = "vision-1",
            draftId = "draft-1",
            mediaAssetId = "media-1",
            description = "A coffee cup on a desk near a notebook.",
            modelName = "vision-model",
            createdAt = createdAt,
        )
        val mappedCaptionResult = ManualWorkflowStorageMapper.captionResult(
            resultId = "caption-result-1",
            requestId = "caption-request-1",
            draftId = "draft-1",
            targetPlatform = "instagram_feed_square",
            caption = "Morning focus, freshly brewed.",
            hashtags = listOf("#coffee", "#work"),
            modelName = "caption-model",
            createdAt = createdAt,
        )
        val mappedAltText = ManualWorkflowStorageMapper.altTextResult(
            resultId = "alt-text-1",
            draftId = "draft-1",
            mediaAssetId = "media-1",
            altText = "Coffee cup beside an open notebook.",
            modelName = "alt-text-model",
            createdAt = createdAt,
        )
        val mappedPhotoEditRequest = ManualWorkflowStorageMapper.photoEditRequest(
            requestId = "photo-edit-request-1",
            draftId = "draft-1",
            sourceMediaAssetId = "media-1",
            intent = "color_correct",
            realismLevel = "photoreal",
            qualityTier = "high",
            prompt = "Make the image brighter while keeping it realistic.",
            createdAt = createdAt,
        )
        val mappedPhotoEditResult = ManualWorkflowStorageMapper.photoEditResult(
            resultId = "photo-edit-result-1",
            requestId = "photo-edit-request-1",
            draftId = "draft-1",
            editedMediaAssetId = "media-edited-1",
            summary = "Adjusted brightness and contrast.",
            modelName = "edit-model",
            createdAt = updatedAt,
            type = "edited_photo",
            uri = "file://photo-edited.jpg",
            mimeType = "image/jpeg",
            widthPx = 1080L,
            heightPx = 1080L,
            mediaCreatedAt = createdAt,
        )
        val mappedPrompt = ManualWorkflowStorageMapper.promptHistoryEntry(
            entryId = "prompt-1",
            draftId = "draft-1",
            operationType = "caption_generation",
            prompt = "Write a concise caption with sk-live_abcdefghi123456.",
            responseSummary = "Generated with sk-live_abcdefghi123456.",
            modelName = "caption-model",
            createdAt = createdAt,
        )
        val mappedExport = ManualWorkflowStorageMapper.exportRecord(
            recordId = "export-1",
            draftId = "draft-1",
            targetPlatform = "bluesky_post",
            status = "exported",
            destinationUri = "content://export",
            errorMessage = "Export failed after sk-live_abcdefghi123456.",
            createdAt = createdAt,
            completedAt = updatedAt,
        )

        assertEquals(sampleMediaAsset(), mappedMedia)
        assertEquals(CaptionRequestId("caption-request-1"), mappedCaptionRequest.id)
        assertEquals(TargetPlatform.InstagramFeedSquare, mappedCaptionRequest.targetPlatform)
        assertEquals(VisionDescriptionId("vision-1"), mappedVision.id)
        assertEquals(sampleCaptionResult(), mappedCaptionResult)
        assertEquals(AltTextResultId("alt-text-1"), mappedAltText.id)
        assertEquals(samplePhotoEditRequest(), mappedPhotoEditRequest)
        assertEquals(PhotoEditResultId("photo-edit-result-1"), mappedPhotoEditResult.id)
        assertEquals(MediaType.EditedPhoto, mappedPhotoEditResult.editedMediaAsset.type)
        assertEquals(PromptHistoryEntryId("prompt-1"), mappedPrompt.id)
        assertEquals(AiOperationType.CaptionGeneration, mappedPrompt.operationType)
        assertEquals("Write a concise caption with ${SecretRedactor.Redacted}.", mappedPrompt.prompt)
        assertEquals("Generated with ${SecretRedactor.Redacted}.", mappedPrompt.responseSummary)
        assertEquals(ExportStatus.Exported, mappedExport.status)
        assertEquals(TargetPlatform.BlueskyPost, mappedExport.targetPlatform)
        assertEquals("Export failed after ${SecretRedactor.Redacted}.", mappedExport.errorMessage)
    }

    @Test
    fun updatesDraftOwnedRecordsWithIndividualSaves() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(samplePostDraft())
        repository.saveVisionDescription(
            VisionDescription(
                id = VisionDescriptionId("vision-1"),
                draftId = PostDraftId("draft-1"),
                mediaAssetId = MediaAssetId("media-1"),
                description = "Updated desk scene.",
                modelName = "vision-model-v2",
                createdAtEpochMillis = updatedAt,
            ),
        )
        repository.saveCaptionRequest(
            sampleCaptionRequest().copy(
                prompt = "Write a sharper caption.",
                tone = "Direct",
            ),
        )
        repository.saveCaptionResult(
            sampleCaptionResult().copy(
                caption = "Brewed focus for the morning.",
                hashtags = listOf("#focus"),
                modelName = "caption-model-v2",
            ),
        )
        repository.saveAltTextResult(
            AltTextResult(
                id = AltTextResultId("alt-text-1"),
                draftId = PostDraftId("draft-1"),
                mediaAssetId = MediaAssetId("media-1"),
                altText = "Updated alt text.",
                modelName = "alt-text-model-v2",
                createdAtEpochMillis = updatedAt,
            ),
        )
        repository.savePhotoEditRequest(
            samplePhotoEditRequest().copy(
                prompt = "Warm the image.",
                qualityTier = QualityTier.Standard,
            ),
        )
        repository.savePhotoEditResult(
            PhotoEditResult(
                id = PhotoEditResultId("photo-edit-result-1"),
                requestId = PhotoEditRequestId("photo-edit-request-1"),
                draftId = PostDraftId("draft-1"),
                editedMediaAsset = sampleMediaAsset().copy(
                    id = MediaAssetId("media-edited-1"),
                    type = MediaType.EditedPhoto,
                    uri = "file://photo-edited-v2.jpg",
                ),
                summary = "Warmer edit.",
                modelName = "edit-model-v2",
                createdAtEpochMillis = updatedAt,
            ),
        )
        repository.savePromptHistoryEntry(
            PromptHistoryEntry(
                id = PromptHistoryEntryId("prompt-1"),
                draftId = PostDraftId("draft-1"),
                operationType = AiOperationType.PhotoEdit,
                prompt = "Warm the photo.",
                responseSummary = "Updated edit prompt.",
                modelName = "edit-model-v2",
                createdAtEpochMillis = updatedAt,
            ),
        )
        repository.saveExportRecord(
            ExportRecord(
                id = ExportRecordId("export-1"),
                draftId = PostDraftId("draft-1"),
                targetPlatform = TargetPlatform.BlueskyPost,
                status = ExportStatus.Failed,
                destinationUri = null,
                errorMessage = "Share target unavailable.",
                createdAtEpochMillis = createdAt,
                completedAtEpochMillis = updatedAt,
            ),
        )

        val stored = repository.get(PostDraftId("draft-1"))
        assertNotNull(stored)
        assertEquals("Updated desk scene.", stored.visionDescription?.description)
        assertEquals("Write a sharper caption.", stored.captionRequests.single().prompt)
        assertEquals("Brewed focus for the morning.", stored.captionResults.single().caption)
        assertEquals(listOf("#focus"), stored.captionResults.single().hashtags)
        assertEquals("Updated alt text.", stored.altTextResults.single().altText)
        assertEquals(QualityTier.Standard, stored.photoEditRequests.single().qualityTier)
        assertEquals("Warmer edit.", stored.photoEditResults.single().summary)
        assertEquals("file://photo-edited-v2.jpg", stored.photoEditResults.single().editedMediaAsset.uri)
        assertEquals(AiOperationType.PhotoEdit, stored.promptHistory.single().operationType)
        assertEquals(ExportStatus.Failed, stored.exportRecords.single().status)
        driver.close()
    }

    @Test
    fun redactsOpenAiApiKeysWhenSavingPromptHistoryAndExportErrors() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(samplePostDraft())
        repository.savePromptHistoryEntry(
            PromptHistoryEntry(
                id = PromptHistoryEntryId("prompt-1"),
                draftId = PostDraftId("draft-1"),
                operationType = AiOperationType.CaptionGeneration,
                prompt = "Use sk-live_abcdefghi123456 in prompt.",
                responseSummary = "Model returned sk-live_abcdefghi123456.",
                modelName = "caption-model",
                createdAtEpochMillis = createdAt,
            ),
        )
        repository.saveExportRecord(
            ExportRecord(
                id = ExportRecordId("export-1"),
                draftId = PostDraftId("draft-1"),
                targetPlatform = TargetPlatform.BlueskyPost,
                status = ExportStatus.Failed,
                destinationUri = null,
                errorMessage = "Failed with sk-live_abcdefghi123456.",
                createdAtEpochMillis = createdAt,
                completedAtEpochMillis = null,
            ),
        )

        val storedPrompt = repository.get(PromptHistoryEntryId("prompt-1"))
        val storedExport = repository.get(ExportRecordId("export-1"))
        assertEquals("Use ${SecretRedactor.Redacted} in prompt.", storedPrompt?.prompt)
        assertEquals("Model returned ${SecretRedactor.Redacted}.", storedPrompt?.responseSummary)
        assertEquals("Failed with ${SecretRedactor.Redacted}.", storedExport?.errorMessage)
        driver.close()
    }

    @Test
    fun hasMigrationScaffoldForVersionOne() {
        assertEquals(1, ShotQuillDatabase.Schema.version.toInt())
    }

    private fun inMemoryDriver(): JdbcSqliteDriver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            ShotQuillDatabase.Schema.create(it)
        }

    private fun samplePostDraft(): PostDraft = PostDraft(
        id = PostDraftId("draft-1"),
        format = PostFormat.SingleImage,
        status = DraftStatus.PhotoAdded,
        mediaItems = listOf(PostMediaItem(mediaAsset = sampleMediaAsset(), order = 0)),
        caption = CaptionDraft(
            text = "Morning focus, freshly brewed.",
            hashtags = listOf("#coffee", "#work"),
        ),
        targetPlatforms = setOf(TargetPlatform.InstagramFeedSquare, TargetPlatform.BlueskyPost),
        brandProfile = sampleBrandProfile(),
        visionDescription = VisionDescription(
            id = VisionDescriptionId("vision-1"),
            draftId = PostDraftId("draft-1"),
            mediaAssetId = MediaAssetId("media-1"),
            description = "A coffee cup on a desk near a notebook.",
            modelName = "vision-model",
            createdAtEpochMillis = createdAt,
        ),
        captionRequests = listOf(sampleCaptionRequest()),
        captionResults = listOf(sampleCaptionResult()),
        altTextResults = listOf(
            AltTextResult(
                id = AltTextResultId("alt-text-1"),
                draftId = PostDraftId("draft-1"),
                mediaAssetId = MediaAssetId("media-1"),
                altText = "Coffee cup beside an open notebook.",
                modelName = "alt-text-model",
                createdAtEpochMillis = createdAt,
            ),
        ),
        photoEditRequests = listOf(samplePhotoEditRequest()),
        photoEditResults = listOf(
            PhotoEditResult(
                id = PhotoEditResultId("photo-edit-result-1"),
                requestId = PhotoEditRequestId("photo-edit-request-1"),
                draftId = PostDraftId("draft-1"),
                editedMediaAsset = sampleMediaAsset().copy(
                    id = MediaAssetId("media-edited-1"),
                    type = MediaType.EditedPhoto,
                    uri = "file://photo-edited.jpg",
                ),
                summary = "Adjusted brightness and contrast.",
                modelName = "edit-model",
                createdAtEpochMillis = updatedAt,
            ),
        ),
        promptHistory = listOf(
            PromptHistoryEntry(
                id = PromptHistoryEntryId("prompt-1"),
                draftId = PostDraftId("draft-1"),
                operationType = AiOperationType.CaptionGeneration,
                prompt = "Write a concise caption.",
                responseSummary = "Generated one caption.",
                modelName = "caption-model",
                createdAtEpochMillis = createdAt,
            ),
        ),
        exportRecords = listOf(
            ExportRecord(
                id = ExportRecordId("export-1"),
                draftId = PostDraftId("draft-1"),
                targetPlatform = TargetPlatform.BlueskyPost,
                status = ExportStatus.Exported,
                destinationUri = "content://share/export-1",
                errorMessage = null,
                createdAtEpochMillis = createdAt,
                completedAtEpochMillis = updatedAt,
            ),
        ),
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun sampleMediaAsset(): MediaAsset = MediaAsset(
        id = MediaAssetId("media-1"),
        type = MediaType.Photo,
        uri = "file://photo.jpg",
        mimeType = "image/jpeg",
        widthPx = 1080,
        heightPx = 1080,
        createdAtEpochMillis = createdAt,
    )

    private fun sampleBrandProfile(): BrandProfile = BrandProfile(
        id = BrandProfileId("brand-1"),
        displayName = "ShotQuill",
        voice = "Warm and concise",
        audience = "Independent creators",
        defaultHashtags = listOf("#photo", "#launch"),
        websiteOrSocialLinks = listOf("https://shotquill.example"),
        visualStyleNotes = "Bright, realistic product photography.",
        productNamingNotes = "Keep beer and product names unchanged.",
        imageAssets = listOf(sampleBrandImageAsset()),
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = updatedAt,
    )

    private fun sampleBrandImageAsset(): BrandImageAsset = BrandImageAsset(
        mediaAsset = sampleMediaAsset(),
        title = "Primary logo",
        description = "Logo to include when branding generated post imagery.",
    )

    private fun sampleCaptionRequest(): CaptionRequest = CaptionRequest(
        id = CaptionRequestId("caption-request-1"),
        draftId = PostDraftId("draft-1"),
        targetPlatform = TargetPlatform.InstagramFeedSquare,
        prompt = "Write a caption for this image.",
        tone = "Friendly",
        brandProfileId = BrandProfileId("brand-1"),
        createdAtEpochMillis = createdAt,
    )

    private fun sampleCaptionResult(): CaptionResult = CaptionResult(
        id = CaptionResultId("caption-result-1"),
        requestId = CaptionRequestId("caption-request-1"),
        draftId = PostDraftId("draft-1"),
        targetPlatform = TargetPlatform.InstagramFeedSquare,
        caption = "Morning focus, freshly brewed.",
        hashtags = listOf("#coffee", "#work"),
        modelName = "caption-model",
        createdAtEpochMillis = createdAt,
    )

    private fun samplePhotoEditRequest(): PhotoEditRequest = PhotoEditRequest(
        id = PhotoEditRequestId("photo-edit-request-1"),
        draftId = PostDraftId("draft-1"),
        sourceMediaAssetId = MediaAssetId("media-1"),
        intent = EditIntent.ColorCorrect,
        realismLevel = RealismLevel.Photoreal,
        qualityTier = QualityTier.High,
        prompt = "Make the image brighter while keeping it realistic.",
        createdAtEpochMillis = createdAt,
    )
}
