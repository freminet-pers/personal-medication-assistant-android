package com.lunamax.medassistant;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Protocol-neutral request assembled before an adapter maps it to JSON. */
final class AiRequest {
    final String modelId;
    final String systemPrompt;
    final String userText;
    final List<ImagePart> images;
    final String effort;
    final int maxTokens;
    final boolean responseJson;
    final JSONArray tools;

    private AiRequest(Builder builder) {
        modelId = builder.modelId;
        systemPrompt = builder.systemPrompt;
        userText = builder.userText;
        images = new ArrayList<>(builder.images);
        effort = builder.effort;
        maxTokens = builder.maxTokens;
        responseJson = builder.responseJson;
        tools = builder.tools;
    }

    static Builder builder() { return new Builder(); }

    static final class ImagePart {
        final String mimeType;
        final String base64;

        ImagePart(String mimeType, String base64) {
            this.mimeType = mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType;
            this.base64 = base64 == null ? "" : base64;
        }
    }

    static final class Builder {
        private String modelId = "";
        private String systemPrompt = "";
        private String userText = "";
        private final List<ImagePart> images = new ArrayList<>();
        private String effort = "off";
        private int maxTokens = 1400;
        private boolean responseJson;
        private JSONArray tools;

        Builder modelId(String value) { modelId = value == null ? "" : value; return this; }
        Builder systemPrompt(String value) { systemPrompt = value == null ? "" : value; return this; }
        Builder userText(String value) { userText = value == null ? "" : value; return this; }
        Builder effort(String value) { effort = value == null ? "off" : value; return this; }
        Builder maxTokens(int value) { maxTokens = Math.max(1, Math.min(32_000, value)); return this; }
        Builder responseJson(boolean value) { responseJson = value; return this; }
        Builder tools(JSONArray value) { tools = value; return this; }
        Builder image(String mimeType, String base64) { images.add(new ImagePart(mimeType, base64)); return this; }

        AiRequest build() {
            if (modelId == null || modelId.trim().isEmpty()) throw new IllegalArgumentException("模型 ID 不能为空");
            if (userText == null || userText.trim().isEmpty()) throw new IllegalArgumentException("请求内容不能为空");
            return new AiRequest(this);
        }
    }
}
