package com.digitumdei.shotquill.shared.settings

class InMemoryLocalSettingsRepository(
    initialSettings: LocalAppSettings = LocalAppSettings(),
) : LocalSettingsRepository {
    private var settings = initialSettings
    private var openAiApiKey: String? = null

    override fun readSettings(): LocalAppSettings = settings

    override fun saveSettings(settings: LocalAppSettings) {
        this.settings = settings
    }

    override fun getOpenAiApiKey(): String? = openAiApiKey

    override fun saveOpenAiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "OpenAI API key cannot be blank" }
        openAiApiKey = trimmed
    }

    override fun hasOpenAiApiKey(): Boolean = !openAiApiKey.isNullOrBlank()

    override fun clearOpenAiApiKey() {
        openAiApiKey = null
    }
}
