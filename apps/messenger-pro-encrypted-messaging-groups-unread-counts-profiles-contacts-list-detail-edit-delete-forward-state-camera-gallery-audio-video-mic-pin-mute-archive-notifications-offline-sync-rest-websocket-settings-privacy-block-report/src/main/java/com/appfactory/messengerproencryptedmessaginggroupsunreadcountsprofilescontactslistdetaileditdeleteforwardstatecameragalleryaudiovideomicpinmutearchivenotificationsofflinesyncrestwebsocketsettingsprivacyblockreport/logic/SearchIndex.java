package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Pure in-memory message search index with naive ranking. */
public final class SearchIndex {

    public static final class Hit {
        public final String conversationId;
        public final String messageId;
        public final String preview;
        public final int score;
        public final long createdAt;

        Hit(String conversationId, String messageId, String preview, int score, long createdAt) {
            this.conversationId = conversationId;
            this.messageId = messageId;
            this.preview = preview;
            this.score = score;
            this.createdAt = createdAt;
        }
    }

    private static final class Record {
        String messageId;
        String conversationId;
        String text;
        long createdAt;
    }

    private final List<Record> records = new ArrayList<>();

    public void add(Message m) {
        if (m == null || m.isSystem()) return;
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).messageId.equals(m.id)) {
                records.get(i).text = m.body;
                records.get(i).conversationId = m.conversationId;
                records.get(i).createdAt = m.createdAt;
                return;
            }
        }
        Record r = new Record();
        r.messageId = m.id;
        r.conversationId = m.conversationId;
        r.text = m.body;
        r.createdAt = m.createdAt;
        records.add(r);
    }

    public void remove(String messageId) {
        for (int i = 0; i < records.size(); i++) {
            if (records.get(i).messageId.equals(messageId)) {
                records.remove(i);
                return;
            }
        }
    }

    public List<Hit> search(String query, int maxResults) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Hit> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        List<String> terms = terms(q);
        for (Record r : records) {
            String body = r.text == null ? "" : r.text.toLowerCase();
            if (body.isEmpty()) continue;
            int score = 0;
            boolean matched = false;
            for (String t : terms) {
                if (body.equals(t)) score += 3;
                else if (body.startsWith(t + " ") || body.contains(" " + t + " "))
                    score += 2;
                else if (body.contains(t)) score += 1;
                if (body.contains(t)) matched = true;
            }
            if (!matched) continue;
            out.add(new Hit(r.conversationId, r.messageId, snippet(r.text, q, 70), score, r.createdAt));
        }
        out.sort(Comparator.comparingInt((Hit h) -> h.score)
                .reversed().thenComparing((a, b) -> Long.compare(b.createdAt, a.createdAt)));
        if (out.size() > maxResults) out = out.subList(0, maxResults);
        return out;
    }

    static List<String> terms(String q) {
        List<String> out = new ArrayList<>();
        for (String s : q.split("\\s+")) {
            s = s.replaceAll("[^a-z0-9]+", "");
            if (s.length() >= 2) out.add(s);
        }
        return out;
    }

    private static String snippet(String text, String query, int max) {
        String low = text.toLowerCase();
        int idx = low.indexOf(query);
        if (idx < 0) {
            for (String t : terms(query)) {
                int i = low.indexOf(t);
                if (i >= 0) { idx = i; break; }
            }
        }
        if (idx < 0) return MessageRules.truncate(text, max);
        int start = Math.max(0, idx - max / 3);
        int end = Math.min(text.length(), idx + query.length() + max / 2);
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }
}