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
 * Dedicated Android Keystore storage for the runtime DeepSeek API key.
 *
 * This store deliberately does not share the health-data alias. The legacy
 * preference is read once and migrated only after the new value has passed a
 * persistence round trip. No key material is ever returned in a status string
 * or exception message.
 */
final class SecretStore {
    private static final String STORE = "AndroidKeyStore";
    private static final String API_ALIAS = "medassistant_deepseek_api_v1";
    private static final String PREFS = "luna_secure_runtime";
    private static final String API_KEY = "deepseek_key_enc_v2";
    private static final String LEGACY_API_KEY = "deepseek_key_enc";
    private static final String SELF_TEST = "deepseek_key_self_test";
    private static final String LAST_STATUS = "last_connection_status";
    private static final String LAST_AT = "last_connection_at";
    private static final String PREFIX = "v2:";

    private final SharedPreferences preferences;
    private final DataCipher legacyCipher;

    SecretStore(Context context) {
        Context app = context.getApplicationContext();
        preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        legacyCipher = new DataCipher(app);
    }

    boolean hasKey() {
        try {
            return !readApiKey().isEmpty();
        } catch (Exception error) {
            clearStoredKeyOnly();
            return false;
        }
    }

    String status() {
        try {
            if (readApiKey().isEmpty()) return "未配置";
            String connection = preferences.getString(LAST_STATUS, "");
            if (!connection.isEmpty()) return "已安全保存 · " + connection;
            return "已安全保存但未测试";
        } catch (Exception error) {
            clearStoredKeyOnly();
            return "旧 Key 已清除，请重新输入";
        }
    }

    String lastConnectionStatus() {
        String value = preferences.getString(LAST_STATUS, "");
        long at = preferences.getLong(LAST_AT, 0L);
        return value.isEmpty() || at == 0L ? "尚未测试连接" : "最近测试：" + value;
    }

    void save(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("请输入 DeepSeek API Key");
        if (!selfTest(normalized)) throw new IllegalStateException("SECURE_STORAGE_SELF_TEST_FAILED：本机安全存储不可用");
        String encrypted;
        try {
            encrypted = encrypt(normalized);
            if (!preferences.edit().putString(API_KEY, encrypted).commit()) {
                throw new IllegalStateException("SECURE_STORAGE_PERSISTENCE_FAILED");
            }
            String roundTrip = decrypt(preferences.getString(API_KEY, ""));
            if (!constantTimeEquals(normalized, roundTrip)) {
                throw new IllegalStateException("SECURE_STORAGE_SELF_TEST_FAILED");
            }
            // Remove the old preference only after the new alias and value are verified.
            if (!preferences.edit().remove(LEGACY_API_KEY).commit()) {
                throw new IllegalStateException("SECURE_STORAGE_PERSISTENCE_FAILED");
            }
        } catch (Exception error) {
            clearStoredKeyOnly();
            throw new IllegalStateException(safeMessage(error, "SECURE_STORAGE_UNAVAILABLE"), error);
        }
    }

    void delete() {
        Exception failure = null;
        try {
            boolean committed = preferences.edit().remove(API_KEY).remove(LEGACY_API_KEY)
                    .remove(SELF_TEST).remove(LAST_STATUS).remove(LAST_AT).commit();
            if (!committed) failure = new IllegalStateException("SECURE_STORAGE_PREFERENCE_CLEANUP_FAILED");
        } catch (Exception error) {
            failure = error;
        }
        try {
            KeyStore store = KeyStore.getInstance(STORE);
            store.load(null);
            if (store.containsAlias(API_ALIAS)) store.deleteEntry(API_ALIAS);
        } catch (Exception error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        }
        if (failure != null) throw new IllegalStateException("SECURE_STORAGE_CLEANUP_FAILED", failure);
    }

    void recordConnection(String status) {
        String safe = redact(status == null ? "请求失败" : status);
        preferences.edit().putString(LAST_STATUS, safe).putLong(LAST_AT, System.currentTimeMillis()).commit();
    }

    String readApiKey() throws Exception {
        String current = preferences.getString(API_KEY, "");
        if (!current.isEmpty()) return decrypt(current);

        String legacy = preferences.getString(LEGACY_API_KEY, "");
        if (legacy.isEmpty()) return "";
        try {
            String recovered = legacyCipher.decryptStrict(legacy);
            if (recovered == null || recovered.trim().isEmpty()) throw new IllegalStateException("LEGACY_KEY_EMPTY");
            save(recovered);
            return decrypt(preferences.getString(API_KEY, ""));
        } catch (Exception error) {
            // A corrupt legacy key is isolated to its own preference. Medication,
            // health, document and database data are never touched here.
            preferences.edit().remove(LEGACY_API_KEY).commit();
            throw new IllegalStateException("LEGACY_KEY_CORRUPT", error);
        }
    }

    private boolean selfTest(String value) {
        try {
            String encrypted = encrypt(value);
            if (!preferences.edit().putString(SELF_TEST, encrypted).commit()) return false;
            String decoded = decrypt(preferences.getString(SELF_TEST, ""));
            boolean equal = constantTimeEquals(value, decoded);
            preferences.edit().remove(SELF_TEST).commit();
            return equal;
        } catch (Exception error) {
            preferences.edit().remove(SELF_TEST).commit();
            return false;
        }
    }

    private String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // The provider supplies the random IV required by the Keystore policy.
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private String decrypt(String encoded) throws Exception {
        if (encoded == null || !encoded.startsWith(PREFIX)) throw new IllegalArgumentException("SECURE_STORAGE_CORRUPT");
        String[] parts = encoded.split(":", 3);
        if (parts.length != 3) throw new IllegalArgumentException("SECURE_STORAGE_CORRUPT");
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);
        if (iv.length != 12 || ciphertext.length < 16) throw new IllegalArgumentException("SECURE_STORAGE_CORRUPT");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(STORE);
        store.load(null);
        if (!store.containsAlias(API_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
            generator.init(new KeyGenParameterSpec.Builder(API_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            try {
                generator.generateKey();
            } catch (Exception unsupported) {
                if (store.containsAlias(API_ALIAS)) store.deleteEntry(API_ALIAS);
                generator.init(new KeyGenParameterSpec.Builder(API_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setKeySize(128)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build());
                generator.generateKey();
            }
        }
        KeyStore.Entry entry = store.getEntry(API_ALIAS, null);
        if (!(entry instanceof KeyStore.SecretKeyEntry)) throw new IllegalStateException("SECURE_STORAGE_KEY_INVALID");
        return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
    }

    private void clearStoredKeyOnly() {
        preferences.edit().remove(API_KEY).remove(LEGACY_API_KEY).remove(SELF_TEST).commit();
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static String safeMessage(Exception error, String fallback) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? fallback : redact(message);
    }

    private static String redact(String message) {
        return message
                .replaceAll("(?i)bearer\\s+\\S+", "Bearer [已隐藏]")
                .replaceAll("(?i)(x-api-key|api[- ]?key)\\s*[:=]?\\s*\\S+", "$1 [已隐藏]")
                .replaceAll("(?i)\\bsk-[A-Za-z0-9_-]{8,}\\b", "[已隐藏]");
    }
}
