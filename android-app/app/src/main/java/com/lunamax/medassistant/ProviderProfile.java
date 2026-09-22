package com.lunamax.medassistant;

import java.util.UUID;

/**
 * Persisted description of one chat/multimodal provider. Secrets never live in
 * this object or in the database; they are resolved through SecretStore by id.
 */
final class ProviderProfile {
    static final String BUILTIN_DEEPSEEK_ID = "00000000-0000-0000-0000-000000000001";
    static final String KIND_BUILTIN_DEEPSEEK = "BUILTIN_DEEPSEEK";
    static final String KIND_CUSTOM = "CUSTOM";

    static final String OPENAI_CHAT_COMPLETIONS = "OPENAI_CHAT_COMPLETIONS";
    static final String OPENAI_RESPONSES = "OPENAI_RESPONSES";
    static final String ANTHROPIC_MESSAGES = "ANTHROPIC_MESSAGES";

    static final String AUTH_BEARER = "BEARER";
    static final String AUTH_X_API_KEY = "X_API_KEY";
    static final String AUTH_BOTH = "BOTH";
    static final String AUTH_NONE = "NONE";

    static final String MAX_TOKENS = "MAX_TOKENS";
    static final String MAX_COMPLETION_TOKENS = "MAX_COMPLETION_TOKENS";
    static final String REASONING_NONE = "NONE";
    static final String REASONING_STANDARD = "REASONING_EFFORT";
    static final String REASONING_DEEPSEEK = "DEEPSEEK_THINKING";

    final String id;
    final String displayName;
    final String kind;
    final String protocol;
    final String baseUrl;
    final String modelId;
    final boolean supportsText;
    final boolean supportsImage;
    final boolean supportsReasoning;
    final String fastEffort;
    final String deepEffort;
    final String maxEffort;
    final String authMode;
    final String anthropicVersion;
    final int connectTimeoutMs;
    final int readTimeoutMs;
    final boolean useDeveloperRole;
    final String maxTokensField;
    final String reasoningMode;
    final boolean imageInputEnabled;
    final String testState;
    final long lastTestAt;
    final String lastTestType;
    final String lastErrorCode;
    final boolean isDefault;
    final long createdAt;
    final long updatedAt;

