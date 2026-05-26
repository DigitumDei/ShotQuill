package com.digitumdei.shotquill.shared.settings

import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import com.digitumdei.shotquill.shared.storage.InMemoryBrandProfileRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveBrandProfileStoreTest {
    @Test
    fun createsUpdatesAndReadsActiveBrandProfile() {
        val settingsRepository = InMemoryLocalSettingsRepository()
        val profileRepository = InMemoryBrandProfileRepository()
        val store = ActiveBrandProfileStore(settingsRepository, profileRepository)

        store.saveActiveBrandProfile(sampleProfile().copy(displayName = "Old name"))
        store.saveActiveBrandProfile(sampleProfile().copy(displayName = "New name"))

        assertEquals("New name", store.readActiveBrandProfile()?.displayName)
        assertEquals(BrandProfileId("brand-1"), settingsRepository.readSettings().activeBrandProfileId)
    }

    @Test
    fun onlyOneProfileIsActive() {
        val settingsRepository = InMemoryLocalSettingsRepository()
        val profileRepository = InMemoryBrandProfileRepository()
        val store = ActiveBrandProfileStore(settingsRepository, profileRepository)

        store.saveActiveBrandProfile(sampleProfile(id = BrandProfileId("brand-1")))
        store.saveActiveBrandProfile(sampleProfile(id = BrandProfileId("brand-2")))

        assertEquals(BrandProfileId("brand-2"), settingsRepository.readSettings().activeBrandProfileId)
        assertEquals("Brand brand-2", store.readActiveBrandProfile()?.displayName)
    }

    @Test
    fun returnsNullWhenNoProfileExists() {
        val store = ActiveBrandProfileStore(
            InMemoryLocalSettingsRepository(),
            InMemoryBrandProfileRepository(),
        )

        assertNull(store.readActiveBrandProfile())
    }

    private fun sampleProfile(id: BrandProfileId = BrandProfileId("brand-1")): BrandProfile =
        BrandProfile(
            id = id,
            displayName = "Brand ${id.value}",
            voice = "Warm and direct",
            audience = "Taproom regulars",
            defaultHashtags = listOf("#beer", "#taproom"),
            websiteOrSocialLinks = listOf("https://example.com", "https://social.example/brand"),
            visualStyleNotes = "Bright natural light.",
            productNamingNotes = "Use full beer names.",
            imageAssets = emptyList(),
            createdAtEpochMillis = 1_700_000_000_000L,
            updatedAtEpochMillis = 1_700_000_000_000L,
        )
}
