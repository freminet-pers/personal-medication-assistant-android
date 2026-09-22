package com.lunamax.medassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Per-profile encrypted credential storage. Profile configuration is kept in
 * SQLite, while every provider/search credential has its own Keystore alias.
 * The old v0.3 DeepSeek value is migrated only after a verified round trip.
 */
final class SecretStore {
    private static final String STORE = "AndroidKeyStore";
    private static final String PREFS = "luna_secure_runtime";
    private static final String NEW_PREFIX = "v3:";
    private static final String PREF_PROVIDER = "provider_key_";
    private static final String PREF_SEARCH = "search_key_";
    private static final String PREF_LAST_STATUS = "last_connection_status_";
    private static final String PREF_LAST_AT = "last_connection_at_";

    // v0.3 storage, retained only for one-way migration.
    private static final String OLD_API_ALIAS = "medassistant_deepseek_api_v1";
    private static final String OLD_API_KEY = "deepseek_key_enc_v2";
    private static final String OLD_LEGACY_API_KEY = "deepseek_key_enc";
    private static final String OLD_SELF_TEST = "deepseek_key_self_test";

    private final SharedPreferences preferences;
    private final DataCipher legacyCipher;

    SecretStore(Context context) {
        Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        legacyCipher = new DataCipher(app);
    }

    boolean hasKey() { return hasProviderKey(ProviderProfile.BUILTIN_DEEPSEEK_ID); }

    String status() { return providerStatus(ProviderProfile.BUILTIN_DEEPSEEK_ID); }

    String lastConnectionStatus() { return lastConnectionStatus(ProviderProfile.BUILTIN_DEEPSEEK_ID); }

    void save(String value) { saveProviderKey(ProviderProfile.BUILTIN_DEEPSEEK_ID, value); }

    void delete() {
        deleteProviderKey(ProviderProfile.BUILTIN_DEEPSEEK_ID);
        clearLegacyValues();
    }

    String readApiKey() throws Exception { return readProviderKey(ProviderProfile.BUILTIN_DEEPSEEK_ID); }

    void recordConnection(String status) { recordConnection(ProviderProfile.BUILTIN_DEEPSEEK_ID, status); }

