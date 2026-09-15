package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model;

import com.appfactory.modules.json.JsonObject;

/** Immutable reaction attached to a message. */
public final class Reaction {

    public final String messageId;
    public final String contactId;
    public final String emoji;
    public final long createdAt;

    public Reaction(String messageId, String contactId, String emoji, long createdAt) {
        this.messageId = messageId;
        this.contactId = contactId;
        this.emoji = emoji;
        this.createdAt = createdAt;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("messageId", messageId);
        o.put("contactId", contactId);
        o.put("emoji", emoji);
        o.put("createdAt", createdAt);
        return o;
    }

    public static Reaction fromJson(JsonObject o) {
        return new Reaction(
                o.getString("messageId", ""),
                o.getString("contactId", ""),
                o.getString("emoji", ""),
                o.getLong("createdAt", 0L));
    }
}