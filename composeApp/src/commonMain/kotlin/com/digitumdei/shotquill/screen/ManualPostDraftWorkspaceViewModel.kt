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
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import com.digitumdei.shotquill.shared.domain.ExportStatus
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PhotoEditPromptAssembler
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
import com.digitumdei.shotquill.shared.domain.VisionDescription
import com.digitumdei.shotquill.shared.domain.VisionDescriptionId
import com.digitumdei.shotquill.shared.domain.VisionDescriptionPromptFactory
import com.digitumdei.shotquill.shared.domain.primaryMediaAsset
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import com.digitumdei.shotquill.shared.workflow.PostTextGenerationError
import com.digitumdei.shotquill.shared.workflow.PostTextGenerationResult
import com.digitumdei.shotquill.shared.workflow.PostTextGenerator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.Instant

enum class PhotoEditFormOperationState {
    Idle,
    Loading,
    Error,
}

data class PhotoEditFormState(
    val selectedIntent: EditIntent,
    val userRefinementText: String,
    val selectedRealismLevel: RealismLevel,
    val selectedTargetPlatform: TargetPlatform,
    val selectedQualityTier: QualityTier,
    val qualityTierModelNotes: String,
    val qualityTierCostNotes: String,
    val unsupportedModelWarning: String?,
    val latestRequestId: PhotoEditRequestId?,
    val latestResultId: PhotoEditResultId?,
    val latestModelName: String?,
    val latestSummary: String?,
    val operationState: PhotoEditFormOperationState,
)

data class ManualPostDraftWorkspaceState(
    val draftId: PostDraftId,
    val originalPhotoUri: String?,
    val editedPhotoUri: String?,
    val visionDescription: String?,
    val generatedCaption: String?,
    val generatedAltText: String?,
    val targetPlatform: TargetPlatform?,
    val draftStatus: DraftStatus?,
    val promptHistory: List<PromptHistoryEntry>,
    val actions: ManualPostDraftWorkspaceActions,
    val statusMessage: String?,
    val isPromptHistoryVisible: Boolean,
    val photoEditForm: PhotoEditFormState,
)

data class ManualPostDraftWorkspaceActions(
    val canAnalyzeVision: Boolean,
    val canGeneratePostText: Boolean,
    val canEditPhotoWithAi: Boolean,
    val canCopyCaption: Boolean,
    val canCopyAltText: Boolean,
    val canShareOrExport: Boolean,
    val canViewPromptHistory: Boolean,
)

interface ManualDraftAiProvider {
    fun analyzeVision(draft: PostDraft, nowEpochMillis: Long): GeneratedVisionDescription
    fun generatePostText(draft: PostDraft, targetPlatform: TargetPlatform, nowEpochMillis: Long): GeneratedPostText
    fun editPhoto(draft: PostDraft, formState: PhotoEditFormState, nowEpochMillis: Long): GeneratedPhotoEdit
}

data class GeneratedVisionDescription(
    val description: String,
    val prompt: String,
    val modelName: String,
)

