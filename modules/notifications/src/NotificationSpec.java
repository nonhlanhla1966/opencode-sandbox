package com.appfactory.modules.notify;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure notification spec model + channel policy. */
public final class NotificationSpec {

    public enum Channel { DEFAULT, IMPORTANT, SILENT }

    public final int id;
    public final String title;
    public final String text;
    public final Channel channel;

    private NotificationSpec(int id, String title, String text, Channel channel) {
        this.id = id; this.title = title; this.text = text; this.channel = channel;
    }

    public static final class Builder {
        private int id = 1;
        private String title = "";
        private String text = "";
        private Channel channel = Channel.DEFAULT;
        public Builder id(int v) { this.id = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder text(String v) { this.text = v; return this; }
        public Builder channel(Channel v) { this.channel = v; return this; }
        public NotificationSpec build() {
            if (text.isEmpty()) throw new IllegalArgumentException("notification text required");
            return new NotificationSpec(id, title, text, channel);
        }
    }

    public boolean isActionable() {
        return !text.isEmpty();
    }

    public static Map<Integer, NotificationSpec> group(String title, String text, int count) {
        Map<Integer, NotificationSpec> out = new LinkedHashMap<>();
        for (int i = 1; i <= count; i++) {
            out.put(i, new NotificationSpec(i, title, text + " " + i, Channel.DEFAULT));
        }
        return out;
    }
}