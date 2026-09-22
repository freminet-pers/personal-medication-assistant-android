package com.lunamax.medassistant;

/** Selects the concrete protocol adapter from persisted configuration. */
final class TransportFactory {
    private TransportFactory() { }

    static AiTransport create(ProviderProfile profile) {
        if (profile == null) throw new IllegalArgumentException("Provider 配置为空");
        if (ProviderProfile.OPENAI_CHAT_COMPLETIONS.equals(profile.protocol)) return new OpenAiChatCompletionsTransport(profile);
        if (ProviderProfile.OPENAI_RESPONSES.equals(profile.protocol)) return new OpenAiResponsesTransport(profile);
        if (ProviderProfile.ANTHROPIC_MESSAGES.equals(profile.protocol)) return new AnthropicMessagesTransport(profile);
        throw new IllegalArgumentException("不支持的 Provider 协议");
    }
}
