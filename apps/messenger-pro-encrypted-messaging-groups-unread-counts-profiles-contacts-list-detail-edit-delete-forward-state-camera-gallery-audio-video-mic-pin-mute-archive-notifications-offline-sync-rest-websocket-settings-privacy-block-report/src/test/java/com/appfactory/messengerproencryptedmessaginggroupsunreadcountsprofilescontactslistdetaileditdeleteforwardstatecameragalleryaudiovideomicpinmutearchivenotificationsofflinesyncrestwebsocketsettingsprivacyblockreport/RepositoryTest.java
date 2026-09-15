package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerRepository;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.SearchIndex;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.ThreadWindow;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.OutboxEntry;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Reaction;
import com.appfactory.modules.json.JsonObject;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RepositoryTest {

    @Test public void directConversationLandsInInbox() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation c = repo.addDirectConversation("c1", "Ada");
        assertFalse(c.isGroup());
        assertTrue(repo.conversation(c.id) != null);
        assertTrue(repo.inboxSorted().contains(c));
    }

    @Test public void unreadIncrementsAndClears() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation conv = repo.addDirectConversation("c1", "Ada");
        Message m = Message.text("m1", conv.id, "c1", "hello", 1000L);
        repo.incoming(m);
        Conversation c = repo.conversation(conv.id);
        assertEquals(1, c.unreadCount);
        repo.markRead(conv.id, "m1");
        assertEquals(0, repo.conversation(conv.id).unreadCount);
    }

    @Test public void editDeleteReactForwardLifecycle() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation c = repo.addDirectConversation("c1", "Ada");
        Message m = repo.sendText(c.id, "original");
        assertNotNull(m);
        assertEquals(Message.Status.PENDING, m.status);

        Message edited = repo.edit(m.id, "revised");
        assertTrue(edited.isEdited());
        assertEquals("revised", edited.body);

        Message gone = repo.delete(m.id);
        assertTrue(gone.isDeleted());

        Message m2 = repo.sendText(c.id, "keep me");
        Message fwd = repo.forwardTo(m2.id, c.id);
        assertNotNull(fwd);
        assertTrue(fwd.conversationId.equals(c.id));

        repo.react(m2.id, "\uD83D\uDC4D", "me");
        List<Reaction> rs = repo.reactionsFor(m2.id);
        assertEquals(1, rs.size());
        assertEquals("\uD83D\uDC4D", rs.get(0).emoji);
    }

    @Test public void outboxEnqueuesAndDrains() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation c = repo.addDirectConversation("c1", "Ada");
        repo.sendText(c.id, "queued");
        assertEquals(1, repo.outboxSize());
        assertEquals(OutboxEntry.Op.SEND_MESSAGE, repo.nextOutbox().op);
        repo.removeOutbox(repo.nextOutbox() == null ? "x" : repo.nextOutbox().id);
        assertTrue(repo.outboxEmpty());
    }

    @Test public void replyCreatesRepliedMessage() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation c = repo.addDirectConversation("c1", "Ada");
        Message m = repo.sendText(c.id, "parent");
        Message r = repo.reply(c.id, m.id, "child");
        assertEquals(m.id, r.replyToId);
    }

    @Test public void attachmentsCarryMetadata() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation c = repo.addDirectConversation("c1", "Ada");
        Attachment a = Attachment.create("att1", Message.Kind.IMAGE, "file:///tmp/x.png", "image/png", 1024, 100, 80, 0);
        Message m = repo.sendAttachment(c.id, "pic", a);
        assertTrue(m.hasAttachment());
        assertTrue(repo.snapshot().toString().contains("image/png"));
    }

    @Test public void pinMuteArchiveBlockFlags() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation c = repo.addDirectConversation("c1", "Ada");
        repo.setPinned(c.id, true);
        repo.setMuted(c.id, true);
        repo.setArchived(c.id, true);
        Conversation c2 = repo.conversation(c.id);
        assertTrue(c2.pinned && c2.muted && c2.archived);
        assertTrue(repo.unreadTotal() >= 0);
    }

    @Test public void searchIndexesMessageBodies() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation c = repo.addDirectConversation("c1", "Ada");
        repo.sendText(c.id, "lunch at noon tomorrow");
        repo.sendText(c.id, "remind me to bring pizza");
        List<SearchIndex.Hit> hits = repo.search("pizza", 10);
        assertEquals(1, hits.size());
        assertEquals("pizza", hits.get(0).preview.substring(hits.get(0).preview.toLowerCase().indexOf("pizza"),
                hits.get(0).preview.toLowerCase().indexOf("pizza") + 5));
    }

    @Test public void snapshotRoundTripsState() {
        MessengerRepository repo = new MessengerRepository("me");
        Conversation c = repo.addDirectConversation("c1", "Ada");
        repo.sendText(c.id, "persist me");
        repo.putContact(Contact.create("c1", "Ada", "+1", "about", 1L));
        JsonObject snap = repo.snapshot();

        MessengerRepository restored = MessengerRepository.restore(snap.toString(), "me");
        assertEquals(1, restored.conversations().size());
        assertEquals(1, restored.thread(c.id).size());
        assertEquals("Ada", restored.contact("c1").displayName);
        assertEquals(restored.snapshot().toString(), snap.toString());
    }

    @Test public void listenersFireOnChange() {
        MessengerRepository repo = new MessengerRepository("me");
        AtomicInteger fired = new AtomicInteger(0);
        MessengerRepository.Listener l = new MessengerRepository.Listener() {
            @Override public void onChanged() { fired.incrementAndGet(); }
        };
        repo.addListener(l);
        Conversation c = repo.addDirectConversation("c1", "Ada");
        repo.removeListener(l);
        repo.sendText(c.id, "no fire");
        assertTrue(fired.get() >= 1);
    }

    @Test public void replyTextValidates() {
        assertEquals(null, com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.MessageRules.validateBody("ok"));
        assertNotNull(com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.MessageRules.validateBody("  "));
        assertNotNull(com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.MessageRules.validateBody(new String(new char[5000]).replace('\0', 'x')));
    }
}