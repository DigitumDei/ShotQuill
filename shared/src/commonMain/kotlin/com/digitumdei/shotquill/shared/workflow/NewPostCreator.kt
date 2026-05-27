package com.digitumdei.shotquill.shared.workflow

import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.EpochClock
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.MediaType
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.domain.PostMediaItem
import com.digitumdei.shotquill.shared.storage.PostDraftRepository
import kotlinx.datetime.Instant

class NewPostCreator(
    private val postDraftRepository: PostDraftRepository,
    private val clock: EpochClock = EpochClock.Default,
) {
    fun createDraftFromMedia(
        draftId: PostDraftId,
        mediaAssetId: MediaAssetId,
        format: PostFormat,
        uri: String,
        mimeType: String?,
        widthPx: Int?,
        heightPx: Int?,
        createdAtEpochMillis: Long = clock.nowMillis(),
    ): PostDraft {
        val nowEpoch = clock.nowMillis()
        val nowInstant = Instant.fromEpochMilliseconds(nowEpoch)

        val mediaAsset = MediaAsset(
            id = mediaAssetId,
            type = MediaType.Photo,
            uri = uri,
            mimeType = mimeType,
            widthPx = widthPx,
            heightPx = heightPx,
            createdAtEpochMillis = createdAtEpochMillis,
        )

        val postDraft = PostDraft(
            id = draftId,
            format = format,
            status = DraftStatus.Draft,
            mediaItems = listOf(PostMediaItem(mediaAsset = mediaAsset, order = 0)),
            caption = null,
            targetPlatforms = emptySet(),
            brandProfile = null,
            visionDescription = null,
            captionRequests = emptyList(),
            captionResults = emptyList(),
            altTextResults = emptyList(),
            photoEditRequests = emptyList(),
            photoEditResults = emptyList(),
            promptHistory = emptyList(),
            exportRecords = emptyList(),
            createdAt = nowInstant,
            updatedAt = nowInstant,
        )
        postDraftRepository.save(postDraft)
        return postDraft
    }
}
