package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.security;

import com.appfactory.modules.crypto.Crypto;
import com.appfactory.modules.crypto.CryptoException;
import com.appfactory.modules.json.Json;
import com.appfactory.modules.json.JsonObject;

import java.security.SecureRandom;

/**
 * SecretBox: passphrase-protected blob sealing (export backup encryption)
 * with tamper detection via AES-256-GCM. Deterministic envelope:
 * {"v":1,"iterations":n,"salt":b64,"data":b64(iv||ct||tag)}.
 * No secrets, keys or plaintext are ever logged.
 */
public final class SecretBox {

    public static final int ITERATIONS = 120_000;
    public static final int SALT_BYTES = 16;

    private SecretBox() { }

    public static String seal(String plaintext, String passphrase) {
        if (plaintext == null) plaintext = "";
        if (passphrase == null || passphrase.isEmpty()) {
            throw new IllegalArgumentException("passphrase required");
        }
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        byte[] key = Crypto.deriveKey(passphrase, salt, ITERATIONS);
        JsonObject o = new JsonObject()
                .put("v", 1)
                .put("iterations", ITERATIONS)
                .put("salt", Crypto.base64UrlEncode(salt))
                .put("data", Crypto.encrypt(key, plaintext));
        return o.toString();
    }

    /** Returns plaintext, or throws {@link CryptoException} on tamper/wrong key. */
    public static String unseal(String envelopeJson, String passphrase) throws CryptoException {
        if (envelopeJson == null || envelopeJson.isEmpty()) {
            throw new CryptoException("missing envelope");
        }
        if (passphrase == null || passphrase.isEmpty()) {
            throw new CryptoException("passphrase required");
        }
        JsonObject o = Json.parseObject(envelopeJson);
        int iterations = o.getInt("iterations", ITERATIONS);
        byte[] salt = decode(o.getString("salt", ""));
        byte[] key = Crypto.deriveKey(passphrase, salt, iterations);
        return Crypto.decrypt(key, o.getString("data", ""));
    }

    public static String rekey(String envelopeJson, String oldPass, String newPass)
            throws CryptoException {
        return seal(unseal(envelopeJson, oldPass), newPass);
    }

    private static byte[] decode(String b64) {
        return java.util.Base64.getUrlDecoder().decode(b64);
    }
}