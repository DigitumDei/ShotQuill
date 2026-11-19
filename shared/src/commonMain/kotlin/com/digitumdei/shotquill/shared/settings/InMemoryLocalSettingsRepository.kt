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

    private val secrets = mutableMapOf<String, String>()

    override fun getSecret(name: String): String? = secrets[name]

    override fun saveSecret(name: String, value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "A secret cannot be blank" }
        secrets[name] = trimmed
    }

    override fun clearSecret(name: String) {
        secrets.remove(name)
    }
}
