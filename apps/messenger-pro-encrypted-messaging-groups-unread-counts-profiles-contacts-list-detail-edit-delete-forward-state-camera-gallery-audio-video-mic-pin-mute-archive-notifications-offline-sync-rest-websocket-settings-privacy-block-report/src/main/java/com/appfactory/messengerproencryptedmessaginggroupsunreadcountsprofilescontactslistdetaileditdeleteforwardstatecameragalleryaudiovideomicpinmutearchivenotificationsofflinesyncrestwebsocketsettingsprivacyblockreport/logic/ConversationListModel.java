package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure, deterministic conversation list ordering + unread aggregation + filters. */
public final class ConversationListModel {

    public enum Sort { PINNED_FIRST, RECENT_FIRST }

    private ConversationListModel() { }

    public static List<Conversation> sort(List<Conversation> in, Sort sort) {
        List<Conversation> out = new ArrayList<>(in);
        Comparator<Conversation> byRecent = (a, b) -> Long.compare(b.lastMessageAt, a.lastMessageAt);
        Comparator<Conversation> cmp;
        if (sort == Sort.PINNED_FIRST) {
            cmp = (a, b) -> {
                boolean ap = a.pinned, bp = b.pinned;
                if (ap != bp) return ap ? -1 : 1;
                return byRecent.compare(a, b);
            };
        } else {
            cmp = byRecent;
        }
        out.sort(cmp);
        return out;
    }

    public static List<Conversation> excludeArchived(List<Conversation> in) {
        List<Conversation> out = new ArrayList<>();
        for (Conversation c : in) if (!c.archived) out.add(c);
        return out;
    }

    public static List<Conversation> onlyArchived(List<Conversation> in) {
        List<Conversation> out = new ArrayList<>();
        for (Conversation c : in) if (c.archived) out.add(c);
        return out;
    }

    public static int totalUnread(List<Conversation> in) {
        int total = 0;
        for (Conversation c : in) if (!c.archived) total += c.unreadCount;
        return total;
    }

    public static List<Conversation> search(List<Conversation> in, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return new ArrayList<>(in);
        List<Conversation> out = new ArrayList<>();
        for (Conversation c : in) {
            if (c.title.toLowerCase().contains(q)
                    || c.lastPreview.toLowerCase().contains(q)) out.add(c);
        }
        return out;
    }

    public static List<Conversation> page(List<Conversation> in, int offset, int limit) {
        if (offset < 0) offset = 0;
        if (limit <= 0) limit = 20;
        return in.subList(Math.min(offset, in.size()),
                Math.min(offset + limit, in.size()));
    }
}