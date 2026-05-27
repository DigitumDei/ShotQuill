package com.digitumdei.shotquill.shared.ai

object OpenAiJson {
    fun extractChatContent(body: String): String? =
        extractString(body, "content")?.jsonUnescaped()

    fun extractModel(body: String): String? =
        extractString(body, "model")

    fun extractFirstImageBase64(body: String): String? =
        extractString(body, "b64_json")

    fun parseCaptionOutput(content: String): CaptionGenerationOutput {
        val caption = extractString(content, "caption")?.jsonUnescaped() ?: content.trim()
        val shortCaption = extractString(content, "shortCaption")?.jsonUnescaped()
            ?: extractString(content, "short_caption")?.jsonUnescaped()
            ?: caption.take(96)
        val hashtags = extractStringArray(content, "hashtags")
        return CaptionGenerationOutput(
            caption = caption,
            shortCaption = shortCaption,
            hashtags = hashtags,
            modelName = null,
        )
    }

    fun extractString(body: String, name: String): String? {
        val pattern = Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return pattern.find(body)?.groupValues?.get(1)
    }

    private fun extractStringArray(body: String, name: String): List<String> {
        val arrayPattern = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val content = arrayPattern.find(body)?.groupValues?.get(1) ?: return emptyList()
        val stringPattern = Regex(""""((?:\\.|[^"\\])*)"""")
        return stringPattern.findAll(content).map { it.groupValues[1].jsonUnescaped() }.toList()
    }

    private fun String.jsonUnescaped(): String =
        replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
}
