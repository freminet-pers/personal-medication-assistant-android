package com.lunamax.medassistant;

import org.json.JSONArray;
import org.json.JSONObject;

/** Anthropic Messages adapter with top-level system and base64 image sources. */
final class AnthropicMessagesTransport extends HttpAiTransport {
    AnthropicMessagesTransport(ProviderProfile profile) { super(profile); }
    @Override public String protocol() { return ProviderProfile.ANTHROPIC_MESSAGES; }

    @Override protected JSONObject buildBody(AiRequest request) throws Exception {
        JSONObject body = new JSONObject().put("model", request.modelId).put("max_tokens", request.maxTokens);
        if (request.systemPrompt != null && !request.systemPrompt.isEmpty()) body.put("system", request.systemPrompt);
        JSONArray content = new JSONArray().put(new JSONObject().put("type", "text").put("text", request.userText));
        for (AiRequest.ImagePart image : request.images) content.put(new JSONObject().put("type", "image").put("source", new JSONObject().put("type", "base64").put("media_type", image.mimeType).put("data", image.base64)));
        body.put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", content)));
        if (request.effort != null && !request.effort.isEmpty() && !"off".equals(request.effort) && ProviderProfile.REASONING_DEEPSEEK.equals(profile.reasoningMode)) body.put("thinking", new JSONObject().put("type", "enabled").put("budget_tokens", Math.max(1024, request.maxTokens / 2)));
        if (request.tools != null) body.put("tools", request.tools); return body;
    }

    @Override protected AiResponse parseResponse(JSONObject payload) { return AiResponse.fromAnthropic(payload); }
}
