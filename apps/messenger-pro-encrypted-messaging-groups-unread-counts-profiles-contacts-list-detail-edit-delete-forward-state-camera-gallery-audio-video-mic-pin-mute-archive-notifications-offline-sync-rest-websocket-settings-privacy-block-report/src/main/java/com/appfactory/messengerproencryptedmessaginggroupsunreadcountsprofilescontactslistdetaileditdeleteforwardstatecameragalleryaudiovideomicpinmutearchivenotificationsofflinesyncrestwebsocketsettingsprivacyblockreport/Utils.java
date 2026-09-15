package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small display helpers (time formatting, initials, labels). */
public final class Utils {

    private Utils() { }

    public static String timeOfDay(long epochMs) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return f.format(new Date(epochMs));
    }

    public static String dateOf(long epochMs) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return f.format(new Date(epochMs));
    }

    public static String initials(String displayName) {
        if (displayName == null || displayName.isEmpty()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(2, parts.length); i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    public static String statusLabel(String status) {
        switch (status) {
            case "SENT": return "✓";
            case "DELIVERED": return "✓✓";
            case "READ": return "✓✓";
            case "PENDING": return "⏳";
            case "FAILED": return "!";
            default: return "";
        }
    }
}