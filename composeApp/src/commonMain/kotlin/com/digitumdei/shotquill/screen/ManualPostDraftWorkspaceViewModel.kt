package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.shared.domain.AiOperationType
import com.digitumdei.shotquill.shared.domain.AltTextResult
import com.digitumdei.shotquill.shared.domain.AltTextResultId
import com.digitumdei.shotquill.shared.domain.CaptionDraft
import com.digitumdei.shotquill.shared.domain.CaptionRequest
import com.digitumdei.shotquill.shared.domain.CaptionRequestId
import com.digitumdei.shotquill.shared.domain.CaptionResult
import com.digitumdei.shotquill.shared.domain.CaptionResultId
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EditIntent
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PhotoEditRequest
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
import com.digitumdei.shotquill.shared.domain.PhotoEditResult
import com.digitumdei.shotquill.shared.domain.PhotoEditResultId
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntry
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import com.digitumdei.shotquill.shared.domain.QualityTier
import com.digitumdei.shotquill.shared.domain.RealismLevel
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.primaryMediaAsset
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import kotlinx.datetime.Instant

data class ManualPostDraftWorkspaceState(
    val draftId: PostDraftId,
    val originalPhotoUri: String?,
    val editedPhotoUri: String?,
    val generatedCaption: String?,
    val generatedAltText: String?,
    val targetPlatform: TargetPlatform?,
    val draftStatus: DraftStatus?,
    val promptHistory: List<PromptHistoryEntry>,
    val actions: ManualPostDraftWorkspaceActions,
    val statusMessage: String?,
    val isPromptHistoryVisible: Boolean,
)

data class ManualPostDraftWorkspaceActions(
    val canGeneratePostText: Boolean,
    val canEditPhotoWithAi: Boolean,
    val canCopyCaption: Boolean,
    val canCopyAltText: Boolean,
    val canShareOrExport: Boolean,
    val canViewPromptHistory: Boolean,
)

interface ManualDraftAiProvider {
    fun generatePostText(draft: PostDraft, targetPlatform: TargetPlatform, nowEpochMillis: Long): GeneratedPostText
    fun editPhoto(draft: PostDraft, nowEpochMillis: Long): GeneratedPhotoEdit
}

data class GeneratedPostText(
    val caption: String,
    val hashtags: List<String>,
    val altText: String,
    val captionPrompt: String,
    val altTextPrompt: String,
    val modelName: String,
)

data class GeneratedPhotoEdit(
    val editedMediaUri: String,
    val prompt: String,
    val summary: String,
    val modelName: String,
)

class FakeManualDraftAiProvider : ManualDraftAiProvider {
    override fun generatePostText(
        draft: PostDraft,
        targetPlatform: TargetPlatform,
        nowEpochMillis: Long,
    ): GeneratedPostText {
        val original = draft.primaryMediaAsset()
        return GeneratedPostText(
            caption = "Ready for ${targetPlatform.wireValue}: ${original.uri.substringAfterLast('/')}",
            hashtags = listOf("#shotquill", "#draft"),
            altText = "Photo prepared for ${targetPlatform.wireValue}.",
            captionPrompt = "Generate a manual post caption for ${targetPlatform.wireValue} from ${original.uri}.",
            altTextPrompt = "Generate accessible alt text for ${original.uri}.",
            modelName = "fake-manual-draft-ai",
        )
    }

    override fun editPhoto(draft: PostDraft, nowEpochMillis: Long): GeneratedPhotoEdit {
        val original = draft.primaryMediaAsset()
        return GeneratedPhotoEdit(
            editedMediaUri = "${original.uri}#edited-$nowEpochMillis",
            prompt = "Enhance the original photo while preserving the subject.",
            summary = "Created a polished preview edit.",
            modelName = "fake-manual-draft-ai",
        )
    }
}

