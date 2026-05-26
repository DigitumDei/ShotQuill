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
            postDraft.visionDescription?.let { saveVisionDescription(it) }
            postDraft.captionRequests.forEach { saveCaptionRequest(it) }
            postDraft.captionResults.forEach { saveCaptionResult(it) }
            postDraft.altTextResults.forEach { saveAltTextResult(it) }
            postDraft.photoEditRequests.forEach { savePhotoEditRequest(it) }
            postDraft.photoEditResults.forEach { savePhotoEditResult(it) }
            postDraft.promptHistory.forEach { savePromptHistoryEntry(it) }
            postDraft.exportRecords.forEach { saveExportRecord(it) }
        }
    }

    override fun get(id: PostDraftId): PostDraft? {
        val draft = queries.selectPostDraftById(id.value) { draftId, format, status, captionText, brandProfileId, createdAt, updatedAt ->
            DraftRow(
                id = draftId,
                format = format,
                status = status,
                captionText = captionText,
                brandProfileId = brandProfileId,
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
            status = enumFromWire(draft.status, DraftStatus::fromWireValue),
            mediaItems = mediaItems,
            caption = draft.captionText?.let {
                CaptionDraft(it, queries.selectPostDraftCaptionHashtags(id.value).executeAsList())
            },
            targetPlatforms = queries.selectPostDraftTargetPlatforms(id.value)
                .executeAsList()
                .mapTo(linkedSetOf()) { enumFromWire(it, TargetPlatform::fromWireValue) },
            brandProfile = draft.brandProfileId?.let { selectBrandProfile(BrandProfileId(it)) },
            visionDescription = selectVisionDescriptions(id).firstOrNull(),
            captionRequests = selectCaptionRequests(id),
            captionResults = selectCaptionResults(id),
            altTextResults = selectAltTextResults(id),
            photoEditRequests = selectPhotoEditRequests(id),
            photoEditResults = selectPhotoEditResults(id),
            promptHistory = selectPromptHistoryEntries(id),
            exportRecords = selectExportRecords(id),
            createdAt = Instant.fromEpochMilliseconds(draft.createdAt),
            updatedAt = Instant.fromEpochMilliseconds(draft.updatedAt),
        )
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

    override fun saveCaptionResult(captionResult: CaptionResult) {
        queries.transaction {
            queries.upsertCaptionResult(
                id = captionResult.id.value,
                request_id = captionResult.requestId.value,
                draft_id = captionResult.draftId.value,
                target_platform = captionResult.targetPlatform.wireValue,
                caption = captionResult.caption,
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

    override fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest) {
        queries.upsertPhotoEditRequest(
            id = photoEditRequest.id.value,
            draft_id = photoEditRequest.draftId.value,
            source_media_asset_id = photoEditRequest.sourceMediaAssetId.value,
            intent = photoEditRequest.intent.wireValue,
            realism_level = photoEditRequest.realismLevel.wireValue,
            quality_tier = photoEditRequest.qualityTier.wireValue,
            prompt = photoEditRequest.prompt,
            created_at_epoch_millis = photoEditRequest.createdAtEpochMillis,
        )
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

    override fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry) {
        queries.upsertPromptHistoryEntry(
            id = promptHistoryEntry.id.value,
            draft_id = promptHistoryEntry.draftId.value,
            operation_type = promptHistoryEntry.operationType.wireValue,
            prompt = promptHistoryEntry.prompt,
            response_summary = promptHistoryEntry.responseSummary,
            model_name = promptHistoryEntry.modelName,
            created_at_epoch_millis = promptHistoryEntry.createdAtEpochMillis,
        )
    }

    override fun saveExportRecord(exportRecord: ExportRecord) {
        queries.upsertExportRecord(
            id = exportRecord.id.value,
            draft_id = exportRecord.draftId.value,
            target_platform = exportRecord.targetPlatform.wireValue,
            status = exportRecord.status.wireValue,
            destination_uri = exportRecord.destinationUri,
            error_message = exportRecord.errorMessage,
            created_at_epoch_millis = exportRecord.createdAtEpochMillis,
            completed_at_epoch_millis = exportRecord.completedAtEpochMillis,
        )
    }

    override fun clearAll() {
        queries.transaction {
            queries.deleteAllExportRecords()
            queries.deleteAllPromptHistoryEntries()
            queries.deleteAllPhotoEditResults()
            queries.deleteAllPhotoEditRequests()
            queries.deleteAllAltTextResults()
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
                createdAt,
                updatedAt,
            ->
            BrandProfileRow(profileId, displayName, voice, audience, createdAt, updatedAt)
        }.executeAsOneOrNull() ?: return null

        val defaultHashtags = queries.selectBrandProfileDefaultHashtags(id.value).executeAsList()
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
            VisionDescription(
                id = VisionDescriptionId(visionId),
                draftId = PostDraftId(draftId),
                mediaAssetId = MediaAssetId(mediaAssetId),
                description = description,
                modelName = modelName,
                createdAtEpochMillis = createdAt,
            )
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
            CaptionRequest(
                id = CaptionRequestId(requestId),
                draftId = PostDraftId(draftId),
                targetPlatform = enumFromWire(targetPlatform, TargetPlatform::fromWireValue),
                prompt = prompt,
                tone = tone,
                brandProfileId = brandProfileId?.let(::BrandProfileId),
                createdAtEpochMillis = createdAt,
            )
        }.executeAsList()

    private fun selectCaptionResults(id: PostDraftId): List<CaptionResult> =
        queries.selectCaptionResultsByDraftId(id.value) {
                resultId,
                requestId,
                draftId,
                targetPlatform,
                caption,
                modelName,
                createdAt,
            ->
            CaptionResult(
                id = CaptionResultId(resultId),
                requestId = CaptionRequestId(requestId),
                draftId = PostDraftId(draftId),
                targetPlatform = enumFromWire(targetPlatform, TargetPlatform::fromWireValue),
                caption = caption,
                hashtags = queries.selectCaptionResultHashtags(resultId).executeAsList(),
                modelName = modelName,
                createdAtEpochMillis = createdAt,
            )
        }.executeAsList()

    private fun selectAltTextResults(id: PostDraftId): List<AltTextResult> =
        queries.selectAltTextResultsByDraftId(id.value) {
                resultId,
                draftId,
                mediaAssetId,
                altText,
                modelName,
                createdAt,
            ->
            AltTextResult(
                id = AltTextResultId(resultId),
                draftId = PostDraftId(draftId),
                mediaAssetId = MediaAssetId(mediaAssetId),
                altText = altText,
                modelName = modelName,
                createdAtEpochMillis = createdAt,
            )
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
                createdAt,
            ->
            PhotoEditRequest(
                id = PhotoEditRequestId(requestId),
                draftId = PostDraftId(draftId),
                sourceMediaAssetId = MediaAssetId(sourceMediaAssetId),
                intent = enumFromWire(intent, EditIntent::fromWireValue),
                realismLevel = enumFromWire(realismLevel, RealismLevel::fromWireValue),
                qualityTier = enumFromWire(qualityTier, QualityTier::fromWireValue),
                prompt = prompt,
                createdAtEpochMillis = createdAt,
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
            PhotoEditResult(
                id = PhotoEditResultId(resultId),
                requestId = PhotoEditRequestId(requestId),
                draftId = PostDraftId(draftId),
                editedMediaAsset = mediaAsset(editedMediaAssetId, type, uri, mimeType, widthPx, heightPx, mediaCreatedAt),
                summary = summary,
                modelName = modelName,
                createdAtEpochMillis = createdAt,
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
            PromptHistoryEntry(
                id = PromptHistoryEntryId(entryId),
                draftId = PostDraftId(draftId),
                operationType = enumFromWire(operationType, AiOperationType::fromWireValue),
                prompt = prompt,
                responseSummary = responseSummary,
                modelName = modelName,
                createdAtEpochMillis = createdAt,
            )
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
            ExportRecord(
                id = ExportRecordId(recordId),
                draftId = PostDraftId(draftId),
                targetPlatform = enumFromWire(targetPlatform, TargetPlatform::fromWireValue),
                status = enumFromWire(status, ExportStatus::fromWireValue),
                destinationUri = destinationUri,
                errorMessage = errorMessage,
                createdAtEpochMillis = createdAt,
                completedAtEpochMillis = completedAt,
            )
        }.executeAsList()

    private fun mediaAsset(
        id: String,
        type: String,
        uri: String,
        mimeType: String?,
        widthPx: Long?,
        heightPx: Long?,
        createdAt: Long,
    ): MediaAsset = MediaAsset(
        id = MediaAssetId(id),
        type = enumFromWire(type, MediaType::fromWireValue),
        uri = uri,
        mimeType = mimeType,
        widthPx = widthPx?.toInt(),
        heightPx = heightPx?.toInt(),
        createdAtEpochMillis = createdAt,
    )

    private fun <T> enumFromWire(value: String, parser: (String) -> T?): T =
        parser(value) ?: error("Unknown persisted wire value: $value")
}

private data class DraftRow(
    val id: String,
    val format: String,
    val status: String,
    val captionText: String?,
    val brandProfileId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

private data class BrandProfileRow(
    val id: String,
    val displayName: String,
    val voice: String,
    val audience: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
