package com.digitumdei.shotquill.shared.ai

/**
 * The things the app may ask an AI service to do. Each has its own list of
 * models, so a cheap model can describe photos while another job, if one is
 * ever added, uses something else.
 */
enum class AiOperation(
    val wireValue: String,
    val label: String,
    /** Used until the list is edited in settings. Model names follow OpenRouter's catalogue. */
    val defaultModels: List<String>,
) {
    AltText(
        wireValue = "alt_text",
        label = "Alt text",
        defaultModels = listOf(
            "anthropic/claude-haiku-5.5",
            "openai/gpt-6-luna",
            "google/gemini-4-hydrogen",
        ),
    );

    companion object {
        fun fromWireValue(value: String): AiOperation? = entries.firstOrNull { it.wireValue == value }
    }
}

/**
 * Where AI requests go and which models they use. Any service that speaks the
 * OpenAI chat-completions protocol works; the address defaults to OpenRouter.
 * The API key is a secret and is stored separately.
 */
data class AiSettings(
    val baseUrl: String = DefaultBaseUrl,
    /** Models per operation, in order of preference. A missing or empty entry means "use the defaults". */
    val models: Map<AiOperation, List<String>> = emptyMap(),
) {
    /** The models for [operation]; the first is the one used unless another is picked. Never empty. */
    fun modelsFor(operation: AiOperation): List<String> =
        models[operation]?.takeIf { it.isNotEmpty() } ?: operation.defaultModels

    fun withModels(operation: AiOperation, list: List<String>): AiSettings =
        copy(models = models + (operation to normalizeModels(list)))

    companion object {
        const val DefaultBaseUrl = "https://openrouter.ai/api/v1"
    }
}

/** Splits text typed into settings into model names: one per line or comma, blanks and repeats dropped. */
fun parseModelList(text: String): List<String> = normalizeModels(text.split('\n', ','))

private fun normalizeModels(list: List<String>): List<String> =
    list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
