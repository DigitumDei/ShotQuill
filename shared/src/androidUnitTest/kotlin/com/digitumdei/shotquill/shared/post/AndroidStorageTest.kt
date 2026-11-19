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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
        assertEquals(LocalAppSettings(), AndroidLocalSettingsRepository(context).readSettings())
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
        )

        AndroidLocalSettingsRepository(context).saveSettings(settings)

        assertEquals(settings, AndroidLocalSettingsRepository(context).readSettings())
    }

    @Test
    fun clearingTheModelListRestoresTheDefaults() {
        val repository = AndroidLocalSettingsRepository(context)
        repository.saveSettings(
            LocalAppSettings(ai = AiSettings().withModels(AiOperation.AltText, listOf("one/model"))),
        )

        repository.saveSettings(LocalAppSettings(ai = AiSettings().withModels(AiOperation.AltText, emptyList())))

        val ai = AndroidLocalSettingsRepository(context).readSettings().ai
        assertEquals(AiOperation.AltText.defaultModels, ai.modelsFor(AiOperation.AltText))
        assertEquals(AiSettings.DefaultBaseUrl, ai.baseUrl)
    }

    @Test
    fun removingEverySnippetIsRemembered() {
        val repository = AndroidLocalSettingsRepository(context)

        repository.saveSettings(LocalAppSettings(snippets = emptyList()))

        assertEquals(emptyList(), AndroidLocalSettingsRepository(context).readSettings().snippets)
    }
}
