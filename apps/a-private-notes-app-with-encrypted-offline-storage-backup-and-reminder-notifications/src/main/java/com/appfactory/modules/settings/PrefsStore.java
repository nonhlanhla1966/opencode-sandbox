package com.appfactory.modules.settings;

import android.content.SharedPreferences;

/** Android glue: Settings.Provider backed by SharedPreferences (API 21+). */
public final class PrefsStore implements Settings.Provider {
    private final SharedPreferences prefs;

    public PrefsStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override public String get(String key, String def) {
        return prefs.getString(key, def);
    }
    @Override public void set(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }
    @Override public void remove(String key) {
        prefs.edit().remove(key).apply();
    }
}