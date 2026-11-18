package com.digitumdei.shotquill.shared.post

import android.content.Context
import android.content.SharedPreferences
import com.digitumdei.shotquill.shared.domain.PostDraftId

/**
 * Keeps each post's photo treatment in a small preferences file, one entry per
 * post. It sits beside the database rather than in it so the treatment can
 * evolve without a schema migration.
 */
class AndroidPhotoTreatmentStore(
    context: Context,
) : PhotoTreatmentStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    override fun get(draftId: PostDraftId): StoredTreatment? =
        preferences.getString(draftId.value, null)?.let(StoredTreatmentCodec::decode)

    override fun save(draftId: PostDraftId, stored: StoredTreatment) {
        preferences.edit().putString(draftId.value, StoredTreatmentCodec.encode(stored)).apply()
    }

    override fun remove(draftId: PostDraftId) {
        preferences.edit().remove(draftId.value).apply()
    }

    private companion object {
        const val PreferencesName = "shotquill_photo_treatments"
    }
}
