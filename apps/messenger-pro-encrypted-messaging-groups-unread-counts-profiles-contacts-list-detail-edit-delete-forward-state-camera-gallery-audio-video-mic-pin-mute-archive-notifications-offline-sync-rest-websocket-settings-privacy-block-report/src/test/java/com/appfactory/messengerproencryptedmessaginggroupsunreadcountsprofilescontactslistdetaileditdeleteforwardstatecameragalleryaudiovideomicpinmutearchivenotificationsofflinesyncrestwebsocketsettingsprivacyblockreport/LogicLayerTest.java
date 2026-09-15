package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.MessageRules;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.SearchIndex;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.StatusSimulator;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.ThreadWindow;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LogicLayerTest {

    @Test public void editWindowAppliesOnlyToOwnRecentText() {
        long now = 10_000L;
        Message mine = Message.text("m1", "c", "me", "hi", now);
        assertTrue(MessageRules.canEdit(mine, "me", now));
        assertFalse(MessageRules.canEdit(mine, "me", now + MessageRules.EDIT_WINDOW_MS + 1));
        assertFalse(MessageRules.canEdit(mine, "alice", now));

        Message theirs = Message.text("m2", "c", "alice", "hi", now);
        assertFalse(MessageRules.canEdit(theirs, "me", now));

        Message failed = mine.withStatus(Message.Status.FAILED);
        assertFalse(MessageRules.canEdit(failed, "me", now));
    }

    @Test public void deletePolicyOwnVsGroup() {
        Message mine = Message.text("m1", "c", "me", "hi", 1L);
        Message theirs = Message.text("m2", "c", "alice", "hi", 1L);
        assertTrue(MessageRules.canDelete(mine, "me", false));
        assertFalse(MessageRules.canDelete(theirs, "me", false));
        assertTrue(MessageRules.canDelete(theirs, "me", true));
        assertFalse(MessageRules.canDelete(mine.markDeleted(2L), "me", true));
    }

    @Test public void canReactForwardReply() {
        Message m = Message.text("m1", "c", "alice", "cool", 1L);
        assertTrue(MessageRules.canReact(m));
        assertTrue(MessageRules.canForward(m));
        assertTrue(MessageRules.canReply(m));
        assertFalse(MessageRules.canReact(m.markDeleted(2L)));

        Message sys = Message.system("s", "c", "created group", 1L);
        assertFalse(MessageRules.canReact(sys));
        assertFalse(MessageRules.canForward(sys));
    }

    @Test public void statusMachineWalksToReadOrStopsWithoutReceipts() {
        StatusSimulator sim = new StatusSimulator(new StatusSimulator.Clock() {
            @Override public long now() { return 1L; }
        });
        Message m = Message.text("m1", "c", "me", "hi", 1L);
        assertEquals(Message.Status.PENDING, m.status);
        Message s1 = sim.tick(m, true);
        assertEquals(Message.Status.SENT, s1.status);
        Message s2 = sim.tick(s1, true);
        assertEquals(Message.Status.DELIVERED, s2.status);
        Message s3 = sim.tick(s2, true);
        assertEquals(Message.Status.READ, s3.status);
        assertEquals(Message.Status.READ, sim.tick(s3, true).status);

        Message noReceipts = sim.tick(Message.text("m2", "c", "me", "hi", 1L), false);
        assertEquals(Message.Status.SENT, noReceipts.status);
        Message d = sim.tick(noReceipts, false);
        assertEquals(Message.Status.DELIVERED, d.status);
        assertEquals(Message.Status.DELIVERED, sim.tick(d, false).status);
    }

    @Test public void failedMessagesDoNotProgress() {
        StatusSimulator sim = new StatusSimulator(new StatusSimulator.Clock() {
            @Override public long now() { return 1L; }
        });
        Message m = Message.text("m1", "c", "me", "hi", 1L).withStatus(Message.Status.FAILED);
        assertEquals(Message.Status.FAILED, sim.tick(m, true).status);
    }

    @Test public void threadWindowOrdersOutOfOrderInserts() {
        ThreadWindow w = new ThreadWindow();
        w.add(Message.text("m1", "c", "a", "first", 100L));
        w.add(Message.text("m3", "c", "a", "third", 300L));
        w.add(Message.text("m2", "c", "a", "second", 200L));
        List<Message> all = w.all();
        assertEquals(3, all.size());
        assertEquals(100L, all.get(0).createdAt);
        assertEquals(200L, all.get(1).createdAt);
        assertEquals(300L, all.get(2).createdAt);

        List<Message> latest = w.latest(2);
        assertEquals(2, latest.size());
        assertEquals("third", latest.get(1).body);

        assertTrue(w.hasMoreOlderThan("m2"));
    }

    @Test public void threadWindowReplaceAndRemove() {
        ThreadWindow w = new ThreadWindow();
        w.add(Message.text("m1", "c", "a", "original", 100L));
        w.replace(Message.text("m1", "c", "a", "edited", 100L));
        assertEquals("edited", w.all().get(0).body);
        w.remove("m1");
        assertEquals(0, w.size());
    }

    @Test public void searchRanksHeadlinesMatches() {
        SearchIndex idx = new SearchIndex();
        idx.add(Message.text("m1", "c1", "a", "meeting about budget", 1L));
        idx.add(Message.text("m2", "c2", "a", "budget review slides", 2L));
        idx.add(Message.text("m3", "c2", "a", "no match here", 3L));
        List<SearchIndex.Hit> hits = idx.search("budget", 10);
        assertEquals(2, hits.size());
        assertTrue(hits.get(0).preview.toLowerCase().contains("budget"));
        idx.remove("m1");
        assertEquals(1, idx.search("budget", 10).size());
    }

    @Test public void replyPreviewTruncatesAndHandlesDeleted() {
        long now = 1L;
        Message m = Message.text("m1", "c", "a", new String(new char[200]).replace('\0', 'x'), now);
        String preview = MessageRules.replyPreview(m);
        assertEquals(MessageRules.MAX_REPLY_PREVIEW_CHARS, preview.length());
        assertEquals("This message was deleted", MessageRules.replyPreview(Message.text("m2", "c", "a", "del", now).markDeleted(2L)));
    }
}