package com.appfactory.modules.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered JSON object. */
public final class JsonObject {
    final Map<String, Object> raw = new LinkedHashMap<>();

    public JsonObject put(String key, String value) { raw.put(key, value); return this; }
    public JsonObject put(String key, boolean value) { raw.put(key, value); return this; }
    public JsonObject put(String key, int value) { raw.put(key, (long) value); return this; }
    public JsonObject put(String key, long value) { raw.put(key, value); return this; }
    public JsonObject put(String key, double value) { raw.put(key, value); return this; }
    public JsonObject put(String key, JsonObject value) { raw.put(key, value); return this; }
    public JsonObject put(String key, JsonArray value) { raw.put(key, value); return this; }
    public JsonObject putNull(String key) { raw.put(key, null); return this; }

    public boolean has(String key) { return raw.containsKey(key); }
    public int size() { return raw.size(); }
    public Iterable<Map.Entry<String, Object>> entries() { return raw.entrySet(); }
    public Iterable<String> keys() { return raw.keySet(); }

    public String getString(String key) {
        Object v = raw.get(key);
        return v == null ? null : String.valueOf(v);
    }
    public String getString(String key, String def) {
        String s = getString(key);
        return s == null ? def : s;
    }
    public long getLong(String key, long def) {
        Object v = raw.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return def;
    }
    public int getInt(String key, int def) {
        Object v = raw.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }
    public double getDouble(String key, double def) {
        Object v = raw.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return def;
    }
    public boolean getBoolean(String key, boolean def) {
        Object v = raw.get(key);
        return v instanceof Boolean ? (Boolean) v : def;
    }
    public JsonObject getObject(String key) {
        Object v = raw.get(key);
        return v instanceof JsonObject ? (JsonObject) v : null;
    }
    public JsonArray getArray(String key) {
        Object v = raw.get(key);
        return v instanceof JsonArray ? (JsonArray) v : null;
    }

    String build() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(Json.quote(e.getKey())).append(':').append(Json.render(e.getValue()));
        }
        return sb.append('}').toString();
    }

    @Override public String toString() { return build(); }
}