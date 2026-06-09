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
import com.digitumdei.shotquill.shared.domain.MaskBounds
import com.digitumdei.shotquill.shared.domain.MaskRegion
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
import kotlin.test.assertFailsWith
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
    fun captionResultPersistsNullShortCaption() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(samplePostDraft().copy(captionResults = emptyList()))
        repository.saveCaptionResult(sampleCaptionResult().copy(shortCaption = null))

        assertNull(repository.getCaptionResult(CaptionResultId("caption-result-1"))?.shortCaption)
        assertNull(repository.listCaptionResultsForDraft(PostDraftId("draft-1")).single().shortCaption)
        driver.close()
    }

    @Test
    fun recordsPostTextGenerationWithoutDeletingExistingHistoryRows() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(samplePostDraft())
        val recorded = repository.recordPostTextGeneration(
            draftId = PostDraftId("draft-1"),
            status = DraftStatus.TextGenerated,
            caption = CaptionDraft("New caption.", listOf("#new")),
            targetPlatform = TargetPlatform.BlueskyPost,
            brandProfile = null,
            captionRequest = sampleCaptionRequest().copy(id = CaptionRequestId("caption-request-2")),
            captionResult = sampleCaptionResult().copy(
                id = CaptionResultId("caption-result-2"),
                requestId = CaptionRequestId("caption-request-2"),
                caption = "New caption.",
                shortCaption = null,
            ),
            altTextResult = sampleAltTextResult().copy(id = AltTextResultId("alt-text-2")),
            promptHistoryEntries = listOf(samplePromptHistoryEntry().copy(id = PromptHistoryEntryId("prompt-2"))),
            updatedAt = Instant.fromEpochMilliseconds(updatedAt + 1_000L),
        )

        assertNotNull(recorded)
        val stored = repository.get(PostDraftId("draft-1"))
        assertEquals("New caption.", stored?.caption?.text)
        assertTrue(stored?.targetPlatforms?.contains(TargetPlatform.BlueskyPost) == true)
        assertEquals(2, stored?.captionResults?.size)
        assertEquals(2, stored?.altTextResults?.size)
        assertEquals(2, stored?.promptHistory?.size)
        driver.close()
    }

    @Test
    fun savePhotoEditFailureReturnsNullAndRollsBackForMissingDraft() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        val editRequest = samplePhotoEditRequest().copy(id = PhotoEditRequestId("photo-edit-failure-1"))
        val promptHistoryEntry = samplePromptHistoryEntry().copy(id = PromptHistoryEntryId("prompt-failure-1"))
        val timestamp = Instant.fromEpochMilliseconds(updatedAt)

        val result = repository.savePhotoEditFailure(
            draftId = PostDraftId("nonexistent-draft"),
            editRequest = editRequest,
            promptHistoryEntry = promptHistoryEntry,
            updatedAt = timestamp,
        )

        assertNull(result)
        assertNull(repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-failure-1")))
        assertNull(repository.get(PromptHistoryEntryId("prompt-failure-1")))
        driver.close()
    }

    @Test
    fun savePhotoEditFailurePersistsForExistingDraft() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())

        val editRequest = samplePhotoEditRequest().copy(id = PhotoEditRequestId("photo-edit-failure-2"))
        val promptHistoryEntry = samplePromptHistoryEntry().copy(id = PromptHistoryEntryId("prompt-failure-2"))
        val timestamp = Instant.fromEpochMilliseconds(updatedAt)

        val result = repository.savePhotoEditFailure(
            draftId = PostDraftId("draft-1"),
            editRequest = editRequest,
            promptHistoryEntry = promptHistoryEntry,
            updatedAt = timestamp,
        )

        assertNotNull(result)
        assertEquals(timestamp, result?.updatedAt)
        assertNotNull(repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-failure-2")))
        assertNotNull(repository.get(PromptHistoryEntryId("prompt-failure-2")))
        driver.close()
    }

    @Test
    fun savePhotoEditSuccessReturnsNullAndRollsBackForMissingDraft() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        val editedAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-new"),
            type = MediaType.EditedPhoto,
            uri = "file://edited-new.jpg",
        )
        val editRequest = samplePhotoEditRequest().copy(id = PhotoEditRequestId("photo-edit-success-missing"))
        val editResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-success-missing"),
            requestId = editRequest.id,
            draftId = PostDraftId("nonexistent-draft"),
            editedMediaAsset = editedAsset,
            summary = "Should not persist.",
            modelName = "edit-model",
            createdAtEpochMillis = updatedAt,
        )
        val promptHistoryEntry = samplePromptHistoryEntry().copy(id = PromptHistoryEntryId("prompt-success-missing"))
        val timestamp = Instant.fromEpochMilliseconds(updatedAt)

        val result = repository.savePhotoEditSuccess(
            draftId = PostDraftId("nonexistent-draft"),
            editedMediaAsset = editedAsset,
            editRequest = editRequest,
            editResult = editResult,
            promptHistoryEntry = promptHistoryEntry,
            targetStatus = DraftStatus.PhotoEdited,
            updatedAt = timestamp,
        )

        assertNull(result)
        assertNull(repository.get(MediaAssetId("media-edited-new")))
        assertNull(repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-success-missing")))
        assertNull(repository.getPhotoEditResult(PhotoEditResultId("photo-edit-result-success-missing")))
        assertNull(repository.get(PromptHistoryEntryId("prompt-success-missing")))
        driver.close()
    }

    @Test
    fun savePhotoEditSuccessRollsBackPartialWritesOnForeignKeyViolation() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        ShotQuillDatabase.Schema.create(driver)
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())

        val draftId = PostDraftId("draft-1")
        val originalDraft = repository.get(draftId)!!
        val originalStatus = originalDraft.status
        val originalUpdatedAt = originalDraft.updatedAt
        val originalSelectedMediaAssetId = originalDraft.selectedMediaAssetId

        val editedAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-rollback"),
            type = MediaType.EditedPhoto,
            uri = "file://edited-rollback.jpg",
        )
        val editRequest = samplePhotoEditRequest().copy(
            id = PhotoEditRequestId("photo-edit-req-rollback"),
            draftId = draftId,
        )
        val editResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-res-rollback"),
            requestId = editRequest.id,
            draftId = draftId,
            editedMediaAsset = editedAsset,
            summary = "Should be rolled back.",
            modelName = "edit-model",
            createdAtEpochMillis = updatedAt,
        )
        val promptHistoryEntry = samplePromptHistoryEntry().copy(
            id = PromptHistoryEntryId("prompt-rollback"),
            draftId = PostDraftId("missing-draft"),
            operationType = AiOperationType.PhotoEdit,
        )
        val timestamp = Instant.fromEpochMilliseconds(updatedAt + 10_000)

        val exception = assertFailsWith<Exception> {
            repository.savePhotoEditSuccess(
                draftId = draftId,
                editedMediaAsset = editedAsset,
                editRequest = editRequest,
                editResult = editResult,
                promptHistoryEntry = promptHistoryEntry,
                targetStatus = DraftStatus.PhotoEdited,
                updatedAt = timestamp,
            )
        }
        assertTrue(exception.message?.contains("FOREIGN KEY") == true)

        assertNull(repository.get(MediaAssetId("media-edited-rollback")))
        assertNull(repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-req-rollback")))
        assertNull(repository.getPhotoEditResult(PhotoEditResultId("photo-edit-res-rollback")))
        assertNull(repository.get(PromptHistoryEntryId("prompt-rollback")))

        val stored = repository.get(draftId)
        assertNotNull(stored)
        assertEquals(originalStatus, stored.status)
        assertEquals(originalUpdatedAt, stored.updatedAt)
        assertEquals(originalSelectedMediaAssetId, stored.selectedMediaAssetId)

        driver.close()
    }

    @Test
    fun savePhotoEditSuccessPersistsAllRecordsAndReturnsRefreshedDraft() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())

        val editedAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-success"),
            type = MediaType.EditedPhoto,
            uri = "file://edited-success.jpg",
        )
        val editRequest = samplePhotoEditRequest().copy(
            id = PhotoEditRequestId("photo-edit-request-success"),
            draftId = PostDraftId("draft-1"),
        )
        val editResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-success"),
            requestId = editRequest.id,
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            summary = "Successful edit.",
            modelName = "edit-model-plus",
            createdAtEpochMillis = updatedAt,
        )
        val promptHistoryEntry = samplePromptHistoryEntry().copy(
            id = PromptHistoryEntryId("prompt-success"),
            draftId = PostDraftId("draft-1"),
            operationType = AiOperationType.PhotoEdit,
        )
        val timestamp = Instant.fromEpochMilliseconds(updatedAt)

        val result = repository.savePhotoEditSuccess(
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            editRequest = editRequest,
            editResult = editResult,
            promptHistoryEntry = promptHistoryEntry,
            targetStatus = DraftStatus.PhotoEdited,
            updatedAt = timestamp,
        )

        assertNotNull(result)
        assertEquals(DraftStatus.PhotoEdited, result?.status)
        assertEquals(timestamp, result?.updatedAt)
        assertEquals(MediaAssetId("media-edited-success"), result?.selectedMediaAssetId)

        val storedAsset = repository.get(MediaAssetId("media-edited-success"))
        assertNotNull(storedAsset)
        assertEquals(MediaType.EditedPhoto, storedAsset.type)
        assertEquals("file://edited-success.jpg", storedAsset.uri)

        val storedRequest = repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-request-success"))
        assertNotNull(storedRequest)
        assertEquals(editRequest.id, storedRequest.id)

        val storedResult = repository.getPhotoEditResult(PhotoEditResultId("photo-edit-result-success"))
        assertNotNull(storedResult)
        assertEquals("Successful edit.", storedResult.summary)

        val storedPrompt = repository.get(PromptHistoryEntryId("prompt-success"))
        assertNotNull(storedPrompt)
        assertEquals(AiOperationType.PhotoEdit, storedPrompt.operationType)

        val storedDraft = repository.get(PostDraftId("draft-1"))
        assertNotNull(storedDraft)
        assertEquals(MediaAssetId("media-edited-success"), storedDraft.selectedMediaAssetId)
        assertTrue(storedDraft.photoEditResults.any { it.id == PhotoEditResultId("photo-edit-result-success") })
        assertTrue(storedDraft.photoEditRequests.any { it.id == PhotoEditRequestId("photo-edit-request-success") })
        assertTrue(storedDraft.promptHistory.any { it.id == PromptHistoryEntryId("prompt-success") })
        driver.close()
    }

    @Test
    fun savePhotoEditSuccessTransitionsStatusAndSetsUpdatedAtWhenDifferent() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft().copy(status = DraftStatus.PhotoAdded, selectedMediaAssetId = null))

        val editedAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-transition"),
            type = MediaType.EditedPhoto,
            uri = "file://edited-transition.jpg",
        )
        val editRequest = samplePhotoEditRequest().copy(
            id = PhotoEditRequestId("photo-edit-req-transition"),
            draftId = PostDraftId("draft-1"),
        )
        val editResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-res-transition"),
            requestId = editRequest.id,
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            summary = "Transition edit.",
            modelName = "edit-model",
            createdAtEpochMillis = updatedAt,
        )
        val promptHistoryEntry = samplePromptHistoryEntry().copy(
            id = PromptHistoryEntryId("prompt-transition"),
            draftId = PostDraftId("draft-1"),
            operationType = AiOperationType.PhotoEdit,
        )
        val timestamp = Instant.fromEpochMilliseconds(updatedAt + 10_000)

        val result = repository.savePhotoEditSuccess(
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            editRequest = editRequest,
            editResult = editResult,
            promptHistoryEntry = promptHistoryEntry,
            targetStatus = DraftStatus.PhotoEdited,
            updatedAt = timestamp,
        )

        assertNotNull(result)
        assertEquals(DraftStatus.PhotoEdited, result?.status)
        assertEquals(timestamp, result?.updatedAt)

        val stored = repository.get(PostDraftId("draft-1"))
        assertEquals(DraftStatus.PhotoEdited, stored?.status)
        assertEquals(timestamp, stored?.updatedAt)
        driver.close()
    }

    @Test
    fun savePhotoEditSuccessKeepsStatusWhenTargetMatchesCurrent() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft().copy(status = DraftStatus.PhotoEdited, selectedMediaAssetId = null))

        val editedAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-same-status"),
            type = MediaType.EditedPhoto,
            uri = "file://edited-same.jpg",
        )
        val editRequest = samplePhotoEditRequest().copy(
            id = PhotoEditRequestId("photo-edit-req-same"),
            draftId = PostDraftId("draft-1"),
        )
        val editResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-res-same"),
            requestId = editRequest.id,
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            summary = "Same status edit.",
            modelName = "edit-model",
            createdAtEpochMillis = updatedAt,
        )
        val promptHistoryEntry = samplePromptHistoryEntry().copy(
            id = PromptHistoryEntryId("prompt-same"),
            draftId = PostDraftId("draft-1"),
            operationType = AiOperationType.PhotoEdit,
        )
        val timestamp = Instant.fromEpochMilliseconds(updatedAt + 10_000)

        val result = repository.savePhotoEditSuccess(
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            editRequest = editRequest,
            editResult = editResult,
            promptHistoryEntry = promptHistoryEntry,
            targetStatus = DraftStatus.PhotoEdited,
            updatedAt = timestamp,
        )

        assertNotNull(result)
        assertEquals(DraftStatus.PhotoEdited, result?.status)
        assertEquals(timestamp, result?.updatedAt)

        val stored = repository.get(PostDraftId("draft-1"))
        assertEquals(DraftStatus.PhotoEdited, stored?.status)
        assertEquals(timestamp, stored?.updatedAt)
        driver.close()
    }

    @Test
    fun savePhotoEditSuccessSetsSelectedMediaAssetIdToEditedAsset() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft().copy(selectedMediaAssetId = null))

        val editedAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-selection"),
            type = MediaType.EditedPhoto,
            uri = "file://edited-selection.jpg",
        )
        val editRequest = samplePhotoEditRequest().copy(
            id = PhotoEditRequestId("photo-edit-req-selection"),
            draftId = PostDraftId("draft-1"),
        )
        val editResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-res-selection"),
            requestId = editRequest.id,
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            summary = "Selection edit.",
            modelName = "edit-model",
            createdAtEpochMillis = updatedAt,
        )
        val promptHistoryEntry = samplePromptHistoryEntry().copy(
            id = PromptHistoryEntryId("prompt-selection"),
            draftId = PostDraftId("draft-1"),
            operationType = AiOperationType.PhotoEdit,
        )
        val timestamp = Instant.fromEpochMilliseconds(updatedAt + 10_000)

        val result = repository.savePhotoEditSuccess(
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            editRequest = editRequest,
            editResult = editResult,
            promptHistoryEntry = promptHistoryEntry,
            targetStatus = DraftStatus.PhotoEdited,
            updatedAt = timestamp,
        )

        assertNotNull(result)
        assertEquals(MediaAssetId("media-edited-selection"), result?.selectedMediaAssetId)

        val stored = repository.get(PostDraftId("draft-1"))
        assertEquals(MediaAssetId("media-edited-selection"), stored?.selectedMediaAssetId)
        driver.close()
    }

    @Test
    fun savePhotoEditSuccessKeepsPreviousMediaAndHistoryWhenAppending() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft().copy(selectedMediaAssetId = null))

        val editedAsset = sampleMediaAsset().copy(
            id = MediaAssetId("media-edited-append"),
            type = MediaType.EditedPhoto,
            uri = "file://edited-append.jpg",
        )
        val editRequest = samplePhotoEditRequest().copy(
            id = PhotoEditRequestId("photo-edit-req-append"),
            draftId = PostDraftId("draft-1"),
        )
        val editResult = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-res-append"),
            requestId = editRequest.id,
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            summary = "Append edit.",
            modelName = "edit-model",
            createdAtEpochMillis = updatedAt,
        )
        val promptHistoryEntry = samplePromptHistoryEntry().copy(
            id = PromptHistoryEntryId("prompt-append"),
            draftId = PostDraftId("draft-1"),
            operationType = AiOperationType.PhotoEdit,
        )
        val timestamp = Instant.fromEpochMilliseconds(updatedAt + 10_000)

        val result = repository.savePhotoEditSuccess(
            draftId = PostDraftId("draft-1"),
            editedMediaAsset = editedAsset,
            editRequest = editRequest,
            editResult = editResult,
            promptHistoryEntry = promptHistoryEntry,
            targetStatus = DraftStatus.PhotoEdited,
            updatedAt = timestamp,
        )

        assertNotNull(result)
        val stored = repository.get(PostDraftId("draft-1"))
        assertEquals(2, stored?.photoEditRequests?.size)
        assertEquals(2, stored?.photoEditResults?.size)
        assertEquals(2, stored?.promptHistory?.size)
        assertEquals(1, stored?.mediaItems?.size)
        assertEquals(1, stored?.captionRequests?.size)
        assertEquals(1, stored?.captionResults?.size)
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
    fun createsPostDraftWithFreshMediaAsset() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        val now = 1_700_100_000_000L

        val mediaAsset = MediaAsset(
            id = MediaAssetId("captured-media-1"),
            type = MediaType.Photo,
            uri = "file://captured/photo.jpg",
            mimeType = "image/jpeg",
            widthPx = 4032,
            heightPx = 3024,
            createdAtEpochMillis = now,
        )
        repository.save(mediaAsset)

        val postDraft = PostDraft(
            id = PostDraftId("captured-draft-1"),
            format = PostFormat.SingleImage,
            status = DraftStatus.Draft,
            mediaItems = listOf(PostMediaItem(mediaAsset = mediaAsset, order = 0)),
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
            createdAt = Instant.fromEpochMilliseconds(now),
            updatedAt = Instant.fromEpochMilliseconds(now),
        )
        repository.save(postDraft)

        val stored = repository.get(PostDraftId("captured-draft-1"))
        assertNotNull(stored)
        assertEquals(DraftStatus.Draft, stored.status)
        assertEquals(PostFormat.SingleImage, stored.format)
        assertEquals(1, stored.mediaItems.size)
        assertEquals(MediaAssetId("captured-media-1"), stored.mediaItems[0].mediaAsset.id)
        assertEquals(0, stored.mediaItems[0].order)
        assertEquals("file://captured/photo.jpg", stored.mediaItems[0].mediaAsset.uri)
        assertEquals(MediaType.Photo, stored.mediaItems[0].mediaAsset.type)
        assertEquals("image/jpeg", stored.mediaItems[0].mediaAsset.mimeType)
        assertEquals(4032, stored.mediaItems[0].mediaAsset.widthPx)
        assertEquals(3024, stored.mediaItems[0].mediaAsset.heightPx)
        assertEquals(emptySet(), stored.targetPlatforms)
        assertEquals(null, stored.caption)
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
            shortCaption = "Freshly brewed focus.",
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
            intent = "improve_lighting",
            realismLevel = "photoreal",
            qualityTier = "high",
            prompt = "Make the image brighter while keeping it realistic.",
            userRefinement = "Focus on the coffee cup",
            subjectDescription = "A coffee cup on a wooden table",
            targetPlatform = "instagram_feed_square",
            maskRegion = null,
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
    fun clearsAllSavedWorkflowRecords() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)

        repository.save(samplePostDraft())
        repository.clearAll()

        assertNull(repository.get(MediaAssetId("media-1")))
        assertNull(repository.get(BrandProfileId("brand-1")))
        assertNull(repository.get(PostDraftId("draft-1")))
        assertNull(repository.getCaptionRequest(CaptionRequestId("caption-request-1")))
        assertNull(repository.getCaptionResult(CaptionResultId("caption-result-1")))
        assertNull(repository.get(AltTextResultId("alt-text-1")))
        assertNull(repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-request-1")))
        assertNull(repository.getPhotoEditResult(PhotoEditResultId("photo-edit-result-1")))
        assertNull(repository.get(PromptHistoryEntryId("prompt-1")))
        assertNull(repository.get(ExportRecordId("export-1")))
        driver.close()
    }

    @Test
    fun hasMigrationScaffoldForVersionTwo() {
        assertEquals(2, ShotQuillDatabase.Schema.version.toInt())
    }

    @Test
    fun v1ToV2MigrationAddsSelectedMediaAssetIdWithNullDefault() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)

        createV1Schema(driver)

        driver.execute(null, "INSERT INTO media_assets(id, type, uri, mime_type, width_px, height_px, created_at_epoch_millis) VALUES('media-1', 'Photo', 'file://photo.jpg', 'image/jpeg', 1080, 1080, 1700000000000)", 0)
        driver.execute(null, "INSERT INTO media_assets(id, type, uri, mime_type, width_px, height_px, created_at_epoch_millis) VALUES('media-2', 'Photo', 'file://photo2.jpg', 'image/jpeg', 1920, 1080, 1700000000000)", 0)
        driver.execute(null, "INSERT INTO post_drafts(id, format, status, caption_text, brand_profile_id, created_at_epoch_millis, updated_at_epoch_millis) VALUES('draft-1', 'SingleImage', 'PhotoAdded', NULL, NULL, 1700000000000, 1700000060000)", 0)
        driver.execute(null, "INSERT INTO post_drafts(id, format, status, caption_text, brand_profile_id, created_at_epoch_millis, updated_at_epoch_millis) VALUES('draft-2', 'SingleImage', 'Draft', 'existing caption', NULL, 1700000000000, 1700000060000)", 0)
        driver.execute(null, "INSERT INTO post_draft_media_items(draft_id, media_asset_id, media_order) VALUES('draft-1', 'media-1', 0)", 0)
        driver.execute(null, "INSERT INTO post_draft_target_platforms(draft_id, platform) VALUES('draft-1', 'InstagramFeedSquare')", 0)

        ShotQuillDatabase.Schema.migrate(driver, 1, 2)

        val repository = SqlDelightManualWorkflowRepository(driver)

        val draft1 = repository.get(PostDraftId("draft-1"))
        assertNotNull(draft1)
        assertEquals(PostFormat.SingleImage, draft1.format)
        assertEquals(DraftStatus.PhotoAdded, draft1.status)
        assertNull(draft1.caption)
        assertEquals(1, draft1.mediaItems.size)
        assertEquals(MediaAssetId("media-1"), draft1.mediaItems[0].mediaAsset.id)
        assertTrue(draft1.targetPlatforms.contains(TargetPlatform.InstagramFeedSquare))
        assertNull(draft1.selectedMediaAssetId)

        val draft2 = repository.get(PostDraftId("draft-2"))
        assertNotNull(draft2)
        assertEquals(PostFormat.SingleImage, draft2.format)
        assertEquals(DraftStatus.Draft, draft2.status)
        assertEquals("existing caption", draft2.caption?.text)
        assertNull(draft2.selectedMediaAssetId)

        repository.updateSelectedMediaAsset(
            id = PostDraftId("draft-1"),
            mediaAssetId = MediaAssetId("media-1"),
            updatedAt = Instant.fromEpochMilliseconds(1_700_000_070_000L),
        )
        val withSelection = repository.get(PostDraftId("draft-1"))
        assertEquals(MediaAssetId("media-1"), withSelection?.selectedMediaAssetId)

        driver.execute(null, "DELETE FROM media_assets WHERE id = 'media-1'", 0)
        val afterCascade = repository.get(PostDraftId("draft-1"))
        assertNull(afterCascade?.selectedMediaAssetId)

        driver.close()
    }

    @Test
    fun savesAndReadsPhotoEditRequestWithAllFieldsRoundTrip() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())

        val request = PhotoEditRequest(
            id = PhotoEditRequestId("photo-edit-request-1"),
            draftId = PostDraftId("draft-1"),
            sourceMediaAssetId = MediaAssetId("media-1"),
            intent = EditIntent.RemoveObject,
            realismLevel = RealismLevel.Photoreal,
            qualityTier = QualityTier.High,
            prompt = "Remove the background object.",
            userRefinement = "Keep the foreground sharp",
            subjectDescription = "A coffee cup on a wooden table",
            targetPlatform = TargetPlatform.InstagramPortrait,
            maskRegion = MaskRegion(MaskBounds.Normalized(0.1f, 0.1f, 0.5f, 0.5f)),
            createdAtEpochMillis = createdAt,
        )
        repository.savePhotoEditRequest(request)

        val stored = repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-request-1"))
        assertNotNull(stored)
        assertEquals(request.id, stored.id)
        assertEquals(request.draftId, stored.draftId)
        assertEquals(request.sourceMediaAssetId, stored.sourceMediaAssetId)
        assertEquals(request.intent, stored.intent)
        assertEquals(request.realismLevel, stored.realismLevel)
        assertEquals(request.qualityTier, stored.qualityTier)
        assertEquals(request.prompt, stored.prompt)
        assertEquals(request.userRefinement, stored.userRefinement)
        assertEquals(request.subjectDescription, stored.subjectDescription)
        assertEquals(request.targetPlatform, stored.targetPlatform)
        assertEquals(request.maskRegion, stored.maskRegion)
        assertEquals(request.createdAtEpochMillis, stored.createdAtEpochMillis)
        driver.close()
    }

    @Test
    fun savesAndReadsPhotoEditRequestWithNullOptionalFields() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())

        val request = PhotoEditRequest(
            id = PhotoEditRequestId("photo-edit-request-1"),
            draftId = PostDraftId("draft-1"),
            sourceMediaAssetId = MediaAssetId("media-1"),
            intent = EditIntent.BackgroundAdjustment,
            realismLevel = RealismLevel.Stylized,
            qualityTier = QualityTier.Draft,
            prompt = "Adjust the background.",
            userRefinement = null,
            subjectDescription = null,
            targetPlatform = TargetPlatform.InstagramStoryReel,
            maskRegion = null,
            createdAtEpochMillis = createdAt,
        )
        repository.savePhotoEditRequest(request)

        val stored = repository.getPhotoEditRequest(PhotoEditRequestId("photo-edit-request-1"))
        assertNotNull(stored)
        assertEquals(request.id, stored.id)
        assertEquals(request.draftId, stored.draftId)
        assertEquals(request.sourceMediaAssetId, stored.sourceMediaAssetId)
        assertEquals(request.intent, stored.intent)
        assertEquals(request.realismLevel, stored.realismLevel)
        assertEquals(request.qualityTier, stored.qualityTier)
        assertEquals(request.prompt, stored.prompt)
        assertNull(stored.userRefinement)
        assertNull(stored.subjectDescription)
        assertEquals(request.targetPlatform, stored.targetPlatform)
        assertNull(stored.maskRegion)
        assertEquals(request.createdAtEpochMillis, stored.createdAtEpochMillis)
        driver.close()
    }

    @Test
    fun hydratesPhotoEditRequestFromPostDraft() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())

        val stored = repository.get(PostDraftId("draft-1"))
        assertNotNull(stored)
        val hydrated = stored.photoEditRequests.single()
        assertEquals(PhotoEditRequestId("photo-edit-request-1"), hydrated.id)
        assertEquals(PostDraftId("draft-1"), hydrated.draftId)
        assertEquals(MediaAssetId("media-1"), hydrated.sourceMediaAssetId)
        assertEquals(EditIntent.ImproveLighting, hydrated.intent)
        assertEquals(RealismLevel.Photoreal, hydrated.realismLevel)
        assertEquals(QualityTier.High, hydrated.qualityTier)
        assertEquals("Make the image brighter while keeping it realistic.", hydrated.prompt)
        assertEquals("Focus on the coffee cup", hydrated.userRefinement)
        assertEquals("A coffee cup on a wooden table", hydrated.subjectDescription)
        assertEquals(TargetPlatform.InstagramFeedSquare, hydrated.targetPlatform)
        assertNull(hydrated.maskRegion)
        assertEquals(createdAt, hydrated.createdAtEpochMillis)
        driver.close()
    }

    private fun inMemoryDriver(): JdbcSqliteDriver =
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            ShotQuillDatabase.Schema.create(it)
        }

    private fun createV1Schema(driver: JdbcSqliteDriver) {
        driver.execute(null, "CREATE TABLE media_assets (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, uri TEXT NOT NULL, mime_type TEXT, width_px INTEGER, height_px INTEGER, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE brand_profiles (id TEXT NOT NULL PRIMARY KEY, display_name TEXT NOT NULL, voice TEXT NOT NULL, audience TEXT, visual_style_notes TEXT, product_naming_notes TEXT, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE brand_profile_default_hashtags (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (profile_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE brand_profile_links (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, link TEXT NOT NULL, link_order INTEGER NOT NULL, PRIMARY KEY (profile_id, link_order))", 0)
        driver.execute(null, "CREATE TABLE brand_image_assets (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, title TEXT NOT NULL, description TEXT, asset_order INTEGER NOT NULL, PRIMARY KEY (profile_id, media_asset_id))", 0)
        driver.execute(null, "CREATE TABLE post_drafts (id TEXT NOT NULL PRIMARY KEY, format TEXT NOT NULL, status TEXT NOT NULL, caption_text TEXT, brand_profile_id TEXT REFERENCES brand_profiles(id) ON DELETE SET NULL, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE post_draft_target_platforms (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, platform TEXT NOT NULL, PRIMARY KEY (draft_id, platform))", 0)
        driver.execute(null, "CREATE TABLE post_draft_media_items (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, media_order INTEGER NOT NULL, PRIMARY KEY (draft_id, media_asset_id), UNIQUE (draft_id, media_order))", 0)
        driver.execute(null, "CREATE TABLE post_draft_caption_hashtags (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (draft_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE vision_descriptions (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, description TEXT NOT NULL, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_requests (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, prompt TEXT NOT NULL, tone TEXT, brand_profile_id TEXT REFERENCES brand_profiles(id) ON DELETE SET NULL, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_results (id TEXT NOT NULL PRIMARY KEY, request_id TEXT NOT NULL REFERENCES caption_requests(id) ON DELETE CASCADE, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, caption TEXT NOT NULL, short_caption TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_result_hashtags (result_id TEXT NOT NULL REFERENCES caption_results(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (result_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE alt_text_results (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, alt_text TEXT NOT NULL, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE photo_edit_requests (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, source_media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, intent TEXT NOT NULL, realism_level TEXT NOT NULL, quality_tier TEXT NOT NULL, prompt TEXT NOT NULL, user_refinement TEXT, subject_description TEXT, target_platform TEXT NOT NULL, mask_region TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE photo_edit_results (id TEXT NOT NULL PRIMARY KEY, request_id TEXT NOT NULL REFERENCES photo_edit_requests(id) ON DELETE CASCADE, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, edited_media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, summary TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE prompt_history_entries (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, operation_type TEXT NOT NULL, prompt TEXT NOT NULL, response_summary TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE export_records (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, status TEXT NOT NULL, destination_uri TEXT, error_message TEXT, created_at_epoch_millis INTEGER NOT NULL, completed_at_epoch_millis INTEGER)", 0)
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
        altTextResults = listOf(sampleAltTextResult()),
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
        promptHistory = listOf(samplePromptHistoryEntry()),
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
        shortCaption = "Freshly brewed focus.",
        hashtags = listOf("#coffee", "#work"),
        modelName = "caption-model",
        createdAtEpochMillis = createdAt,
    )

    private fun sampleAltTextResult(): AltTextResult = AltTextResult(
        id = AltTextResultId("alt-text-1"),
        draftId = PostDraftId("draft-1"),
        mediaAssetId = MediaAssetId("media-1"),
        altText = "Coffee cup beside an open notebook.",
        modelName = "alt-text-model",
        createdAtEpochMillis = createdAt,
    )

    private fun samplePromptHistoryEntry(): PromptHistoryEntry = PromptHistoryEntry(
        id = PromptHistoryEntryId("prompt-1"),
        draftId = PostDraftId("draft-1"),
        operationType = AiOperationType.CaptionGeneration,
        prompt = "Write a concise caption.",
        responseSummary = "Generated one caption.",
        modelName = "caption-model",
        createdAtEpochMillis = createdAt,
    )

    private fun samplePhotoEditRequest(): PhotoEditRequest = PhotoEditRequest(
        id = PhotoEditRequestId("photo-edit-request-1"),
        draftId = PostDraftId("draft-1"),
        sourceMediaAssetId = MediaAssetId("media-1"),
        intent = EditIntent.ImproveLighting,
        realismLevel = RealismLevel.Photoreal,
        qualityTier = QualityTier.High,
        prompt = "Make the image brighter while keeping it realistic.",
        userRefinement = "Focus on the coffee cup",
        subjectDescription = "A coffee cup on a wooden table",
        targetPlatform = TargetPlatform.InstagramFeedSquare,
        maskRegion = null,
        createdAtEpochMillis = createdAt,
    )

    @Test fun `saveToGetRoundTrip preserves selectedMediaAssetId`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        val editedId = MediaAssetId("media-edited-1")
        repository.save(samplePostDraft().copy(selectedMediaAssetId = editedId))
        val stored = repository.get(PostDraftId("draft-1"))!!
        assertEquals(editedId, stored.selectedMediaAssetId)
        driver.close()
    }

    @Test fun `saveToGetRoundTrip defaults selectedMediaAssetId to null`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())
        assertNull(repository.get(PostDraftId("draft-1"))?.selectedMediaAssetId)
        driver.close()
    }

    @Test fun `updateSelectedMediaAsset sets non-null value and advances timestamp`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())
        val original = repository.get(PostDraftId("draft-1"))!!
        assertNull(original.selectedMediaAssetId)
        val editedId = MediaAssetId("media-edited-1")
        val newUpdatedAt = Instant.fromEpochMilliseconds(original.updatedAt.toEpochMilliseconds() + 10_000)
        assertTrue(repository.updateSelectedMediaAsset(PostDraftId("draft-1"), editedId, newUpdatedAt))
        val stored = repository.get(PostDraftId("draft-1"))!!
        assertEquals(editedId, stored.selectedMediaAssetId)
        assertEquals(newUpdatedAt, stored.updatedAt)
        driver.close()
    }

    @Test fun `selectionChangeDoesNotDisturbUnrelatedDraftState`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())
        val original = repository.get(PostDraftId("draft-1"))!!
        val editedId = MediaAssetId("media-edited-1")
        val newUpdatedAt = Instant.fromEpochMilliseconds(original.updatedAt.toEpochMilliseconds() + 10_000)
        repository.updateSelectedMediaAsset(PostDraftId("draft-1"), editedId, newUpdatedAt)
        val stored = repository.get(PostDraftId("draft-1"))!!
        assertEquals(original.format, stored.format)
        assertEquals(original.status, stored.status)
        assertEquals(original.caption, stored.caption)
        assertEquals(original.mediaItems, stored.mediaItems)
        assertEquals(original.targetPlatforms, stored.targetPlatforms)
        assertEquals(original.brandProfile, stored.brandProfile)
        assertEquals(original.visionDescription, stored.visionDescription)
        assertEquals(original.captionRequests, stored.captionRequests)
        assertEquals(original.captionResults, stored.captionResults)
        assertEquals(original.altTextResults, stored.altTextResults)
        assertEquals(original.photoEditRequests, stored.photoEditRequests)
        assertEquals(original.photoEditResults, stored.photoEditResults)
        assertEquals(original.promptHistory, stored.promptHistory)
        assertEquals(original.exportRecords, stored.exportRecords)
        assertEquals(editedId, stored.selectedMediaAssetId)
        assertEquals(newUpdatedAt, stored.updatedAt)
        driver.close()
    }

    @Test fun `updateUpdatedAt advances timestamp without changing status`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())
        val original = repository.get(PostDraftId("draft-1"))!!
        val newUpdatedAt = Instant.fromEpochMilliseconds(original.updatedAt.toEpochMilliseconds() + 10_000)
        assertTrue(repository.updateUpdatedAt(PostDraftId("draft-1"), newUpdatedAt))
        assertFalse(repository.updateUpdatedAt(PostDraftId("missing"), newUpdatedAt))
        val stored = repository.get(PostDraftId("draft-1"))!!
        assertEquals(original.status, stored.status)
        assertEquals(newUpdatedAt, stored.updatedAt)
        driver.close()
    }

    @Test fun `updateSelectedMediaAsset with null clears selection and advances timestamp`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        val editedId = MediaAssetId("media-edited-1")
        repository.save(samplePostDraft().copy(selectedMediaAssetId = editedId))
        val original = repository.get(PostDraftId("draft-1"))!!
        assertEquals(editedId, original.selectedMediaAssetId)
        val newUpdatedAt = Instant.fromEpochMilliseconds(original.updatedAt.toEpochMilliseconds() + 10_000)
        assertTrue(repository.updateSelectedMediaAsset(PostDraftId("draft-1"), null, newUpdatedAt))
        assertFalse(repository.updateSelectedMediaAsset(PostDraftId("missing"), null, newUpdatedAt))
        val stored = repository.get(PostDraftId("draft-1"))!!
        assertNull(stored.selectedMediaAssetId)
        assertEquals(newUpdatedAt, stored.updatedAt)
        driver.close()
    }

    @Test fun `updateSelectedMediaAsset rejects unrelated existing media asset`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())
        val foreignMediaId = MediaAssetId("media-2")
        repository.save(sampleMediaAsset().copy(id = foreignMediaId, uri = "file://other.jpg"))
        val otherDraft = samplePostDraft().copy(
            id = PostDraftId("draft-2"),
            mediaItems = listOf(PostMediaItem(sampleMediaAsset().copy(id = foreignMediaId), 0)),
            photoEditResults = emptyList(),
        )
        repository.save(otherDraft)
        val original = repository.get(PostDraftId("draft-1"))!!
        val newUpdatedAt = Instant.fromEpochMilliseconds(original.updatedAt.toEpochMilliseconds() + 10_000)
        assertFalse(repository.updateSelectedMediaAsset(PostDraftId("draft-1"), foreignMediaId, newUpdatedAt))
        val stored = repository.get(PostDraftId("draft-1"))!!
        assertNull(stored.selectedMediaAssetId)
        driver.close()
    }

    @Test fun `updateSelectedMediaAsset accepts draft-owned original media asset`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())
        val original = repository.get(PostDraftId("draft-1"))!!
        assertNull(original.selectedMediaAssetId)
        val originalId = MediaAssetId("media-1")
        val newUpdatedAt = Instant.fromEpochMilliseconds(original.updatedAt.toEpochMilliseconds() + 10_000)
        assertTrue(repository.updateSelectedMediaAsset(PostDraftId("draft-1"), originalId, newUpdatedAt))
        val stored = repository.get(PostDraftId("draft-1"))!!
        assertEquals(originalId, stored.selectedMediaAssetId)
        assertEquals(newUpdatedAt, stored.updatedAt)
        driver.close()
    }

    @Test fun `updateSelectedMediaAsset accepts draft-owned edited media asset`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())
        val original = repository.get(PostDraftId("draft-1"))!!
        assertNull(original.selectedMediaAssetId)
        val editedId = MediaAssetId("media-edited-1")
        val newUpdatedAt = Instant.fromEpochMilliseconds(original.updatedAt.toEpochMilliseconds() + 10_000)
        assertTrue(repository.updateSelectedMediaAsset(PostDraftId("draft-1"), editedId, newUpdatedAt))
        val stored = repository.get(PostDraftId("draft-1"))!!
        assertEquals(editedId, stored.selectedMediaAssetId)
        assertEquals(newUpdatedAt, stored.updatedAt)
        driver.close()
    }

    @Test fun `updateSelectedMediaAsset rejects media asset not in media_assets`() {
        val driver = inMemoryDriver()
        val repository = SqlDelightManualWorkflowRepository(driver)
        repository.save(samplePostDraft())
        val original = repository.get(PostDraftId("draft-1"))!!
        val nonexistentId = MediaAssetId("no-such-media")
        val newUpdatedAt = Instant.fromEpochMilliseconds(original.updatedAt.toEpochMilliseconds() + 10_000)
        assertFalse(repository.updateSelectedMediaAsset(PostDraftId("draft-1"), nonexistentId, newUpdatedAt))
        driver.close()
    }
}
