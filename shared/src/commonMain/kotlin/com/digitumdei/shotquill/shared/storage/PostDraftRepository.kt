package com.digitumdei.shotquill.shared.storage

import com.digitumdei.shotquill.shared.domain.DraftSummary
import com.digitumdei.shotquill.shared.domain.PostDraft
import com.digitumdei.shotquill.shared.domain.PostDraftId

interface PostDraftRepository {
    /** Stores the draft as given, replacing whatever was stored for it. */
    fun save(postDraft: PostDraft)
    fun get(id: PostDraftId): PostDraft?

    /** Every draft, removed ones included, most recently changed first. */
    fun listPostDrafts(): List<DraftSummary>

    /**
     * Deletes the draft and everything stored for it, for good. Returns the
     * addresses of its media that no other draft uses, so the caller can
     * delete those files. Returns an empty list when there is no such draft.
     */
    fun delete(id: PostDraftId): List<String>
}
