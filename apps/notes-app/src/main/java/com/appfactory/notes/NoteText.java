package com.appfactory.notes;

import java.util.Locale;

/**
 * Pure, database-free logic for note text handling. Kept free of Android
 * dependencies so it can be unit-tested on the JVM.
 */
public final class NoteText {

    public static final String UNTITLED = "Untitled";

    private NoteText() {
    }

    /** Trims a raw title; a blank title becomes {@link #UNTITLED}. */
    public static String titleOf(String rawTitle) {
        String t = rawTitle == null ? "" : rawTitle.trim();
        return t.isEmpty() ? UNTITLED : t;
    }

    /** Trims a raw body, collapsing trailing pure-whitespace lines. */
    public static String bodyOf(String rawBody) {
        return rawBody == null ? "" : rawBody.trim();
    }

    /** A note is meaningless when both title and body are blank. */
    public static boolean isBlank(String rawTitle, String rawBody) {
        String t = rawTitle == null ? "" : rawTitle.trim();
        String b = rawBody == null ? "" : rawBody.trim();
        return t.isEmpty() && b.isEmpty();
    }

    /** Case-insensitive substring match over title and body (search filter). */
    public static boolean matches(String title, String body, String query) {
        if (query == null) {
            return true;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        String haystack = ((title == null ? "" : title)
                + "\n" + (body == null ? "" : body)).toLowerCase(Locale.ROOT);
        return haystack.contains(q);
    }

    /** First non-empty line of the body, truncated for list snippets. */
    public static String snippet(String body, int maxLength) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        String line = body;
        int nl = body.indexOf('\n');
        if (nl >= 0) {
            line = body.substring(0, nl);
        }
        line = line.trim();
        if (line.length() <= maxLength) {
            return line;
        }
        return line.substring(0, maxLength - 1).trim() + "\u2026";
    }
}