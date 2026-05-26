package com.digitumdei.shotquill.shared.storage

import android.content.Context

class AndroidBrandProfileRepositoryFactory(
    private val context: Context,
) {
    fun create(): BrandProfileRepository =
        SqlDelightManualWorkflowRepository(AndroidDatabaseDriverFactory(context).create())
}
