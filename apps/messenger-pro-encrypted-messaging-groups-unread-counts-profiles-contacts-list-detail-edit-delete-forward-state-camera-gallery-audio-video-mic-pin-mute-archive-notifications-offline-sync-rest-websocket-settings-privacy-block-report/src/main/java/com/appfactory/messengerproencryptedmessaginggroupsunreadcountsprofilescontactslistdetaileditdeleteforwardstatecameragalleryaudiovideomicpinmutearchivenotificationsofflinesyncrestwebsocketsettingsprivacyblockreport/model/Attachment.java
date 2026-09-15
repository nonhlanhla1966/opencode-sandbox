package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model;

import com.appfactory.modules.json.JsonObject;

/** Attachment metadata for image/video/audio/document/voice-note messages. */
public final class Attachment {

    public enum State { LOCAL, UPLOADED, ERROR }

    public final String id;
    public final Message.Kind kind;
    public final String uri;
    public final String mimeType;
    public final long sizeBytes;
    public final int width;
    public final int height;
    public final long durationMs;
    public final State state;

    private Attachment(String id, Message.Kind kind, String uri, String mimeType,
                       long sizeBytes, int width, int height, long durationMs, State state) {
        this.id = id;
        this.kind = kind;
        this.uri = uri == null ? "" : uri;
        this.mimeType = mimeType == null ? "" : mimeType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.durationMs = durationMs;
        this.state = state;
    }

    public static Attachment create(String id, Message.Kind kind, String uri, String mimeType,
                                    long sizeBytes, int width, int height, long durationMs) {
        return new Attachment(id, kind, uri, mimeType, sizeBytes, width, height, durationMs, State.LOCAL);
    }

    public Attachment uploaded(String uri) {
        return new Attachment(id, kind, uri, mimeType, sizeBytes, width, height, durationMs, State.UPLOADED);
    }

    public Attachment failed() {
        return new Attachment(id, kind, uri, mimeType, sizeBytes, width, height, durationMs, State.ERROR);
    }

    public boolean isMedia() {
        return kind == Message.Kind.IMAGE || kind == Message.Kind.VIDEO || kind == Message.Kind.AUDIO || kind == Message.Kind.VOICE_NOTE;
    }

    public boolean hasSize() {
        return sizeBytes > 0;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("id", id);
        o.put("kind", kind.name());
        o.put("uri", uri);
        o.put("mimeType", mimeType);
        o.put("sizeBytes", sizeBytes);
        o.put("width", width);
        o.put("height", height);
        o.put("durationMs", durationMs);
        o.put("state", state.name());
        return o;
    }

    public static Attachment fromJson(JsonObject o) {
        return new Attachment(
                o.getString("id", ""),
                Message.Kind.valueOf(o.getString("kind", "TEXT")),
                o.getString("uri", ""),
                o.getString("mimeType", ""),
                o.getLong("sizeBytes", 0L),
                o.getInt("width", 0),
                o.getInt("height", 0),
                o.getLong("durationMs", 0L),
                State.valueOf(o.getString("state", "LOCAL")));
    }
}