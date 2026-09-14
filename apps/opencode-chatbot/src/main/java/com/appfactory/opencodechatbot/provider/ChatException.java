package com.appfactory.opencodechatbot.provider;

/** Error thrown by providers; carries a safe, user-presentable message. */
public final class ChatException extends Exception {

    public ChatException(String message) {
        super(message);
    }

    public ChatException(String message, Throwable cause) {
        super(message, cause);
    }
}