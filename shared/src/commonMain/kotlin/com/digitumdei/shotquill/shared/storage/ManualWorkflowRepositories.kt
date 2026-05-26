package com.digitumdei.shotquill.shared.storage

import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.domain.CaptionRequest
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.VisionDescription

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
}

interface ManualWorkflowRepository : MediaAssetRepository, BrandProfileRepository, PostDraftRepository {
    fun saveVisionDescription(visionDescription: VisionDescription)
    fun saveCaptionRequest(captionRequest: CaptionRequest)
    fun saveCaptionResult(captionResult: CaptionResult)
    fun saveAltTextResult(altTextResult: AltTextResult)
    fun savePhotoEditRequest(photoEditRequest: PhotoEditRequest)
    fun savePhotoEditResult(photoEditResult: PhotoEditResult)
    fun savePromptHistoryEntry(promptHistoryEntry: PromptHistoryEntry)
    fun saveExportRecord(exportRecord: ExportRecord)
    fun clearAll()
}
