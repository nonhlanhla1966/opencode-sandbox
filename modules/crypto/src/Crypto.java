package com.appfactory.modules.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM with a random 96-bit IV per encryption. Wire format is
 * base64(iv || ciphertext || authTag) with IV length prefixed for safety.
 * Never logs keys or plaintext.
 */
public final class Crypto {

    public static final int IV_BYTES = 12;
    public static final int KEY_BYTES = 32;
    public static final int TAG_BITS = 128;

    private Crypto() { }

    public static byte[] sha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(password.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Derive a 32-byte key from a passphrase via iterative SHA-256. */
    public static byte[] deriveKey(String passphrase, byte[] salt, int iterations) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] key = salt;
            for (int i = 0; i < iterations; i++) {
                md.reset();
                md.update(key);
                key = md.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            }
            return Arrays.copyOf(key, KEY_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Encrypt plaintext; returns base64(iv || ct || tag). */
    public static String encrypt(byte[] key, String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[1 + IV_BYTES + ct.length];
            out[0] = (byte) IV_BYTES;
            System.arraycopy(iv, 0, out, 1, IV_BYTES);
            System.arraycopy(ct, 0, out, 1 + IV_BYTES, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new CryptoException("encrypt failed", e);
        }
    }

    public static String decrypt(byte[] key, String encoded) throws CryptoException {
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            if (in.length < 1 + IV_BYTES) throw new CryptoException("bad payload");
            int ivLen = in[0] & 0xFF;
            if (ivLen != IV_BYTES) throw new CryptoException("bad IV length");
            byte[] iv = Arrays.copyOfRange(in, 1, 1 + ivLen);
            byte[] ct = Arrays.copyOfRange(in, 1 + ivLen, in.length);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("decrypt failed", e);
        }
    }

    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}