package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.settings;

import com.appfactory.modules.settings.Settings;

/**
 * Typed application settings over the vendored Settings module (pure JVM
 * defaults; device glue swaps in a PrefsStore provider at runtime).
 */
public final class MessengerSettings {

    public enum ThemeMode { SYSTEM, LIGHT, DARK }

    public enum MediaDownload { WIFI_ONLY, ALWAYS }

    // keys
    public static final String KEY_THEME = "ui.theme_mode";
    public static final String KEY_NOTIFICATIONS = "notif.enabled";
    public static final String KEY_READ_RECEIPTS = "privacy.read_receipts";
    public static final String KEY_TYPING = "privacy.typing_indicator";
    public static final String KEY_LAST_SEEN = "privacy.last_seen_public";
    public static final String KEY_MEDIA_DOWNLOAD = "media.auto_download";
    public static final String KEY_API_BASE = "backend.rest_base_url";
    public static final String KEY_API_TOKEN = "backend.auth_token";
    public static final String KEY_BACKUP_PIN = "backup.pin_configured";
    public static final String KEY_BACKUP_ENVELOPE = "backup.envelope_json";
    public static final String KEY_CACHE_MB = "media.cache_mb";

    private final Settings settings;

    public MessengerSettings(Settings settings) {
        this.settings = settings;
    }

    public static MessengerSettings withProvider(Settings.Provider provider) {
        return new MessengerSettings(new Settings(provider));
    }

    public Settings raw() {
        return settings;
    }

    public ThemeMode themeMode() {
        String v = settings.getString(KEY_THEME, ThemeMode.SYSTEM.name());
        try {
            return ThemeMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            return ThemeMode.SYSTEM;
        }
    }

    public MessengerSettings setThemeMode(ThemeMode m) {
        settings.put(KEY_THEME, m.name());
        return this;
    }

    public boolean notificationsEnabled() {
        return settings.getBoolean(KEY_NOTIFICATIONS, true);
    }

    public MessengerSettings setNotificationsEnabled(boolean v) {
        settings.putBoolean(KEY_NOTIFICATIONS, v);
        return this;
    }

    public boolean readReceiptsEnabled() {
        return settings.getBoolean(KEY_READ_RECEIPTS, true);
    }

    public MessengerSettings setReadReceiptsEnabled(boolean v) {
        settings.putBoolean(KEY_READ_RECEIPTS, v);
        return this;
    }

    public boolean typingIndicator() {
        return settings.getBoolean(KEY_TYPING, true);
    }

    public MessengerSettings setTypingIndicator(boolean v) {
        settings.putBoolean(KEY_TYPING, v);
        return this;
    }

    public boolean lastSeenPublic() {
        return settings.getBoolean(KEY_LAST_SEEN, true);
    }

    public MessengerSettings setLastSeenPublic(boolean v) {
        settings.putBoolean(KEY_LAST_SEEN, v);
        return this;
    }

    public MediaDownload mediaDownload() {
        String v = settings.getString(KEY_MEDIA_DOWNLOAD, MediaDownload.WIFI_ONLY.name());
        try {
            return MediaDownload.valueOf(v);
        } catch (IllegalArgumentException e) {
            return MediaDownload.WIFI_ONLY;
        }
    }

    public MessengerSettings setMediaDownload(MediaDownload m) {
        settings.put(KEY_MEDIA_DOWNLOAD, m.name());
        return this;
    }

    public String apiBaseUrl() {
        return settings.getString(KEY_API_BASE, "");
    }

    public MessengerSettings setApiBaseUrl(String url) {
        settings.put(KEY_API_BASE, url == null ? "" : url.trim());
        return this;
    }

    /** Returns null when the configured REST base is usable; else error. */
    public String apiValidationError() {
        String base = apiBaseUrl();
        if (base.isEmpty()) return null; // mock backend
        return com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.RestContract.validateBase(base);
    }

    public boolean authTokenConfigured() {
        String t = settings.getString(KEY_API_TOKEN, "");
        return !t.isEmpty();
    }

    public String authToken() {
        return settings.getString(KEY_API_TOKEN, "");
    }

    public MessengerSettings setAuthToken(String token) {
        settings.put(KEY_API_TOKEN, token == null ? "" : token);
        return this;
    }

    public boolean backupPinConfigured() {
        return settings.getBoolean(KEY_BACKUP_PIN, false);
    }

    public MessengerSettings setBackupPinConfigured(boolean v) {
        settings.putBoolean(KEY_BACKUP_PIN, v);
        return this;
    }

    public String backupEnvelope() {
        return settings.getString(KEY_BACKUP_ENVELOPE, "");
    }

    public MessengerSettings setBackupEnvelope(String envelope) {
        settings.put(KEY_BACKUP_ENVELOPE, envelope == null ? "" : envelope);
        return this;
    }

    public int cacheCapacityMb() {
        int v = settings.getInt(KEY_CACHE_MB, 64);
        return Math.max(8, Math.min(512, v));
    }
}