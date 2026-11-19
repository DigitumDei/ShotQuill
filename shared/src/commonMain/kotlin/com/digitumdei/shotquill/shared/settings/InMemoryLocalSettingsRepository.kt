package com.digitumdei.shotquill.shared.settings

class InMemoryLocalSettingsRepository(
    initialSettings: LocalAppSettings = LocalAppSettings(),
) : LocalSettingsRepository {
    private var settings = initialSettings
    private var aiApiKey: String? = null

    override fun readSettings(): LocalAppSettings = settings

    override fun saveSettings(settings: LocalAppSettings) {
        this.settings = settings
    }

    override fun getAiApiKey(): String? = aiApiKey

    override fun saveAiApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        require(trimmed.isNotEmpty()) { "The AI key cannot be blank" }
        aiApiKey = trimmed
    }

    override fun hasAiApiKey(): Boolean = !aiApiKey.isNullOrBlank()

    override fun clearAiApiKey() {
        aiApiKey = null
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
