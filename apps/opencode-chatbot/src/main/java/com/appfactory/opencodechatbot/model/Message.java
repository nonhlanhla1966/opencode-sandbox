package com.appfactory.opencodechatbot.model;

/**
 * A single chat message. Pure data class with no Android dependencies so it can
 * be serialized and tested on the JVM.
 */
public final class Message {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    public static final String STATUS_DONE = "done";
    public static final String STATUS_PARTIAL = "partial";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_SENDING = "sending";

    private final String id;
    private String role;
    private String content;
    private long timestamp;
    private String status;

    public Message(String id, String role, String content, long timestamp, String status) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.status = status;
    }

    public static Message user(String content) {
        return new Message(newId(), ROLE_USER, content, now(), STATUS_DONE);
    }

    public static Message assistant(String content, String status) {
        return new Message(newId(), ROLE_ASSISTANT, content, now(), status);
    }

    private static String newId() {
        return java.util.UUID.randomUUID().toString();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isUser() {
        return ROLE_USER.equals(role);
    }

    public boolean isAssistant() {
        return ROLE_ASSISTANT.equals(role);
    }

    public boolean isError() {
        return STATUS_ERROR.equals(status);
    }

    public boolean isGenerating() {
        return STATUS_SENDING.equals(status) || STATUS_PARTIAL.equals(status);
    }
}