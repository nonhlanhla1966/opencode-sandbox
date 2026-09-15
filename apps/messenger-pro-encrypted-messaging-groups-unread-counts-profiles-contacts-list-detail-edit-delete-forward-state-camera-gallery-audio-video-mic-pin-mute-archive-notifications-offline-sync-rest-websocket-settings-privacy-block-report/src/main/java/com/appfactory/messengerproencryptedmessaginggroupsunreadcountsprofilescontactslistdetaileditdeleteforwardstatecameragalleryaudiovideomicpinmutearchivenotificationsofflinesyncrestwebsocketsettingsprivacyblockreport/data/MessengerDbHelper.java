package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.OutboxEntry;
import com.appfactory.modules.storage_sqlite.Migrations;
import com.appfactory.modules.storage_sqlite.Row;

import java.util.ArrayList;
import java.util.List;

/** On-device SQLite store built on the pure DbCodec schema + migrations. */
public final class MessengerDbHelper extends SQLiteOpenHelper {

    public MessengerDbHelper(Context context) {
        super(context, DbCodec.DB_NAME, null, DbCodec.DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String sql : DbCodec.createSql()) db.execSQL(sql);
        apply(db, 0, DbCodec.DB_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        apply(db, oldVersion, newVersion);
    }

    private void apply(final SQLiteDatabase db, int from, int to) {
        Migrations.apply(DbCodec.migrations(), new Migrations.VersionStore() {
            @Override public int appliedVersion() {
                return db.getVersion();
            }
            @Override public void setAppliedVersion(int version) {
                db.setVersion(version);
            }
        }, to, new Migrations.Executor() {
            @Override public void execute(String sql) {
                db.execSQL(sql);
            }
        });
    }

    // ---- rows <-> models ----

    private static Row rowFrom(Cursor c) {
        Row r = new Row();
        String[] names = c.getColumnNames();
        for (String n : names) {
            int i = c.getColumnIndex(n);
            if (c.isNull(i)) continue;
            switch (c.getType(i)) {
                case Cursor.FIELD_TYPE_INTEGER: r.put(n, c.getLong(i)); break;
                case Cursor.FIELD_TYPE_FLOAT: r.put(n, c.getDouble(i)); break;
                default: r.put(n, c.getString(i)); break;
            }
        }
        return r;
    }

    public List<Conversation> conversations() {
        List<Conversation> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("conversations", null, null, null,
                null, null, "last_message_at DESC");
        try {
            while (c.moveToNext()) out.add(DbCodec.conversationOf(rowFrom(c)));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Message> messages(String conversationId, int limit) {
        List<Message> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages",
                null, "conversation_id=?", new String[]{conversationId},
                null, null, "created_at ASC", String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(DbCodec.messageOf(rowFrom(c)));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Message> searchMessages(String needle, int limit) {
        List<Message> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages", null,
                "body LIKE ? AND kind IN ('TEXT','VOICE_NOTE','IMAGE','VIDEO','AUDIO','DOCUMENT')",
                new String[]{"%" + needle + "%"}, null, null, "created_at DESC",
                String.valueOf(Math.max(1, limit)));
        try {
            while (c.moveToNext()) out.add(DbCodec.messageOf(rowFrom(c)));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Contact> contacts() {
        List<Contact> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("contacts", null, null, null,
                null, null, "display_name COLLATE NOCASE ASC");
        try {
            while (c.moveToNext()) out.add(DbCodec.contactOf(rowFrom(c)));
        } finally {
            c.close();
        }
        return out;
    }

    // ---- writes ----

    public void upsertConversation(Conversation c) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("id", c.id);
        v.put("kind", c.kind.name());
        v.put("peer_id", c.peerId);
        v.put("title", c.title);
        v.put("last_preview", c.lastPreview);
        v.put("last_message_id", c.lastMessageId);
        v.put("last_message_at", c.lastMessageAt);
        v.put("unread", c.unreadCount);
        v.put("pinned", c.pinned ? 1 : 0);
        v.put("muted", c.muted ? 1 : 0);
        v.put("archived", c.archived ? 1 : 0);
        v.put("blocked", c.blocked ? 1 : 0);
        v.put("created_at", c.createdAt);
        db.insertWithOnConflict("conversations", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void upsertMessage(Message m) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("id", m.id);
        v.put("conversation_id", m.conversationId);
        v.put("sender_id", m.senderId);
        v.put("body", m.body);
        v.put("kind", m.kind.name());
        v.put("status", m.status.name());
        v.put("created_at", m.createdAt);
        v.put("updated_at", m.updatedAt);
        v.put("edited_at", m.editedAt);
        v.put("reply_to_id", m.replyToId);
        v.put("deleted_at", m.deletedAt);
        db.insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void upsertContact(Contact c) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("id", c.id);
        v.put("display_name", c.displayName);
        v.put("phone", c.phone);
        v.put("about", c.about);
        v.put("avatar_uri", c.avatarUri);
        v.put("blocked", c.blocked ? 1 : 0);
        v.put("muted", c.muted ? 1 : 0);
        v.put("created_at", c.createdAt);
        db.insertWithOnConflict("contacts", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void upsertOutbox(OutboxEntry e) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("id", e.id);
        v.put("op", e.op.name());
        v.put("conversation_id", e.conversationId);
        v.put("payload", e.payload);
        v.put("attempts", e.attempts);
        v.put("last_error", e.lastError);
        v.put("queued_at", e.queuedAt);
        v.put("last_attempt_at", e.lastAttemptAt);
        db.insertWithOnConflict("outbox", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void deleteMessage(String id) {
        getWritableDatabase().delete("messages", "id=?", new String[]{id});
    }

    public void clearAll() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("outbox", null, null);
        db.delete("attachments", null, null);
        db.delete("messages", null, null);
        db.delete("conversations", null, null);
        db.delete("contacts", null, null);
    }

    public long count(String table) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + table, null);
        try {
            c.moveToFirst();
            return c.getLong(0);
        } finally {
            c.close();
        }
    }
}