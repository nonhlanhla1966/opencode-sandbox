package com.appfactory.modules.crypto;

/** Failure wrapping for encryption/decryption and key handling. */
public final class CryptoException extends RuntimeException {
    public CryptoException(String message) { super(message); }
    public CryptoException(String message, Throwable cause) { super(message, cause); }
}