package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerRepository;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.MessagingApi;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.MockMessagingApi;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.MockRealtimeChannel;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.RealtimeChannel;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.SyncEngine;
import com.appfactory.modules.network.Network;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SyncEngineTest {

    private static Network online() {
        Network n = new Network();
        n.update(true);
        return n;
    }

    /** Minimal backend that never throws; records ops so tests can assert mapping. */
    static final class RecordingMessagingApi implements MessagingApi {
        int sends;
        int edits;
        int deletes;
        int reads;
        int reactions;
        int forwards;
        int reports;
        String editedBody;
        String forwardTarget;

        @Override public List<Message> fetchMessages(String conversationId, String beforeMessageId, int limit) {
            return new java.util.ArrayList<>();
        }
        @Override public Message send(Message out) {
            sends++;
            return out.withStatus(Message.Status.SENT);
        }
        @Override public Message edit(String messageId, String newBody) {
            edits++;
            editedBody = newBody;
            return Message.text(messageId, "", "", newBody, 0L);
        }
        @Override public void delete(String messageId) { deletes++; }
        @Override public void markRead(String conversationId, String upToMessageId) { reads++; }
        @Override public void react(String messageId, String emoji) { reactions++; }
        @Override public void forward(String messageId, String targetConversationId) {
            forwards++;
            forwardTarget = targetConversationId;
        }
        @Override public void report(String ref, String reason) { reports++; }
        @Override public List<Message> fetchIncoming(String conversationId, long sinceCreatedAt) {
            return new java.util.ArrayList<>();
        }
        @Override public boolean isReachable() { return true; }
    }

    @Test public void sendIsFlushedToServerAndAcked() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation conv = repo.addDirectConversation("c1", "Ada");
        MockMessagingApi api = new MockMessagingApi();
        MockRealtimeChannel ch = new MockRealtimeChannel();
        ch.open();
        SyncEngine engine = new SyncEngine(repo, api, ch, online());
        Message m = repo.sendText(conv.id, "hello server");
        assertEquals(1, repo.outboxSize());

        engine.sync();

        assertTrue(repo.outboxEmpty());
        assertEquals(Message.Status.SENT, repo.thread(conv.id).all().get(0).status);
        List<Message> mirror = api.mirrorFor(conv.id);
        assertFalse(mirror.isEmpty());
        assertEquals("hello server", mirror.get(0).body);
    }

    @Test public void offlineKeepsOutboxAndSetsState() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation conv = repo.addDirectConversation("c1", "Ada");
        MockMessagingApi api = new MockMessagingApi();
        SyncEngine engine = new SyncEngine(repo, api, new MockRealtimeChannel(), new Network());
        repo.sendText(conv.id, "queued while offline");
        engine.sync();
        assertEquals(SyncEngine.State.OFFLINE, engine.state());
        assertEquals(1, repo.outboxSize());
        assertEquals(0, api.mirrorFor(conv.id).size());
    }

    @Test public void readReceiptOutboxDrains() throws Exception {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation conv = repo.addDirectConversation("c1", "Ada");
        Message peer = Message.text("p1", conv.id, "c1", "read me", 1L);
        repo.incoming(peer);
        repo.markRead(conv.id, "p1");
        assertTrue(repo.outboxSize() >= 1);

        MockMessagingApi api = new MockMessagingApi();
        SyncEngine engine = new SyncEngine(repo, api, new MockRealtimeChannel(), online());
        engine.sync();
        assertTrue(repo.outboxEmpty());
    }

    @Test public void channelNewMessageAppliesToRepo() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation conv = repo.addDirectConversation("c1", "Ada");
        MockRealtimeChannel ch = new MockRealtimeChannel();
        ch.open();
        SyncEngine engine = new SyncEngine(repo, new MockMessagingApi(), ch, online());

        Message incoming = Message.text("r1", conv.id, "c1", "via socket", 5000L);
        ch.push(new RealtimeChannel.Event("NEW_MESSAGE", conv.id, "r1", incoming.toJson().toString()));
        engine.processChannelEvents();

        assertNotNull(repo.thread(conv.id).latest(10).stream()
                .filter(m -> m.id.equals("r1")).findFirst().orElse(null));
        assertTrue(repo.conversation(conv.id).unreadCount >= 1);
    }

    @Test public void channelEditAndDeleteApply() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation conv = repo.addDirectConversation("c1", "Ada");
        Message peer = Message.text("p1", conv.id, "c1", "original from peer", 1000L);
        repo.incoming(peer);

        MockRealtimeChannel ch = new MockRealtimeChannel();
        ch.open();
        SyncEngine engine = new SyncEngine(repo, new MockMessagingApi(), ch, online());

        ch.push(new RealtimeChannel.Event("EDITED", conv.id, "p1", "{\"newBody\":\"edited by peer\"}"));
        ch.push(new RealtimeChannel.Event("DELETED", conv.id, "p1", "{}"));
        engine.processChannelEvents();

        Message after = repo.thread(conv.id).all().get(0);
        assertTrue(after.isDeleted());
    }

    @Test public void editAndForwardOpsExecute() throws Exception {
        MessagingApi recorder = new RecordingMessagingApi();
        MessengerRepository repo = new MessengerRepository("me");
        Conversation convA = repo.addDirectConversation("c1", "Ada");
        Conversation convB = repo.addDirectConversation("c2", "Grace");
        MockRealtimeChannel ch = new MockRealtimeChannel();
        ch.open();
        SyncEngine engine = new SyncEngine(repo, recorder, ch, online());

        Message m = repo.sendText(convA.id, "original");
        repo.edit(m.id, "revised");
        repo.forwardTo(m.id, convB.id);
        assertEquals(3, repo.outboxSize());

        engine.sync();
        assertTrue(repo.outboxEmpty());
        assertEquals(Message.Status.SENT, repo.thread(convA.id).all().get(0).status);

        RecordingMessagingApi rec = (RecordingMessagingApi) recorder;
        assertEquals(1, rec.sends);
        assertEquals(1, rec.edits);
        assertEquals("revised", rec.editedBody);
        assertEquals(1, rec.forwards);
        assertEquals(convB.id, rec.forwardTarget);
    }

    @Test public void transientFailureRetriesThenFails() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation conv = repo.addDirectConversation("c1", "Ada");
        MockMessagingApi api = new MockMessagingApi();
        api.setOffline(true);
        MockRealtimeChannel ch = new MockRealtimeChannel();
        ch.open();
        SyncEngine engine = new SyncEngine(repo, api, ch, online());

        repo.sendText(conv.id, "will fail twice");
        engine.sync();
        assertFalse(repo.outboxEmpty());
        assertEquals(Message.Status.PENDING, repo.thread(conv.id).all().get(0).status);

        api.setOffline(false);
        engine.sync();
        assertTrue(repo.outboxEmpty());
        assertEquals(Message.Status.SENT, repo.thread(conv.id).all().get(0).status);
    }

    @Test public void engineRunsFullPassWithListenerNotifications() {
        final AtomicInteger completed = new AtomicInteger(0);
        final AtomicInteger states = new AtomicInteger(0);
        MessengerRepository repo = new MessengerRepository("me");
        Conversation conv = repo.addDirectConversation("c1", "Ada");
        MockMessagingApi api = new MockMessagingApi();
        api.setEchoIncoming(true);
        api.seed(Message.text("s1", conv.id, "c1", "echo inbound", 1000L));
        MockRealtimeChannel ch = new MockRealtimeChannel();
        ch.open();
        SyncEngine engine = new SyncEngine(repo, api, ch, online());
        engine.addListener(new SyncEngine.StateListener() {
            @Override public void onStateChanged(SyncEngine.State s) { states.incrementAndGet(); }
            @Override public void onSyncCompleted(int p) { completed.addAndGet(p); }
        });

        repo.sendText(conv.id, "outbound");

        engine.sync();
        assertTrue(repo.outboxEmpty());
        assertTrue(completed.get() >= 1);
        assertTrue(states.get() >= 2);
        assertEquals(SyncEngine.State.IDLE, engine.state());
    }
}