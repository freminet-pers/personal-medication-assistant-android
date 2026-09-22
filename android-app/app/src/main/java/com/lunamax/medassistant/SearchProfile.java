package com.lunamax.medassistant;

import java.util.UUID;

/** Search is deliberately independent from a chat provider. */
final class SearchProfile {
    static final String NONE = "NONE";
    static final String DEEPSEEK_NATIVE = "DEEPSEEK_NATIVE";
    static final String OPENAI_RESPONSES_NATIVE = "OPENAI_RESPONSES_NATIVE";
    static final String ANTHROPIC_NATIVE = "ANTHROPIC_NATIVE";

    final String id;
    final String name;
    final String type;
    final String providerId;
    final String baseUrl;
    final String modelId;
    final String authMode;
    final int maxUses;
    final String testState;
    final long lastTestAt;
    final String lastErrorCode;
    final boolean enabled;
    final long createdAt;
    final long updatedAt;

    SearchProfile(String id, String name, String type, String providerId, String baseUrl,
                  String modelId, String authMode, int maxUses, String testState,
                  long lastTestAt, String lastErrorCode, boolean enabled,
                  long createdAt, long updatedAt) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.providerId = providerId;
        this.baseUrl = baseUrl;
        this.modelId = modelId;
        this.authMode = authMode;
        this.maxUses = maxUses;
        this.testState = testState;
        this.lastTestAt = lastTestAt;
        this.lastErrorCode = lastErrorCode;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static SearchProfile none() {
        long now = System.currentTimeMillis();
        return new SearchProfile("none", "不使用联网搜索", NONE, "", "", "", ProviderProfile.AUTH_NONE,
                5, "NOT_TESTED", 0L, "", false, now, now);
    }

    static Builder builder() { return new Builder(); }

    boolean isNativeDeepSeek() { return DEEPSEEK_NATIVE.equals(type); }

    Builder toBuilder() {
        return new Builder().id(id).name(name).type(type).providerId(providerId).baseUrl(baseUrl)
                .modelId(modelId).authMode(authMode).maxUses(maxUses).testState(testState)
                .lastTestAt(lastTestAt).lastErrorCode(lastErrorCode).enabled(enabled)
                .createdAt(createdAt).updatedAt(updatedAt);
    }

    static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String name = "自定义搜索服务";
        private String type = NONE;
        private String providerId = "";
        private String baseUrl = "";
        private String modelId = "";
        private String authMode = ProviderProfile.AUTH_BEARER;
        private int maxUses = 5;
        private String testState = "NOT_TESTED";
        private long lastTestAt;
        private String lastErrorCode = "";
        private boolean enabled;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = createdAt;

        Builder id(String value) { id = value; return this; }
        Builder name(String value) { name = value; return this; }
        Builder type(String value) { type = value; return this; }
        Builder providerId(String value) { providerId = value; return this; }
        Builder baseUrl(String value) { baseUrl = value; return this; }
        Builder modelId(String value) { modelId = value; return this; }
        Builder authMode(String value) { authMode = value; return this; }
        Builder maxUses(int value) { maxUses = value; return this; }
        Builder testState(String value) { testState = value; return this; }
        Builder lastTestAt(long value) { lastTestAt = value; return this; }
        Builder lastErrorCode(String value) { lastErrorCode = value; return this; }
        Builder enabled(boolean value) { enabled = value; return this; }
        Builder createdAt(long value) { createdAt = value; return this; }
        Builder updatedAt(long value) { updatedAt = value; return this; }

        SearchProfile build() {
            if (id == null || id.trim().isEmpty()) id = UUID.randomUUID().toString();
            if (name == null || name.trim().isEmpty()) name = "自定义搜索服务";
            if (type == null || type.isEmpty()) type = NONE;
            if (providerId == null) providerId = "";
            if (baseUrl == null) baseUrl = "";
            if (modelId == null) modelId = "";
            if (authMode == null || authMode.isEmpty()) authMode = ProviderProfile.AUTH_BEARER;
            if (testState == null || testState.isEmpty()) testState = "NOT_TESTED";
            if (lastErrorCode == null) lastErrorCode = "";
            return new SearchProfile(id.trim(), name.trim(), type, providerId.trim(), baseUrl.trim(),
                    modelId.trim(), authMode, Math.max(1, Math.min(10, maxUses)), testState,
                    lastTestAt, lastErrorCode, enabled, createdAt, updatedAt);
        }
    }
}
