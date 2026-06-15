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
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import com.digitumdei.shotquill.shared.domain.FinalPostContent
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
    /**
     * Persists the draft and replaces its owned child rows.
     *
     * Final post content is the exception to full replacement: when
     * [PostDraft.finalPostContent] is null the previously persisted content is
     * preserved rather than deleted. Share and archive flows persist final post
     * content separately and then save drafts that were loaded without it, so
     * deleting on null would silently discard the user's edited caption and alt
     * text. A non-null [PostDraft.finalPostContent] overwrites the stored row.
     *
     * Prompt history is also exempt from replacement: entries are immutable and
     * append-only, so [PostDraft.promptHistory] entries are inserted if new and
     * otherwise left untouched. Entries already persisted for the draft are
     * never deleted or rewritten by this method.
     */
    fun save(postDraft: PostDraft)
    fun get(id: PostDraftId): PostDraft?
    fun updateStatus(id: PostDraftId, status: DraftStatus, updatedAt: Instant): Boolean
    fun updateUpdatedAt(id: PostDraftId, updatedAt: Instant): Boolean
    fun replaceMediaItems(id: PostDraftId, mediaItems: List<MediaAssetId>): Boolean
    fun updateSelectedMediaAsset(id: PostDraftId, mediaAssetId: MediaAssetId?, updatedAt: Instant): UpdateSelectionResult
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
    fun listPromptHistoryForMediaAsset(id: MediaAssetId): List<PromptHistoryEntry>
}

interface ExportRepository {
    fun save(exportRecord: ExportRecord)
    fun get(id: ExportRecordId): ExportRecord?
    fun listExportRecordsForDraft(id: PostDraftId): List<ExportRecord>
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
    ExportRepository {
    fun saveVisionDescription(visionDescription: VisionDescription)
    fun saveCaptionRequest(captionRequest: CaptionRequest)
    fun saveCaptionResult(captionResult: CaptionResult)
    fun saveAltTextResult(altTextResult: AltTextResult)
    fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest)
    fun savePhotoEditResult(photoEditResult: PhotoEditResult)
    fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry)
    fun saveExportRecord(exportRecord: ExportRecord)
    fun saveFinalPostContent(finalPostContent: FinalPostContent)
    fun getFinalPostContent(draftId: PostDraftId): FinalPostContent?
    fun savePhotoEditSuccess(
        draftId: PostDraftId,
        editedMediaAsset: MediaAsset,
        editRequest: PhotoEditRequest,
        editResult: PhotoEditResult,
        promptHistoryEntry: PromptHistoryEntry,
        targetStatus: DraftStatus,
        updatedAt: Instant,
    ): PostDraft?
    fun savePhotoEditFailure(
        draftId: PostDraftId,
        editRequest: PhotoEditRequest,
        promptHistoryEntry: PromptHistoryEntry,
        updatedAt: Instant,
    ): PostDraft?
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
