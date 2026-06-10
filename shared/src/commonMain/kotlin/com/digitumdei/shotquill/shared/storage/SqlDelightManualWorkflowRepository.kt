package com.digitumdei.shotquill.shared.storage

import app.cash.sqldelight.db.SqlDriver
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
import com.digitumdei.shotquill.shared.domain.FinalPostContent
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MaskRegion
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

class SqlDelightManualWorkflowRepository(
    driver: SqlDriver,
) : ManualWorkflowRepository {
    private val database = ShotQuillDatabase(driver)
    private val queries = database.shotQuillQueries

    override fun save(mediaAsset: MediaAsset) {
        queries.upsertMediaAsset(
            id = mediaAsset.id.value,
            type = mediaAsset.type.wireValue,
            uri = mediaAsset.uri,
            mime_type = mediaAsset.mimeType,
            width_px = mediaAsset.widthPx?.toLong(),
            height_px = mediaAsset.heightPx?.toLong(),
            created_at_epoch_millis = mediaAsset.createdAtEpochMillis,
        )
    }

    override fun get(id: MediaAssetId): MediaAsset? = selectMediaAsset(id)

    override fun save(brandProfile: BrandProfile) {
        queries.transaction {
            brandProfile.imageAssets.forEach { save(it.mediaAsset) }
            queries.upsertBrandProfile(
                id = brandProfile.id.value,
                display_name = brandProfile.displayName,
                voice = brandProfile.voice,
                audience = brandProfile.audience,
                visual_style_notes = brandProfile.visualStyleNotes,
                product_naming_notes = brandProfile.productNamingNotes,
                created_at_epoch_millis = brandProfile.createdAtEpochMillis,
                updated_at_epoch_millis = brandProfile.updatedAtEpochMillis,
            )
            queries.deleteBrandProfileDefaultHashtags(brandProfile.id.value)
            brandProfile.defaultHashtags.forEachIndexed { index, hashtag ->
                queries.insertBrandProfileDefaultHashtag(
                    profile_id = brandProfile.id.value,
                    hashtag = hashtag,
                    hashtag_order = index.toLong(),
                )
            }
            queries.deleteBrandProfileLinks(brandProfile.id.value)
            brandProfile.websiteOrSocialLinks.forEachIndexed { index, link ->
                queries.insertBrandProfileLink(
                    profile_id = brandProfile.id.value,
                    link = link,
                    link_order = index.toLong(),
                )
            }
            queries.deleteBrandImageAssets(brandProfile.id.value)
            brandProfile.imageAssets.forEachIndexed { index, imageAsset ->
                queries.insertBrandImageAsset(
                    profile_id = brandProfile.id.value,
                    media_asset_id = imageAsset.mediaAsset.id.value,
                    title = imageAsset.title,
                    description = imageAsset.description,
                    asset_order = index.toLong(),
                )
            }
        }
    }

    override fun get(id: BrandProfileId): BrandProfile? = selectBrandProfile(id)

    override fun save(postDraft: PostDraft) {
        queries.transaction {
            postDraft.brandProfile?.let { save(it) }
            postDraft.mediaItems.forEach { save(it.mediaAsset) }
            postDraft.photoEditResults.forEach { save(it.editedMediaAsset) }
            queries.upsertPostDraft(
                id = postDraft.id.value,
                format = postDraft.format.name,
                status = postDraft.status.wireValue,
                caption_text = postDraft.caption?.text,
                brand_profile_id = postDraft.brandProfile?.id?.value,
                selected_media_asset_id = postDraft.selectedMediaAssetId?.value,
                created_at_epoch_millis = postDraft.createdAt.toEpochMilliseconds(),
                updated_at_epoch_millis = postDraft.updatedAt.toEpochMilliseconds(),
            )
            queries.deletePostDraftTargetPlatforms(postDraft.id.value)
            postDraft.targetPlatforms.forEach {
                queries.insertPostDraftTargetPlatform(postDraft.id.value, it.wireValue)
            }
            queries.deletePostDraftMediaItems(postDraft.id.value)
            postDraft.mediaItems.forEach {
                queries.insertPostDraftMediaItem(
                    draft_id = postDraft.id.value,
                    media_asset_id = it.mediaAsset.id.value,
                    media_order = it.order.toLong(),
                )
            }
            queries.deletePostDraftCaptionHashtags(postDraft.id.value)
            postDraft.caption?.hashtags.orEmpty().forEachIndexed { index, hashtag ->
                queries.insertPostDraftCaptionHashtag(
                    draft_id = postDraft.id.value,
                    hashtag = hashtag,
                    hashtag_order = index.toLong(),
                )
            }
            deleteOwnedDraftRows(postDraft.id)
            postDraft.visionDescriptions.forEach { saveVisionDescription(it) }
            postDraft.captionRequests.forEach { saveCaptionRequest(it) }
            postDraft.captionResults.forEach { saveCaptionResult(it) }
            postDraft.altTextResults.forEach { saveAltTextResult(it) }
            postDraft.photoEditRequests.forEach { savePhotoEditRequest(it) }
            postDraft.photoEditResults.forEach { savePhotoEditResult(it) }
            postDraft.promptHistory.forEach { savePromptHistoryEntry(it) }
            postDraft.exportRecords.forEach { saveExportRecord(it) }
            postDraft.finalPostContent?.let { saveFinalPostContent(it) }
        }
    }

    override fun get(id: PostDraftId): PostDraft? {
        val draft = queries.selectPostDraftById(id.value) { draftId, format, status, captionText, brandProfileId, selectedMediaAssetId, createdAt, updatedAt ->
            DraftRow(
                id = draftId,
                format = format,
                status = status,
                captionText = captionText,
                brandProfileId = brandProfileId,
                selectedMediaAssetId = selectedMediaAssetId,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull() ?: return null

        val mediaItems = queries.selectPostDraftMediaItems(id.value) {
                mediaId,
                type,
                uri,
                mimeType,
                widthPx,
                heightPx,
                mediaCreatedAt,
                mediaOrder,
            ->
            PostMediaItem(
                mediaAsset = mediaAsset(mediaId, type, uri, mimeType, widthPx, heightPx, mediaCreatedAt),
                order = mediaOrder.toInt(),
            )
        }.executeAsList()

        return PostDraft(
            id = PostDraftId(draft.id),
            format = PostFormat.valueOf(draft.format),
            status = ManualWorkflowStorageMapper.enumFromWire(draft.status, DraftStatus::fromWireValue),
            mediaItems = mediaItems,
            selectedMediaAssetId = draft.selectedMediaAssetId?.let(::MediaAssetId),
            caption = draft.captionText?.let {
                CaptionDraft(it, queries.selectPostDraftCaptionHashtags(id.value).executeAsList())
            },
            targetPlatforms = queries.selectPostDraftTargetPlatforms(id.value)
                .executeAsList()
                .mapTo(linkedSetOf()) { ManualWorkflowStorageMapper.enumFromWire(it, TargetPlatform::fromWireValue) },
            brandProfile = draft.brandProfileId?.let { selectBrandProfile(BrandProfileId(it)) },
            visionDescriptions = selectVisionDescriptions(id),
            captionRequests = selectCaptionRequests(id),
            captionResults = selectCaptionResults(id),
            altTextResults = selectAltTextResults(id),
            photoEditRequests = selectPhotoEditRequests(id),
            photoEditResults = selectPhotoEditResults(id),
            promptHistory = selectPromptHistoryEntries(id),
            exportRecords = selectExportRecords(id),
            finalPostContent = selectFinalPostContent(id),
            createdAt = Instant.fromEpochMilliseconds(draft.createdAt),
            updatedAt = Instant.fromEpochMilliseconds(draft.updatedAt),
        )
    }

    override fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean {
        val current = get(id) ?: return false
        current.transitionTo(status, updatedAt)
        queries.updatePostDraftStatus(
            status = status.wireValue,
            updated_at_epoch_millis = updatedAt.toEpochMilliseconds(),
            id = id.value,
        )
        return true
    }

    override fun updateUpdatedAt(id: PostDraftId, updatedAt: Instant): Boolean {
        if (queries.selectPostDraftById(id.value).executeAsOneOrNull() == null) return false
        queries.updatePostDraftUpdatedAt(
            updated_at_epoch_millis = updatedAt.toEpochMilliseconds(),
            id = id.value,
        )
        return true
    }

    override fun updateSelectedMediaAsset(id: PostDraftId, mediaAssetId: MediaAssetId?, updatedAt: Instant): UpdateSelectionResult {
        if (queries.selectPostDraftById(id.value).executeAsOneOrNull() == null) return UpdateSelectionResult.DraftNotFound
        if (mediaAssetId != null) {
            val isDraftOriginalMedia = queries.selectDraftOriginalMediaAssetId(id.value, mediaAssetId.value).executeAsOneOrNull() != null
            val isDraftEditedMedia = queries.selectDraftEditedMediaAssetId(id.value, mediaAssetId.value).executeAsOneOrNull() != null
            if (!isDraftOriginalMedia && !isDraftEditedMedia) return UpdateSelectionResult.AssetNotOwnedByDraft
        }
        queries.updatePostDraftSelectedMediaAsset(
            selected_media_asset_id = mediaAssetId?.value,
            updated_at_epoch_millis = updatedAt.toEpochMilliseconds(),
            id = id.value,
        )
        return UpdateSelectionResult.Success
    }

    override fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean {
        if (mediaItems.isEmpty() || queries.selectPostDraftById(id.value).executeAsOneOrNull() == null) {
            return false
        }
        if (mediaItems.size != mediaItems.distinct().size) {
            return false
        }
        if (mediaItems.any { selectMediaAsset(it) == null }) {
            return false
        }

        queries.transaction {
            queries.deletePostDraftMediaItems(id.value)
            mediaItems.forEachIndexed { index, mediaAssetId ->
                queries.insertPostDraftMediaItem(
                    draft_id = id.value,
                    media_asset_id = mediaAssetId.value,
                    media_order = index.toLong(),
                )
            }
        }
        return true
    }

    override fun save(visionDescription: VisionDescription) {
        saveVisionDescription(visionDescription)
    }

    override fun saveVisionDescription(visionDescription: VisionDescription) {
        queries.upsertVisionDescription(
            id = visionDescription.id.value,
            draft_id = visionDescription.draftId.value,
            media_asset_id = visionDescription.mediaAssetId.value,
            description = visionDescription.description,
            model_name = visionDescription.modelName,
            created_at_epoch_millis = visionDescription.createdAtEpochMillis,
        )
    }

    override fun get(id: VisionDescriptionId): VisionDescription? =
        queries.selectVisionDescriptionById(id.value, ManualWorkflowStorageMapper::visionDescription).executeAsOneOrNull()

    override fun listVisionDescriptionsForDraft(id: PostDraftId): List<VisionDescription> = selectVisionDescriptions(id)

    override fun save(captionRequest: CaptionRequest) {
        saveCaptionRequest(captionRequest)
    }

    override fun saveCaptionRequest(captionRequest: CaptionRequest) {
        queries.upsertCaptionRequest(
            id = captionRequest.id.value,
            draft_id = captionRequest.draftId.value,
            target_platform = captionRequest.targetPlatform.wireValue,
            prompt = captionRequest.prompt,
            tone = captionRequest.tone,
            brand_profile_id = captionRequest.brandProfileId?.value,
            created_at_epoch_millis = captionRequest.createdAtEpochMillis,
        )
    }

    override fun getCaptionRequest(id: CaptionRequestId): CaptionRequest? =
        queries.selectCaptionRequestById(id.value, ManualWorkflowStorageMapper::captionRequest).executeAsOneOrNull()

    override fun listCaptionRequestsForDraft(id: PostDraftId): List<CaptionRequest> = selectCaptionRequests(id)

    override fun save(captionResult: CaptionResult) {
        saveCaptionResult(captionResult)
    }

    override fun saveCaptionResult(captionResult: CaptionResult) {
        queries.transaction {
            queries.upsertCaptionResult(
                id = captionResult.id.value,
                request_id = captionResult.requestId.value,
                draft_id = captionResult.draftId.value,
                target_platform = captionResult.targetPlatform.wireValue,
                caption = captionResult.caption,
                short_caption = captionResult.shortCaption,
                model_name = captionResult.modelName,
                created_at_epoch_millis = captionResult.createdAtEpochMillis,
            )
            queries.deleteCaptionResultHashtags(captionResult.id.value)
            captionResult.hashtags.forEachIndexed { index, hashtag ->
                queries.insertCaptionResultHashtag(
                    result_id = captionResult.id.value,
                    hashtag = hashtag,
                    hashtag_order = index.toLong(),
                )
            }
        }
    }

    override fun getCaptionResult(id: CaptionResultId): CaptionResult? {
        val result = queries.selectCaptionResultById(id.value, ::captionResultWithoutHashtags).executeAsOneOrNull()
            ?: return null
        return result.withCaptionResultHashtags()
    }

    override fun listCaptionResultsForDraft(id: PostDraftId): List<CaptionResult> = selectCaptionResults(id)

    override fun save(altTextResult: AltTextResult) {
        saveAltTextResult(altTextResult)
    }

    override fun saveAltTextResult(altTextResult: AltTextResult) {
        queries.upsertAltTextResult(
            id = altTextResult.id.value,
            draft_id = altTextResult.draftId.value,
            media_asset_id = altTextResult.mediaAssetId.value,
            alt_text = altTextResult.altText,
            model_name = altTextResult.modelName,
            created_at_epoch_millis = altTextResult.createdAtEpochMillis,
        )
    }

    override fun get(id: AltTextResultId): AltTextResult? =
        queries.selectAltTextResultById(id.value, ManualWorkflowStorageMapper::altTextResult).executeAsOneOrNull()

    override fun listAltTextResultsForDraft(id: PostDraftId): List<AltTextResult> = selectAltTextResults(id)

    override fun save(photoEditRequest: PhotoEditRequest) {
        savePhotoEditRequest(photoEditRequest)
    }

    override fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest) {
        queries.upsertPhotoEditRequest(
            id = photoEditRequest.id.value,
            draft_id = photoEditRequest.draftId.value,
            source_media_asset_id = photoEditRequest.sourceMediaAssetId.value,
            intent = photoEditRequest.intent.wireValue,
            realism_level = photoEditRequest.realismLevel.wireValue,
            quality_tier = photoEditRequest.qualityTier.wireValue,
            prompt = photoEditRequest.prompt,
            user_refinement = photoEditRequest.userRefinement,
            subject_description = photoEditRequest.subjectDescription,
            target_platform = photoEditRequest.targetPlatform.wireValue,
            mask_region = photoEditRequest.maskRegion?.toString(),
            created_at_epoch_millis = photoEditRequest.createdAtEpochMillis,
        )
    }

    override fun getPhotoEditRequest(id: PhotoEditRequestId): PhotoEditRequest? =
        queries.selectPhotoEditRequestById(id.value, ManualWorkflowStorageMapper::photoEditRequest).executeAsOneOrNull()

    override fun listPhotoEditRequestsForDraft(id: PostDraftId): List<PhotoEditRequest> = selectPhotoEditRequests(id)

    override fun save(photoEditResult: PhotoEditResult) {
        savePhotoEditResult(photoEditResult)
    }

    override fun savePhotoEditResult(photoEditResult: PhotoEditResult) {
        queries.transaction {
            save(photoEditResult.editedMediaAsset)
            queries.upsertPhotoEditResult(
                id = photoEditResult.id.value,
                request_id = photoEditResult.requestId.value,
                draft_id = photoEditResult.draftId.value,
                edited_media_asset_id = photoEditResult.editedMediaAsset.id.value,
                summary = photoEditResult.summary,
                model_name = photoEditResult.modelName,
                created_at_epoch_millis = photoEditResult.createdAtEpochMillis,
            )
        }
    }

    override fun getPhotoEditResult(id: PhotoEditResultId): PhotoEditResult? =
        queries.selectPhotoEditResultById(id.value, ManualWorkflowStorageMapper::photoEditResult).executeAsOneOrNull()

    override fun listPhotoEditResultsForDraft(id: PostDraftId): List<PhotoEditResult> = selectPhotoEditResults(id)

    override fun save(promptHistoryEntry: PromptHistoryEntry) {
        savePromptHistoryEntry(promptHistoryEntry)
    }

    override fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry) {
        queries.upsertPromptHistoryEntry(
            id = promptHistoryEntry.id.value,
            draft_id = promptHistoryEntry.draftId.value,
            operation_type = promptHistoryEntry.operationType.wireValue,
            prompt = SecretRedactor.redactOpenAiApiKeys(promptHistoryEntry.prompt),
            response_summary = promptHistoryEntry.responseSummary?.let(SecretRedactor::redactOpenAiApiKeys),
            model_name = promptHistoryEntry.modelName,
            created_at_epoch_millis = promptHistoryEntry.createdAtEpochMillis,
        )
    }

    override fun get(id: PromptHistoryEntryId): PromptHistoryEntry? =
        queries.selectPromptHistoryEntryById(id.value, ManualWorkflowStorageMapper::promptHistoryEntry).executeAsOneOrNull()

    override fun listPromptHistoryForDraft(id: PostDraftId): List<PromptHistoryEntry> = selectPromptHistoryEntries(id)

    override fun save(exportRecord: ExportRecord) {
        saveExportRecord(exportRecord)
    }

    override fun saveExportRecord(exportRecord: ExportRecord) {
        queries.upsertExportRecord(
            id = exportRecord.id.value,
            draft_id = exportRecord.draftId.value,
            target_platform = exportRecord.targetPlatform.wireValue,
            status = exportRecord.status.wireValue,
            destination_uri = exportRecord.destinationUri,
            error_message = exportRecord.errorMessage?.let(SecretRedactor::redactOpenAiApiKeys),
            created_at_epoch_millis = exportRecord.createdAtEpochMillis,
            completed_at_epoch_millis = exportRecord.completedAtEpochMillis,
        )
    }

    override fun get(id: ExportRecordId): ExportRecord? =
        queries.selectExportRecordById(id.value, ManualWorkflowStorageMapper::exportRecord).executeAsOneOrNull()

    override fun listExportRecordsForDraft(id: PostDraftId): List<ExportRecord> = selectExportRecords(id)

    override fun saveFinalPostContent(finalPostContent: FinalPostContent) {
        queries.upsertFinalPostContent(
            draft_id = finalPostContent.draftId.value,
            edited_caption = finalPostContent.editedCaption,
            edited_alt_text = finalPostContent.editedAltText,
            updated_at_epoch_millis = finalPostContent.updatedAtEpochMillis,
        )
    }

    override fun getFinalPostContent(draftId: PostDraftId): FinalPostContent? =
        selectFinalPostContent(draftId)

    override fun savePhotoEditSuccess(
        draftId: PostDraftId,
        editedMediaAsset: MediaAsset,
        editRequest: PhotoEditRequest,
        editResult: PhotoEditResult,
        promptHistoryEntry: PromptHistoryEntry,
        targetStatus: DraftStatus,
        updatedAt: Instant,
    ): PostDraft? {
        var savedDraft: PostDraft? = null
        queries.transaction {
            val currentDraft = get(draftId) ?: return@transaction
            save(editedMediaAsset)
            savePhotoEditRequest(editRequest)
            savePhotoEditResult(editResult)
            savePromptHistoryEntry(promptHistoryEntry)
            if (targetStatus != currentDraft.status) {
                currentDraft.transitionTo(targetStatus, updatedAt)
                queries.updatePostDraftStatus(
                    status = targetStatus.wireValue,
                    updated_at_epoch_millis = updatedAt.toEpochMilliseconds(),
                    id = draftId.value,
                )
            } else {
                queries.updatePostDraftUpdatedAt(
                    updated_at_epoch_millis = updatedAt.toEpochMilliseconds(),
                    id = draftId.value,
                )
            }
            queries.updatePostDraftSelectedMediaAsset(
                selected_media_asset_id = editedMediaAsset.id.value,
                updated_at_epoch_millis = updatedAt.toEpochMilliseconds(),
                id = draftId.value,
            )
            savedDraft = get(draftId)
        }
        return savedDraft
    }

    override fun savePhotoEditFailure(
        draftId: PostDraftId,
        editRequest: PhotoEditRequest,
        promptHistoryEntry: PromptHistoryEntry,
        updatedAt: Instant,
    ): PostDraft? {
        var savedDraft: PostDraft? = null
        queries.transaction {
            val currentDraft = get(draftId) ?: return@transaction
            savePhotoEditRequest(editRequest)
            savePromptHistoryEntry(promptHistoryEntry)
            queries.updatePostDraftUpdatedAt(
                updated_at_epoch_millis = updatedAt.toEpochMilliseconds(),
                id = draftId.value,
            )
            savedDraft = get(draftId)
        }
        return savedDraft
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
        var savedDraft: PostDraft? = null
        queries.transaction {
            val currentDraft = get(draftId) ?: return@transaction
            val storedStatus = postTextGenerationStatus(currentDraft.status, status) ?: return@transaction
            val storedUpdatedAt = if (updatedAt >= currentDraft.updatedAt) updatedAt else currentDraft.updatedAt
            val storedBrandProfile = brandProfile ?: currentDraft.brandProfile
            storedBrandProfile?.let { save(it) }
            queries.updatePostDraftGeneratedText(
                status = storedStatus.wireValue,
                caption_text = caption.text,
                brand_profile_id = storedBrandProfile?.id?.value,
                updated_at_epoch_millis = storedUpdatedAt.toEpochMilliseconds(),
                id = draftId.value,
            )
            queries.insertOrIgnorePostDraftTargetPlatform(draftId.value, targetPlatform.wireValue)
            queries.deletePostDraftCaptionHashtags(draftId.value)
            caption.hashtags.forEachIndexed { index, hashtag ->
                queries.insertPostDraftCaptionHashtag(
                    draft_id = draftId.value,
                    hashtag = hashtag,
                    hashtag_order = index.toLong(),
                )
            }
            saveCaptionRequest(captionRequest)
            saveCaptionResult(captionResult)
            saveAltTextResult(altTextResult)
            promptHistoryEntries.forEach(::savePromptHistoryEntry)
            savedDraft = get(draftId)
        }
        return savedDraft
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

    override fun clearAll() {
        queries.transaction {
            queries.deleteAllExportRecords()
            queries.deleteAllPromptHistoryEntries()
            queries.deleteAllPhotoEditResults()
            queries.deleteAllPhotoEditRequests()
            queries.deleteAllAltTextResults()
            queries.deleteAllFinalPostContent()
            queries.deleteAllCaptionResultHashtags()
            queries.deleteAllCaptionResults()
            queries.deleteAllCaptionRequests()
            queries.deleteAllVisionDescriptions()
            queries.deleteAllPostDraftCaptionHashtags()
            queries.deleteAllPostDraftMediaItems()
            queries.deleteAllPostDraftTargetPlatforms()
            queries.deleteAllPostDrafts()
            queries.deleteAllBrandImageAssets()
            queries.deleteAllBrandProfileDefaultHashtags()
            queries.deleteAllBrandProfileLinks()
            queries.deleteAllBrandProfiles()
            queries.deleteAllMediaAssets()
        }
    }

    private fun deleteOwnedDraftRows(id: PostDraftId) {
        queries.deleteExportRecordsByDraftId(id.value)
        queries.deletePromptHistoryEntriesByDraftId(id.value)
        queries.deletePhotoEditResultsByDraftId(id.value)
        queries.deletePhotoEditRequestsByDraftId(id.value)
        queries.deleteAltTextResultsByDraftId(id.value)
        queries.deleteFinalPostContentByDraftId(id.value)
        queries.deleteCaptionResultHashtagsByDraftId(id.value)
        queries.deleteCaptionResultsByDraftId(id.value)
        queries.deleteCaptionRequestsByDraftId(id.value)
        queries.deleteVisionDescriptionsByDraftId(id.value)
    }

    private fun selectMediaAsset(id: MediaAssetId): MediaAsset? =
        queries.selectMediaAssetById(id.value, ::mediaAsset).executeAsOneOrNull()

    private fun selectBrandProfile(id: BrandProfileId): BrandProfile? {
        val profile = queries.selectBrandProfileById(id.value) {
                profileId,
                displayName,
                voice,
                audience,
                visualStyleNotes,
                productNamingNotes,
                createdAt,
                updatedAt,
            ->
            BrandProfileRow(profileId, displayName, voice, audience, visualStyleNotes, productNamingNotes, createdAt, updatedAt)
        }.executeAsOneOrNull() ?: return null

        val defaultHashtags = queries.selectBrandProfileDefaultHashtags(id.value).executeAsList()
        val websiteOrSocialLinks = queries.selectBrandProfileLinks(id.value).executeAsList()
        val imageAssets = queries.selectBrandImageAssets(id.value) {
                mediaId,
                type,
                uri,
                mimeType,
                widthPx,
                heightPx,
                mediaCreatedAt,
                title,
                description,
            ->
            BrandImageAsset(
                mediaAsset = mediaAsset(mediaId, type, uri, mimeType, widthPx, heightPx, mediaCreatedAt),
                title = title,
                description = description,
            )
        }.executeAsList()

        return BrandProfile(
            id = BrandProfileId(profile.id),
            displayName = profile.displayName,
            voice = profile.voice,
            audience = profile.audience,
            defaultHashtags = defaultHashtags,
            websiteOrSocialLinks = websiteOrSocialLinks,
            visualStyleNotes = profile.visualStyleNotes,
            productNamingNotes = profile.productNamingNotes,
            imageAssets = imageAssets,
            createdAtEpochMillis = profile.createdAt,
            updatedAtEpochMillis = profile.updatedAt,
        )
    }

    private fun selectVisionDescriptions(id: PostDraftId): List<VisionDescription> =
        queries.selectVisionDescriptionsByDraftId(id.value) {
                visionId,
                draftId,
                mediaAssetId,
                description,
                modelName,
                createdAt,
            ->
            ManualWorkflowStorageMapper.visionDescription(visionId, draftId, mediaAssetId, description, modelName, createdAt)
        }.executeAsList()

    private fun selectCaptionRequests(id: PostDraftId): List<CaptionRequest> =
        queries.selectCaptionRequestsByDraftId(id.value) {
                requestId,
                draftId,
                targetPlatform,
                prompt,
                tone,
                brandProfileId,
                createdAt,
            ->
            ManualWorkflowStorageMapper.captionRequest(requestId, draftId, targetPlatform, prompt, tone, brandProfileId, createdAt)
        }.executeAsList()

    private fun selectCaptionResults(id: PostDraftId): List<CaptionResult> =
        queries.selectCaptionResultsByDraftId(id.value, ::captionResultWithoutHashtags)
            .executeAsList()
            .map { it.withCaptionResultHashtags() }

    private fun captionResultWithoutHashtags(
        resultId: String,
        requestId: String,
        draftId: String,
        targetPlatform: String,
        caption: String,
        shortCaption: String?,
        modelName: String?,
        createdAt: Long,
    ): CaptionResult = ManualWorkflowStorageMapper.captionResult(
        resultId = resultId,
        requestId = requestId,
        draftId = draftId,
        targetPlatform = targetPlatform,
        caption = caption,
        shortCaption = shortCaption,
        hashtags = emptyList(),
        modelName = modelName,
        createdAt = createdAt,
    )

    private fun CaptionResult.withCaptionResultHashtags(): CaptionResult =
        copy(hashtags = queries.selectCaptionResultHashtags(id.value).executeAsList())

    private fun selectAltTextResults(id: PostDraftId): List<AltTextResult> =
        queries.selectAltTextResultsByDraftId(id.value) {
                resultId,
                draftId,
                mediaAssetId,
                altText,
                modelName,
                createdAt,
            ->
            ManualWorkflowStorageMapper.altTextResult(resultId, draftId, mediaAssetId, altText, modelName, createdAt)
        }.executeAsList()

    private fun selectPhotoEditRequests(id: PostDraftId): List<PhotoEditRequest> =
        queries.selectPhotoEditRequestsByDraftId(id.value) {
                requestId,
                draftId,
                sourceMediaAssetId,
                intent,
                realismLevel,
                qualityTier,
                prompt,
                userRefinement,
                subjectDescription,
                targetPlatform,
                maskRegion,
                createdAt,
            ->
            ManualWorkflowStorageMapper.photoEditRequest(
                requestId, draftId, sourceMediaAssetId, intent, realismLevel, qualityTier, prompt,
                userRefinement, subjectDescription, targetPlatform, maskRegion, createdAt,
            )
        }.executeAsList()

    private fun selectPhotoEditResults(id: PostDraftId): List<PhotoEditResult> =
        queries.selectPhotoEditResultsByDraftId(id.value) {
                resultId,
                requestId,
                draftId,
                editedMediaAssetId,
                summary,
                modelName,
                createdAt,
                type,
                uri,
                mimeType,
                widthPx,
                heightPx,
                mediaCreatedAt,
            ->
            ManualWorkflowStorageMapper.photoEditResult(
                resultId = resultId,
                requestId = requestId,
                draftId = draftId,
                editedMediaAssetId = editedMediaAssetId,
                summary = summary,
                modelName = modelName,
                createdAt = createdAt,
                type = type,
                uri = uri,
                mimeType = mimeType,
                widthPx = widthPx,
                heightPx = heightPx,
                mediaCreatedAt = mediaCreatedAt,
            )
        }.executeAsList()

    private fun selectPromptHistoryEntries(id: PostDraftId): List<PromptHistoryEntry> =
        queries.selectPromptHistoryEntriesByDraftId(id.value) {
                entryId,
                draftId,
                operationType,
                prompt,
                responseSummary,
                modelName,
                createdAt,
            ->
            ManualWorkflowStorageMapper.promptHistoryEntry(entryId, draftId, operationType, prompt, responseSummary, modelName, createdAt)
        }.executeAsList()

    private fun selectExportRecords(id: PostDraftId): List<ExportRecord> =
        queries.selectExportRecordsByDraftId(id.value) {
                recordId,
                draftId,
                targetPlatform,
                status,
                destinationUri,
                errorMessage,
                createdAt,
                completedAt,
            ->
            ManualWorkflowStorageMapper.exportRecord(recordId, draftId, targetPlatform, status, destinationUri, errorMessage, createdAt, completedAt)
        }.executeAsList()

    private fun selectFinalPostContent(id: PostDraftId): FinalPostContent? =
        queries.selectFinalPostContentByDraftId(id.value) {
                draftId,
                editedCaption,
                editedAltText,
                updatedAt,
            ->
            FinalPostContent(
                draftId = PostDraftId(draftId),
                editedCaption = editedCaption,
                editedAltText = editedAltText,
                updatedAtEpochMillis = updatedAt,
            )
        }.executeAsOneOrNull()

    private fun mediaAsset(
        id: String,
        type: String,
        uri: String,
        mimeType: String?,
        widthPx: Long?,
        heightPx: Long?,
        createdAt: Long,
    ): MediaAsset = ManualWorkflowStorageMapper.mediaAsset(
        id = MediaAssetId(id),
        type = ManualWorkflowStorageMapper.enumFromWire(type, MediaType::fromWireValue),
        uri = uri,
        mimeType = mimeType,
        widthPx = widthPx?.toInt(),
        heightPx = heightPx?.toInt(),
        createdAtEpochMillis = createdAt,
    )
}

private data class DraftRow(
    val id: String,
    val format: String,
    val status: String,
    val captionText: String?,
    val brandProfileId: String?,
    val selectedMediaAssetId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

private data class BrandProfileRow(
    val id: String,
    val displayName: String,
    val voice: String,
    val audience: String?,
    val visualStyleNotes: String?,
    val productNamingNotes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

internal object ManualWorkflowStorageMapper {
    fun mediaAsset(
        id: MediaAssetId,
        type: MediaType,
        uri: String,
        mimeType: String?,
        widthPx: Int?,
        heightPx: Int?,
        createdAtEpochMillis: Long,
    ): MediaAsset = MediaAsset(
        id = id,
        type = type,
        uri = uri,
        mimeType = mimeType,
        widthPx = widthPx,
        heightPx = heightPx,
        createdAtEpochMillis = createdAtEpochMillis,
    )

    fun mediaAsset(
        id: String,
        type: String,
        uri: String,
        mimeType: String?,
        widthPx: Long?,
        heightPx: Long?,
        createdAt: Long,
    ): MediaAsset = mediaAsset(
        id = MediaAssetId(id),
        type = enumFromWire(type, MediaType::fromWireValue),
        uri = uri,
        mimeType = mimeType,
        widthPx = widthPx?.toInt(),
        heightPx = heightPx?.toInt(),
        createdAtEpochMillis = createdAt,
    )

    fun visionDescription(
        visionId: String,
        draftId: String,
        mediaAssetId: String,
        description: String,
        modelName: String?,
        createdAt: Long,
    ): VisionDescription = VisionDescription(
        id = VisionDescriptionId(visionId),
        draftId = PostDraftId(draftId),
        mediaAssetId = MediaAssetId(mediaAssetId),
        description = description,
        modelName = modelName,
        createdAtEpochMillis = createdAt,
    )

    fun captionRequest(
        requestId: String,
        draftId: String,
        targetPlatform: String,
        prompt: String,
        tone: String?,
        brandProfileId: String?,
        createdAt: Long,
    ): CaptionRequest = CaptionRequest(
        id = CaptionRequestId(requestId),
        draftId = PostDraftId(draftId),
        targetPlatform = enumFromWire(targetPlatform, TargetPlatform::fromWireValue),
        prompt = prompt,
        tone = tone,
        brandProfileId = brandProfileId?.let(::BrandProfileId),
        createdAtEpochMillis = createdAt,
    )

    fun captionResult(
        resultId: String,
        requestId: String,
        draftId: String,
        targetPlatform: String,
        caption: String,
        shortCaption: String?,
        hashtags: List<String>,
        modelName: String?,
        createdAt: Long,
    ): CaptionResult = CaptionResult(
        id = CaptionResultId(resultId),
        requestId = CaptionRequestId(requestId),
        draftId = PostDraftId(draftId),
        targetPlatform = enumFromWire(targetPlatform, TargetPlatform::fromWireValue),
        caption = caption,
        shortCaption = shortCaption,
        hashtags = hashtags,
        modelName = modelName,
        createdAtEpochMillis = createdAt,
    )

    fun altTextResult(
        resultId: String,
        draftId: String,
        mediaAssetId: String,
        altText: String,
        modelName: String?,
        createdAt: Long,
    ): AltTextResult = AltTextResult(
        id = AltTextResultId(resultId),
        draftId = PostDraftId(draftId),
        mediaAssetId = MediaAssetId(mediaAssetId),
        altText = altText,
        modelName = modelName,
        createdAtEpochMillis = createdAt,
    )

    fun photoEditRequest(
        requestId: String,
        draftId: String,
        sourceMediaAssetId: String,
        intent: String,
        realismLevel: String,
        qualityTier: String,
        prompt: String,
        userRefinement: String?,
        subjectDescription: String?,
        targetPlatform: String,
        maskRegion: String?,
        createdAt: Long,
    ): PhotoEditRequest = PhotoEditRequest(
        id = PhotoEditRequestId(requestId),
        draftId = PostDraftId(draftId),
        sourceMediaAssetId = MediaAssetId(sourceMediaAssetId),
        intent = enumFromWire(intent, EditIntent::fromWireValue),
        realismLevel = enumFromWire(realismLevel, RealismLevel::fromWireValue),
        qualityTier = enumFromWire(qualityTier, QualityTier::fromWireValue),
        prompt = prompt,
        userRefinement = userRefinement,
        subjectDescription = subjectDescription,
        targetPlatform = enumFromWire(targetPlatform, TargetPlatform::fromWireValue),
        maskRegion = maskRegion?.let { MaskRegion.parse(it) },
        createdAtEpochMillis = createdAt,
    )

    fun photoEditResult(
        resultId: String,
        requestId: String,
        draftId: String,
        editedMediaAssetId: String,
        summary: String?,
        modelName: String?,
        createdAt: Long,
        type: String,
        uri: String,
        mimeType: String?,
        widthPx: Long?,
        heightPx: Long?,
        mediaCreatedAt: Long,
    ): PhotoEditResult = PhotoEditResult(
        id = PhotoEditResultId(resultId),
        requestId = PhotoEditRequestId(requestId),
        draftId = PostDraftId(draftId),
        editedMediaAsset = mediaAsset(editedMediaAssetId, type, uri, mimeType, widthPx, heightPx, mediaCreatedAt),
        summary = summary,
        modelName = modelName,
        createdAtEpochMillis = createdAt,
    )

    fun promptHistoryEntry(
        entryId: String,
        draftId: String,
        operationType: String,
        prompt: String,
        responseSummary: String?,
        modelName: String?,
        createdAt: Long,
    ): PromptHistoryEntry = PromptHistoryEntry(
        id = PromptHistoryEntryId(entryId),
        draftId = PostDraftId(draftId),
        operationType = enumFromWire(operationType, AiOperationType::fromWireValue),
        prompt = SecretRedactor.redactOpenAiApiKeys(prompt),
        responseSummary = responseSummary?.let(SecretRedactor::redactOpenAiApiKeys),
        modelName = modelName,
        createdAtEpochMillis = createdAt,
    )

    fun exportRecord(
        recordId: String,
        draftId: String,
        targetPlatform: String,
        status: String,
        destinationUri: String?,
        errorMessage: String?,
        createdAt: Long,
        completedAt: Long?,
    ): ExportRecord = ExportRecord(
        id = ExportRecordId(recordId),
        draftId = PostDraftId(draftId),
        targetPlatform = enumFromWire(targetPlatform, TargetPlatform::fromWireValue),
        status = enumFromWire(status, ExportStatus::fromWireValue),
        destinationUri = destinationUri,
        errorMessage = errorMessage?.let(SecretRedactor::redactOpenAiApiKeys),
        createdAtEpochMillis = createdAt,
        completedAtEpochMillis = completedAt,
    )

    fun <T> enumFromWire(value: String, parser: (String) -> T?): T =
        parser(value) ?: error("Unknown persisted wire value: $value")
}
