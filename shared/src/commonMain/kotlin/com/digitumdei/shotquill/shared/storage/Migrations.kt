package com.digitumdei.shotquill.shared.storage

object Migrations {
    fun migrateV1ToV2(execSql: (String) -> Unit) {
        execSql("ALTER TABLE post_drafts ADD COLUMN selected_media_asset_id TEXT REFERENCES media_assets(id) ON DELETE SET NULL")
    }
}
