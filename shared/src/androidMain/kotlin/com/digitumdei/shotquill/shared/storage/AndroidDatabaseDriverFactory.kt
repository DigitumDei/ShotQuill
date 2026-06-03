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

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion < 2) {
                        db.execSQL("ALTER TABLE post_drafts ADD COLUMN selected_media_asset_id TEXT REFERENCES media_assets(id) ON DELETE SET NULL")
                    }
                }
            },
        )
}