    ProviderProfile(String id, String displayName, String kind, String protocol, String baseUrl,
                    String modelId, boolean supportsText, boolean supportsImage,
                    boolean supportsReasoning, String fastEffort, String deepEffort,
                    String maxEffort, String authMode, String anthropicVersion,
                    int connectTimeoutMs, int readTimeoutMs, boolean useDeveloperRole,
                    String maxTokensField, String reasoningMode, boolean imageInputEnabled,
                    String testState, long lastTestAt, String lastTestType, String lastErrorCode,
                    boolean isDefault, long createdAt, long updatedAt) {
        this.id = id;
        this.displayName = displayName;
        this.kind = kind;
        this.protocol = protocol;
        this.baseUrl = baseUrl;
        this.modelId = modelId;
        this.supportsText = supportsText;
        this.supportsImage = supportsImage;
        this.supportsReasoning = supportsReasoning;
        this.fastEffort = fastEffort;
        this.deepEffort = deepEffort;
        this.maxEffort = maxEffort;
        this.authMode = authMode;
        this.anthropicVersion = anthropicVersion;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.useDeveloperRole = useDeveloperRole;
        this.maxTokensField = maxTokensField;
        this.reasoningMode = reasoningMode;
        this.imageInputEnabled = imageInputEnabled;
        this.testState = testState;
        this.lastTestAt = lastTestAt;
        this.lastTestType = lastTestType;
        this.lastErrorCode = lastErrorCode;
        this.isDefault = isDefault;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static ProviderProfile builtInDeepSeek() {
        long now = System.currentTimeMillis();
        return new ProviderProfile(
                BUILTIN_DEEPSEEK_ID,
                "DeepSeek V4.1 Flash",
                KIND_BUILTIN_DEEPSEEK,
                OPENAI_CHAT_COMPLETIONS,
                "https://api.deepseek.com",
                "deepseek-flash",
                true,
                true,
                true,
                "off",
                "high",
                "max",
                AUTH_BEARER,
                "2023-06-01",
                15_000,
                30_000,
                false,
                MAX_TOKENS,
                REASONING_DEEPSEEK,
                true,
                "NOT_TESTED",
                0L,
                "",
                "",
                true,
                now,
                now);
    }

    static Builder builder() { return new Builder(); }

    boolean isBuiltIn() { return KIND_BUILTIN_DEEPSEEK.equals(kind); }

    String modelLabel() {
        if ("deepseek-flash".equals(modelId)) return "V4.1 Flash";
        return modelId == null || modelId.isEmpty() ? "未填写模型" : modelId;
    }

    Builder toBuilder() {
        return new Builder()
                .id(id).displayName(displayName).kind(kind).protocol(protocol).baseUrl(baseUrl)
                .modelId(modelId).supportsText(supportsText).supportsImage(supportsImage)
                .supportsReasoning(supportsReasoning).fastEffort(fastEffort).deepEffort(deepEffort)
                .maxEffort(maxEffort).authMode(authMode).anthropicVersion(anthropicVersion)
                .connectTimeoutMs(connectTimeoutMs).readTimeoutMs(readTimeoutMs)
                .useDeveloperRole(useDeveloperRole).maxTokensField(maxTokensField)
                .reasoningMode(reasoningMode).imageInputEnabled(imageInputEnabled)
                .testState(testState).lastTestAt(lastTestAt).lastTestType(lastTestType)
                .lastErrorCode(lastErrorCode).isDefault(isDefault).createdAt(createdAt)
                .updatedAt(updatedAt);
    }

    static final class Builder {
        private String id = UUID.randomUUID().toString();
        private String displayName = "自定义 AI 服务";
        private String kind = KIND_CUSTOM;
        private String protocol = OPENAI_CHAT_COMPLETIONS;
        private String baseUrl = "";
        private String modelId = "";
        private boolean supportsText = true;
        private boolean supportsImage;
        private boolean supportsReasoning;
        private String fastEffort = "off";
        private String deepEffort = "high";
        private String maxEffort = "max";
        private String authMode = AUTH_BEARER;
        private String anthropicVersion = "2023-06-01";
        private int connectTimeoutMs = 15_000;
        private int readTimeoutMs = 30_000;
        private boolean useDeveloperRole;
        private String maxTokensField = MAX_TOKENS;
        private String reasoningMode = REASONING_NONE;
        private boolean imageInputEnabled;
        private String testState = "NOT_TESTED";
        private long lastTestAt;
        private String lastTestType = "";
        private String lastErrorCode = "";
        private boolean isDefault;
        private long createdAt = System.currentTimeMillis();
        private long updatedAt = createdAt;

        Builder id(String value) { id = value; return this; }
        Builder displayName(String value) { displayName = value; return this; }
        Builder kind(String value) { kind = value; return this; }
        Builder protocol(String value) { protocol = value; return this; }
        Builder baseUrl(String value) { baseUrl = value; return this; }
        Builder modelId(String value) { modelId = value; return this; }
        Builder supportsText(boolean value) { supportsText = value; return this; }
        Builder supportsImage(boolean value) { supportsImage = value; return this; }
        Builder supportsReasoning(boolean value) { supportsReasoning = value; return this; }
        Builder fastEffort(String value) { fastEffort = value; return this; }
        Builder deepEffort(String value) { deepEffort = value; return this; }
        Builder maxEffort(String value) { maxEffort = value; return this; }
        Builder authMode(String value) { authMode = value; return this; }
        Builder anthropicVersion(String value) { anthropicVersion = value; return this; }
        Builder connectTimeoutMs(int value) { connectTimeoutMs = value; return this; }
        Builder readTimeoutMs(int value) { readTimeoutMs = value; return this; }
        Builder useDeveloperRole(boolean value) { useDeveloperRole = value; return this; }
        Builder maxTokensField(String value) { maxTokensField = value; return this; }
        Builder reasoningMode(String value) { reasoningMode = value; return this; }
        Builder imageInputEnabled(boolean value) { imageInputEnabled = value; return this; }
        Builder testState(String value) { testState = value; return this; }
        Builder lastTestAt(long value) { lastTestAt = value; return this; }
        Builder lastTestType(String value) { lastTestType = value; return this; }
        Builder lastErrorCode(String value) { lastErrorCode = value; return this; }
        Builder isDefault(boolean value) { isDefault = value; return this; }
        Builder createdAt(long value) { createdAt = value; return this; }
        Builder updatedAt(long value) { updatedAt = value; return this; }

        ProviderProfile build() {
            if (id == null || id.trim().isEmpty()) id = UUID.randomUUID().toString();
            if (displayName == null || displayName.trim().isEmpty()) displayName = "自定义 AI 服务";
            if (kind == null || kind.isEmpty()) kind = KIND_CUSTOM;
            if (protocol == null || protocol.isEmpty()) protocol = OPENAI_CHAT_COMPLETIONS;
            if (baseUrl == null) baseUrl = "";
            if (modelId == null) modelId = "";
            if (authMode == null || authMode.isEmpty()) authMode = AUTH_BEARER;
            if (anthropicVersion == null || anthropicVersion.isEmpty()) anthropicVersion = "2023-06-01";
            if (testState == null || testState.isEmpty()) testState = "NOT_TESTED";
            if (lastTestType == null) lastTestType = "";
            if (lastErrorCode == null) lastErrorCode = "";
            if (connectTimeoutMs < 1_000) connectTimeoutMs = 1_000;
            if (readTimeoutMs < 1_000) readTimeoutMs = 1_000;
            return new ProviderProfile(id.trim(), displayName.trim(), kind, protocol, baseUrl.trim(),
                    modelId.trim(), supportsText, supportsImage, supportsReasoning, safe(fastEffort),
                    safe(deepEffort), safe(maxEffort), authMode, anthropicVersion.trim(),
                    connectTimeoutMs, readTimeoutMs, useDeveloperRole, safe(maxTokensField),
                    safe(reasoningMode), imageInputEnabled, testState, lastTestAt, safe(lastTestType),
                    safe(lastErrorCode), isDefault, createdAt, updatedAt);
        }

        private static String safe(String value) { return value == null ? "" : value; }
    }
}
