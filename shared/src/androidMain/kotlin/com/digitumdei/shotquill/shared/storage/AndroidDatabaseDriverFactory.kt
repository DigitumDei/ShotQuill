package com.digitumdei.shotquill.shared.storage

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.digitumdei.shotquill.shared.db.ShotQuillDatabase

/**
 * Opens the app's database. An existing database in an older layout is
 * brought up to date as it is opened, inside one transaction: if that fails,
 * the database is left exactly as it was.
 */
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
