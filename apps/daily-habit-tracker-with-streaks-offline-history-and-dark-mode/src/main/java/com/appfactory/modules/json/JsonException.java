package com.appfactory.modules.json;

/** Thrown on malformed JSON. */
public final class JsonException extends RuntimeException {
    public JsonException(String message) {
        super(message);
    }
}