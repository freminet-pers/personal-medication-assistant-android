package com.lunamax.medassistant;

import org.json.JSONArray;
import org.json.JSONObject;

/** Normalized response while retaining the raw JSON for audited drafts. */
final class AiResponse {
    final JSONObject raw;
    final String text;
    final JSONArray citations;
    final JSONObject usage;
    final String model;

    AiResponse(JSONObject raw, String text, JSONArray citations, JSONObject usage, String model) {
        this.raw = raw == null ? new JSONObject() : raw;
        this.text = text == null ? "" : text.trim();
        this.citations = citations == null ? new JSONArray() : citations;
        this.usage = usage == null ? new JSONObject() : usage;
        this.model = model == null ? "" : model;
    }

    static AiResponse fromChat(JSONObject payload) {
        StringBuilder text = new StringBuilder(); JSONArray choices = payload == null ? null : payload.optJSONArray("choices");
        if (choices != null) for (int i = 0; i < choices.length(); i++) {
            JSONObject choice = choices.optJSONObject(i); JSONObject message = choice == null ? null : choice.optJSONObject("message");
            appendContent(text, message == null ? null : message.opt("content"));
        }
        return new AiResponse(payload, text.toString(), new JSONArray(), payload == null ? null : payload.optJSONObject("usage"), payload == null ? "" : payload.optString("model", ""));
    }

    static AiResponse fromResponses(JSONObject payload) {
        StringBuilder text = new StringBuilder(); JSONArray citations = new JSONArray();
        if (payload != null) {
            appendContent(text, payload.opt("output_text"));
            JSONArray output = payload.optJSONArray("output");
            if (output != null) for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i); if (item == null) continue;
                appendContent(text, item.opt("text")); appendContent(text, item.opt("content"));
                collectResponseCitations(item, citations);
            }
        }
        return new AiResponse(payload, text.toString(), citations, payload == null ? null : payload.optJSONObject("usage"), payload == null ? "" : payload.optString("model", ""));
    }

    static AiResponse fromAnthropic(JSONObject payload) {
        StringBuilder text = new StringBuilder(); JSONArray citations = new JSONArray(); JSONArray blocks = payload == null ? null : payload.optJSONArray("content");
        if (blocks != null) for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i); if (block == null) continue;
            String type = block.optString("type", "");
            if ("text".equals(type) || "thinking".equals(type)) appendContent(text, block.opt("text"));
            JSONArray blockCitations = block.optJSONArray("citations");
            if (blockCitations != null) for (int j = 0; j < blockCitations.length(); j++) { JSONObject citation = blockCitations.optJSONObject(j); if (citation != null && citation.optString("url", "").length() > 0) citations.put(citation); }
        }
        return new AiResponse(payload, text.toString(), citations, payload == null ? null : payload.optJSONObject("usage"), payload == null ? "" : payload.optString("model", ""));
    }

    private static void appendContent(StringBuilder output, Object value) {
        if (value instanceof String) { if (output.length() > 0) output.append("\n"); output.append((String) value); return; }
        if (!(value instanceof JSONArray)) return;
        JSONArray blocks = (JSONArray) value;
        for (int i = 0; i < blocks.length(); i++) {
            Object blockValue = blocks.opt(i);
            if (blockValue instanceof String) { appendContent(output, blockValue); continue; }
            JSONObject block = blocks.optJSONObject(i);
            if (block == null) continue;
            String type = block.optString("type", "");
            if ("text".equals(type) || "output_text".equals(type) || "input_text".equals(type) || "thinking".equals(type)) appendContent(output, block.opt("text"));
        }
    }

    private static void collectResponseCitations(JSONObject item, JSONArray output) {
        JSONArray annotations = item.optJSONArray("annotations");
        if (annotations != null) for (int i = 0; i < annotations.length(); i++) { JSONObject annotation = annotations.optJSONObject(i); if (annotation != null && annotation.optString("url", "").length() > 0) output.put(annotation); }
        JSONArray content = item.optJSONArray("content");
        if (content != null) for (int i = 0; i < content.length(); i++) { JSONObject block = content.optJSONObject(i); if (block != null) collectResponseCitations(block, output); }
    }
}
