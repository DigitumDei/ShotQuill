package com.digitumdei.shotquill.shared.settings

object SecretRedactor {
    const val Redacted = "[REDACTED]"

    private const val MinimumKnownSecretLength = 6
    private val openAiApiKeyPattern = Regex("""sk-[A-Za-z0-9_\-]{8,}""")

    fun maskOpenAiApiKey(apiKey: String?): String =
        when {
            apiKey.isNullOrBlank() -> ""
            apiKey.length <= 8 -> Redacted
            else -> "${apiKey.take(3)}...${apiKey.takeLast(4)}"
        }

    fun redactKnownSecrets(message: String, secrets: Iterable<String?>): String {
        val withoutKnownSecrets = secrets
            .filterNotNull()
            .filter { it.length >= MinimumKnownSecretLength }
            .fold(message) { redacted, secret -> redacted.replace(secret, Redacted) }
        return redactOpenAiApiKeys(withoutKnownSecrets)
    }

    fun redactOpenAiApiKeys(message: String): String =
        message.replace(openAiApiKeyPattern, Redacted)

    fun containsOpenAiApiKey(value: String): Boolean = openAiApiKeyPattern.containsMatchIn(value)
}
