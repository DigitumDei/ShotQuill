package com.digitumdei.shotquill.shared.post

import android.content.Context
import com.digitumdei.shotquill.shared.ai.AiOperation
import com.digitumdei.shotquill.shared.ai.AiSettings
import com.digitumdei.shotquill.shared.domain.CaptionSnippet
import com.digitumdei.shotquill.shared.domain.CropShape
import com.digitumdei.shotquill.shared.domain.LogoCorner
import com.digitumdei.shotquill.shared.domain.LogoSettings
import com.digitumdei.shotquill.shared.domain.LookSettings
import com.digitumdei.shotquill.shared.domain.PhotoTreatment
import com.digitumdei.shotquill.shared.domain.PostDraftId
import com.digitumdei.shotquill.shared.settings.AndroidLocalSettingsRepository
import com.digitumdei.shotquill.shared.settings.LocalAppSettings
import com.digitumdei.shotquill.shared.social.BlueskySettings
import com.digitumdei.shotquill.shared.social.FirebaseHostSettings
import com.digitumdei.shotquill.shared.social.InstagramSettings
import com.digitumdei.shotquill.shared.social.PublishTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AndroidStorageTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun treatmentsAreKeptPerPostAcrossInstances() {
        val id = PostDraftId("draft-1")
        val stored = StoredTreatment(
            treatment = PhotoTreatment(cropShape = CropShape.Story, zoom = 1.5f, logoCorner = LogoCorner.TopStart),
            renderedUri = "file:///edited/draft-1.jpg",
        )

        AndroidPhotoTreatmentStore(context).save(id, stored)

        val reopened = AndroidPhotoTreatmentStore(context)
        assertEquals(stored, reopened.get(id))
        assertNull(reopened.get(PostDraftId("other")))

        reopened.remove(id)
        assertNull(AndroidPhotoTreatmentStore(context).get(id))
    }

    @Test
    fun aFreshInstallStartsWithTheDefaultSettings() {
        assertEquals(LocalAppSettings(), settingsRepository().readSettings())
    }

    @Test
    fun lookLogoShapeAndSnippetsAreKeptAcrossInstances() {
        val settings = LocalAppSettings(
            defaultCropShape = CropShape.Square,
            look = LookSettings(warmth = -0.25f, contrast = 0.5f, saturation = 0.1f, fade = 0.75f),
            logo = LogoSettings(
                uri = "file:///brand/logo.png",
                widthFraction = 0.3f,
                opacity = 0.6f,
                marginFraction = 0.02f,
                corner = LogoCorner.TopEnd,
            ),
            snippets = listOf(CaptionSnippet("Tags", "#doctrinebrewing\n#kzn")),
            ai = AiSettings(baseUrl = "https://api.example.test/v1")
                .withModels(AiOperation.AltText, listOf("one/model", "two/model")),
            bluesky = BlueskySettings(handle = "doctrine.bsky.social", server = "https://pds.example.org"),
            instagram = InstagramSettings(
                username = "doctrinebrewing",
                tokenSavedAtMillis = 1_795_000_000_000L,
                tokenExpiresAtMillis = 1_800_184_000_000L,
            ),
            firebaseHost = FirebaseHostSettings(
                apiKey = "AIza",
                appId = "1:2:android:3",
                projectId = "doctrine-site",
                storageBucket = "doctrine-site.appspot.com",
                email = "uploads@doctrine.example",
            ),
            usualTags = mapOf(PublishTarget.Bluesky to "#craftbeer #bskybeer", PublishTarget.Instagram to "#craftbeer"),
            mentionHistory = mapOf(
                PublishTarget.Bluesky to listOf("doctrinebrewing.co.za", "bsky.app"),
                PublishTarget.Instagram to listOf("some_friend"),
            ),
        )

        settingsRepository().saveSettings(settings)

        assertEquals(settings, settingsRepository().readSettings())
    }

    @Test
    fun clearingTheModelListRestoresTheDefaults() {
        val repository = settingsRepository()
        repository.saveSettings(
            LocalAppSettings(ai = AiSettings().withModels(AiOperation.AltText, listOf("one/model"))),
        )

        repository.saveSettings(LocalAppSettings(ai = AiSettings().withModels(AiOperation.AltText, emptyList())))

        val ai = settingsRepository().readSettings().ai
        assertEquals(AiOperation.AltText.defaultModels, ai.modelsFor(AiOperation.AltText))
        assertEquals(AiSettings.DefaultBaseUrl, ai.baseUrl)
    }

    /** The repository with ordinary preferences standing in for the encrypted store. */
    private fun settingsRepository() = AndroidLocalSettingsRepository(context) {
        it.getSharedPreferences("stand_in_for_encrypted_secrets", android.content.Context.MODE_PRIVATE)
    }

    @Test
    @Config(sdk = [35])
    fun secretsAreRefusedNotStoredUnprotectedWhenEncryptionIsUnavailable() {
        // A phone whose keystore is broken: the encrypted store cannot be opened at all.
        val repository = AndroidLocalSettingsRepository(context) { throw IllegalStateException("no keystore") }

        assertEquals(AndroidLocalSettingsRepository.SecretsUnavailableMessage, repository.secretStorageNotice)
        assertNull(repository.getSecret(BlueskySettings.AppPasswordSecret))
        assertNull(repository.getOpenAiApiKey())
        assertFalse(repository.hasOpenAiApiKey())
        val refused = kotlin.runCatching { repository.saveSecret(BlueskySettings.AppPasswordSecret, "abcd-efgh-ijkl-mnop") }
        assertEquals(AndroidLocalSettingsRepository.SecretsUnavailableMessage, refused.exceptionOrNull()?.message)
        assertTrue(kotlin.runCatching { repository.saveOpenAiApiKey("key-abcdef-123456") }.isFailure)
        repository.clearSecret(BlueskySettings.AppPasswordSecret)
        repository.clearOpenAiApiKey()

        // Nothing was written anywhere it could be read back from.
        val plain = context.getSharedPreferences(
            AndroidLocalSettingsRepository.SecretPreferencesName,
            android.content.Context.MODE_PRIVATE,
        )
        assertTrue(plain.all.isEmpty())
        // Ordinary settings are unaffected.
        repository.saveSettings(LocalAppSettings().withUsualTags(PublishTarget.Bluesky, "#craftbeer"))
        assertEquals("#craftbeer", repository.readSettings().usualTagsFor(PublishTarget.Bluesky))
    }

    @Test
    @Config(sdk = [35])
    fun aStoreThatCannotBeReadIsClearedAndTheLossIsReported() {
        // Something was saved earlier, in a form the store can no longer open.
        context.getSharedPreferences(AndroidLocalSettingsRepository.SecretPreferencesName, android.content.Context.MODE_PRIVATE)
            .edit().putString("secret_old", "unreadable").commit()
        var attempts = 0
        val repository = AndroidLocalSettingsRepository(context) {
            attempts++
            if (attempts == 1) throw IllegalStateException("key was lost")
            it.getSharedPreferences(AndroidLocalSettingsRepository.SecretPreferencesName, android.content.Context.MODE_PRIVATE)
        }

        assertEquals(AndroidLocalSettingsRepository.SecretsResetMessage, repository.secretStorageNotice)
        assertNull(repository.getSecret("old"))
        repository.saveSecret("fresh", "value-1")
        assertEquals("value-1", repository.getSecret("fresh"))
        assertEquals(2, attempts)

        // A healthy store has nothing to report.
        assertNull(settingsRepository().secretStorageNotice)
    }

    @Test
    fun secretsAreKeptSeparatelyByName() {
        val repository = settingsRepository()

        repository.saveSecret(BlueskySettings.AppPasswordSecret, " abcd-efgh-ijkl-mnop ")

        val reopened = settingsRepository()
        assertEquals("abcd-efgh-ijkl-mnop", reopened.getSecret(BlueskySettings.AppPasswordSecret))
        assertNull(reopened.getSecret("something_else"))
        assertNull(reopened.getOpenAiApiKey())

        reopened.clearSecret(BlueskySettings.AppPasswordSecret)
        assertNull(settingsRepository().getSecret(BlueskySettings.AppPasswordSecret))
    }

    @Test
    fun clearingUsualTagsRemovesThem() {
        val repository = settingsRepository()
        repository.saveSettings(LocalAppSettings().withUsualTags(PublishTarget.Bluesky, "#craftbeer"))
        assertEquals("#craftbeer", settingsRepository().readSettings().usualTagsFor(PublishTarget.Bluesky))

        repository.saveSettings(repository.readSettings().withUsualTags(PublishTarget.Bluesky, "  "))

        val reopened = settingsRepository().readSettings()
        assertEquals("", reopened.usualTagsFor(PublishTarget.Bluesky))
        assertEquals(emptyMap(), reopened.usualTags)
    }

    @Test
    fun removingEverySnippetIsRemembered() {
        val repository = settingsRepository()

        repository.saveSettings(LocalAppSettings(snippets = emptyList()))

        assertEquals(emptyList(), settingsRepository().readSettings().snippets)
    }
}
