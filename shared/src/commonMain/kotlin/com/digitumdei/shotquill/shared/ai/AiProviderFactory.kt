package com.digitumdei.shotquill.shared.ai

import com.digitumdei.shotquill.shared.settings.LocalSettingsRepository

object AiProviderFactory {
    fun fake(modelName: String = "fake-ai-provider"): AiProvider =
        FakeAiProvider(modelName)

    fun openAi(
        settingsRepository: LocalSettingsRepository,
        transport: OpenAiHttpTransport,
        config: OpenAiProviderConfig = OpenAiProviderConfig(),
        logger: AiRequestLogger = NoopAiRequestLogger,
    ): AiProvider =
        OpenAiProvider(
            settingsRepository = settingsRepository,
            transport = transport,
            config = config,
            logger = logger,
        )
}
