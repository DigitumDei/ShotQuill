package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.clipboard.ClipboardWriter
import com.digitumdei.shotquill.share.PostShareLauncher
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.ExportRecord
import com.digitumdei.shotquill.shared.domain.ExportRecordId
import com.digitumdei.shotquill.shared.domain.ExportStatus
import com.digitumdei.shotquill.shared.domain.FinalPostContent
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.effectiveAltText
import com.digitumdei.shotquill.shared.domain.effectiveCaption
import com.digitumdei.shotquill.shared.domain.primaryMediaAsset
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import com.digitumdei.shotquill.shared.storage.UpdateSelectionResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.Instant

data class FinalPostComposerState(
    val draftId: PostDraftId,
    val originalPhotoUri: String?,
    val editedPhotoUri: String?,
    val selectedPhotoUri: String?,
    val isShowingEdited: Boolean,
    val caption: String?,
    val shortCaption: String?,
    val altText: String?,
    val hashtags: List<String>,
    val targetPlatform: TargetPlatform?,
    val isLoaded: Boolean,
    val statusMessage: String?,
    val actions: FinalPostComposerActions,
)

data class FinalPostComposerActions(
    val canShare: Boolean,
    val canSelectEdited: Boolean,
)

class FinalPostComposerViewModel(
    private val draftId: PostDraftId,
    private val repository: ManualWorkflowRepository,
    private val clipboardWriter: ClipboardWriter,
    private val postShareLauncher: PostShareLauncher,
    private val clock: EpochClock = EpochClock.Default,
    private val defaultTargetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
) {
    var state: FinalPostComposerState by mutableStateOf(unloadedState())
        private set
    private var operationSequence = 0

    fun load() {
        val draft = repository.get(draftId)
        if (draft == null) {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        state = draft.toState()
    }

    fun updateCaption(text: String) {
        if (!state.isLoaded) return
        val newCanShare = text.isNotBlank() && state.selectedPhotoUri != null
        state = state.copy(caption = text, actions = state.actions.copy(canShare = newCanShare))
        val existing = repository.getFinalPostContent(draftId)
        val now = clock.nowMillis()
        repository.saveFinalPostContent(
            FinalPostContent(
                draftId = draftId,
                editedCaption = text,
                editedAltText = existing?.editedAltText,
                updatedAtEpochMillis = now,
            ),
        )
    }

    fun updateAltText(text: String) {
        if (!state.isLoaded) return
        state = state.copy(altText = text)
        val existing = repository.getFinalPostContent(draftId)
        val now = clock.nowMillis()
        repository.saveFinalPostContent(
            FinalPostContent(
                draftId = draftId,
                editedCaption = existing?.editedCaption,
                editedAltText = text,
                updatedAtEpochMillis = now,
            ),
        )
    }

    fun selectOriginalPhoto() {
        val draft = repository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        val now = clock.nowMillis()
        val updatedAt = operationUpdatedAt(draft, now)
        when (repository.updateSelectedMediaAsset(draftId, null, updatedAt)) {
            UpdateSelectionResult.Success -> {
                state = repository.get(draftId)?.toState(statusMessage = "Using original photo")
                    ?: unloadedState(statusMessage = "Draft not found")
            }
            UpdateSelectionResult.DraftNotFound -> {
                state = unloadedState(statusMessage = "Draft not found")
            }
            UpdateSelectionResult.AssetNotOwnedByDraft -> {
                state = state.copy(statusMessage = "Selected asset is not part of this draft")
            }
        }
    }

    fun selectEditedPhoto() {
        val draft = repository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        val latestResult = draft.photoEditResults.maxByOrNull { it.createdAtEpochMillis } ?: run {
            state = repository.get(draftId)?.toState(statusMessage = "No edited photo available")
                ?: unloadedState(statusMessage = "Draft not found")
            return
        }
        val now = clock.nowMillis()
        val updatedAt = operationUpdatedAt(draft, now)
        when (repository.updateSelectedMediaAsset(draftId, latestResult.editedMediaAsset.id, updatedAt)) {
            UpdateSelectionResult.Success -> {
                state = repository.get(draftId)?.toState(statusMessage = "Using edited photo")
                    ?: unloadedState(statusMessage = "Draft not found")
            }
            UpdateSelectionResult.DraftNotFound -> {
                state = unloadedState(statusMessage = "Draft not found")
            }
            UpdateSelectionResult.AssetNotOwnedByDraft -> {
                state = state.copy(statusMessage = "Selected asset is not part of this draft")
            }
        }
    }

    fun copyCaption() {
        val text = state.caption
        if (text != null) {
            clipboardWriter.copy("caption", text)
            state = state.copy(statusMessage = "Caption copied")
        }
    }

    fun copyAltText() {
        val text = state.altText
        if (text != null) {
            clipboardWriter.copy("alt text", text)
            state = state.copy(statusMessage = "Alt text copied")
        }
    }

    fun shareOrExport() {
        if (!state.actions.canShare) {
            state = state.copy(statusMessage = "Cannot share: caption and photo are required")
            return
        }
        val draft = repository.get(draftId) ?: run {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        val now = clock.nowMillis()
        val updatedAt = operationUpdatedAt(draft, now)
        val idSuffix = nextIdSuffix(now)
        val platform = state.targetPlatform ?: defaultTargetPlatform
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
        val transitioned = if (draft.status == DraftStatus.ReadyToShare || draft.status == DraftStatus.Shared) {
            draft.copy(updatedAt = updatedAt)
        } else {
            draft.transitionTo(DraftStatus.ReadyToShare, updatedAt)
        }
        val draftWithExport = transitioned.copy(
            targetPlatforms = draft.targetPlatforms + platform,
            exportRecords = draft.exportRecords + exportRecord,
        )
        repository.save(draftWithExport)

        val caption = state.caption ?: ""
        val hashtagText = state.hashtags.joinToString(" ")
        val composedText = if (hashtagText.isNotEmpty()) "$caption\n\n$hashtagText" else caption
        val shareSuccess = postShareLauncher.share(state.selectedPhotoUri, composedText)

        if (shareSuccess) {
            val completedExport = exportRecord.copy(
                status = ExportStatus.Exported,
                completedAtEpochMillis = now,
            )
            val sharedDraft = if (draft.status == DraftStatus.Shared) {
                draftWithExport
            } else {
                draftWithExport.transitionTo(DraftStatus.Shared, updatedAt)
            }
            repository.save(
                sharedDraft.copy(
                    exportRecords = sharedDraft.exportRecords.map {
                        if (it.id == exportRecord.id) completedExport else it
                    },
                ),
            )
            state = repository.get(draftId)?.toState(statusMessage = "Post shared")
                ?: unloadedState(statusMessage = "Draft not found")
        } else {
            val failedExport = exportRecord.copy(
                status = ExportStatus.Failed,
                errorMessage = "Share was cancelled or failed",
                completedAtEpochMillis = now,
            )
            repository.save(
                draftWithExport.copy(
                    exportRecords = draftWithExport.exportRecords.map {
                        if (it.id == exportRecord.id) failedExport else it
                    },
                ),
            )
            state = state.copy(statusMessage = "Share was cancelled or failed")
        }
    }

    private fun PostDraft.toState(statusMessage: String? = null): FinalPostComposerState {
        val originalAsset = mediaItems
            .filter { it.mediaAsset.type == MediaType.Photo }
            .minByOrNull { it.order }
            ?.mediaAsset
        val editedAsset = photoEditResults.maxByOrNull { it.createdAtEpochMillis }?.editedMediaAsset
        val activeAsset = primaryMediaAsset()
        val latestCaptionResult = captionResults.maxByOrNull { it.createdAtEpochMillis }
        val platform = latestCaptionResult?.targetPlatform
            ?: preferredTargetPlatform()
            ?: defaultTargetPlatform
        val content = finalPostContent
        return FinalPostComposerState(
            draftId = id,
            originalPhotoUri = originalAsset?.uri,
            editedPhotoUri = editedAsset?.uri,
            selectedPhotoUri = activeAsset?.uri,
            isShowingEdited = editedAsset != null && activeAsset?.id == editedAsset.id,
            caption = effectiveCaption(content),
            shortCaption = latestCaptionResult?.shortCaption,
            altText = effectiveAltText(content),
            hashtags = latestCaptionResult?.hashtags ?: emptyList(),
            targetPlatform = platform,
            isLoaded = true,
            statusMessage = statusMessage,
            actions = FinalPostComposerActions(
                canShare = effectiveCaption(content) != null && activeAsset?.uri != null,
                canSelectEdited = editedAsset != null,
            ),
        )
    }

    private fun unloadedState(statusMessage: String? = null): FinalPostComposerState =
        FinalPostComposerState(
            draftId = draftId,
            originalPhotoUri = null,
            editedPhotoUri = null,
            selectedPhotoUri = null,
            isShowingEdited = false,
            caption = null,
            shortCaption = null,
            altText = null,
            hashtags = emptyList(),
            targetPlatform = null,
            isLoaded = false,
            statusMessage = statusMessage,
            actions = FinalPostComposerActions(
                canShare = false,
                canSelectEdited = false,
            ),
        )

    private fun PostDraft.preferredTargetPlatform(): TargetPlatform? =
        targetPlatforms.sortedBy { it.wireValue }.firstOrNull()

    private fun operationUpdatedAt(draft: PostDraft, nowEpochMillis: Long): Instant {
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
        return if (now >= draft.updatedAt) now else draft.updatedAt
    }

    private fun nextIdSuffix(nowEpochMillis: Long): String =
        "$nowEpochMillis-${operationSequence++}"
}
