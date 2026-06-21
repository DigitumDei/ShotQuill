package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.settings.SecretRedactor

sealed class AiError {
    abstract val userMessage: String

    data object MissingApiKey : AiError() {
        override val userMessage: String = "Add an OpenAI API key before running AI features."
    }

    data class InvalidApiKey(
        override val userMessage: String = "The OpenAI API key was rejected.",
    ) : AiError()

    data class RateLimited(
        override val userMessage: String = "The AI provider is rate limited. Try again later.",
    ) : AiError()

    data class QuotaExceeded(
        override val userMessage: String = "The OpenAI account quota has been reached.",
    ) : AiError()

    data class ContextLengthExceeded(
        override val userMessage: String = "The request was too large for the AI model. Try a smaller image or shorter prompt.",
    ) : AiError()

    data class ImageRejected(
        override val userMessage: String = "The image could not be used by the AI provider.",
    ) : AiError()

    data class ContentPolicyViolation(
        override val userMessage: String = "The AI provider rejected the request for safety policy reasons.",
    ) : AiError()

    data class NetworkFailure(
        override val userMessage: String = "The network request to the AI provider failed.",
    ) : AiError()

    data class ProviderFailure(
        val statusCode: Int?,
        override val userMessage: String = "The AI provider returned an unexpected error.",
    ) : AiError()
}

sealed class AiProviderResult<out T> {
    data class Success<T>(val value: T) : AiProviderResult<T>()
    data class Failure(val error: AiError) : AiProviderResult<Nothing>()
}

object AiErrorMapper {
    fun fromHttpStatus(statusCode: Int, body: String, knownSecrets: Iterable<String?> = emptyList()): AiError {
        val redactedBody = SecretRedactor.redactKnownSecrets(body, knownSecrets).lowercase()
        return when (statusCode) {
            401 -> AiError.InvalidApiKey()
            413 -> AiError.ContextLengthExceeded()
            429 -> {
                if (redactedBody.contains("quota") || redactedBody.contains("insufficient_quota")) {
                    AiError.QuotaExceeded()
                } else {
                    AiError.RateLimited()
                }
            }
            400 -> {
                when {
                    redactedBody.contains("content_policy") ||
                        redactedBody.contains("content policy") ||
                        redactedBody.contains("safety") -> AiError.ContentPolicyViolation()
                    redactedBody.contains("context_length") ||
                        redactedBody.contains("context length") ||
                        redactedBody.contains("maximum context") ||
                        redactedBody.contains("too long") ||
                        redactedBody.contains("too many tokens") -> AiError.ContextLengthExceeded()
                    redactedBody.contains("image") ||
                        redactedBody.contains("size") ||
                        redactedBody.contains("format") -> AiError.ImageRejected()
                    else -> AiError.ProviderFailure(statusCode = statusCode)
                }
            }
            else -> AiError.ProviderFailure(statusCode = statusCode)
        }
    }

    fun fromNetworkFailure(message: String?, knownSecrets: Iterable<String?> = emptyList()): AiError.NetworkFailure {
        val redacted = SecretRedactor.redactKnownSecrets(
            message = message?.takeIf { it.isNotBlank() } ?: "The network request to the AI provider failed.",
            secrets = knownSecrets,
        )
        return AiError.NetworkFailure(userMessage = redacted)
    }
}