data class GeneratedPostText(
    val caption: String,
    val shortCaption: String?,
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
    override fun analyzeVision(draft: PostDraft, nowEpochMillis: Long): GeneratedVisionDescription {
        val original = draft.primaryMediaAsset()
        return GeneratedVisionDescription(
            description = "Photo shows ${original.uri.substringAfterLast('/')} prepared for social content.",
            prompt = VisionDescriptionPromptFactory.buildPrompt(original),
            modelName = "fake-manual-draft-ai",
        )
    }

    override fun generatePostText(
        draft: PostDraft,
        targetPlatform: TargetPlatform,
        nowEpochMillis: Long,
    ): GeneratedPostText {
        val original = draft.primaryMediaAsset()
        return GeneratedPostText(
            caption = "Ready for ${targetPlatform.wireValue}: ${original.uri.substringAfterLast('/')}",
            shortCaption = "Ready for ${targetPlatform.wireValue}",
            hashtags = listOf("#shotquill", "#draft"),
            altText = "Photo prepared for ${targetPlatform.wireValue}.",
            captionPrompt = "Generate a manual post caption for ${targetPlatform.wireValue} from ${original.uri}.",
            altTextPrompt = "Generate accessible alt text for ${original.uri}.",
            modelName = "fake-manual-draft-ai",
        )
    }

    override fun editPhoto(draft: PostDraft, formState: PhotoEditFormState, nowEpochMillis: Long): GeneratedPhotoEdit {
        val original = draft.primaryMediaAsset()
        val summaryDetails = listOfNotNull(
            "intent=${formState.selectedIntent.wireValue}",
            "platform=${formState.selectedTargetPlatform.wireValue}",
            "realism=${formState.selectedRealismLevel.wireValue}",
            "quality=${formState.selectedQualityTier.wireValue}",
            formState.userRefinementText.trim().takeIf { it.isNotEmpty() }?.let { "refinement=$it" },
        ).joinToString(",")
        return GeneratedPhotoEdit(
            editedMediaUri = "${original.uri}#edited-${formState.selectedIntent.wireValue}-${formState.selectedTargetPlatform.wireValue}-$nowEpochMillis",
            prompt = "Edit the image (${formState.selectedIntent.wireValue}, ${formState.selectedTargetPlatform.wireValue}).",
            summary = "Fake preview: $summaryDetails",
            modelName = "fake-manual-draft-ai",
        )
    }
}

