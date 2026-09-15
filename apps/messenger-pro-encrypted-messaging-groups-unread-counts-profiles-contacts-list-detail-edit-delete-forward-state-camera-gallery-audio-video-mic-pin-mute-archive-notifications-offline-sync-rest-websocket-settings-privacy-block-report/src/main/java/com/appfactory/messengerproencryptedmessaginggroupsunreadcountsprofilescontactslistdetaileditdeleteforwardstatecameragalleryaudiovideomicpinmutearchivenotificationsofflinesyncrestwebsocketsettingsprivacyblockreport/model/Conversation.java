package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model;

import com.appfactory.modules.json.JsonObject;

/** Immutable conversation (direct or group) model. */
public final class Conversation {

    public enum Kind { DIRECT, GROUP }

    public final String id;
    public final Kind kind;
    public final String peerId;        // contact id (DIRECT) or group ref (GROUP)
    public final String title;
    public final String lastPreview;
    public final String lastMessageId;
    public final long lastMessageAt;
    public final int unreadCount;
    public final boolean pinned;
    public final boolean muted;
    public final boolean archived;
    public final boolean blocked;
    public final long createdAt;

    public Conversation(String id, Kind kind, String peerId, String title,
                        String lastPreview, String lastMessageId, long lastMessageAt,
                        int unreadCount, boolean pinned, boolean muted, boolean archived,
                        boolean blocked, long createdAt) {
        this.id = id;
        this.kind = kind;
        this.peerId = peerId == null ? "" : peerId;
        this.title = title == null ? "" : title;
        this.lastPreview = lastPreview == null ? "" : lastPreview;
        this.lastMessageId = lastMessageId == null ? "" : lastMessageId;
        this.lastMessageAt = lastMessageAt;
        this.unreadCount = unreadCount;
        this.pinned = pinned;
        this.muted = muted;
        this.archived = archived;
        this.blocked = blocked;
        this.createdAt = createdAt;
    }

    public static Conversation direct(String id, String peerId, String title, long createdAt) {
        return new Conversation(id, Kind.DIRECT, peerId, title, "", "", 0, 0, false, false, false, false, createdAt);
    }

    public static Conversation group(String id, String title, long createdAt) {
        return new Conversation(id, Kind.GROUP, id, title, "", "", 0, 0, false, false, false, false, createdAt);
    }

    public Conversation withLastMessage(String messageId, String preview, long at) {
        return new Conversation(id, kind, peerId, title, preview, messageId, at,
                unreadCount, pinned, muted, archived, blocked, createdAt);
    }

    public Conversation withUnread(int count) {
        return new Conversation(id, kind, peerId, title, lastPreview, lastMessageId,
                lastMessageAt, count, pinned, muted, archived, blocked, createdAt);
    }

    public Conversation pinned(boolean v) {
        return new Conversation(id, kind, peerId, title, lastPreview, lastMessageId,
                lastMessageAt, unreadCount, v, muted, archived, blocked, createdAt);
    }

    public Conversation muted(boolean v) {
        return new Conversation(id, kind, peerId, title, lastPreview, lastMessageId,
                lastMessageAt, unreadCount, pinned, v, archived, blocked, createdAt);
    }

    public Conversation archived(boolean v) {
        return new Conversation(id, kind, peerId, title, lastPreview, lastMessageId,
                lastMessageAt, unreadCount, pinned, muted, v, blocked, createdAt);
    }

    public Conversation blocked(boolean v) {
        return new Conversation(id, kind, peerId, title, lastPreview, lastMessageId,
                lastMessageAt, unreadCount, pinned, muted, archived, v, createdAt);
    }

    public boolean isGroup() {
        return kind == Kind.GROUP;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("id", id);
        o.put("kind", kind.name());
        o.put("peerId", peerId);
        o.put("title", title);
        o.put("lastPreview", lastPreview);
        o.put("lastMessageId", lastMessageId);
        o.put("lastMessageAt", lastMessageAt);
        o.put("unreadCount", unreadCount);
        o.put("pinned", pinned);
        o.put("muted", muted);
        o.put("archived", archived);
        o.put("blocked", blocked);
        o.put("createdAt", createdAt);
        return o;
    }

    public static Conversation fromJson(JsonObject o) {
        return new Conversation(
                o.getString("id", ""),
                Kind.valueOf(o.getString("kind", "DIRECT")),
                o.getString("peerId", ""),
                o.getString("title", ""),
                o.getString("lastPreview", ""),
                o.getString("lastMessageId", ""),
                o.getLong("lastMessageAt", 0L),
                o.getInt("unreadCount", 0),
                o.getBoolean("pinned", false),
                o.getBoolean("muted", false),
                o.getBoolean("archived", false),
                o.getBoolean("blocked", false),
                o.getLong("createdAt", 0L));
    }
}