package com.appfactory.modules.json;

import java.util.ArrayList;
import java.util.List;

/** Ordered JSON array. */
public final class JsonArray {
    final List<Object> raw = new ArrayList<>();

    public JsonArray add(String value) { raw.add(value); return this; }
    public JsonArray add(boolean value) { raw.add(value); return this; }
    public JsonArray add(long value) { raw.add(value); return this; }
    public JsonArray add(int value) { raw.add((long) value); return this; }
    public JsonArray add(double value) { raw.add(value); return this; }
    public JsonArray add(JsonObject value) { raw.add(value); return this; }
    public JsonArray add(JsonArray value) { raw.add(value); return this; }

    public int size() { return raw.size(); }
    public boolean isEmpty() { return raw.isEmpty(); }

    public String getString(int idx) {
        Object v = raw.get(idx);
        return v == null ? null : String.valueOf(v);
    }
public long getLong(int idx, long def) {
        Object v = raw.get(idx);
        if (v instanceof Number) return ((Number) v).longValue();
        return def;
    }
    public int getInt(int idx, int def) {
        Object v = raw.get(idx);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }
    public double getDouble(int idx, double def) {
        Object v = raw.get(idx);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }
    public boolean getBoolean(int idx, boolean def) {
        Object v = raw.get(idx);
        return v instanceof Boolean ? (Boolean) v : def;
    }
    public JsonObject getObject(int idx) {
        Object v = raw.get(idx);
        return v instanceof JsonObject ? (JsonObject) v : null;
    }
    public JsonArray getArray(int idx) {
        Object v = raw.get(idx);
        return v instanceof JsonArray ? (JsonArray) v : null;
    }

    String build() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Object v : raw) {
            if (!first) sb.append(',');
            first = false;
            sb.append(Json.render(v));
        }
        return sb.append(']').toString();
    }

    @Override public String toString() { return build(); }
}