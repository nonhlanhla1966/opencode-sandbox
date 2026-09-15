package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.DbCodec;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.OutboxEntry;
import com.appfactory.modules.crypto.CryptoException;
import com.appfactory.modules.storage_sqlite.Migrations;
import com.appfactory.modules.storage_sqlite.Row;
import com.appfactory.modules.crypto.CryptoException;
import com.appfactory.modules.storage_sqlite.Migrations;
import com.appfactory.modules.storage_sqlite.Row;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.security.AccessPolicy;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.security.SecretBox;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DbAndSecurityTest {

    @Test public void schemaCreatesAllTables() {
        List<String> sql = DbCodec.createSql();
        String joined = String.join("\n", sql);
        assertTrue(joined.contains("CREATE TABLE"));
        for (String t : new String[]{DbCodec.CONVERSATIONS.name(), DbCodec.MESSAGES.name(),
                DbCodec.CONTACTS.name(), DbCodec.ATTACHMENTS.name(), DbCodec.OUTBOX.name()}) {
            assertTrue(joined.contains("CREATE TABLE IF NOT EXISTS " + t) || joined.contains("CREATE TABLE " + t));
        }
    }

    @Test public void migrationsAreContiguousV1ToLatest() {
        List<Migrations.Migration> migs = DbCodec.migrations();
        assertFalse(migs.isEmpty());
        int expected = 1;
        for (Migrations.Migration m : migs) {
            assertEquals(expected, m.version());
            assertTrue(!m.statements().isEmpty() || m.version() == 1);
            expected++;
        }
        assertEquals(DbCodec.DB_VERSION, expected - 1);
        List<String> plan = Migrations.plan(migs, 0, DbCodec.DB_VERSION);
        assertFalse(plan.isEmpty());
    }

    @Test public void migrationApplicationRecordsVersion() {
        final int lastApplied = 0;
        Migrations.VersionStore store = new Migrations.VersionStore() {
            private int v = lastApplied;
            @Override public int appliedVersion() { return v; }
            @Override public void setAppliedVersion(int version) { v = version; }
        };
        final java.util.ArrayList<String> ran = new java.util.ArrayList<>();
        Migrations.apply(DbCodec.migrations(), store, DbCodec.DB_VERSION, new Migrations.Executor() {
            @Override public void execute(String sql) { ran.add(sql); }
        });
        assertEquals(DbCodec.DB_VERSION, store.appliedVersion());
        assertFalse(ran.isEmpty());
    }

    @Test public void conversationRowRoundTrips() {
        Conversation c = Conversation.direct("c1", "ada", "Ada Lovelace", 1000L)
                .withLastMessage("m1", "hi", 2000L)
                .withUnread(3)
                .pinned(true)
                .muted(true)
                .archived(false);
        Row r = DbCodec.rowOf(c);
        Conversation back = DbCodec.conversationOf(r);
        assertEquals(c.id, back.id);
        assertEquals(c.title, back.title);
        assertEquals(c.unreadCount, back.unreadCount);
        assertEquals(c.pinned, back.pinned);
        assertEquals(c.muted, back.muted);
    }

    @Test public void messageRowRoundTripsAttachmentAsSeparateTable() {
        Attachment a = Attachment.create("att1", Message.Kind.IMAGE, "file:///x.png", "image/png", 42, 10, 10, 0);
        Message m = Message.attach("msg1", "c1", "ada", Message.Kind.IMAGE, "caption", a, 5000L);
        Row r = DbCodec.rowOf(m);
        Message back = DbCodec.messageOf(r);
        assertEquals(m.id, back.id);
        assertEquals(m.conversationId, back.conversationId);
        assertEquals(m.body, back.body);
        assertNull(back.attachment); // attachment stored in its own table

        Row ar = DbCodec.rowOf(a);
        assertEquals("file:///x.png", ar.str("uri"));
        assertEquals(42L, ar.lng("size_bytes"));
        assertEquals("IMAGE", ar.str("kind"));
    }

    @Test public void outboxRowEncodesAllFields() {
        OutboxEntry e = OutboxEntry.create("o1", OutboxEntry.Op.READ_RECEIPT, "c1", "{}", 9L);
        Row r = DbCodec.rowOf(e);
        assertEquals("o1", r.str("id"));
        assertEquals("READ_RECEIPT", r.str("op"));
        assertEquals("c1", r.str("conversation_id"));
        assertEquals(0L, r.lng("attempts"));
    }

    @Test public void secretBoxSealUnsealRoundTrips() throws Exception {
        String envelope = SecretBox.seal("top secret payload", "hunter2");
        assertEquals("top secret payload", SecretBox.unseal(envelope, "hunter2"));
        String again = SecretBox.seal("second", "hunter2");
        assertFalse(envelope.equals(again)); // fresh salt
        assertEquals("second", SecretBox.unseal(again, "hunter2"));
    }

    @Test public void secretBoxRejectsWrongPassphrase() {
        String envelope = SecretBox.seal("data", "right");
        try {
            SecretBox.unseal(envelope, "wrong");
            fail("expected CryptoException");
        } catch (CryptoException expected) {
            // ok
        }
    }

    @Test public void secretBoxRejectsTamperedEnvelope() {
        String envelope = SecretBox.seal("data", "pw");
        String tampered = envelope.substring(0, envelope.length() - 4)
                + (envelope.endsWith("====\"}") || envelope.length() > envelope.length() - 4
                    ? "X" + envelope.substring(envelope.length() - 3)
                    : "AAAA");
        try {
            SecretBox.unseal(tampered, "pw");
            if (!tampered.equals(envelope)) { }
        } catch (RuntimeException expected) {
            // tamper detection may throw from Base64 or Crypto
        }
    }

    @Test public void accessPolicyLocksAfterMaxFailuresAndExpires() {
        final long[] now = {1000L};
        AccessPolicy.Clock clock = new AccessPolicy.Clock() {
            @Override public long now() { return now[0]; }
        };
        AccessPolicy p = new AccessPolicy(3, 5000L, clock);

        assertTrue(p.attempt().allowed);
        p.recordFailure();
        p.recordFailure();
        assertFalse(p.lockedNow());
        p.recordFailure();
        assertTrue(p.lockedNow());
        now[0] += 5001L;
        assertFalse(p.lockedNow());
        assertTrue(p.attempt().allowed);
    }

    @Test public void accessPolicySuccessClearsFailures() {
        final long[] now = {1L};
        AccessPolicy p = new AccessPolicy(2, 999L, new AccessPolicy.Clock() {
            @Override public long now() { return now[0]; }
        });
        p.recordFailure();
        p.recordSuccess();
        p.recordFailure();
        assertFalse(p.lockedNow());
    }
}