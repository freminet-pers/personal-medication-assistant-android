package com.lunamax.medassistant;

import android.content.Context;

import java.util.List;

/** Coordinates persisted provider/search profiles and isolated credentials. */
final class ProviderProfileRepository {
    private final LunaDatabase database;
    private final SecretStore secrets;

    ProviderProfileRepository(Context context, LunaDatabase database) {
        this.database = database;
        this.secrets = new SecretStore(context);
        ensureDefaults();
    }

    private void ensureDefaults() {
        ProviderProfile builtIn = database.providerProfile(ProviderProfile.BUILTIN_DEEPSEEK_ID);
        if (builtIn == null) database.saveProviderProfile(ProviderProfile.builtInDeepSeek());
        else if (!"deepseek-flash".equals(builtIn.modelId) || !"DeepSeek V4.1 Flash".equals(builtIn.displayName)) {
            // Preserve a user's test state while correcting only the built-in
            // display/model migration mandated by v0.4.0.
            database.saveProviderProfile(builtIn.toBuilder().displayName("DeepSeek V4.1 Flash")
                    .modelId("deepseek-flash").supportsImage(true).imageInputEnabled(true)
                    .updatedAt(System.currentTimeMillis()).build());
        }
        database.selectProvider(database.defaultProviderProfile() == null
                ? ProviderProfile.BUILTIN_DEEPSEEK_ID : database.defaultProviderProfile().id);
        secrets.migrateLegacyToProvider(ProviderProfile.BUILTIN_DEEPSEEK_ID);
    }

    List<ProviderProfile> providers() { return database.providerProfiles(); }

    ProviderProfile current() {
        ProviderProfile selected = database.defaultProviderProfile();
        return selected == null ? database.providerProfile(ProviderProfile.BUILTIN_DEEPSEEK_ID) : selected;
    }

    ProviderProfile get(String id) { return database.providerProfile(id); }

    boolean hasCredential(String providerId) { return secrets.hasProviderKey(providerId); }

    String credentialStatus(String providerId) { return secrets.providerStatus(providerId); }

    String lastConnectionStatus(String providerId) { return secrets.lastConnectionStatus(providerId); }

    void save(ProviderProfile profile, String apiKey) {
        if (profile == null) throw new IllegalArgumentException("Provider 配置为空");
        database.saveProviderProfile(profile);
        if (apiKey != null && !apiKey.trim().isEmpty()) secrets.saveProviderKey(profile.id, apiKey);
    }

    void saveCredential(String providerId, String apiKey) { secrets.saveProviderKey(providerId, apiKey); }

    String readCredential(String providerId) throws Exception { return secrets.readProviderKey(providerId); }

    void deleteCredential(String providerId) { secrets.deleteProviderKey(providerId); }

    void select(String providerId) {
        if (database.providerProfile(providerId) == null) throw new IllegalArgumentException("Provider 不存在");
        database.selectProvider(providerId);
    }

    void markTest(String providerId, String state, String testType, String errorCode) {
        database.markProviderTest(providerId, state, System.currentTimeMillis(), testType, errorCode);
    }

    void delete(String providerId) {
        ProviderProfile profile = database.providerProfile(providerId);
        if (profile == null || profile.isBuiltIn()) throw new IllegalArgumentException("内置 DeepSeek 不能删除");
        boolean wasCurrent = profile.isDefault;
        database.deleteProviderProfile(providerId);
        secrets.deleteProviderKey(providerId);
        if (wasCurrent) select(ProviderProfile.BUILTIN_DEEPSEEK_ID);
    }

    ProviderProfile copyAsCustom(String providerId) {
        ProviderProfile source = get(providerId);
        if (source == null) throw new IllegalArgumentException("Provider 不存在");
        long now = System.currentTimeMillis();
        return source.toBuilder().id(java.util.UUID.randomUUID().toString()).kind(ProviderProfile.KIND_CUSTOM)
                .displayName(source.displayName + "（副本）").isDefault(false).testState("NOT_TESTED")
                .lastTestAt(0).lastTestType("").lastErrorCode("").createdAt(now).updatedAt(now).build();
    }

    List<SearchProfile> searchProfiles() { return database.searchProfiles(); }

    SearchProfile currentSearch() { return database.enabledSearchProfile(); }

    SearchProfile search(String id) { return database.searchProfile(id); }

    void saveSearch(SearchProfile profile, String apiKey) {
        if (profile == null) throw new IllegalArgumentException("搜索配置为空");
        database.saveSearchProfile(profile);
        if (apiKey != null && !apiKey.trim().isEmpty() && !profile.isNativeDeepSeek()) secrets.saveSearchKey(profile.id, apiKey);
    }

    void saveSearchCredential(String searchId, String apiKey) { secrets.saveSearchKey(searchId, apiKey); }

    String readSearchCredential(String searchId) throws Exception { return secrets.readSearchKey(searchId); }

    boolean hasSearchCredential(String searchId) { return secrets.hasSearchKey(searchId); }

    void selectSearch(String searchId) { database.selectSearchProfile(searchId); }

    void markSearchTest(String searchId, String state, String errorCode) {
        database.markSearchTest(searchId, state, System.currentTimeMillis(), errorCode);
    }

    void deleteSearch(String searchId) {
        database.deleteSearchProfile(searchId);
        secrets.deleteSearchKey(searchId);
        if (searchId != null && searchId.equals(currentSearch() == null ? "" : currentSearch().id)) database.selectSearchProfile("");
    }
}
