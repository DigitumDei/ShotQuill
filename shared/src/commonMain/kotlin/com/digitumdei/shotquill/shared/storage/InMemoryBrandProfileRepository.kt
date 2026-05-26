package com.digitumdei.shotquill.shared.storage

import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.domain.BrandProfileId

class InMemoryBrandProfileRepository(
    initialProfiles: List<BrandProfile> = emptyList(),
) : BrandProfileRepository {
    private val profiles = initialProfiles.associateBy { it.id }.toMutableMap()

    override fun save(brandProfile: BrandProfile) {
        profiles[brandProfile.id] = brandProfile
    }

    override fun get(id: BrandProfileId): BrandProfile? = profiles[id]
}
