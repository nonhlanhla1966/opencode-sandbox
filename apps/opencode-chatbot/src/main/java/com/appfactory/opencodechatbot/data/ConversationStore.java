package com.appfactory.opencodechatbot.data;

import android.content.Context;

import com.appfactory.opencodechatbot.data.ConversationCodec.Document;
import com.appfactory.opencodechatbot.model.Conversation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Persists conversations as individual JSON files under {@code filesDir}
 * (via the pure {@link ConversationCodec}). No external storage, no accounts.
 */
public final class ConversationStore {

    private final File dir;

    public ConversationStore(Context context) {
        this.dir = new File(context.getApplicationContext().getFilesDir(), "conversations");
    }

    private File fileFor(String id) {
        // ids are UUIDs; sanitize defensively regardless.
        String safe = id.replaceAll("[^A-Za-z0-9_-]", "_");
        return new File(dir, safe + ".json");
    }

    public synchronized void save(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return;
        }
        conversation.deriveTitle();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = fileFor(conversation.getId());
        String json = ConversationCodec.encodeAll(Collections.singletonList(conversation));
        try {
            writeString(file, json);
        } catch (IOException e) {
            // Best-effort local persistence.
        }
    }

    public synchronized List<Conversation> loadAll() {
        List<Conversation> out = new ArrayList<>();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return out;
        }
        for (File f : files) {
            try {
                Document doc = ConversationCodec.decodeAll(readString(f));
                out.addAll(doc.conversations);
            } catch (IOException | RuntimeException ignored) {
                // skip corrupt files rather than crashing the list
            }
        }
        out.sort(new Comparator<Conversation>() {
            @Override
            public int compare(Conversation a, Conversation b) {
                return Long.compare(b.getUpdatedAt(), a.getUpdatedAt());
            }
        });
        return out;
    }

    public synchronized Conversation find(String id) {
        if (id == null) {
            return null;
        }
        for (Conversation c : loadAll()) {
            if (c.getId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    public synchronized void delete(String id) {
        if (id == null) {
            return;
        }
        File f = fileFor(id);
        if (f.exists()) {
            f.delete();
        }
    }

    public synchronized void deleteAll() {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            f.delete();
        }
    }

    /**
     * Export every conversation to a JSON document in the given output stream
     * (used by the Storage Access Framework exporter).
     */
    public synchronized void exportAll(OutputStream out) throws IOException {
        String json = ConversationCodec.encodeAll(loadAll());
        out.write(json.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Replace the whole store from an imported JSON document. */
    public synchronized void importDocument(String json) {
        Document doc = ConversationCodec.decodeAll(json);
        if (doc.conversations.isEmpty()) {
            return;
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (Conversation c : doc.conversations) {
            save(c);
        }
    }

    public synchronized int importDocument(InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        String json = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        Document doc = ConversationCodec.decodeAll(json);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (Conversation c : doc.conversations) {
            save(c);
        }
        return doc.conversations.size();
    }

    private static void writeString(File file, String content) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } finally {
            fos.close();
        }
    }

    private static String readString(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            fis.close();
        }
    }
}