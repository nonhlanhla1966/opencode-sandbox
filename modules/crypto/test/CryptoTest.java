package com.appfactory.modules.crypto;

import org.junit.Test;
import static org.junit.Assert.*;

public class CryptoTest {
    @Test public void roundTrip() {
        byte[] key = Crypto.sha256("test-passphrase");
        String enc = Crypto.encrypt(key, "secret-ñ-emoji-🚀");
        assertNotEquals("secret-ñ-emoji-🚀", enc);
        assertEquals("secret-ñ-emoji-🚀", Crypto.decrypt(key, enc));
    }
    @Test public void tamperDetected() {
        byte[] key = Crypto.sha256("k");
        String enc = Crypto.encrypt(key, "hello");
        char altered = enc.charAt(0) == 'A' ? 'B' : 'A';
        String tampered = altered + enc.substring(1);
        try {
            Crypto.decrypt(key, tampered);
            fail("tamper should be detected");
        } catch (CryptoException expected) { }
    }
    @Test public void deterministicKeyDerivation() {
        byte[] a = Crypto.deriveKey("pw", new byte[]{1,2,3}, 100);
        byte[] b = Crypto.deriveKey("pw", new byte[]{1,2,3}, 100);
        assertArrayEquals(a, b);
        assertEquals(32, a.length);
    }
    @Test public void diffKeysFail() {
        byte[] k1 = Crypto.sha256("one");
        byte[] k2 = Crypto.sha256("two");
        String enc = Crypto.encrypt(k2, "data");
        try {
            Crypto.decrypt(k1, enc);
            fail("wrong key should fail");
        } catch (CryptoException expected) { }
    }
}