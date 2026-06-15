package com.digitumdei.shotquill.shared.storage

import android.content.Context
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.digitumdei.shotquill.shared.db.ShotQuillDatabase
import com.digitumdei.shotquill.shared.domain.MediaAssetId
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.domain.PromptHistoryEntryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidDatabaseDriverUpgradeTest {

    @Test
    fun androidDriverUpgradeFromV1ToV2ReadsSelectedMediaAssetId() {
        val context = RuntimeEnvironment.application as Context
        val dbName = "android-upgrade-v1.db"
        val dbPath = context.getDatabasePath(dbName)

        dbPath.delete()
        dbPath.parentFile?.mkdirs()

        val v1Driver = JdbcSqliteDriver("jdbc:sqlite:${dbPath.absolutePath}")
        createV1Schema(v1Driver)

        v1Driver.execute(null, "INSERT INTO media_assets(id, type, uri, mime_type, width_px, height_px, created_at_epoch_millis) VALUES('media-1', 'photo', 'file://photo.jpg', 'image/jpeg', 1080, 1080, 1700000000000)", 0)
        v1Driver.execute(null, "INSERT INTO post_drafts(id, format, status, caption_text, brand_profile_id, created_at_epoch_millis, updated_at_epoch_millis) VALUES('draft-1', 'SingleImage', 'draft', 'Hello world', NULL, 1700000000000, 1700000060000)", 0)
        v1Driver.execute(null, "INSERT INTO post_draft_media_items(draft_id, media_asset_id, media_order) VALUES('draft-1', 'media-1', 0)", 0)

        v1Driver.execute(null, "PRAGMA user_version = 1", 0)
        v1Driver.close()

        val factory = AndroidDatabaseDriverFactory(context)
        val driver = factory.create(dbName)

        val repository = SqlDelightManualWorkflowRepository(driver)
        val draft = repository.get(PostDraftId("draft-1"))
        assertNotNull(draft, "Draft must be readable after v1->v2 upgrade")
        assertEquals("Hello world", draft.caption?.text, "Caption must survive upgrade")
        assertNull(draft.selectedMediaAssetId, "selectedMediaAssetId must default to null after migration")

        driver.close()

        val driver2 = factory.create(dbName)
        driver2.execute(null, "UPDATE post_drafts SET selected_media_asset_id = 'media-1', updated_at_epoch_millis = 1700000070000 WHERE id = 'draft-1'", 0)
        val repository2 = SqlDelightManualWorkflowRepository(driver2)
        val reselected = repository2.get(PostDraftId("draft-1"))
        assertNotNull(reselected, "Draft must still be readable after setting selection")
        assertEquals("media-1", reselected.selectedMediaAssetId?.value, "selectedMediaAssetId must be settable on migrated database")

        driver2.close()
        dbPath.delete()
    }

    @Test
    fun schemaVersionMatchesMigrationScaffold() {
        assertEquals(4, ShotQuillDatabase.Schema.version.toInt())
    }

    @Test
    fun androidDriverUpgradeFromV3ToV4ReadsNewColumnsAsNull() {
        val context = RuntimeEnvironment.application as Context
        val dbName = "android-upgrade-v3.db"
        val dbPath = context.getDatabasePath(dbName)

        dbPath.delete()
        dbPath.parentFile?.mkdirs()

        val v3Driver = JdbcSqliteDriver("jdbc:sqlite:${dbPath.absolutePath}")
        createV3Schema(v3Driver)

        v3Driver.execute(null, "INSERT INTO media_assets(id, type, uri, mime_type, width_px, height_px, created_at_epoch_millis) VALUES('media-1', 'photo', 'file://photo.jpg', 'image/jpeg', 1080, 1080, 1700000000000)", 0)
        v3Driver.execute(null, "INSERT INTO post_drafts(id, format, status, caption_text, brand_profile_id, selected_media_asset_id, created_at_epoch_millis, updated_at_epoch_millis) VALUES('draft-1', 'SingleImage', 'photo_added', NULL, NULL, NULL, 1700000000000, 1700000060000)", 0)
        v3Driver.execute(null, "INSERT INTO post_draft_media_items(draft_id, media_asset_id, media_order) VALUES('draft-1', 'media-1', 0)", 0)
        v3Driver.execute(null, "INSERT INTO prompt_history_entries(id, draft_id, operation_type, prompt, response_summary, model_name, created_at_epoch_millis) VALUES('prompt-v3-1', 'draft-1', 'caption_generation', 'Old prompt text.', 'Old response.', 'caption-model', 1700000000000)", 0)

        v3Driver.execute(null, "PRAGMA user_version = 3", 0)
        v3Driver.close()

        val factory = AndroidDatabaseDriverFactory(context)
        val driver = factory.create(dbName)

        val repository = SqlDelightManualWorkflowRepository(driver)
        val draft = repository.get(PostDraftId("draft-1"))
        assertNotNull(draft, "Draft must be readable after v3->v4 upgrade")

        val entry = repository.get(PromptHistoryEntryId("prompt-v3-1"))
        assertNotNull(entry, "Prompt history entry must survive v3->v4 migration")
        assertEquals("Old prompt text.", entry.prompt)
        assertEquals("Old response.", entry.responseSummary)
        assertEquals("caption-model", entry.modelName)
        assertNull(entry.provider, "provider must be null after migration")
        assertNull(entry.mediaAssetId, "mediaAssetId must be null after migration")
        assertNull(entry.requestSettings, "requestSettings must be null after migration")
        assertNull(entry.resultReference, "resultReference must be null after migration")
        assertNull(entry.errorMessage, "errorMessage must be null after migration")

        val history = repository.listPromptHistoryForDraft(PostDraftId("draft-1"))
        assertTrue(history.any { it.id == PromptHistoryEntryId("prompt-v3-1") })

        val forMedia = repository.listPromptHistoryForMediaAsset(MediaAssetId("media-1"))
        assertTrue(forMedia.isEmpty(), "no entries have media_asset_id set after migration")

        driver.close()
        dbPath.delete()
    }

    private fun createV1Schema(driver: JdbcSqliteDriver) {
        driver.execute(null, "CREATE TABLE media_assets (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, uri TEXT NOT NULL, mime_type TEXT, width_px INTEGER, height_px INTEGER, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE brand_profiles (id TEXT NOT NULL PRIMARY KEY, display_name TEXT NOT NULL, voice TEXT NOT NULL, audience TEXT, visual_style_notes TEXT, product_naming_notes TEXT, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE brand_profile_default_hashtags (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (profile_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE brand_profile_links (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, link TEXT NOT NULL, link_order INTEGER NOT NULL, PRIMARY KEY (profile_id, link_order))", 0)
        driver.execute(null, "CREATE TABLE brand_image_assets (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, title TEXT NOT NULL, description TEXT, asset_order INTEGER NOT NULL, PRIMARY KEY (profile_id, media_asset_id))", 0)
        driver.execute(null, "CREATE TABLE post_drafts (id TEXT NOT NULL PRIMARY KEY, format TEXT NOT NULL, status TEXT NOT NULL, caption_text TEXT, brand_profile_id TEXT REFERENCES brand_profiles(id) ON DELETE SET NULL, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE post_draft_target_platforms (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, platform TEXT NOT NULL, PRIMARY KEY (draft_id, platform))", 0)
        driver.execute(null, "CREATE TABLE post_draft_media_items (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, media_order INTEGER NOT NULL, PRIMARY KEY (draft_id, media_asset_id), UNIQUE (draft_id, media_order))", 0)
        driver.execute(null, "CREATE TABLE post_draft_caption_hashtags (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (draft_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE vision_descriptions (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, description TEXT NOT NULL, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_requests (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, prompt TEXT NOT NULL, tone TEXT, brand_profile_id TEXT REFERENCES brand_profiles(id) ON DELETE SET NULL, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_results (id TEXT NOT NULL PRIMARY KEY, request_id TEXT NOT NULL REFERENCES caption_requests(id) ON DELETE CASCADE, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, caption TEXT NOT NULL, short_caption TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_result_hashtags (result_id TEXT NOT NULL REFERENCES caption_results(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (result_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE alt_text_results (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, alt_text TEXT NOT NULL, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE photo_edit_requests (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, source_media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, intent TEXT NOT NULL, realism_level TEXT NOT NULL, quality_tier TEXT NOT NULL, prompt TEXT NOT NULL, user_refinement TEXT, subject_description TEXT, target_platform TEXT NOT NULL, mask_region TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE photo_edit_results (id TEXT NOT NULL PRIMARY KEY, request_id TEXT NOT NULL REFERENCES photo_edit_requests(id) ON DELETE CASCADE, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, edited_media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, summary TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE prompt_history_entries (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, operation_type TEXT NOT NULL, prompt TEXT NOT NULL, response_summary TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE export_records (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, status TEXT NOT NULL, destination_uri TEXT, error_message TEXT, created_at_epoch_millis INTEGER NOT NULL, completed_at_epoch_millis INTEGER)", 0)
    }

    private fun createV3Schema(driver: JdbcSqliteDriver) {
        driver.execute(null, "CREATE TABLE media_assets (id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, uri TEXT NOT NULL, mime_type TEXT, width_px INTEGER, height_px INTEGER, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE brand_profiles (id TEXT NOT NULL PRIMARY KEY, display_name TEXT NOT NULL, voice TEXT NOT NULL, audience TEXT, visual_style_notes TEXT, product_naming_notes TEXT, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE brand_profile_default_hashtags (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (profile_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE brand_profile_links (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, link TEXT NOT NULL, link_order INTEGER NOT NULL, PRIMARY KEY (profile_id, link_order))", 0)
        driver.execute(null, "CREATE TABLE brand_image_assets (profile_id TEXT NOT NULL REFERENCES brand_profiles(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, title TEXT NOT NULL, description TEXT, asset_order INTEGER NOT NULL, PRIMARY KEY (profile_id, media_asset_id))", 0)
        driver.execute(null, "CREATE TABLE post_drafts (id TEXT NOT NULL PRIMARY KEY, format TEXT NOT NULL, status TEXT NOT NULL, caption_text TEXT, brand_profile_id TEXT REFERENCES brand_profiles(id) ON DELETE SET NULL, selected_media_asset_id TEXT REFERENCES media_assets(id) ON DELETE SET NULL, created_at_epoch_millis INTEGER NOT NULL, updated_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE post_draft_target_platforms (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, platform TEXT NOT NULL, PRIMARY KEY (draft_id, platform))", 0)
        driver.execute(null, "CREATE TABLE post_draft_media_items (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, media_order INTEGER NOT NULL, PRIMARY KEY (draft_id, media_asset_id), UNIQUE (draft_id, media_order))", 0)
        driver.execute(null, "CREATE TABLE post_draft_caption_hashtags (draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (draft_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE vision_descriptions (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, description TEXT NOT NULL, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_requests (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, prompt TEXT NOT NULL, tone TEXT, brand_profile_id TEXT REFERENCES brand_profiles(id) ON DELETE SET NULL, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_results (id TEXT NOT NULL PRIMARY KEY, request_id TEXT NOT NULL REFERENCES caption_requests(id) ON DELETE CASCADE, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, caption TEXT NOT NULL, short_caption TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE caption_result_hashtags (result_id TEXT NOT NULL REFERENCES caption_results(id) ON DELETE CASCADE, hashtag TEXT NOT NULL, hashtag_order INTEGER NOT NULL, PRIMARY KEY (result_id, hashtag_order))", 0)
        driver.execute(null, "CREATE TABLE alt_text_results (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, alt_text TEXT NOT NULL, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE photo_edit_requests (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, source_media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, intent TEXT NOT NULL, realism_level TEXT NOT NULL, quality_tier TEXT NOT NULL, prompt TEXT NOT NULL, user_refinement TEXT, subject_description TEXT, target_platform TEXT NOT NULL, mask_region TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE photo_edit_results (id TEXT NOT NULL PRIMARY KEY, request_id TEXT NOT NULL REFERENCES photo_edit_requests(id) ON DELETE CASCADE, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, edited_media_asset_id TEXT NOT NULL REFERENCES media_assets(id) ON DELETE CASCADE, summary TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE prompt_history_entries (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, operation_type TEXT NOT NULL, prompt TEXT NOT NULL, response_summary TEXT, model_name TEXT, created_at_epoch_millis INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE export_records (id TEXT NOT NULL PRIMARY KEY, draft_id TEXT NOT NULL REFERENCES post_drafts(id) ON DELETE CASCADE, target_platform TEXT NOT NULL, status TEXT NOT NULL, destination_uri TEXT, error_message TEXT, created_at_epoch_millis INTEGER NOT NULL, completed_at_epoch_millis INTEGER)", 0)
        driver.execute(null, "CREATE TABLE final_post_content (draft_id TEXT NOT NULL PRIMARY KEY REFERENCES post_drafts(id) ON DELETE CASCADE, edited_caption TEXT, edited_alt_text TEXT, updated_at_epoch_millis INTEGER NOT NULL)", 0)
    }
}
