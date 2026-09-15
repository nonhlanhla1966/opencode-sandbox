package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.Arrays;
import java.util.List;

/** Pure decision rules for edit/delete/react/forward + reply previews (testable). */
public final class MessageRules {

    public static final long EDIT_WINDOW_MS = 30L * 60L * 1000L; // 30 minutes
    public static final int MAX_REPLY_PREVIEW_CHARS = 80;
    public static final int MAX_BODY_CHARS = 4096;

    private static final List<String> QUICK_REACTIONS =
            Arrays.asList("\uD83D\uDC4D", "\uD83D\uDC4E", "\u2764\uFE0F", "\uD83D\uDE04", "\uD83D\uDE22", "\uD83D\uDEA9");

    private MessageRules() { }

    public static boolean canEdit(Message m, String myId, long now) {
        return m != null
                && !m.isDeleted()
                && m.isText()
                && m.isFrom(myId)
                && m.status != Message.Status.FAILED
                && now - m.createdAt <= EDIT_WINDOW_MS;
    }

    /** Delete policy: own messages always; others' messages only in groups. */
    public static boolean canDelete(Message m, String myId, boolean inGroup) {
        return m != null
                && !m.isDeleted()
                && (m.isFrom(myId) || inGroup)
                && !m.isSystem();
    }

    public static boolean canReact(Message m) {
        return m != null && !m.isDeleted() && !m.isSystem();
    }

    public static boolean canForward(Message m) {
        return m != null && !m.isDeleted() && !m.isSystem() && m.kind != Message.Kind.SYSTEM;
    }

    public static boolean canReply(Message m) {
        return m != null && !m.isDeleted() && !m.isSystem();
    }

    public static boolean quickReactionAllowed(String emoji) {
        return emoji != null && emoji.length() <= 8;
    }

    public static List<String> quickReactions() {
        return QUICK_REACTIONS;
    }

    public static String replyPreview(Message m) {
        if (m == null) return "";
        String base = m.isDeleted() ? "This message was deleted" : m.body;
        if (m.hasAttachment() && base.isEmpty()) base = "Attachment";
        return truncate(base, MAX_REPLY_PREVIEW_CHARS);
    }

    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String validateBody(String body) {
        if (body == null || body.trim().isEmpty()) return "Message cannot be empty";
        if (body.length() > MAX_BODY_CHARS) return "Message too long";
        return null;
    }
}