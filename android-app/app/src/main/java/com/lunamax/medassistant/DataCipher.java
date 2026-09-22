package com.lunamax.medassistant;

import android.content.Context;
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

/** Android Keystore AES/GCM storage for health text, private documents and runtime API keys. */
final class DataCipher {
    private static final String STORE = "AndroidKeyStore";
    private static final String ALIAS = "luna_max_data_v1";
    private static final String PREFIX = "v1:";
    DataCipher(Context ignored) { }

    String encrypt(String value) {
        if (value == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // AndroidKeyStore generates a fresh IV for every encryption. Supplying
            // our own IV is incompatible with randomizedEncryptionRequired on a
            // number of real devices.
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        } catch (Exception error) {
            throw new IllegalStateException("SECURE_STORAGE_UNAVAILABLE", error);
        }
    }

    /** Safe database read: malformed legacy/plaintext data becomes empty rather than crashing the app. */
    String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        if (!encoded.startsWith(PREFIX)) return encoded;
        try { return decryptStrict(encoded); }
        catch (Exception ignored) { return ""; }
    }

    String decryptStrict(String encoded) throws Exception {
        if (encoded == null || encoded.isEmpty()) return "";
        if (!encoded.startsWith(PREFIX)) return encoded;
        String[] parts = encoded.split(":", 3);
        if (parts.length != 3) throw new IllegalArgumentException("SECURE_STORAGE_CORRUPT");
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);
        if (iv.length != 12 || ciphertext.length < 16) throw new IllegalArgumentException("SECURE_STORAGE_CORRUPT");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    boolean selfTest(String value) {
        try {
            String roundTrip = decryptStrict(encrypt(value));
            return MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), roundTrip.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { return false; }
    }

    void deleteKey() {
        try {
            KeyStore store = KeyStore.getInstance(STORE);
            store.load(null);
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
        } catch (Exception error) {
            throw new IllegalStateException("SECURE_STORAGE_CLEANUP_FAILED", error);
        }
    }

    private synchronized SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(STORE);
        store.load(null);
        if (!store.containsAlias(ALIAS)) generate(store, 256);
        KeyStore.Entry entry = store.getEntry(ALIAS, null);
        if (!(entry instanceof KeyStore.SecretKeyEntry)) {
            store.deleteEntry(ALIAS);
            generate(store, 256);
            entry = store.getEntry(ALIAS, null);
        }
        SecretKey secret = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        if (!"AES".equalsIgnoreCase(secret.getAlgorithm())) throw new IllegalStateException("SECURE_STORAGE_KEY_INVALID");
        return secret;
    }

    private void generate(KeyStore store, int size) throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(size)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        try {
            generator.init(spec);
            generator.generateKey();
        } catch (Exception unsupported) {
            if (size != 256) throw unsupported;
            try { if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS); } catch (Exception cleanup) { unsupported.addSuppressed(cleanup); }
            generate(store, 128);
        }
    }
}
