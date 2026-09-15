package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model;

import com.appfactory.modules.json.JsonArray;
import com.appfactory.modules.json.JsonObject;

/** Immutable message model with safe mutation helpers (pure JVM). */
public final class Message {

    public enum Kind { TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, VOICE_NOTE, SYSTEM }

    public enum Status { PENDING, SENT, DELIVERED, READ, FAILED }

    public static final long MAX_BODY_CHARS = 4096;
    public static final long MAX_FORWARD_DEPTH = 20;

    public final String id;
    public final String conversationId;
    public final String senderId;
    public final String body;
    public final Kind kind;
    public final Status status;
    public final long createdAt;
    public final long updatedAt;
    public final long editedAt;      // -1 when never edited
    public final String replyToId;   // "" when not a reply
    public final long deletedAt;     // -1 when alive
    public final Attachment attachment; // nullable

    public Message(String id, String conversationId, String senderId, String body,
                   Kind kind, Status status, long createdAt, long updatedAt,
                   long editedAt, String replyToId, long deletedAt, Attachment attachment) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.body = body == null ? "" : body;
        this.kind = kind;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.editedAt = editedAt;
        this.replyToId = replyToId == null ? "" : replyToId;
        this.deletedAt = deletedAt;
        this.attachment = attachment;
    }

    public static Message text(String id, String conversationId, String senderId,
                               String body, long createdAt) {
        return new Message(id, conversationId, senderId, body, Kind.TEXT, Status.PENDING,
                createdAt, createdAt, -1, "", -1, null);
    }

    public static Message attach(String id, String conversationId, String senderId,
                                 Kind kind, String body, Attachment attachment, long createdAt) {
        return new Message(id, conversationId, senderId, body, kind, Status.PENDING,
                createdAt, createdAt, -1, "", -1, attachment);
    }

    public static Message system(String id, String conversationId, String body, long createdAt) {
        return new Message(id, conversationId, "system", body, Kind.SYSTEM, Status.READ,
                createdAt, createdAt, -1, "", -1, null);
    }

    public Message withStatus(Status next) {
        return new Message(id, conversationId, senderId, body, kind, next,
                createdAt, System.currentTimeMillis(), editedAt, replyToId, deletedAt, attachment);
    }

    public Message withServerId(String newId, Status next) {
        return new Message(newId, conversationId, senderId, body, kind, next,
                createdAt, System.currentTimeMillis(), editedAt, replyToId, deletedAt, attachment);
    }

    public Message edit(String newBody, long now) {
        return new Message(id, conversationId, senderId, newBody, kind, status,
                createdAt, now, now, replyToId, deletedAt, attachment);
    }

    public Message markDeleted(long now) {
        return new Message(id, conversationId, senderId, body, kind, status,
                createdAt, now, editedAt, replyToId, now, attachment);
    }

    public Message asReply(String replyTo) {
        return new Message(id, conversationId, senderId, body, kind, status,
                createdAt, createdAt, editedAt, replyTo, deletedAt, attachment);
    }

    public boolean isDeleted() {
        return deletedAt >= 0;
    }

    public boolean isEdited() {
        return editedAt >= 0;
    }

    public boolean isText() {
        return kind == Kind.TEXT;
    }

    public boolean isSystem() {
        return kind == Kind.SYSTEM;
    }

    public boolean hasAttachment() {
        return attachment != null;
    }

    public boolean isFrom(String userId) {
        return senderId.equals(userId);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("id", id);
        o.put("conversationId", conversationId);
        o.put("senderId", senderId);
        o.put("body", body);
        o.put("kind", kind.name());
        o.put("status", status.name());
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        o.put("editedAt", editedAt);
        o.put("replyToId", replyToId);
        o.put("deletedAt", deletedAt);
        if (attachment != null) o.put("attachment", attachment.toJson());
        return o;
    }

    public static Message fromJson(JsonObject o) {
        Attachment a = null;
        JsonObject aobj = o.getObject("attachment");
        if (aobj != null) {
            a = Attachment.fromJson(aobj);
        }
        return new Message(
                o.getString("id", ""),
                o.getString("conversationId", ""),
                o.getString("senderId", ""),
                o.getString("body", ""),
                Kind.valueOf(o.getString("kind", "TEXT")),
                Status.valueOf(o.getString("status", "PENDING")),
                o.getLong("createdAt", 0L),
                o.getLong("updatedAt", 0L),
                o.getLong("editedAt", -1L),
                o.getString("replyToId", ""),
                o.getLong("deletedAt", -1L),
                a);
    }

    public static JsonArray toJsonArray(java.util.List<Message> msgs) {
        JsonArray arr = new JsonArray();
        for (Message m : msgs) arr.add(m.toJson());
        return arr;
    }
}