package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.ai.AiProvider
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository

interface AnalyzeVision {
    fun analyzePrimaryPhoto(
        draftId: PostDraftId,
        reuseCached: Boolean = true,
    ): VisionDescriptionAnalysisResult
}

class AnalyzeVisionWorkflow(
    private val repository: ManualWorkflowRepository,
    private val aiProvider: AiProvider,
    private val imageSource: VisionImageSource,
    private val clock: EpochClock = EpochClock.Default,
) : AnalyzeVision {

    override fun analyzePrimaryPhoto(
        draftId: PostDraftId,
        reuseCached: Boolean,
    ): VisionDescriptionAnalysisResult {
        return VisionDescriptionAnalyzer(
            repository = repository,
            aiProvider = aiProvider,
            imageSource = imageSource,
            clock = clock,
        ).analyzePrimaryPhoto(draftId, reuseCached)
    }
}
