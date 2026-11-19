package com.digitumdei.shotquill.shared.storage

import java.io.File

/**
 * A database as version 1 of the app laid it out, for trying the migration on.
 *
 * [emptyDatabase] is the snapshot of that layout kept in the repository, so
 * the tables are exactly the ones a phone has. [posts] fills it with posts in
 * every state a phone could hold: made by the app as it is now, and made by
 * the old AI flow that the migration clears away.
 */
internal object VersionOneFixture {
    val emptyDatabase = File("src/commonMain/sqldelight/databases/1.db")

    const val Created = 1_700_000_000_000L

    /** Copies the empty version 1 database to [target]. */
    fun copyTo(target: File) {
        check(emptyDatabase.isFile) { "The version 1 snapshot is missing: ${emptyDatabase.absolutePath}" }
        target.parentFile?.mkdirs()
        emptyDatabase.copyTo(target, overwrite = true)
    }

    val posts: List<String> = listOf(
        // --- A post as the app makes them now: shared, with a caption and hand-written alt text.
        photo("media-phone", "file:///photos/phone.jpg"),
        draft("draft-phone", status = "shared", caption = "'First pour of the witbier.'", updated = Created + 90_000),
        mediaItem("draft-phone", "media-phone"),
        finalContent("draft-phone", caption = "NULL", altText = "'A glass of beer on a bar.'"),

        // --- A post with a photo and nothing typed yet.
        """
        INSERT INTO media_assets(id, type, uri, mime_type, width_px, height_px, created_at_epoch_millis)
        VALUES ('media-new', 'photo', 'file:///photos/new.jpg', NULL, NULL, NULL, $Created)
        """,
        draft("draft-new", status = "photo_added", caption = "NULL", updated = Created + 80_000),
        mediaItem("draft-new", "media-new"),

        // --- A removed post, whose alt text was typed and then cleared.
        photo("media-removed", "file:///photos/removed.jpg"),
        draft("draft-removed", status = "archived", caption = "'Gone'", updated = Created + 70_000),
        mediaItem("draft-removed", "media-removed"),
        finalContent("draft-removed", caption = "NULL", altText = "NULL"),

        // --- A post from the old AI flow, with a row in every table that is going.
        photo("media-brand", "file:///brand/logo.png"),
        """
        INSERT INTO brand_profiles(id, display_name, voice, audience, visual_style_notes, product_naming_notes,
                                   created_at_epoch_millis, updated_at_epoch_millis)
        VALUES ('brand-1', 'Doctrine Brewing', 'Dry', NULL, NULL, NULL, $Created, $Created)
        """,
        "INSERT INTO brand_profile_default_hashtags(profile_id, hashtag, hashtag_order) VALUES ('brand-1', '#beer', 0)",
        "INSERT INTO brand_profile_links(profile_id, link, link_order) VALUES ('brand-1', 'https://example.test', 0)",
        """
        INSERT INTO brand_image_assets(profile_id, media_asset_id, title, description, asset_order)
        VALUES ('brand-1', 'media-brand', 'Logo', NULL, 0)
        """,
        photo("media-old", "file:///photos/old.jpg"),
        """
        INSERT INTO media_assets(id, type, uri, mime_type, width_px, height_px, created_at_epoch_millis)
        VALUES ('media-edited', 'edited_photo', 'file:///edited/old-ai.png', 'image/png', 1024, 1024, $Created)
        """,
        """
        INSERT INTO post_drafts(id, format, status, caption_text, brand_profile_id,
                                created_at_epoch_millis, updated_at_epoch_millis, selected_media_asset_id)
        VALUES ('draft-old', 'SingleImage', 'text_generated', 'A generated caption', 'brand-1',
                $Created, ${Created + 60_000}, 'media-edited')
        """,
        mediaItem("draft-old", "media-old"),
        "INSERT INTO post_draft_target_platforms(draft_id, platform) VALUES ('draft-old', 'instagram_portrait')",
        "INSERT INTO post_draft_caption_hashtags(draft_id, hashtag, hashtag_order) VALUES ('draft-old', '#beer', 0)",
        """
        INSERT INTO vision_descriptions(id, draft_id, media_asset_id, description, model_name, created_at_epoch_millis)
        VALUES ('vision-1', 'draft-old', 'media-old', 'A glass', NULL, $Created)
        """,
        """
        INSERT INTO caption_requests(id, draft_id, target_platform, prompt, tone, brand_profile_id, created_at_epoch_millis)
        VALUES ('request-1', 'draft-old', 'instagram_portrait', 'Write it', NULL, 'brand-1', $Created)
        """,
        """
        INSERT INTO caption_results(id, request_id, draft_id, target_platform, caption, short_caption, model_name,
                                    created_at_epoch_millis)
        VALUES ('result-1', 'request-1', 'draft-old', 'instagram_portrait', 'A generated caption', NULL, NULL, $Created)
        """,
        "INSERT INTO caption_result_hashtags(result_id, hashtag, hashtag_order) VALUES ('result-1', '#beer', 0)",
        """
        INSERT INTO alt_text_results(id, draft_id, media_asset_id, alt_text, model_name, created_at_epoch_millis)
        VALUES ('alt-1', 'draft-old', 'media-old', 'An earlier description', NULL, $Created)
        """,
        """
        INSERT INTO alt_text_results(id, draft_id, media_asset_id, alt_text, model_name, created_at_epoch_millis)
        VALUES ('alt-2', 'draft-old', 'media-old', 'The latest description', NULL, ${Created + 5_000})
        """,
        """
        INSERT INTO photo_edit_requests(id, draft_id, source_media_asset_id, intent, realism_level, quality_tier,
                                        prompt, user_refinement, subject_description, target_platform, mask_region,
                                        created_at_epoch_millis)
        VALUES ('edit-1', 'draft-old', 'media-old', 'improve_lighting', 'photoreal', 'standard',
                'Brighten', NULL, NULL, 'instagram_portrait', NULL, $Created)
        """,
        """
        INSERT INTO photo_edit_results(id, request_id, draft_id, edited_media_asset_id, summary, model_name,
                                       created_at_epoch_millis)
        VALUES ('edited-1', 'edit-1', 'draft-old', 'media-edited', NULL, NULL, $Created)
        """,
        """
        INSERT INTO prompt_history_entries(id, draft_id, operation_type, prompt, response_summary, model_name,
                                           created_at_epoch_millis, provider, media_asset_id, request_settings,
                                           result_reference, error_message)
        VALUES ('prompt-1', 'draft-old', 'photo_edit', 'Brighten', NULL, NULL, $Created, NULL, 'media-old', NULL, NULL, NULL)
        """,
        """
        INSERT INTO export_records(id, draft_id, target_platform, status, destination_uri, error_message,
                                   created_at_epoch_millis, completed_at_epoch_millis)
        VALUES ('export-1', 'draft-old', 'instagram_portrait', 'exported', NULL, NULL, $Created, NULL)
        """,

        // --- An old post whose only caption was one typed over the generated text.
        photo("media-typed", "file:///photos/typed.jpg"),
        draft("draft-typed", status = "ready_to_share", caption = "NULL", updated = Created + 50_000),
        mediaItem("draft-typed", "media-typed"),
        finalContent("draft-typed", caption = "'Typed by hand'", altText = "NULL"),
    )

    private fun photo(id: String, uri: String) =
        """
        INSERT INTO media_assets(id, type, uri, mime_type, width_px, height_px, created_at_epoch_millis)
        VALUES ('$id', 'photo', '$uri', 'image/jpeg', 4000, 3000, $Created)
        """

    private fun draft(id: String, status: String, caption: String, updated: Long) =
        """
        INSERT INTO post_drafts(id, format, status, caption_text, brand_profile_id,
                                created_at_epoch_millis, updated_at_epoch_millis, selected_media_asset_id)
        VALUES ('$id', 'SingleImage', '$status', $caption, NULL, $Created, $updated, NULL)
        """

    private fun mediaItem(draftId: String, mediaId: String) =
        "INSERT INTO post_draft_media_items(draft_id, media_asset_id, media_order) VALUES ('$draftId', '$mediaId', 0)"

    private fun finalContent(draftId: String, caption: String, altText: String) =
        """
        INSERT INTO final_post_content(draft_id, edited_caption, edited_alt_text, updated_at_epoch_millis)
        VALUES ('$draftId', $caption, $altText, $Created)
        """
}
