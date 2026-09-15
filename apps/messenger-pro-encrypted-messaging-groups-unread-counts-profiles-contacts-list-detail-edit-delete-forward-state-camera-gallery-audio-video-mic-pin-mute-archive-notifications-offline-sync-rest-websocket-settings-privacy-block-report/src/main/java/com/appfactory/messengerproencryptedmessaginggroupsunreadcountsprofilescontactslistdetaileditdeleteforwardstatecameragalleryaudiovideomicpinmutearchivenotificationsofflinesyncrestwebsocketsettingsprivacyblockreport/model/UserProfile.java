package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model;

import com.appfactory.modules.json.JsonObject;

/** Local device user profile (still editable, can be backed up / exported). */
public final class UserProfile {

    public final String id;
    public final String displayName;
    public final String phone;
    public final String about;
    public final String avatarUri;

    private UserProfile(String id, String displayName, String phone, String about, String avatarUri) {
        this.id = id;
        this.displayName = displayName == null ? "" : displayName;
        this.phone = phone == null ? "" : phone;
        this.about = about == null ? "" : about;
        this.avatarUri = avatarUri == null ? "" : avatarUri;
    }

    public static UserProfile defaultProfile(String id) {
        return new UserProfile(id, "Me", "", "Hey", "");
    }

    public UserProfile named(String name) {
        return new UserProfile(id, name, phone, about, avatarUri);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("id", id);
        o.put("displayName", displayName);
        o.put("phone", phone);
        o.put("about", about);
        o.put("avatarUri", avatarUri);
        return o;
    }

    public static UserProfile fromJson(JsonObject o) {
        return new UserProfile(
                o.getString("id", "me"),
                o.getString("displayName", "Me"),
                o.getString("phone", ""),
                o.getString("about", ""),
                o.getString("avatarUri", ""));
    }
}