class ManualPostDraftWorkspaceViewModel(
    private val draftId: PostDraftId,
    private val postDraftRepository: PostDraftRepository,
    private val aiProvider: ManualDraftAiProvider = FakeManualDraftAiProvider(),
    private val postTextGenerator: PostTextGenerator? = null,
    private val clock: EpochClock = EpochClock.Default,
    private val defaultTargetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
    private val defaultRealismLevel: RealismLevel = RealismLevel.Photoreal,
    private val defaultQualityTier: QualityTier = QualityTier.Standard,
) {
    var state: ManualPostDraftWorkspaceState by mutableStateOf(unloadedState())
        private set
    private var operationSequence = 0

    fun load() {
        state = postDraftRepository.get(draftId)?.toState(
            statusMessage = null,
            isPromptHistoryVisible = state.isPromptHistoryVisible,
        ) ?: unloadedState(statusMessage = "Draft not found")
    }

    fun analyzeVisionDescription() {
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        draft.visionDescription?.let {
            state = draft.toState("Reused cached vision description", state.isPromptHistoryVisible)
            return
        }
        val now = clock.nowMillis()
        val operationUpdatedAt = operationUpdatedAt(draft, now)
        if (draft.status !in mutableDraftStatuses) {
            state = draft.toState(
                statusMessage = "Cannot analyze vision while status is ${draft.status.wireValue}",
                isPromptHistoryVisible = state.isPromptHistoryVisible,
            )
            return
        }
        val idSuffix = nextIdSuffix(now)
        val generated = aiProvider.analyzeVision(draft, now)
        val original = draft.primaryMediaAsset()
        val visionDescription = VisionDescription(
            id = VisionDescriptionId("vision-description-$idSuffix"),
            draftId = draft.id,
            mediaAssetId = original.id,
            description = generated.description,
            modelName = generated.modelName,
            createdAtEpochMillis = now,
        )
        val updated = draft.copy(
            visionDescription = visionDescription,
            promptHistory = draft.promptHistory + PromptHistoryEntry(
                id = PromptHistoryEntryId("prompt-vision-description-$idSuffix"),
                draftId = draft.id,
                operationType = AiOperationType.VisionDescription,
                prompt = generated.prompt,
                responseSummary = generated.description,
                modelName = generated.modelName,
                createdAtEpochMillis = now,
            ),
            updatedAt = operationUpdatedAt,
        )
        postDraftRepository.save(updated)
        state = updated.toState("Analyzed photo", state.isPromptHistoryVisible)
    }

    fun generatePostText() {
        postTextGenerator?.let {
            generatePostTextWithPipeline(it)
            return
        }
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        val now = clock.nowMillis()
        val nextStatus = if (draft.status == DraftStatus.PhotoEdited) DraftStatus.PhotoEdited else DraftStatus.TextGenerated
        val operationUpdatedAt = operationUpdatedAt(draft, now)
        if (!draft.canUpdateOrTransitionTo(nextStatus)) {
            state = draft.toState(
                statusMessage = "Cannot generate text while status is ${draft.status.wireValue}",
                isPromptHistoryVisible = state.isPromptHistoryVisible,
            )
            return
        }
        val platform = draft.preferredTargetPlatform() ?: defaultTargetPlatform
        val idSuffix = nextIdSuffix(now)
        val generated = aiProvider.generatePostText(draft, platform, now)
        val captionRequest = CaptionRequest(
            id = CaptionRequestId("caption-request-$idSuffix"),
            draftId = draft.id,
            targetPlatform = platform,
            prompt = generated.captionPrompt,
            tone = draft.brandProfile?.voice,
            brandProfileId = draft.brandProfile?.id,
            createdAtEpochMillis = now,
        )
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-$idSuffix"),
            requestId = captionRequest.id,
            draftId = draft.id,
            targetPlatform = platform,
            caption = generated.caption,
            shortCaption = generated.shortCaption?.trim()?.takeIf { it.isNotEmpty() },
            hashtags = generated.hashtags,
            modelName = generated.modelName,
            createdAtEpochMillis = now,
        )
        val altText = AltTextResult(
            id = AltTextResultId("alt-text-$idSuffix"),
            draftId = draft.id,
            mediaAssetId = draft.primaryMediaAsset().id,
            altText = generated.altText,
            modelName = generated.modelName,
            createdAtEpochMillis = now,
        )
        val transitioned = if (draft.status == nextStatus) {
            draft.copy(updatedAt = operationUpdatedAt)
        } else {
            draft.transitionTo(nextStatus, operationUpdatedAt)
        }
        val updated = transitioned.copy(
            caption = CaptionDraft(generated.caption, generated.hashtags),
            targetPlatforms = draft.targetPlatforms + platform,
            captionRequests = draft.captionRequests + captionRequest,
            captionResults = draft.captionResults + captionResult,
            altTextResults = draft.altTextResults + altText,
            promptHistory = draft.promptHistory + listOf(
                PromptHistoryEntry(
                    id = PromptHistoryEntryId("prompt-caption-$idSuffix"),
                    draftId = draft.id,
                    operationType = AiOperationType.CaptionGeneration,
                    prompt = generated.captionPrompt,
                    responseSummary = generated.caption,
                    modelName = generated.modelName,
                    createdAtEpochMillis = now,
                ),
                PromptHistoryEntry(
                    id = PromptHistoryEntryId("prompt-alt-text-$idSuffix"),
                    draftId = draft.id,
                    operationType = AiOperationType.AltTextGeneration,
                    prompt = generated.altTextPrompt,
                    responseSummary = generated.altText,
                    modelName = generated.modelName,
                    createdAtEpochMillis = now,
                ),
            ),
        )
        postDraftRepository.save(updated)
        state = updated.toState("Generated caption and alt text", state.isPromptHistoryVisible)
    }

    private fun generatePostTextWithPipeline(generator: PostTextGenerator) {
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        val platform = draft.preferredTargetPlatform() ?: defaultTargetPlatform
        when (val result = generator.generateText(draftId, platform)) {
            is PostTextGenerationResult.Success -> {
                state = result.draft.toState("Generated caption and alt text", state.isPromptHistoryVisible)
            }
            is PostTextGenerationResult.Failure -> {
                state = draft.toState(result.error.statusMessage(), state.isPromptHistoryVisible)
            }
        }
    }

    fun updatePhotoEditIntent(intent: EditIntent) {
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(
                selectedIntent = intent,
                unsupportedModelWarning = unsupportedModelWarningForIntent(intent),
            ),
        )
    }

    fun updatePhotoEditRefinement(refinement: String) {
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(userRefinementText = refinement),
        )
    }

    fun updatePhotoEditRealism(realism: RealismLevel) {
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(selectedRealismLevel = realism),
        )
    }

    fun updatePhotoEditTargetPlatform(platform: TargetPlatform) {
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(selectedTargetPlatform = platform),
        )
    }

    fun updatePhotoEditQualityTier(quality: QualityTier) {
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(
                selectedQualityTier = quality,
                qualityTierModelNotes = quality.modelMappingNote,
                qualityTierCostNotes = quality.costNote,
            ),
        )
    }

    fun editPhotoWithAi() {
        val draft = postDraftRepository.get(draftId) ?: run {
            state = state.copy(
                statusMessage = "Draft not found",
                photoEditForm = state.photoEditForm.copy(operationState = PhotoEditFormOperationState.Error),
                actions = state.actions.copy(canEditPhotoWithAi = false),
            )
            return
        }
        val now = clock.nowMillis()
        val operationUpdatedAt = operationUpdatedAt(draft, now)
        if (!draft.canUpdateOrTransitionTo(DraftStatus.PhotoEdited)) {
            state = state.copy(
                statusMessage = "Cannot edit photo while status is ${draft.status.wireValue}",
                photoEditForm = state.photoEditForm.copy(operationState = PhotoEditFormOperationState.Error),
                actions = state.actions.copy(canEditPhotoWithAi = false),
            )
            return
        }
        state = state.copy(
            statusMessage = "Editing photo with AI...",
            photoEditForm = state.photoEditForm.copy(operationState = PhotoEditFormOperationState.Loading),
            actions = state.actions.copy(canEditPhotoWithAi = false),
        )
        val currentForm = state.photoEditForm
        try {
            val idSuffix = nextIdSuffix(now)
            val generated = aiProvider.editPhoto(draft, currentForm, now)
            val original = draft.primaryMediaAsset()
            val request = PhotoEditRequest(
                id = PhotoEditRequestId("photo-edit-request-$idSuffix"),
                draftId = draft.id,
                sourceMediaAssetId = original.id,
                intent = currentForm.selectedIntent,
                realismLevel = currentForm.selectedRealismLevel,
                qualityTier = currentForm.selectedQualityTier,
                prompt = generated.prompt,
                userRefinement = currentForm.userRefinementText.trim().takeIf { it.isNotEmpty() },
                subjectDescription = draft.visionDescription?.description,
                targetPlatform = currentForm.selectedTargetPlatform,
                maskRegion = null,
                createdAtEpochMillis = now,
            )
            val assembledPrompt = PhotoEditPromptAssembler.assemble(request)
            val editedMedia = MediaAsset(
                id = MediaAssetId("edited-media-$idSuffix"),
                type = MediaType.EditedPhoto,
                uri = generated.editedMediaUri,
                mimeType = original.mimeType,
                widthPx = original.widthPx,
                heightPx = original.heightPx,
                createdAtEpochMillis = now,
            )
            val result = PhotoEditResult(
                id = PhotoEditResultId("photo-edit-result-$idSuffix"),
                requestId = request.id,
                draftId = draft.id,
                editedMediaAsset = editedMedia,
                summary = generated.summary,
                modelName = generated.modelName,
                createdAtEpochMillis = now,
            )
            val transitioned = if (draft.status == DraftStatus.PhotoEdited) {
                draft.copy(updatedAt = operationUpdatedAt)
            } else {
                draft.transitionTo(DraftStatus.PhotoEdited, operationUpdatedAt)
            }
            val updated = transitioned.copy(
                photoEditRequests = draft.photoEditRequests + request,
                photoEditResults = draft.photoEditResults + result,
                promptHistory = draft.promptHistory + PromptHistoryEntry(
                    id = PromptHistoryEntryId("prompt-photo-edit-$idSuffix"),
                    draftId = draft.id,
                    operationType = AiOperationType.PhotoEdit,
                    prompt = assembledPrompt,
                    responseSummary = generated.summary,
                    modelName = generated.modelName,
                    createdAtEpochMillis = now,
                ),
            )
            postDraftRepository.save(updated)
            state = updated.toState("Edited photo preview created", state.isPromptHistoryVisible)
            state = state.copy(
                photoEditForm = state.photoEditForm.copy(operationState = PhotoEditFormOperationState.Idle),
            )
        } catch (e: Exception) {
            state = state.copy(
                statusMessage = "Photo edit failed: ${e.message ?: "Unknown error"}",
                photoEditForm = currentForm.copy(operationState = PhotoEditFormOperationState.Error),
                actions = state.actions.copy(canEditPhotoWithAi = state.draftStatus in mutableDraftStatuses),
            )
        }
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
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        if (!state.actions.canShareOrExport || !draft.canUpdateOrTransitionTo(DraftStatus.ReadyToShare)) {
            state = draft.toState(
                statusMessage = "Cannot share/export while status is ${draft.status.wireValue}",
                isPromptHistoryVisible = state.isPromptHistoryVisible,
            )
            return
        }
        val now = clock.nowMillis()
        val operationUpdatedAt = operationUpdatedAt(draft, now)
        val platform = draft.preferredTargetPlatform() ?: defaultTargetPlatform
        val idSuffix = nextIdSuffix(now)
        val exportRecord = ExportRecord(
            id = ExportRecordId("export-$idSuffix"),
            draftId = draft.id,
            targetPlatform = platform,
            status = ExportStatus.Pending,
            destinationUri = null,
            errorMessage = null,
            createdAtEpochMillis = now,
            completedAtEpochMillis = null,
        )
        val transitioned = if (draft.status == DraftStatus.ReadyToShare) {
            draft.copy(updatedAt = operationUpdatedAt)
        } else {
            draft.transitionTo(DraftStatus.ReadyToShare, operationUpdatedAt)
        }
        val updated = transitioned.copy(
            targetPlatforms = draft.targetPlatforms + platform,
            exportRecords = draft.exportRecords + exportRecord,
        )
        postDraftRepository.save(updated)
        state = updated.toState("Share/export ready", state.isPromptHistoryVisible)
    }

    fun togglePromptHistory() {
        state = state.copy(isPromptHistoryVisible = !state.isPromptHistoryVisible)
    }

    private fun PostDraft.toState(
        statusMessage: String?,
        isPromptHistoryVisible: Boolean,
    ): ManualPostDraftWorkspaceState {
        val originalPhoto = mediaItems
            .firstOrNull { it.mediaAsset.type == MediaType.Photo }
            ?.mediaAsset
        val editedPhoto = photoEditResults.maxByOrNull { it.createdAtEpochMillis }?.editedMediaAsset
        val captionText = caption?.text ?: captionResults.maxByOrNull { it.createdAtEpochMillis }?.caption
        val altText = altTextResults.maxByOrNull { it.createdAtEpochMillis }?.altText
        val canMutateDraft = status in mutableDraftStatuses
        val platform = preferredTargetPlatform() ?: defaultTargetPlatform
        val latestRequest = photoEditRequests.maxByOrNull { it.createdAtEpochMillis }
        val latestResult = photoEditResults.maxByOrNull { it.createdAtEpochMillis }
        return ManualPostDraftWorkspaceState(
            draftId = id,
            originalPhotoUri = originalPhoto?.uri,
            editedPhotoUri = editedPhoto?.uri,
            visionDescription = visionDescription?.description,
            generatedCaption = captionText,
            generatedAltText = altText,
            targetPlatform = platform,
            draftStatus = status,
            promptHistory = promptHistory,
            actions = ManualPostDraftWorkspaceActions(
                canAnalyzeVision = canMutateDraft,
                canGeneratePostText = canMutateDraft,
                canEditPhotoWithAi = canMutateDraft,
                canCopyCaption = !captionText.isNullOrBlank(),
                canCopyAltText = !altText.isNullOrBlank(),
                canShareOrExport = canMutateDraft && !captionText.isNullOrBlank() && !altText.isNullOrBlank(),
                canViewPromptHistory = promptHistory.isNotEmpty(),
            ),
            statusMessage = statusMessage,
            isPromptHistoryVisible = isPromptHistoryVisible,
            photoEditForm = PhotoEditFormState(
                selectedIntent = latestRequest?.intent ?: EditIntent.ImproveLighting,
                userRefinementText = latestRequest?.userRefinement ?: "",
                selectedRealismLevel = latestRequest?.realismLevel ?: defaultRealismLevel,
                selectedTargetPlatform = latestRequest?.targetPlatform ?: platform,
                selectedQualityTier = latestRequest?.qualityTier ?: defaultQualityTier,
                qualityTierModelNotes = (latestRequest?.qualityTier ?: defaultQualityTier).modelMappingNote,
                qualityTierCostNotes = (latestRequest?.qualityTier ?: defaultQualityTier).costNote,
                latestRequestId = latestRequest?.id,
                latestResultId = latestResult?.id,
                latestModelName = latestResult?.modelName,
                latestSummary = latestResult?.summary,
                unsupportedModelWarning = unsupportedModelWarningForIntent(latestRequest?.intent ?: EditIntent.ImproveLighting),
                operationState = PhotoEditFormOperationState.Idle,
            ),
        )
    }

    private fun unloadedState(statusMessage: String? = null): ManualPostDraftWorkspaceState =
        ManualPostDraftWorkspaceState(
            draftId = draftId,
            originalPhotoUri = null,
            editedPhotoUri = null,
            visionDescription = null,
            generatedCaption = null,
            generatedAltText = null,
            targetPlatform = null,
            draftStatus = null,
            promptHistory = emptyList(),
            actions = ManualPostDraftWorkspaceActions(
                canAnalyzeVision = false,
                canGeneratePostText = false,
                canEditPhotoWithAi = false,
                canCopyCaption = false,
                canCopyAltText = false,
                canShareOrExport = false,
                canViewPromptHistory = false,
            ),
            statusMessage = statusMessage,
            isPromptHistoryVisible = false,
            photoEditForm = PhotoEditFormState(
                selectedIntent = EditIntent.ImproveLighting,
                userRefinementText = "",
                selectedRealismLevel = defaultRealismLevel,
                selectedTargetPlatform = defaultTargetPlatform,
                selectedQualityTier = defaultQualityTier,
                qualityTierModelNotes = defaultQualityTier.modelMappingNote,
                qualityTierCostNotes = defaultQualityTier.costNote,
                unsupportedModelWarning = null,
                latestRequestId = null,
                latestResultId = null,
                latestModelName = null,
                latestSummary = null,
                operationState = PhotoEditFormOperationState.Idle,
            ),
        )

    private fun PostDraft.preferredTargetPlatform(): TargetPlatform? =
        targetPlatforms.sortedBy { it.wireValue }.firstOrNull()

    private fun PostDraft.canUpdateOrTransitionTo(nextStatus: DraftStatus): Boolean =
        status == nextStatus || status.canTransitionTo(nextStatus)

    private fun operationUpdatedAt(draft: PostDraft, nowEpochMillis: Long): Instant {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return if (now >= draft.updatedAt) now else draft.updatedAt
    }

    private fun unsupportedModelWarningForIntent(intent: EditIntent): String? {
        return null
    }

    private fun nextIdSuffix(nowEpochMillis: Long): String =
        "$nowEpochMillis-${operationSequence++}"

    private fun PostTextGenerationError.statusMessage(): String =
        when (this) {
            PostTextGenerationError.DraftNotFound -> "Draft not found"
            is PostTextGenerationError.InvalidDraftStatus -> "Cannot generate text while status is ${status.wireValue}"
            is PostTextGenerationError.Provider -> "Unable to generate text: ${error.userMessage}"
        }

    private companion object {
        val mutableDraftStatuses = setOf(
            DraftStatus.PhotoAdded,
            DraftStatus.TextGenerated,
            DraftStatus.PhotoEdited,
            DraftStatus.ReadyToShare,
        )
    }
}
