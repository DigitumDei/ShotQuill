package com.digitumdei.shotquill.shared.storage

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.digitumdei.shotquill.shared.db.ShotQuillDatabase

class AndroidDatabaseDriverFactory(
    private val context: Context,
) {
    fun create(name: String = "shotquill.db"): AndroidSqliteDriver =
        AndroidSqliteDriver(ShotQuillDatabase.Schema, context, name)
}
