package com.appfactory.opencodechatbot.data;

import com.appfactory.opencodechatbot.model.Conversation;
import com.appfactory.opencodechatbot.model.Message;
import com.appfactory.opencodechatbot.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serializes/deserializes conversations to/from JSON. Pure logic —
 * JVM unit-testable. The Android {@link ConversationStore} handles file I/O
 * on top of this.
 */
public final class ConversationCodec {

    public static final String VERSION = "1";
    private static final String KEY_VERSION = "v";
    private static final String KEY_MODE = "mode";
    private static final String VALUE_MODE = "opencode-chatbot";

    private ConversationCodec() {
    }

    public static final class Document {
        public final List<Conversation> conversations;

        public Document(List<Conversation> conversations) {
            this.conversations = conversations;
        }
    }

    /** Serialize all conversations into a single JSON document string. */
    public static String encodeAll(List<Conversation> conversations) {
        return Json.stringify(toJson(conversations));
    }

    static Object toJson(List<Conversation> conversations) {
        Map<String, Object> doc = new java.util.LinkedHashMap<>();
        doc.put(KEY_VERSION, VERSION);
        doc.put(KEY_MODE, VALUE_MODE);
        List<Object> list = new ArrayList<>();
        if (conversations != null) {
            for (Conversation c : conversations) {
                list.add(conversationToJson(c));
            }
        }
        doc.put("conversations", list);
        return doc;
    }

    static Map<String, Object> conversationToJson(Conversation c) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("title", c.getTitle());
        m.put("systemPrompt", c.getSystemPrompt());
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        List<Object> msgs = new ArrayList<>();
        for (Message msg : c.getMessages()) {
            msgs.add(messageToJson(msg));
        }
        m.put("messages", msgs);
        return m;
    }

    static Map<String, Object> messageToJson(Message m) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("role", m.getRole());
        map.put("content", m.getContent());
        map.put("timestamp", m.getTimestamp());
        map.put("status", m.getStatus());
        return map;
    }

    /** Parse a JSON document string into a {@link Document}. */
    public static Document decodeAll(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new Document(new ArrayList<>());
        }
        Object parsed;
        try {
            parsed = Json.parse(json);
        } catch (RuntimeException e) {
            return new Document(new ArrayList<>());
        }
        if (!(parsed instanceof Map)) {
            return new Document(new ArrayList<>());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> doc = (Map<String, Object>) parsed;
        List<Conversation> conversations = new ArrayList<>();
        Object listObj = doc.get("conversations");
        if (listObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) listObj;
            for (Object item : list) {
                if (item instanceof Map) {
                    Conversation c = conversationFromJson((Map<String, Object>) item);
                    if (c != null) {
                        conversations.add(c);
                    }
                }
            }
        }
        return new Document(conversations);
    }

    static Conversation conversationFromJson(Map<String, Object> m) {
        if (m.get("id") == null) {
            return null;
        }
        Conversation c = new Conversation(String.valueOf(m.get("id")),
                str(m.get("title"), ""),
                str(m.get("systemPrompt"), ""));
        if (m.get("createdAt") instanceof Number) {
            c.setCreatedAt(((Number) m.get("createdAt")).longValue());
        }
        if (m.get("updatedAt") instanceof Number) {
            c.setUpdatedAt(((Number) m.get("updatedAt")).longValue());
        }
        Object msgs = m.get("messages");
        if (msgs instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) msgs;
            for (Object item : list) {
                if (item instanceof Map) {
                    Message msg = messageFromJson((Map<String, Object>) item);
                    if (msg != null) {
                        c.getMessages().add(msg);
                    }
                }
            }
        }
        return c;
    }

    static Message messageFromJson(Map<String, Object> m) {
        if (m.get("role") == null || m.get("content") == null) {
            return null;
        }
        String id = str(m.get("id"), java.util.UUID.randomUUID().toString());
        long ts = m.get("timestamp") instanceof Number
                ? ((Number) m.get("timestamp")).longValue() : System.currentTimeMillis();
        String status = str(m.get("status"), Message.STATUS_DONE);
        return new Message(id, String.valueOf(m.get("role")), String.valueOf(m.get("content")), ts, status);
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }
}