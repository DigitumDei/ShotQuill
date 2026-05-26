package com.digitumdei.shotquill.shared.storage

import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryBrandProfileRepositoryTest {
    @Test
    fun loadsInitialProfilesAndUpdatesSavedProfiles() {
        val repository = InMemoryBrandProfileRepository(listOf(sampleProfile(displayName = "Initial")))

        assertEquals("Initial", repository.get(BrandProfileId("brand-1"))?.displayName)

        repository.save(sampleProfile(displayName = "Updated"))

        assertEquals("Updated", repository.get(BrandProfileId("brand-1"))?.displayName)
        assertNull(repository.get(BrandProfileId("missing")))
    }

    private fun sampleProfile(displayName: String): BrandProfile =
        BrandProfile(
            id = BrandProfileId("brand-1"),
            displayName = displayName,
            voice = "Warm and direct",
            audience = "Taproom regulars",
            defaultHashtags = listOf("#beer"),
            websiteOrSocialLinks = listOf("https://example.com"),
            visualStyleNotes = "Bright natural light.",
            productNamingNotes = "Use full beer names.",
            imageAssets = emptyList(),
            createdAtEpochMillis = 1_700_000_000_000L,
            updatedAtEpochMillis = 1_700_000_000_000L,
        )
}
