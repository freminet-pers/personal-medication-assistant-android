package com.lunamax.medassistant;

import org.json.JSONArray;
import org.json.JSONObject;

/** OpenAI Responses adapter; it never assumes output[0] is the answer. */
final class OpenAiResponsesTransport extends HttpAiTransport {
    OpenAiResponsesTransport(ProviderProfile profile) { super(profile); }
    @Override public String protocol() { return ProviderProfile.OPENAI_RESPONSES; }

    @Override protected JSONObject buildBody(AiRequest request) throws Exception {
        JSONObject body = new JSONObject().put("model", request.modelId).put("max_output_tokens", request.maxTokens);
        JSONArray input = new JSONArray();
        if (request.systemPrompt != null && !request.systemPrompt.isEmpty()) input.put(new JSONObject().put("role", "system").put("content", new JSONArray().put(new JSONObject().put("type", "input_text").put("text", request.systemPrompt))));
        JSONArray content = new JSONArray().put(new JSONObject().put("type", "input_text").put("text", request.userText));
        for (AiRequest.ImagePart image : request.images) content.put(new JSONObject().put("type", "input_image").put("image_url", "data:" + image.mimeType + ";base64," + image.base64));
        input.put(new JSONObject().put("role", "user").put("content", content)); body.put("input", input);
        if (request.effort != null && !request.effort.isEmpty() && !"off".equals(request.effort)) body.put("reasoning", new JSONObject().put("effort", request.effort));
        if (request.tools != null) body.put("tools", request.tools); return body;
    }

    @Override protected AiResponse parseResponse(JSONObject payload) { return AiResponse.fromResponses(payload); }
}
