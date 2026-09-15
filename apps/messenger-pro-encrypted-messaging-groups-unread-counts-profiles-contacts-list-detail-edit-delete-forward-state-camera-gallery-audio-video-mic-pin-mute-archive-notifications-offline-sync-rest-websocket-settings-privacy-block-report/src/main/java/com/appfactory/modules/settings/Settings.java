package com.appfactory.modules.settings;

import java.util.LinkedHashMap;
import java.util.Map;

/** Type-safe settings provider with in-memory default (JVM-testable). */
public final class Settings {

    public interface Provider {
        String get(String key, String def);
        void set(String key, String value);
        void remove(String key);
    }

    public interface Listener {
        void onChanged(String key);
    }

    private final Provider provider;
    private final Map<String, Listener> listeners = new LinkedHashMap<>();

    public Settings(Provider provider) {
        this.provider = provider;
    }

    public String getString(String key) { return provider.get(key, null); }
    public String getString(String key, String def) { return provider.get(key, def); }

    public int getInt(String key, int def) {
        String v = provider.get(key, null);
        if (v == null) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }
    public long getLong(String key, long def) {
        String v = provider.get(key, null);
        if (v == null) return def;
        try { return Long.parseLong(v); } catch (NumberFormatException e) { return def; }
    }
    public boolean getBoolean(String key, boolean def) {
        String v = provider.get(key, null);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    public void put(String key, String value) { provider.set(key, value); notify(key); }
    public void putInt(String key, int v) { provider.set(key, String.valueOf(v)); notify(key); }
    public void putLong(String key, long v) { provider.set(key, String.valueOf(v)); notify(key); }
    public void putBoolean(String key, boolean v) { provider.set(key, String.valueOf(v)); notify(key); }
    public void remove(String key) { provider.remove(key); notify(key); }
    public boolean has(String key) { return provider.get(key, null) != null; }

    public void addListener(String key, Listener l) { listeners.put(key, l); }
    private void notify(String key) {
        Listener l = listeners.get(key);
        if (l != null) l.onChanged(key);
    }

    /** In-memory provider useful for tests and preview. */
    public static final class MemoryProvider implements Provider {
        public final Map<String, String> map = new LinkedHashMap<>();
        public String get(String key, String def) { return map.containsKey(key) ? map.get(key) : def; }
        public void set(String key, String value) { map.put(key, value); }
        public void remove(String key) { map.remove(key); }
    }
}