package com.digitumdei.shotquill.shared.storage

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.digitumdei.shotquill.shared.db.ShotQuillDatabase

class AndroidDatabaseDriverFactory(
    private val context: Context,
) {
    fun create(name: String = "shotquill.db"): AndroidSqliteDriver =
        AndroidSqliteDriver(
            schema = ShotQuillDatabase.Schema,
            context = context.applicationContext,
            name = name,
            callback = object : AndroidSqliteDriver.Callback(ShotQuillDatabase.Schema) {
                override fun onConfigure(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
}
