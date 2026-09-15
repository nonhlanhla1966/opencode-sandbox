package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.OutboxEntry;
import com.appfactory.modules.storage_sqlite.Migrations;
import com.appfactory.modules.storage_sqlite.Row;
import com.appfactory.modules.storage_sqlite.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure SQLite schema + Row&lt;-&gt;model codecs + migrations v1..v3.
 * Deterministic, exercised by unit tests before the on-device helper runs.
 */
public final class DbCodec {

    public static final String DB_NAME = "messenger_pro.db";
    public static final int DB_VERSION = 3;

    private DbCodec() { }

    // ---- tables ----

    public static final Schema.Table CONVERSATIONS = new Schema.Table() {
        public String name() { return "conversations"; }
        public Schema.Column[] columns() {
            return new Schema.Column[] {
                    new Schema.Column("id", Schema.Type.TEXT).primaryKey().notNull(),
                    new Schema.Column("kind", Schema.Type.TEXT).notNull(),
                    new Schema.Column("peer_id", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("title", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("last_preview", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("last_message_id", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("last_message_at", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("unread", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("pinned", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("muted", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("archived", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("blocked", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("created_at", Schema.Type.INTEGER).defaults("0")
            };
        }
        public Schema.Index[] indexes() {
            return new Schema.Index[] {
                    new Schema.Index("idx_conversations_order", "conversations", false, "last_message_at")
            };
        }
    };

    public static final Schema.Table MESSAGES = new Schema.Table() {
        public String name() { return "messages"; }
        public Schema.Column[] columns() {
            return new Schema.Column[] {
                    new Schema.Column("id", Schema.Type.TEXT).primaryKey().notNull(),
                    new Schema.Column("conversation_id", Schema.Type.TEXT).notNull(),
                    new Schema.Column("sender_id", Schema.Type.TEXT).notNull(),
                    new Schema.Column("body", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("kind", Schema.Type.TEXT).notNull(),
                    new Schema.Column("status", Schema.Type.TEXT).notNull(),
                    new Schema.Column("created_at", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("updated_at", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("edited_at", Schema.Type.INTEGER).notNull().defaults("-1"),
                    new Schema.Column("reply_to_id", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("deleted_at", Schema.Type.INTEGER).notNull().defaults("-1")
            };
        }
        public Schema.Index[] indexes() {
            return new Schema.Index[] {
                    new Schema.Index("idx_messages_conv", "messages", false, "conversation_id", "created_at")
            };
        }
    };

    public static final Schema.Table CONTACTS = new Schema.Table() {
        public String name() { return "contacts"; }
        public Schema.Column[] columns() {
            return new Schema.Column[] {
                    new Schema.Column("id", Schema.Type.TEXT).primaryKey().notNull(),
                    new Schema.Column("display_name", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("phone", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("about", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("avatar_uri", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("blocked", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("muted", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("created_at", Schema.Type.INTEGER).defaults("0")
            };
        }
        public Schema.Index[] indexes() {
            return new Schema.Index[] {
                    new Schema.Index("idx_contacts_name", "contacts", false, "display_name")
            };
        }
    };

    public static final Schema.Table ATTACHMENTS = new Schema.Table() {
        public String name() { return "attachments"; }
        public Schema.Column[] columns() {
            return new Schema.Column[] {
                    new Schema.Column("id", Schema.Type.TEXT).primaryKey().notNull(),
                    new Schema.Column("message_id", Schema.Type.TEXT).notNull(),
                    new Schema.Column("kind", Schema.Type.TEXT).notNull(),
                    new Schema.Column("uri", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("mime_type", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("size_bytes", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("width", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("height", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("duration_ms", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("state", Schema.Type.TEXT).notNull()
            };
        }
    };

    public static final Schema.Table OUTBOX = new Schema.Table() {
        public String name() { return "outbox"; }
        public Schema.Column[] columns() {
            return new Schema.Column[] {
                    new Schema.Column("id", Schema.Type.TEXT).primaryKey().notNull(),
                    new Schema.Column("op", Schema.Type.TEXT).notNull(),
                    new Schema.Column("conversation_id", Schema.Type.TEXT).notNull(),
                    new Schema.Column("payload", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("attempts", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("last_error", Schema.Type.TEXT).defaults("''"),
                    new Schema.Column("queued_at", Schema.Type.INTEGER).defaults("0"),
                    new Schema.Column("last_attempt_at", Schema.Type.INTEGER).defaults("0")
            };
        }
        public Schema.Index[] indexes() {
            return new Schema.Index[] {
                    new Schema.Index("idx_outbox_queued", "outbox", false, "queued_at")
            };
        }
    };

    private static final Schema.Table[] TABLES = {
            CONVERSATIONS, MESSAGES, CONTACTS, ATTACHMENTS, OUTBOX
    };

    public static List<String> createSql() {
        List<String> out = new ArrayList<>();
        for (Schema.Table t : TABLES) {
            out.add(t.createSql());
            for (Schema.Index i : t.indexes()) out.add(i.sql());
        }
        return out;
    }

    public static List<Migrations.Migration> migrations() {
        List<String> v1 = createSql();
        return new Migrations.Builder()
                .add(1, v1.toArray(new String[0]))
                .add(2, "CREATE INDEX IF NOT EXISTS idx_messages_reply ON messages(reply_to_id)",
                        "CREATE INDEX IF NOT EXISTS idx_attachments_msg ON attachments(message_id)")
                .add(3, "CREATE INDEX IF NOT EXISTS idx_conversations_pinned ON conversations(pinned)",
                        "CREATE INDEX IF NOT EXISTS idx_outbox_attempts ON outbox(attempts)")
                .build();
    }

    // ---- codecs ----

    public static Row rowOf(Conversation c) {
        Row r = new Row();
        r.put("id", c.id).put("kind", c.kind.name()).put("peer_id", c.peerId)
                .put("title", c.title).put("last_preview", c.lastPreview)
                .put("last_message_id", c.lastMessageId).put("last_message_at", c.lastMessageAt)
                .put("unread", c.unreadCount).put("pinned", c.pinned)
                .put("muted", c.muted).put("archived", c.archived)
                .put("blocked", c.blocked).put("created_at", c.createdAt);
        return r;
    }

    public static Conversation conversationOf(Row r) {
        return new Conversation(
                r.str("id"), Conversation.Kind.valueOf(r.str("kind")),
                r.str("peer_id"), r.str("title"), r.str("last_preview"),
                r.str("last_message_id"), r.lng("last_message_at"),
                (int) r.lng("unread"), r.bool("pinned"), r.bool("muted"),
                r.bool("archived"), r.bool("blocked"), r.lng("created_at"));
    }

    public static Row rowOf(Message m) {
        Row r = new Row();
        r.put("id", m.id).put("conversation_id", m.conversationId)
                .put("sender_id", m.senderId).put("body", m.body)
                .put("kind", m.kind.name()).put("status", m.status.name())
                .put("created_at", m.createdAt).put("updated_at", m.updatedAt)
                .put("edited_at", m.editedAt).put("reply_to_id", m.replyToId)
                .put("deleted_at", m.deletedAt);
        return r;
    }

    public static Message messageOf(Row r) {
        return new Message(
                r.str("id"), r.str("conversation_id"), r.str("sender_id"), r.str("body"),
                Message.Kind.valueOf(r.str("kind")), Message.Status.valueOf(r.str("status")),
                r.lng("created_at"), r.lng("updated_at"), r.lng("edited_at"),
                r.str("reply_to_id"), r.lng("deleted_at"), null);
    }

    public static Row rowOf(Contact c) {
        return new Row()
                .put("id", c.id).put("display_name", c.displayName)
                .put("phone", c.phone).put("about", c.about)
                .put("avatar_uri", c.avatarUri).put("blocked", c.blocked)
                .put("muted", c.muted).put("created_at", c.createdAt);
    }

    public static Contact contactOf(Row r) {
        return new Contact(
                r.str("id"), r.str("display_name"), r.str("phone"), r.str("about"),
                r.str("avatar_uri"), r.bool("blocked"), r.bool("muted"), r.lng("created_at"));
    }

    public static Row rowOf(Attachment a) {
        return new Row()
                .put("id", a.id).put("message_id", a.id.substring(0, Math.max(0, a.id.indexOf('_'))))
                .put("kind", a.kind.name()).put("uri", a.uri).put("mime_type", a.mimeType)
                .put("size_bytes", a.sizeBytes).put("width", a.width).put("height", a.height)
                .put("duration_ms", a.durationMs).put("state", a.state.name());
    }

public static Row rowOf(OutboxEntry e) {
        return new Row()
                .put("id", e.id).put("op", e.op.name()).put("conversation_id", e.conversationId)
                .put("payload", e.payload).put("attempts", e.attempts)
                .put("last_error", e.lastError).put("queued_at", e.queuedAt)
                .put("last_attempt_at", e.lastAttemptAt);
    }
}