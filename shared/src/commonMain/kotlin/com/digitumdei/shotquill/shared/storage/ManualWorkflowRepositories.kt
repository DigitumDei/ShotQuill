package com.digitumdei.shotquill.shared.storage

import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.CaptionRequest
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PhotoEditResultId
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.VisionDescriptionId
import com.digitumdei.shotquill.shared.domain.AltTextResultId
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.AiFailureRecord
import com.digitumdei.shotquill.shared.domain.AiFailureRecordId
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import kotlinx.datetime.Instant

interface MediaAssetRepository {
    fun save(mediaAsset: MediaAsset)
    fun get(id: MediaAssetId): MediaAsset?
}

interface BrandProfileRepository {
    fun save(brandProfile: BrandProfile)
    fun get(id: BrandProfileId): BrandProfile?
}

interface PostDraftRepository {
    fun save(postDraft: PostDraft)
    fun get(id: PostDraftId): PostDraft?
    fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean
    fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean
}

interface VisionDescriptionRepository {
    fun save(visionDescription: VisionDescription)
    fun get(id: VisionDescriptionId): VisionDescription?
    fun listVisionDescriptionsForDraft(id: PostDraftId): List<VisionDescription>
}

interface CaptionRepository {
    fun save(captionRequest: CaptionRequest)
    fun getCaptionRequest(id: CaptionRequestId): CaptionRequest?
    fun listCaptionRequestsForDraft(id: PostDraftId): List<CaptionRequest>
    fun save(captionResult: CaptionResult)
    fun getCaptionResult(id: CaptionResultId): CaptionResult?
    fun listCaptionResultsForDraft(id: PostDraftId): List<CaptionResult>
}

interface AltTextRepository {
    fun save(altTextResult: AltTextResult)
    fun get(id: AltTextResultId): AltTextResult?
    fun listAltTextResultsForDraft(id: PostDraftId): List<AltTextResult>
}

interface PhotoEditRepository {
    fun save(photoEditRequest: PhotoEditRequest)
    fun getPhotoEditRequest(id: PhotoEditRequestId): PhotoEditRequest?
    fun listPhotoEditRequestsForDraft(id: PostDraftId): List<PhotoEditRequest>
    fun save(photoEditResult: PhotoEditResult)
    fun getPhotoEditResult(id: PhotoEditResultId): PhotoEditResult?
    fun listPhotoEditResultsForDraft(id: PostDraftId): List<PhotoEditResult>
}

interface PromptHistoryRepository {
    fun save(promptHistoryEntry: PromptHistoryEntry)
    fun get(id: PromptHistoryEntryId): PromptHistoryEntry?
    fun listPromptHistoryForDraft(id: PostDraftId): List<PromptHistoryEntry>
}

interface ExportRepository {
    fun save(exportRecord: ExportRecord)
    fun get(id: ExportRecordId): ExportRecord?
    fun listExportRecordsForDraft(id: PostDraftId): List<ExportRecord>
}

interface AiFailureRepository {
    fun save(aiFailureRecord: AiFailureRecord)
    fun get(id: AiFailureRecordId): AiFailureRecord?
    fun listAiFailureRecordsForDraft(id: PostDraftId): List<AiFailureRecord>
}

interface ManualWorkflowRepository :
    MediaAssetRepository,
    BrandProfileRepository,
    PostDraftRepository,
    VisionDescriptionRepository,
    CaptionRepository,
    AltTextRepository,
    PhotoEditRepository,
    PromptHistoryRepository,
    ExportRepository,
    AiFailureRepository {
    fun saveVisionDescription(visionDescription: VisionDescription)
    fun saveCaptionRequest(captionRequest: CaptionRequest)
    fun saveCaptionResult(captionResult: CaptionResult)
    fun saveAltTextResult(altTextResult: AltTextResult)
    fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest)
    fun savePhotoEditResult(photoEditResult: PhotoEditResult)
    fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry)
    fun saveExportRecord(exportRecord: ExportRecord)
    fun saveAiFailureRecord(aiFailureRecord: AiFailureRecord)
    fun recordPostTextGeneration(
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
    ): PostDraft?
    fun clearAll()
}
