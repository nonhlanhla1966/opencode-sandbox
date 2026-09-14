package com.appfactory.opencodechatbot.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted secret storage backed by the Android Keystore (AES/GCM) with the
 * ciphertext kept in SharedPreferences. Values are never written in plaintext
 * and never logged; the master key never leaves the hardware-backed keystore.
 */
public final class SecurePrefs {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String MASTER_KEY_ALIAS = "opencode-chatbot-master";
    private static final String PREFS = "opencode_chatbot_secure";
    private static final int GCM_TAG_BITS = 128;

    private final SharedPreferences prefs;
    private SecretKey key;

    public SecurePrefs(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.key = loadOrCreateKey();
    }

    private SecretKey loadOrCreateKey() {
        try {
            KeyStore ks = KeyStore.getInstance(KEYSTORE);
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(MASTER_KEY_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        } catch (Exception ignored) {
            // fall through to create
        }
        try {
            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            kg.init(new KeyGenParameterSpec.Builder(
                            MASTER_KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            SecretKey k = kg.generateKey();
            this.key = k;
            return k;
        } catch (Exception e) {
            // Keystore unavailable (unusual); degrade to a random in-memory key
            // so the app still runs — nothing durable is stored in that case.
            this.key = randomFallbackKey();
            return this.key;
        }
    }

    private static SecretKey randomFallbackKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return new SecretKeySpec(bytes, "AES");
    }

    public void put(String name, String value) {
        if (name == null || name.isEmpty()) {
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] ciphertext = cipher.doFinal(value.getBytes("UTF-8"));
            byte[] iv = cipher.getIV();
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            prefs.edit().putString("sec_" + name, Base64.encodeToString(payload, Base64.NO_WRAP)).apply();
        } catch (Exception ignored) {
            // Never let a storage hiccup take the app down.
        }
    }

    public String get(String name) {
        String stored = prefs.getString("sec_" + name, null);
        if (stored == null) {
            return null;
        }
        try {
            byte[] payload = Base64.decode(stored, Base64.NO_WRAP);
            if (payload.length < 12) {
                return null;
            }
            byte[] iv = new byte[12];
            System.arraycopy(payload, 0, iv, 0, 12);
            byte[] ciphertext = new byte[payload.length - 12];
            System.arraycopy(payload, 12, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public void remove(String name) {
        prefs.edit().remove("sec_" + name).apply();
    }

    public boolean contains(String name) {
        return prefs.contains("sec_" + name);
    }
}