class ManualPostDraftWorkspaceViewModel(
    private val draftId: PostDraftId,
    private val postDraftRepository: PostDraftRepository,
    private val aiProvider: ManualDraftAiProvider = FakeManualDraftAiProvider(),
    private val clock: EpochClock = EpochClock.Default,
    private val defaultTargetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
) {
    var state: ManualPostDraftWorkspaceState = unloadedState()
        private set

    fun load() {
        state = postDraftRepository.get(draftId)?.toState(
            statusMessage = null,
            isPromptHistoryVisible = state.isPromptHistoryVisible,
        ) ?: unloadedState(statusMessage = "Draft not found")
    }

    fun generatePostText() {
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        val now = clock.nowMillis()
        val platform = draft.targetPlatforms.firstOrNull() ?: defaultTargetPlatform
        val generated = aiProvider.generatePostText(draft, platform, now)
        val captionRequest = CaptionRequest(
            id = CaptionRequestId("caption-request-$now"),
            draftId = draft.id,
            targetPlatform = platform,
            prompt = generated.captionPrompt,
            tone = draft.brandProfile?.voice,
            brandProfileId = draft.brandProfile?.id,
            createdAtEpochMillis = now,
        )
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-$now"),
            requestId = captionRequest.id,
            draftId = draft.id,
            targetPlatform = platform,
            caption = generated.caption,
            hashtags = generated.hashtags,
            modelName = generated.modelName,
            createdAtEpochMillis = now,
        )
        val altText = AltTextResult(
            id = AltTextResultId("alt-text-$now"),
            draftId = draft.id,
            mediaAssetId = draft.primaryMediaAsset().id,
            altText = generated.altText,
            modelName = generated.modelName,
            createdAtEpochMillis = now,
        )
        val updated = draft.copy(
            status = if (draft.status == DraftStatus.PhotoEdited) DraftStatus.PhotoEdited else DraftStatus.TextGenerated,
            caption = CaptionDraft(generated.caption, generated.hashtags),
            targetPlatforms = draft.targetPlatforms + platform,
            captionRequests = draft.captionRequests + captionRequest,
            captionResults = draft.captionResults + captionResult,
            altTextResults = draft.altTextResults + altText,
            promptHistory = draft.promptHistory + listOf(
                PromptHistoryEntry(
                    id = PromptHistoryEntryId("prompt-caption-$now"),
                    draftId = draft.id,
                    operationType = AiOperationType.CaptionGeneration,
                    prompt = generated.captionPrompt,
                    responseSummary = generated.caption,
                    modelName = generated.modelName,
                    createdAtEpochMillis = now,
                ),
                PromptHistoryEntry(
                    id = PromptHistoryEntryId("prompt-alt-text-$now"),
                    draftId = draft.id,
                    operationType = AiOperationType.AltTextGeneration,
                    prompt = generated.altTextPrompt,
                    responseSummary = generated.altText,
                    modelName = generated.modelName,
                    createdAtEpochMillis = now,
                ),
            ),
            updatedAt = Instant.fromEpochMilliseconds(now),
        )
        postDraftRepository.save(updated)
        state = updated.toState("Generated caption and alt text", state.isPromptHistoryVisible)
    }

    fun editPhotoWithAi() {
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        val now = clock.nowMillis()
        val generated = aiProvider.editPhoto(draft, now)
        val original = draft.primaryMediaAsset()
        val request = PhotoEditRequest(
            id = PhotoEditRequestId("photo-edit-request-$now"),
            draftId = draft.id,
            sourceMediaAssetId = original.id,
            intent = EditIntent.Enhance,
            realismLevel = RealismLevel.Polished,
            qualityTier = QualityTier.Draft,
            prompt = generated.prompt,
            createdAtEpochMillis = now,
        )
        val editedMedia = MediaAsset(
            id = MediaAssetId("edited-media-$now"),
            type = MediaType.EditedPhoto,
            uri = generated.editedMediaUri,
            mimeType = original.mimeType,
            widthPx = original.widthPx,
            heightPx = original.heightPx,
            createdAtEpochMillis = now,
        )
        val result = PhotoEditResult(
            id = PhotoEditResultId("photo-edit-result-$now"),
            requestId = request.id,
            draftId = draft.id,
            editedMediaAsset = editedMedia,
            summary = generated.summary,
            modelName = generated.modelName,
            createdAtEpochMillis = now,
        )
        val updated = draft.copy(
            status = DraftStatus.PhotoEdited,
            photoEditRequests = draft.photoEditRequests + request,
            photoEditResults = draft.photoEditResults + result,
            promptHistory = draft.promptHistory + PromptHistoryEntry(
                id = PromptHistoryEntryId("prompt-photo-edit-$now"),
                draftId = draft.id,
                operationType = AiOperationType.PhotoEdit,
                prompt = generated.prompt,
                responseSummary = generated.summary,
                modelName = generated.modelName,
                createdAtEpochMillis = now,
            ),
            updatedAt = Instant.fromEpochMilliseconds(now),
        )
        postDraftRepository.save(updated)
        state = updated.toState("Edited photo preview created", state.isPromptHistoryVisible)
    }

    fun markCaptionCopied() {
        if (state.actions.canCopyCaption) {
            state = state.copy(statusMessage = "Caption copied")
        }
    }

    fun markAltTextCopied() {
        if (state.actions.canCopyAltText) {
            state = state.copy(statusMessage = "Alt text copied")
        }
    }

    fun markShareOrExportStarted() {
        if (state.actions.canShareOrExport) {
            state = state.copy(statusMessage = "Share/export ready")
        }
    }

    fun togglePromptHistory() {
        state = state.copy(isPromptHistoryVisible = !state.isPromptHistoryVisible)
    }

    private fun PostDraft.toState(
        statusMessage: String?,
        isPromptHistoryVisible: Boolean,
    ): ManualPostDraftWorkspaceState {
        val originalPhoto = mediaItems
            .map { it.mediaAsset }
            .firstOrNull { it.type == MediaType.Photo }
            ?: primaryMediaAsset()
        val editedPhoto = photoEditResults.maxByOrNull { it.createdAtEpochMillis }?.editedMediaAsset
        val captionText = caption?.text ?: captionResults.maxByOrNull { it.createdAtEpochMillis }?.caption
        val altText = altTextResults.maxByOrNull { it.createdAtEpochMillis }?.altText
        val canMutateDraft = status != DraftStatus.Archived && status != DraftStatus.Shared
        return ManualPostDraftWorkspaceState(
            draftId = id,
            originalPhotoUri = originalPhoto.uri,
            editedPhotoUri = editedPhoto?.uri,
            generatedCaption = captionText,
            generatedAltText = altText,
            targetPlatform = targetPlatforms.firstOrNull() ?: captionResults.lastOrNull()?.targetPlatform,
            draftStatus = status,
            promptHistory = promptHistory,
            actions = ManualPostDraftWorkspaceActions(
                canGeneratePostText = canMutateDraft,
                canEditPhotoWithAi = canMutateDraft,
                canCopyCaption = !captionText.isNullOrBlank(),
                canCopyAltText = !altText.isNullOrBlank(),
                canShareOrExport = canMutateDraft && !captionText.isNullOrBlank() && !altText.isNullOrBlank(),
                canViewPromptHistory = promptHistory.isNotEmpty(),
            ),
            statusMessage = statusMessage,
            isPromptHistoryVisible = isPromptHistoryVisible,
        )
    }

    private fun unloadedState(statusMessage: String? = null): ManualPostDraftWorkspaceState =
        ManualPostDraftWorkspaceState(
            draftId = draftId,
            originalPhotoUri = null,
            editedPhotoUri = null,
            generatedCaption = null,
            generatedAltText = null,
            targetPlatform = null,
            draftStatus = null,
            promptHistory = emptyList(),
            actions = ManualPostDraftWorkspaceActions(
                canGeneratePostText = false,
                canEditPhotoWithAi = false,
                canCopyCaption = false,
                canCopyAltText = false,
                canShareOrExport = false,
                canViewPromptHistory = false,
            ),
            statusMessage = statusMessage,
            isPromptHistoryVisible = false,
        )
}