    boolean hasProviderKey(String providerId) {
        try { return !readProviderKey(providerId).isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    String providerStatus(String providerId) {
        try {
            if (readProviderKey(providerId).isEmpty()) return "未配置";
            String connection = preferences.getString(statusKey(providerId), "");
            return connection.isEmpty() ? "已保存但未测试" : "已保存 · " + connection;
        } catch (Exception error) {
            return "凭据损坏，请重新配置";
        }
    }

    String lastConnectionStatus(String providerId) {
        String value = preferences.getString(statusKey(providerId), "");
        long at = preferences.getLong(atKey(providerId), 0L);
        return value.isEmpty() || at == 0L ? "尚未测试连接" : "最近测试：" + value;
    }

    void saveProviderKey(String providerId, String value) {
        String normalized = value == null ? "" : value.trim();
        if (providerId == null || providerId.trim().isEmpty()) throw new IllegalArgumentException("Provider 标识无效");
        if (normalized.isEmpty()) throw new IllegalArgumentException("请输入 API Key");
        String id = providerId.trim();
        String alias = aliasFor("provider", id);
        try {
            if (!selfTest(alias, normalized)) throw new IllegalStateException("SECURE_STORAGE_SELF_TEST_FAILED");
            String encrypted = encrypt(alias, normalized);
            if (!preferences.edit().putString(providerPrefKey(id), encrypted).commit()) {
                throw new IllegalStateException("SECURE_STORAGE_PERSISTENCE_FAILED");
            }
            String roundTrip = decrypt(alias, preferences.getString(providerPrefKey(id), ""));
            if (!constantTimeEquals(normalized, roundTrip)) throw new IllegalStateException("SECURE_STORAGE_SELF_TEST_FAILED");
            if (ProviderProfile.BUILTIN_DEEPSEEK_ID.equals(id)) clearLegacyValues();
        } catch (Exception error) {
            // Only this profile's new value is removed. Other provider/search
            // credentials and all health data remain untouched.
            preferences.edit().remove(providerPrefKey(id)).commit();
            throw new IllegalStateException(safeMessage(error, "SECURE_STORAGE_UNAVAILABLE"), error);
        }
    }

    String readProviderKey(String providerId) throws Exception {
        if (providerId == null || providerId.trim().isEmpty()) return "";
        String id = providerId.trim();
        String stored = preferences.getString(providerPrefKey(id), "");
        if (!stored.isEmpty()) return decrypt(aliasFor("provider", id), stored);
        if (ProviderProfile.BUILTIN_DEEPSEEK_ID.equals(id)) {
            String legacy = readLegacyDeepSeekKey();
            if (!legacy.isEmpty()) {
                migrateLegacyToProvider(id, legacy);
                return decrypt(aliasFor("provider", id), preferences.getString(providerPrefKey(id), ""));
            }
        }
        return "";
    }

    /** Returns true only when the legacy value was copied and verified. */
    boolean migrateLegacyToProvider(String providerId) {
        if (providerId == null || providerId.trim().isEmpty()) return false;
        try {
            if (!preferences.getString(providerPrefKey(providerId), "").isEmpty()) return true;
            String legacy = readLegacyDeepSeekKey();
            if (legacy.isEmpty()) return false;
            migrateLegacyToProvider(providerId.trim(), legacy);
            return true;
        } catch (Exception ignored) {
            // Keep the old ciphertext intact so a later app start can retry.
            return false;
        }
    }

    void saveSearchKey(String searchProfileId, String value) { saveKey("search", searchProfileId, value); }

    String readSearchKey(String searchProfileId) throws Exception { return readKey("search", searchProfileId); }

    boolean hasSearchKey(String searchProfileId) {
        try { return !readSearchKey(searchProfileId).isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    void deleteSearchKey(String searchProfileId) { deleteKey("search", searchProfileId); }

    void deleteProviderKey(String providerId) { deleteKey("provider", providerId); }

    void recordConnection(String providerId, String status) {
        String id = providerId == null ? "" : providerId.trim();
        if (id.isEmpty()) return;
        String safe = redact(status == null ? "请求失败" : status);
        preferences.edit().putString(statusKey(id), safe).putLong(atKey(id), System.currentTimeMillis()).commit();
    }

    private void saveKey(String purpose, String idValue, String value) {
        String id = requireId(idValue);
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("请输入搜索 API Key");
        String alias = aliasFor(purpose, id);
        try {
            if (!selfTest(alias, normalized)) throw new IllegalStateException("SECURE_STORAGE_SELF_TEST_FAILED");
            String encrypted = encrypt(alias, normalized);
            if (!preferences.edit().putString(prefKey(purpose, id), encrypted).commit()) throw new IllegalStateException("SECURE_STORAGE_PERSISTENCE_FAILED");
            if (!constantTimeEquals(normalized, decrypt(alias, preferences.getString(prefKey(purpose, id), "")))) throw new IllegalStateException("SECURE_STORAGE_SELF_TEST_FAILED");
        } catch (Exception error) {
            preferences.edit().remove(prefKey(purpose, id)).commit();
            throw new IllegalStateException(safeMessage(error, "SECURE_STORAGE_UNAVAILABLE"), error);
        }
    }

    private String readKey(String purpose, String idValue) throws Exception {
        String id = requireId(idValue);
        String stored = preferences.getString(prefKey(purpose, id), "");
        return stored.isEmpty() ? "" : decrypt(aliasFor(purpose, id), stored);
    }

    private void deleteKey(String purpose, String idValue) {
        if (idValue == null || idValue.trim().isEmpty()) return;
        String id = idValue.trim();
        Exception failure = null;
        try {
            if (!preferences.edit().remove(prefKey(purpose, id)).remove(statusKey(id)).remove(atKey(id)).commit()) {
                failure = new IllegalStateException("SECURE_STORAGE_PREFERENCE_CLEANUP_FAILED");
            }
        } catch (Exception error) { failure = error; }
        try {
            KeyStore store = KeyStore.getInstance(STORE); store.load(null);
            String alias = aliasFor(purpose, id);
            if (store.containsAlias(alias)) store.deleteEntry(alias);
        } catch (Exception error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        if (failure != null) throw new IllegalStateException("SECURE_STORAGE_CLEANUP_FAILED", failure);
    }

    private void migrateLegacyToProvider(String providerId, String legacy) throws Exception {
        String alias = aliasFor("provider", providerId);
        String encrypted = encrypt(alias, legacy);
        if (!preferences.edit().putString(providerPrefKey(providerId), encrypted).commit()) throw new IllegalStateException("SECURE_STORAGE_PERSISTENCE_FAILED");
        String roundTrip = decrypt(alias, preferences.getString(providerPrefKey(providerId), ""));
        if (!constantTimeEquals(legacy, roundTrip)) throw new IllegalStateException("SECURE_STORAGE_SELF_TEST_FAILED");
        // The legacy ciphertext is removed only after the new value is proven readable.
        clearLegacyValues();
    }

    private String readLegacyDeepSeekKey() throws Exception {
        String current = preferences.getString(OLD_API_KEY, "");
        if (!current.isEmpty()) {
            try { return decrypt(OLD_API_ALIAS, current); }
            catch (Exception ignored) { /* Try the earlier v0.3 preference below. */ }
        }
        String legacy = preferences.getString(OLD_LEGACY_API_KEY, "");
        if (legacy.isEmpty()) return "";
        if (!legacy.startsWith("v1:")) throw new IllegalStateException("LEGACY_KEY_CORRUPT");
        String recovered = legacyCipher.decryptStrict(legacy);
        return recovered == null ? "" : recovered.trim();
    }

    private void clearLegacyValues() {
        preferences.edit().remove(OLD_API_KEY).remove(OLD_LEGACY_API_KEY).remove(OLD_SELF_TEST).commit();
        try {
            KeyStore store = KeyStore.getInstance(STORE); store.load(null);
            if (store.containsAlias(OLD_API_ALIAS)) store.deleteEntry(OLD_API_ALIAS);
        } catch (Exception ignored) { }
    }

    private boolean selfTest(String alias, String value) {
        try { return constantTimeEquals(value, decrypt(alias, encrypt(alias, value))); }
        catch (Exception ignored) { return false; }
    }

    private String encrypt(String alias, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(alias));
        return NEW_PREFIX + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }

    private String decrypt(String alias, String encoded) throws Exception {
        if (encoded == null || encoded.isEmpty()) return "";
        String[] parts = encoded.split(":", 3);
        if (parts.length != 3 || !("v2".equals(parts[0]) || "v3".equals(parts[0]))) throw new IllegalArgumentException("SECURE_STORAGE_CORRUPT");
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP); byte[] ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);
        if (iv.length != 12 || ciphertext.length < 16) throw new IllegalArgumentException("SECURE_STORAGE_CORRUPT");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(alias), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private synchronized SecretKey key(String alias) throws Exception {
        KeyStore store = KeyStore.getInstance(STORE); store.load(null);
        if (!store.containsAlias(alias)) {
            try { generate(alias, 256); }
            catch (Exception unsupported) { if (store.containsAlias(alias)) store.deleteEntry(alias); generate(alias, 128); }
        }
        KeyStore.Entry entry = store.getEntry(alias, null);
        if (!(entry instanceof KeyStore.SecretKeyEntry)) throw new IllegalStateException("SECURE_STORAGE_KEY_INVALID");
        return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
    }

    private void generate(String alias, int size) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(size).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        generator.generateKey();
    }

    private static String requireId(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("凭据标识无效");
        return value.trim();
    }

    private static String providerPrefKey(String id) { return PREF_PROVIDER + id; }
    private static String prefKey(String purpose, String id) { return ("search".equals(purpose) ? PREF_SEARCH : PREF_PROVIDER) + id; }
    private static String statusKey(String id) { return PREF_LAST_STATUS + id; }
    private static String atKey(String id) { return PREF_LAST_AT + id; }

    private static String aliasFor(String purpose, String id) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((purpose + ":" + id).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("medassistant_").append(purpose).append('_');
            for (int i = 0; i < 16; i++) result.append(String.format(java.util.Locale.US, "%02x", bytes[i]));
            return result.toString();
        } catch (Exception error) { throw new IllegalStateException("SECURE_STORAGE_ALIAS_FAILED", error); }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeMessage(Exception error, String fallback) {
        String message = error.getMessage(); return message == null || message.isEmpty() ? fallback : redact(message);
    }

    private static String redact(String message) {
        return message.replaceAll("(?i)bearer\\s+\\S+", "Bearer [已隐藏]")
                .replaceAll("(?i)(x-api-key|api[- ]?key)\\s*[:=]?\\s*\\S+", "$1 [已隐藏]")
                .replaceAll("(?i)\\bsk-[A-Za-z0-9_-]{8,}\\b", "[已隐藏]");
    }
}
