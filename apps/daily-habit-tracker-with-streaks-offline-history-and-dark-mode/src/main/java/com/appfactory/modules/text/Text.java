package com.appfactory.modules.text;

import java.util.Locale;

/** Dependency-free text helpers shared across apps. */
public final class Text {

    private Text() { }

    /** "A Stop watch! With Lap Times" -> "a-stop-watch-with-lap-times" */
    public static String slugify(String s) {
        String out = s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return out.isEmpty() ? "app" : out;
    }

    /** "hello world" -> "Hello World" */
    public static String titleCase(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (Character.isWhitespace(c) || c == '-') {
                sb.append(c);
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
    }

    public static String formatDuration(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return String.format(Locale.ROOT, "%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format(Locale.ROOT, "%dm %02ds", m, s);
        return s + "s";
    }
}