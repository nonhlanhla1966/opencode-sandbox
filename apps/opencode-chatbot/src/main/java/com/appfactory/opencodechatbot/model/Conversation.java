package com.appfactory.opencodechatbot.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A conversation with a title and an ordered list of messages. Pure data class,
 * serializable via {@link com.appfactory.opencodechatbot.data.ConversationCodec}.
 */
public final class Conversation {

    private String id;
    private String title;
    private String systemPrompt;
    private long createdAt;
    private long updatedAt;
    private final List<Message> messages = new ArrayList<>();

    public Conversation(String id, String title, String systemPrompt) {
        this.id = id;
        this.title = title;
        this.systemPrompt = systemPrompt;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Conversation create(String title, String systemPrompt) {
        return new Conversation(newId(), title, systemPrompt);
    }

    private static String newId() {
        return java.util.UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSystemPrompt() {
        return systemPrompt == null ? "" : systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Message> getMessages() {
        return messages;
    }

    /**
     * Derive a human title for an untitled conversation from its first user
     * message: first line, trimmed, capped at 40 characters.
     */
    public void deriveTitle() {
        if (title != null && !title.trim().isEmpty()) {
            return;
        }
        for (Message m : messages) {
            if (m.isUser()) {
                String text = firstContentLine(m.getContent());
                if (!text.isEmpty()) {
                    setTitle(text);
                    return;
                }
            }
        }
        setTitle("New conversation");
    }

    public static String firstContentLine(String content) {
        if (content == null) {
            return "";
        }
        String firstLine = content.trim().split("\n", 2)[0].trim();
        if (firstLine.length() > 40) {
            firstLine = firstLine.substring(0, 40) + "…";
        }
        return firstLine;
    }

    /**
     * The default suffix for the most recent assistant message; used for tests.
     */
    public Message lastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public Message lastUserMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m.isUser()) {
                return m;
            }
        }
        return null;
    }

    public String preview() {
        Message last = lastMessage();
        if (last == null) {
            return "";
        }
        if (last.isUser()) {
            return "You: " + firstContentLine(last.getContent());
        }
        return firstContentLine(last.getContent());
    }
}