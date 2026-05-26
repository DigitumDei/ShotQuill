package com.digitumdei.shotquill.shared.settings

import com.digitumdei.shotquill.shared.domain.BrandProfile
import com.digitumdei.shotquill.shared.storage.BrandProfileRepository

class ActiveBrandProfileStore(
    private val settingsRepository: LocalSettingsRepository,
    private val brandProfileRepository: BrandProfileRepository,
) {
    fun readActiveBrandProfile(): BrandProfile? =
        settingsRepository.readSettings().activeBrandProfileId?.let(brandProfileRepository::get)

    fun saveActiveBrandProfile(profile: BrandProfile) {
        brandProfileRepository.save(profile)
        settingsRepository.saveSettings(
            settingsRepository.readSettings().copy(activeBrandProfileId = profile.id),
        )
    }

    fun clearActiveBrandProfile() {
        settingsRepository.saveSettings(
            settingsRepository.readSettings().copy(activeBrandProfileId = null),
        )
    }
}
