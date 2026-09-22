package com.lunamax.medassistant;

import org.json.JSONArray;
import org.json.JSONObject;

/** OpenAI Chat Completions adapter, including image_url content blocks. */
final class OpenAiChatCompletionsTransport extends HttpAiTransport {
    OpenAiChatCompletionsTransport(ProviderProfile profile) { super(profile); }
    @Override public String protocol() { return ProviderProfile.OPENAI_CHAT_COMPLETIONS; }

    @Override protected JSONObject buildBody(AiRequest request) throws Exception {
        JSONObject body = new JSONObject().put("model", request.modelId).put(profile.maxTokensField.equals(ProviderProfile.MAX_COMPLETION_TOKENS) ? "max_completion_tokens" : "max_tokens", request.maxTokens);
        JSONArray messages = new JSONArray();
        if (request.systemPrompt != null && !request.systemPrompt.isEmpty()) messages.put(new JSONObject().put("role", profile.useDeveloperRole ? "developer" : "system").put("content", request.systemPrompt));
        Object content = request.images.isEmpty() ? request.userText : imageContent(request);
        messages.put(new JSONObject().put("role", "user").put("content", content)); body.put("messages", messages);
        addReasoning(body, request.effort); if (request.responseJson) body.put("response_format", new JSONObject().put("type", "json_object"));
        if (request.tools != null) body.put("tools", request.tools); return body;
    }

    private JSONArray imageContent(AiRequest request) throws Exception {
        JSONArray content = new JSONArray().put(new JSONObject().put("type", "text").put("text", request.userText));
        for (AiRequest.ImagePart image : request.images) content.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:" + image.mimeType + ";base64," + image.base64)));
        return content;
    }

    private void addReasoning(JSONObject body, String effort) throws Exception {
        if (effort == null || effort.isEmpty() || "off".equals(effort) || ProviderProfile.REASONING_NONE.equals(profile.reasoningMode)) return;
        if (ProviderProfile.REASONING_DEEPSEEK.equals(profile.reasoningMode)) body.put("thinking", new JSONObject().put("type", "enabled")).put("reasoning_effort", effort);
        else if (ProviderProfile.REASONING_STANDARD.equals(profile.reasoningMode)) body.put("reasoning_effort", effort);
    }

    @Override protected AiResponse parseResponse(JSONObject payload) { return AiResponse.fromChat(payload); }
}
