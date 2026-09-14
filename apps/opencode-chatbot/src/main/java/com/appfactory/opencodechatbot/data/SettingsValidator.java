package com.appfactory.opencodechatbot.data;

import java.util.Locale;

/**
 * Validation + clamping helpers for settings fields. Pure logic — testable.
 */
public final class SettingsValidator {

    public static final float TEMPERATURE_MIN = 0f;
    public static final float TEMPERATURE_MAX = 2f;
    public static final int MAX_TOKENS_MIN = 1;
    public static final int MAX_TOKENS_MAX = 32768;

    private SettingsValidator() {
    }

    public static boolean isNonBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** Endpoint must be a valid http(s) URL. */
    public static boolean isValidEndpoint(String endpoint) {
        if (!isNonBlank(endpoint)) {
            return false;
        }
        String e = endpoint.trim();
        if (!(e.startsWith("http://") || e.startsWith("https://"))) {
            return false;
        }
        int schemeEnd = e.indexOf("://") + 3;
        return schemeEnd < e.length();
    }

    /** Clamp a temperature into [0, 2]; NaN becomes the default. */
    public static float clampTemperature(float value) {
        if (Float.isNaN(value)) {
            return 0.7f;
        }
        return Math.max(TEMPERATURE_MIN, Math.min(TEMPERATURE_MAX, value));
    }

    /** Clamp max-tokens into [1, 32768]. */
    public static int clampMaxTokens(int value) {
        return Math.max(MAX_TOKENS_MIN, Math.min(MAX_TOKENS_MAX, value));
    }

    /** Parse a temperature string; returns fallback on garbage. */
    public static float parseTemperature(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Parse a max-token string; returns fallback on garbage. */
    public static int parseMaxTokens(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Simple URL-like validity for display (used in tests). */
    public static String normalizeEndpointForDisplay(String endpoint) {
        String e = endpoint == null ? "" : endpoint.trim();
        return e.endsWith("/") ? e.substring(0, e.length() - 1) : e;
    }

    public static String lowerTrim(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.US);
    }
}