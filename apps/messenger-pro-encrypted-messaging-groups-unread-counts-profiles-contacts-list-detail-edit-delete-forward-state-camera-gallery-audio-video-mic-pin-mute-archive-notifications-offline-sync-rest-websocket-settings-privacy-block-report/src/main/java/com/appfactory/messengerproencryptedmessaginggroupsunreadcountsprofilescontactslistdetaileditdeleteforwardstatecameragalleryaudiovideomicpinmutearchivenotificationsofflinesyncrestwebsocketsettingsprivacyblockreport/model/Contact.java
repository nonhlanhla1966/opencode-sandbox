package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model;

import com.appfactory.modules.json.JsonObject;

/** Immutable contact profile. */
public final class Contact {

    public final String id;
    public final String displayName;
    public final String phone;
    public final String about;
    public final String avatarUri;
    public final boolean blocked;
    public final boolean muted;
    public final long createdAt;

    public Contact(String id, String displayName, String phone, String about,
                   String avatarUri, boolean blocked, boolean muted, long createdAt) {
        this.id = id;
        this.displayName = displayName == null ? "" : displayName;
        this.phone = phone == null ? "" : phone;
        this.about = about == null ? "" : about;
        this.avatarUri = avatarUri == null ? "" : avatarUri;
        this.blocked = blocked;
        this.muted = muted;
        this.createdAt = createdAt;
    }

    public static Contact create(String id, String displayName, String phone, String about, long createdAt) {
        return new Contact(id, displayName, about == null ? phone : about, phone, "", false, false, createdAt);
    }

    public Contact withName(String name) {
        return new Contact(id, name, phone, about, avatarUri, blocked, muted, createdAt);
    }

    public Contact withPhone(String p) {
        return new Contact(id, displayName, p, about, avatarUri, blocked, muted, createdAt);
    }

    public Contact withAbout(String a) {
        return new Contact(id, displayName, phone, a, avatarUri, blocked, muted, createdAt);
    }

    public Contact withAvatar(String uri) {
        return new Contact(id, displayName, phone, about, uri, blocked, muted, createdAt);
    }

    public Contact blocked(boolean v) {
        return new Contact(id, displayName, phone, about, avatarUri, v, muted, createdAt);
    }

    public Contact muted(boolean v) {
        return new Contact(id, displayName, phone, about, avatarUri, blocked, v, createdAt);
    }

    public boolean hasAvatar() {
        return avatarUri != null && !avatarUri.isEmpty();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("id", id);
        o.put("displayName", displayName);
        o.put("phone", phone);
        o.put("about", about);
        o.put("avatarUri", avatarUri);
        o.put("blocked", blocked);
        o.put("muted", muted);
        o.put("createdAt", createdAt);
        return o;
    }

    public static Contact fromJson(JsonObject o) {
        return new Contact(
                o.getString("id", ""),
                o.getString("displayName", ""),
                o.getString("phone", ""),
                o.getString("about", ""),
                o.getString("avatarUri", ""),
                o.getBoolean("blocked", false),
                o.getBoolean("muted", false),
                o.getLong("createdAt", 0L));
    }
}