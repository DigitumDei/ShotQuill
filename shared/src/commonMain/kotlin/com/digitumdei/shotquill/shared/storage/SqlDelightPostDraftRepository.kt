package com.digitumdei.shotquill.shared.storage

import app.cash.sqldelight.db.SqlDriver
import com.digitumdei.shotquill.shared.db.ShotQuillDatabase
import com.digitumdei.shotquill.shared.domain.DraftStatus
import com.digitumdei.shotquill.shared.domain.DraftSummary
import com.digitumdei.shotquill.shared.domain.MediaAsset
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PostFormat
import com.digitumdei.shotquill.shared.domain.PostMediaItem
import kotlinx.datetime.Instant

class SqlDelightPostDraftRepository(
    driver: SqlDriver,
) : PostDraftRepository {
    private val queries = ShotQuillDatabase(driver).shotQuillQueries

    override fun save(postDraft: PostDraft) {
        queries.transaction {
            postDraft.mediaItems.forEach { item ->
                val asset = item.mediaAsset
                queries.insertMediaAssetIfNew(
                    id = asset.id.value,
                    uri = asset.uri,
                    mime_type = asset.mimeType,
                    width_px = asset.widthPx?.toLong(),
                    height_px = asset.heightPx?.toLong(),
                    created_at_epoch_millis = asset.createdAtEpochMillis,
                )
                queries.updateMediaAsset(
                    uri = asset.uri,
                    mime_type = asset.mimeType,
                    width_px = asset.widthPx?.toLong(),
                    height_px = asset.heightPx?.toLong(),
                    created_at_epoch_millis = asset.createdAtEpochMillis,
                    id = asset.id.value,
                )
            }
            queries.insertPostDraftIfNew(
                id = postDraft.id.value,
                format = postDraft.format.name,
                status = postDraft.status.wireValue,
                caption_text = postDraft.caption,
                alt_text = postDraft.altText,
                created_at_epoch_millis = postDraft.createdAt.toEpochMilliseconds(),
                updated_at_epoch_millis = postDraft.updatedAt.toEpochMilliseconds(),
            )
            queries.updatePostDraft(
                format = postDraft.format.name,
                status = postDraft.status.wireValue,
                caption_text = postDraft.caption,
                alt_text = postDraft.altText,
                created_at_epoch_millis = postDraft.createdAt.toEpochMilliseconds(),
                updated_at_epoch_millis = postDraft.updatedAt.toEpochMilliseconds(),
                id = postDraft.id.value,
            )
            queries.deletePostDraftMediaItems(postDraft.id.value)
            postDraft.mediaItems.forEach { item ->
                queries.insertPostDraftMediaItem(
                    draft_id = postDraft.id.value,
                    media_asset_id = item.mediaAsset.id.value,
                    media_order = item.order.toLong(),
                )
            }
        }
    }

    override fun get(id: PostDraftId): PostDraft? {
        val draft = queries.selectPostDraftById(id.value).executeAsOneOrNull() ?: return null
        val mediaItems = queries.selectPostDraftMediaItems(id.value) {
                mediaId,
                uri,
                mimeType,
                widthPx,
                heightPx,
                mediaCreatedAt,
                mediaOrder,
            ->
            PostMediaItem(
                mediaAsset = MediaAsset(
                    id = MediaAssetId(mediaId),
                    uri = uri,
                    mimeType = mimeType,
                    widthPx = widthPx?.toInt(),
                    heightPx = heightPx?.toInt(),
                    createdAtEpochMillis = mediaCreatedAt,
                ),
                order = mediaOrder.toInt(),
            )
        }.executeAsList()

        return PostDraft(
            id = PostDraftId(draft.id),
            format = PostFormat.valueOf(draft.format),
            status = status(draft.status),
            mediaItems = mediaItems,
            caption = draft.caption_text,
            altText = draft.alt_text,
            createdAt = Instant.fromEpochMilliseconds(draft.created_at_epoch_millis),
            updatedAt = Instant.fromEpochMilliseconds(draft.updated_at_epoch_millis),
        )
    }

    override fun listPostDrafts(): List<DraftSummary> =
        queries.selectAllPostDrafts { id, status, captionText, createdAt, updatedAt, primaryMediaUri ->
            DraftSummary(
                id = PostDraftId(id),
                status = status(status),
                captionText = captionText,
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = updatedAt,
                primaryMediaUri = primaryMediaUri,
            )
        }.executeAsList()

    override fun delete(id: PostDraftId): List<String> =
        queries.transactionWithResult {
            if (queries.selectPostDraftById(id.value).executeAsOneOrNull() == null) {
                return@transactionWithResult emptyList()
            }
            val assetIds = queries.selectPostDraftMediaAssetIds(id.value).executeAsList()
            // The database removes the draft's media rows by itself when it
            // enforces its links between tables; they are named here so the
            // result is the same when it does not.
            queries.deletePostDraftMediaItems(id.value)
            queries.deletePostDraftById(id.value)
            assetIds.mapNotNull { assetId ->
                val uri = queries.selectMediaAssetUriById(assetId).executeAsOneOrNull()
                queries.deleteMediaAssetIfUnused(assetId)
                uri.takeIf { queries.selectMediaAssetUriById(assetId).executeAsOneOrNull() == null }
            }
        }

    private fun status(wireValue: String): DraftStatus =
        DraftStatus.fromWireValue(wireValue) ?: error("Unknown stored post status: $wireValue")
}
