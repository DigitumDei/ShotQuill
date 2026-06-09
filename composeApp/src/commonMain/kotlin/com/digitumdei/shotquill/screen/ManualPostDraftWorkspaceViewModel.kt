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
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PhotoEditRequestId
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
import com.digitumdei.shotquill.shared.storage.UpdateSelectionResult
import com.digitumdei.shotquill.shared.workflow.AnalyzeVision
import com.digitumdei.shotquill.shared.workflow.PhotoEditExecutionError
import com.digitumdei.shotquill.shared.workflow.PhotoEditExecutionResult
import com.digitumdei.shotquill.shared.workflow.PhotoEditExecutor
import com.digitumdei.shotquill.shared.workflow.PostTextGenerationError
import com.digitumdei.shotquill.shared.workflow.PostTextGenerationResult
import com.digitumdei.shotquill.shared.workflow.PostTextGenerator
import com.digitumdei.shotquill.shared.workflow.VisionDescriptionAnalysisError
import com.digitumdei.shotquill.shared.workflow.VisionDescriptionAnalysisResult
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
    val activePhotoUri: String?,
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
    val canSelectEditedPhoto: Boolean,
    val canSelectOriginalPhoto: Boolean,
)

class ManualPostDraftWorkspaceViewModel(
    private val draftId: PostDraftId,
    private val postDraftRepository: PostDraftRepository,
    private val analyzeVision: AnalyzeVision? = null,
    private val postTextGenerator: PostTextGenerator? = null,
    private val photoEditExecutor: PhotoEditExecutor? = null,
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
        val analyzer = analyzeVision ?: run {
            state = state.copy(statusMessage = "Vision analysis not available")
            return
        }
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        if (draft.status !in mutableDraftStatuses) {
            state = draft.toState(
                statusMessage = "Cannot analyze vision while status is ${draft.status.wireValue}",
                isPromptHistoryVisible = state.isPromptHistoryVisible,
            )
            return
        }
        when (val result = analyzer.analyzePrimaryPhoto(draftId)) {
            is VisionDescriptionAnalysisResult.Success -> {
                val msg = if (result.cacheHit) "Reused cached vision description" else "Analyzed photo"
                state = (postDraftRepository.get(draftId)?.toState(msg, state.isPromptHistoryVisible)
                    ?: unloadedState(statusMessage = "Draft not found")).copy(
                    visionDescription = result.visionDescription.description,
                )
            }
            is VisionDescriptionAnalysisResult.Failure -> {
                when (val error = result.error) {
                    VisionDescriptionAnalysisError.DraftNotFound -> {
                        state = unloadedState(statusMessage = error.statusMessage())
                    }
                    else -> {
                        state = state.copy(statusMessage = error.statusMessage())
                    }
                }
            }
        }
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
        val original = draft.primaryMediaAsset()
        val captionText = "Ready for ${platform.wireValue}: ${original.uri.substringAfterLast('/')}"
        val shortCaptionText = "Ready for ${platform.wireValue}"
        val hashtags = listOf("#shotquill", "#draft")
        val altTextText = "Photo prepared for ${platform.wireValue}."
        val captionPrompt = "Generate a manual post caption for ${platform.wireValue} from ${original.uri}."
        val altTextPrompt = "Generate accessible alt text for ${original.uri}."
        val captionRequest = CaptionRequest(
            id = CaptionRequestId("caption-request-$idSuffix"),
            draftId = draft.id,
            targetPlatform = platform,
            prompt = captionPrompt,
            tone = draft.brandProfile?.voice,
            brandProfileId = draft.brandProfile?.id,
            createdAtEpochMillis = now,
        )
        val captionResult = CaptionResult(
            id = CaptionResultId("caption-result-$idSuffix"),
            requestId = captionRequest.id,
            draftId = draft.id,
            targetPlatform = platform,
            caption = captionText,
            shortCaption = shortCaptionText.trim().takeIf { it.isNotEmpty() },
            hashtags = hashtags,
            modelName = "fake-manual-draft-ai",
            createdAtEpochMillis = now,
        )
        val altText = AltTextResult(
            id = AltTextResultId("alt-text-$idSuffix"),
            draftId = draft.id,
            mediaAssetId = draft.primaryMediaAsset().id,
            altText = altTextText,
            modelName = "fake-manual-draft-ai",
            createdAtEpochMillis = now,
        )
        val transitioned = if (draft.status == nextStatus) {
            draft.copy(updatedAt = operationUpdatedAt)
        } else {
            draft.transitionTo(nextStatus, operationUpdatedAt)
        }
        val updated = transitioned.copy(
            caption = CaptionDraft(captionText, hashtags),
            targetPlatforms = draft.targetPlatforms + platform,
            captionRequests = draft.captionRequests + captionRequest,
            captionResults = draft.captionResults + captionResult,
            altTextResults = draft.altTextResults + altText,
            promptHistory = draft.promptHistory + listOf(
                PromptHistoryEntry(
                    id = PromptHistoryEntryId("prompt-caption-$idSuffix"),
                    draftId = draft.id,
                    operationType = AiOperationType.CaptionGeneration,
                    prompt = captionPrompt,
                    responseSummary = captionText,
                    modelName = "fake-manual-draft-ai",
                    createdAtEpochMillis = now,
                ),
                PromptHistoryEntry(
                    id = PromptHistoryEntryId("prompt-alt-text-$idSuffix"),
                    draftId = draft.id,
                    operationType = AiOperationType.AltTextGeneration,
                    prompt = altTextPrompt,
                    responseSummary = altTextText,
                    modelName = "fake-manual-draft-ai",
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
                when (result.error) {
                    is PostTextGenerationError.ImageLoadFailure -> {
                        state = state.copy(
                            statusMessage = result.error.statusMessage(),
                        )
                    }
                    else -> {
                        state = draft.toState(result.error.statusMessage(), state.isPromptHistoryVisible)
                    }
                }
            }
        }
    }

    fun updatePhotoEditIntent(intent: EditIntent) {
        if (!canUpdatePhotoEditForm()) return
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(
                selectedIntent = intent,
            ),
        )
    }

    fun updatePhotoEditRefinement(refinement: String) {
        if (!canUpdatePhotoEditForm()) return
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(userRefinementText = refinement),
        )
    }

    fun updatePhotoEditRealism(realism: RealismLevel) {
        if (!canUpdatePhotoEditForm()) return
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(selectedRealismLevel = realism),
        )
    }

    fun updatePhotoEditTargetPlatform(platform: TargetPlatform) {
        if (!canUpdatePhotoEditForm()) return
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(selectedTargetPlatform = platform),
        )
    }

    fun updatePhotoEditQualityTier(quality: QualityTier) {
        if (!canUpdatePhotoEditForm()) return
        state = state.copy(
            photoEditForm = state.photoEditForm.copy(
                selectedQualityTier = quality,
                qualityTierModelNotes = quality.modelMappingNote,
                qualityTierCostNotes = quality.costNote,
            ),
        )
    }

    fun editPhotoWithAi() {
        val executor = photoEditExecutor ?: run {
            state = state.copy(
                statusMessage = "Photo edit execution pipeline not available",
                photoEditForm = state.photoEditForm.copy(operationState = PhotoEditFormOperationState.Error),
                actions = state.actions.copy(canEditPhotoWithAi = false),
            )
            return
        }
        editPhotoWithPipeline(executor)
    }

    private fun editPhotoWithPipeline(executor: PhotoEditExecutor) {
        val draft = postDraftRepository.get(draftId) ?: run {
            state = state.copy(
                statusMessage = "Draft not found",
                photoEditForm = state.photoEditForm.copy(operationState = PhotoEditFormOperationState.Error),
                actions = state.actions.copy(canEditPhotoWithAi = false),
            )
            return
        }
        val currentForm = state.photoEditForm
        state = state.copy(
            statusMessage = "Editing photo with AI...",
            photoEditForm = currentForm.copy(operationState = PhotoEditFormOperationState.Loading),
            actions = state.actions.copy(canEditPhotoWithAi = false),
        )
        val result = try {
            executor.execute(
                draftId = draftId,
                intent = currentForm.selectedIntent,
                realismLevel = currentForm.selectedRealismLevel,
                qualityTier = currentForm.selectedQualityTier,
                targetPlatform = currentForm.selectedTargetPlatform,
                userRefinement = currentForm.userRefinementText.trim().takeIf { it.isNotEmpty() },
                reuseVisionDescription = true,
            )
        } catch (e: Exception) {
            state = state.copy(
                statusMessage = "Photo edit failed: ${e.message ?: "Unknown error"}",
                photoEditForm = currentForm.copy(operationState = PhotoEditFormOperationState.Error),
                actions = state.actions.copy(canEditPhotoWithAi = state.draftStatus in mutableDraftStatuses),
            )
            return
        }
        when (result) {
            is PhotoEditExecutionResult.Success -> {
                val updatedDraft = result.updatedDraft
                state = updatedDraft.toState("Edited photo created", state.isPromptHistoryVisible)
            }
            is PhotoEditExecutionResult.Failure -> {
                val cause = result.error
                val msg = when (cause) {
                    is PhotoEditExecutionError.DraftNotFound -> "Draft not found"
                    is PhotoEditExecutionError.SourceMediaNotFound -> "The source photo for this draft is no longer available"
                    is PhotoEditExecutionError.InvalidDraftStatus -> "Cannot edit photo while status is ${cause.status.wireValue}"
                    is PhotoEditExecutionError.Provider -> "Unable to edit photo: ${cause.error.userMessage}"
                    is PhotoEditExecutionError.FailurePersisted -> {
                        val innerMsg = when (val inner = cause.cause) {
                            is PhotoEditExecutionError.Provider -> "Unable to edit photo: ${inner.error.userMessage}"
                            is PhotoEditExecutionError.FailedToLoadSourceImage -> "Photo edit failed: Unable to load the source photo"
                            is PhotoEditExecutionError.FailedToSaveEditedImage -> "Photo edit failed: Unable to save the edited photo"
                            else -> null
                        }
                        innerMsg ?: "Photo edit failed"
                    }
                    else -> "Photo edit failed"
                }
                if (cause is PhotoEditExecutionError.FailurePersisted) {
                    val baseState = cause.updatedDraft.toState(msg, state.isPromptHistoryVisible)
                    state = baseState.copy(
                        photoEditForm = baseState.photoEditForm.copy(
                            operationState = PhotoEditFormOperationState.Error,
                        ),
                    )
                } else if (cause is PhotoEditExecutionError.DraftNotFound) {
                    state = state.copy(
                        statusMessage = msg,
                        photoEditForm = currentForm.copy(operationState = PhotoEditFormOperationState.Error),
                        actions = state.actions.copy(canEditPhotoWithAi = false),
                    )
                } else {
                    state = state.copy(
                        statusMessage = msg,
                        photoEditForm = currentForm.copy(operationState = PhotoEditFormOperationState.Error),
                        actions = state.actions.copy(canEditPhotoWithAi = state.draftStatus in mutableDraftStatuses),
                    )
                }
            }
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

    fun selectEditedPhoto() {
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        if (draft.status !in mutableDraftStatuses) {
            state = draft.toState(
                statusMessage = "Cannot select edited photo while status is ${draft.status.wireValue}",
                isPromptHistoryVisible = state.isPromptHistoryVisible,
            )
            return
        }
        val latestResult = draft.photoEditResults.maxByOrNull { it.createdAtEpochMillis } ?: run {
            state = draft.toState("No edited photo available", state.isPromptHistoryVisible)
            return
        }
        val now = clock.nowMillis()
        val operationUpdatedAt = operationUpdatedAt(draft, now)
        when (postDraftRepository.updateSelectedMediaAsset(draftId, latestResult.editedMediaAsset.id, operationUpdatedAt)) {
            UpdateSelectionResult.Success -> {
                val updated = draft.copy(
                    selectedMediaAssetId = latestResult.editedMediaAsset.id,
                    updatedAt = operationUpdatedAt,
                )
                state = updated.toState("Using edited photo", state.isPromptHistoryVisible)
            }
            UpdateSelectionResult.DraftNotFound -> {
                state = unloadedState(statusMessage = "Draft not found")
            }
            UpdateSelectionResult.AssetNotOwnedByDraft -> {
                state = state.copy(statusMessage = "Selected asset is not part of this draft")
            }
        }
    }

    fun selectOriginalPhoto() {
        val draft = postDraftRepository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        if (draft.status !in mutableDraftStatuses) {
            state = draft.toState(
                statusMessage = "Cannot select original photo while status is ${draft.status.wireValue}",
                isPromptHistoryVisible = state.isPromptHistoryVisible,
            )
            return
        }
        val now = clock.nowMillis()
        val operationUpdatedAt = operationUpdatedAt(draft, now)
        when (postDraftRepository.updateSelectedMediaAsset(draftId, null, operationUpdatedAt)) {
            UpdateSelectionResult.Success -> {
                val updated = draft.copy(
                    selectedMediaAssetId = null,
                    updatedAt = operationUpdatedAt,
                )
                state = updated.toState("Using original photo", state.isPromptHistoryVisible)
            }
            UpdateSelectionResult.DraftNotFound -> {
                state = unloadedState(statusMessage = "Draft not found")
            }
            UpdateSelectionResult.AssetNotOwnedByDraft -> {
                state = state.copy(statusMessage = "Selected asset is not part of this draft")
            }
        }
    }

    private fun PostDraft.toState(
        statusMessage: String?,
        isPromptHistoryVisible: Boolean,
    ): ManualPostDraftWorkspaceState {
        val originalPhoto = mediaItems
            .firstOrNull { it.mediaAsset.type == MediaType.Photo }
            ?.mediaAsset
        val editedPhoto = photoEditResults.maxByOrNull { it.createdAtEpochMillis }?.editedMediaAsset
        val activePhoto = try {
            primaryMediaAsset()
        } catch (_: IllegalStateException) {
            null
        }
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
            activePhotoUri = activePhoto?.uri,
            visionDescription = visionDescription?.takeIf { it.mediaAssetId == activePhoto?.id }?.description,
            generatedCaption = captionText,
            generatedAltText = altText,
            targetPlatform = platform,
            draftStatus = status,
            promptHistory = promptHistory.sortedByDescending { it.createdAtEpochMillis },
            actions = ManualPostDraftWorkspaceActions(
                canAnalyzeVision = canMutateDraft,
                canGeneratePostText = canMutateDraft,
                canEditPhotoWithAi = canMutateDraft,
                canCopyCaption = !captionText.isNullOrBlank(),
                canCopyAltText = !altText.isNullOrBlank(),
                canShareOrExport = canMutateDraft && !captionText.isNullOrBlank() && !altText.isNullOrBlank(),
                canViewPromptHistory = promptHistory.isNotEmpty(),
                canSelectEditedPhoto = canMutateDraft && editedPhoto != null && editedPhoto.id != activePhoto?.id,
                canSelectOriginalPhoto = canMutateDraft && originalPhoto != null && (activePhoto == null || activePhoto.id != originalPhoto.id),
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
                operationState = PhotoEditFormOperationState.Idle,
            ),
        )
    }

    private fun unloadedState(statusMessage: String? = null): ManualPostDraftWorkspaceState =
        ManualPostDraftWorkspaceState(
            draftId = draftId,
            originalPhotoUri = null,
            editedPhotoUri = null,
            activePhotoUri = null,
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
                canSelectEditedPhoto = false,
                canSelectOriginalPhoto = false,
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

    private fun canUpdatePhotoEditForm(): Boolean =
        state.photoEditForm.operationState != PhotoEditFormOperationState.Loading

    private fun nextIdSuffix(nowEpochMillis: Long): String =
        "$nowEpochMillis-${operationSequence++}"

    private fun VisionDescriptionAnalysisError.statusMessage(): String =
        when (this) {
            VisionDescriptionAnalysisError.DraftNotFound -> "Draft not found"
            is VisionDescriptionAnalysisError.Provider -> "Unable to analyze photo: ${error.userMessage}"
            is VisionDescriptionAnalysisError.ImageLoadFailure -> message
        }

    private fun PostTextGenerationError.statusMessage(): String =
        when (this) {
            PostTextGenerationError.DraftNotFound -> "Draft not found"
            is PostTextGenerationError.InvalidDraftStatus -> "Cannot generate text while status is ${status.wireValue}"
            is PostTextGenerationError.Provider -> "Unable to generate text: ${error.userMessage}"
            is PostTextGenerationError.ImageLoadFailure -> message
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
