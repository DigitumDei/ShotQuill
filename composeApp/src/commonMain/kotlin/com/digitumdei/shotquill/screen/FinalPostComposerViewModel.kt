package com.digitumdei.shotquill.screen

import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.TargetPlatform
import com.digitumdei.shotquill.shared.domain.effectiveAltText
import com.digitumdei.shotquill.shared.domain.effectiveCaption
import com.digitumdei.shotquill.shared.domain.primaryMediaAsset
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    private val clock: EpochClock = EpochClock.Default,
    private val defaultTargetPlatform: TargetPlatform = TargetPlatform.InstagramFeedSquare,
) {
    var state: FinalPostComposerState by mutableStateOf(unloadedState())
        private set

    fun load() {
        val draft = repository.get(draftId)
        if (draft == null) {
            state = unloadedState(statusMessage = "Draft not found")
            return
        }
        state = draft.toState()
    }

    private fun PostDraft.toState(): FinalPostComposerState {
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
            statusMessage = null,
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
}
