package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model;

import com.appfactory.modules.json.JsonObject;

/** Outbox op pending sync to the backend (offline-first queue item). */
public final class OutboxEntry {

    public enum Op {
        SEND_MESSAGE, EDIT_MESSAGE, DELETE_MESSAGE, READ_RECEIPT, REACTION, FORWARD, REPORT
    }

    public final String id;
    public final Op op;
    public final String conversationId;
    public final String payload;      // json string of Message / edit body / etc.
    public final int attempts;
    public final String lastError;    // "" when clean
    public final long queuedAt;
    public final long lastAttemptAt;  // 0 when never attempted

    private OutboxEntry(String id, Op op, String conversationId, String payload,
                        int attempts, String lastError, long queuedAt, long lastAttemptAt) {
        this.id = id;
        this.op = op;
        this.conversationId = conversationId;
        this.payload = payload == null ? "" : payload;
        this.attempts = attempts;
        this.lastError = lastError == null ? "" : lastError;
        this.queuedAt = queuedAt;
        this.lastAttemptAt = lastAttemptAt;
    }

    public static OutboxEntry create(String id, Op op, String conversationId, String payload, long queuedAt) {
        return new OutboxEntry(id, op, conversationId, payload, 0, "", queuedAt, 0);
    }

    public OutboxEntry attempted(long at, String error) {
        return new OutboxEntry(id, op, conversationId, payload, attempts + 1, error, queuedAt, at);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("id", id);
        o.put("op", op.name());
        o.put("conversationId", conversationId);
        o.put("payload", payload);
        o.put("attempts", attempts);
        o.put("lastError", lastError);
        o.put("queuedAt", queuedAt);
        o.put("lastAttemptAt", lastAttemptAt);
        return o;
    }

    public static OutboxEntry fromJson(JsonObject o) {
        return new OutboxEntry(
                o.getString("id", ""),
                Op.valueOf(o.getString("op", "SEND_MESSAGE")),
                o.getString("conversationId", ""),
                o.getString("payload", ""),
                o.getInt("attempts", 0),
                o.getString("lastError", ""),
                o.getLong("queuedAt", 0L),
                o.getLong("lastAttemptAt", 0L));
    }
}