package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.ConversationListModel;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.MessageRules;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.SearchIndex;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.ThreadWindow;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.OutboxEntry;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Reaction;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.UserProfile;
import com.appfactory.modules.json.Json;
import com.appfactory.modules.json.JsonArray;
import com.appfactory.modules.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure in-memory aggregate: conversations, messages, contacts, reactions,
 * outbox + search index. Source of truth for the UI and unit tests. Optional
 * Store facade persists JSON snapshots to the device backend.
 */
public final class MessengerRepository {

    public interface Listener {
        void onChanged();
    }

    public interface Store {
        void save(String json);
    }

    private final String myId;
    private final OutboxQueue outbox = new OutboxQueue();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final Map<String, Message> messages = new LinkedHashMap<>();
    private final Map<String, Contact> contacts = new LinkedHashMap<>();
    private final Map<String, ThreadWindow> windows = new LinkedHashMap<>();
    private final List<Reaction> reactions = new ArrayList<>();
    private final SearchIndex search = new SearchIndex();
    private final List<Listener> listeners = new ArrayList<>();
    private Store store;

    private UserProfile profile = UserProfile.defaultProfile("me");
    private long seq = 0;

    public MessengerRepository(String myId) {
        this.myId = myId == null || myId.isEmpty() ? "me" : myId;
        this.profile = UserProfile.defaultProfile(this.myId);
    }

    public String myId() {
        return myId;
    }

    public void attach(Store s) {
        this.store = s;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onChanged();
        if (store != null) store.save(snapshot().toString());
    }

    private String nextId(String prefix) {
        return prefix + (++seq);
    }

    // ---- conversation CRUD ----

    public Conversation addDirectConversation(String peerId, String title) {
        String id = nextId("convo-");
        Conversation c = Conversation.direct(id, peerId, title, now());
        conversations.put(id, c);
        windows.put(id, new ThreadWindow());
        changed();
        return c;
    }

    public Conversation addGroupConversation(String title) {
        String id = nextId("convo-");
        Conversation c = Conversation.group(id, title, now());
        conversations.put(id, c);
        windows.put(id, new ThreadWindow());
        changed();
        return c;
    }

    public void upsert(Conversation c) {
        conversations.put(c.id, c);
        if (!windows.containsKey(c.id)) windows.put(c.id, new ThreadWindow());
        changed();
    }

    public Conversation conversation(String id) {
        return conversations.get(id);
    }

    public List<Conversation> conversations() {
        return Collections.unmodifiableList(new ArrayList<>(conversations.values()));
    }

    public List<Conversation> inboxSorted() {
        return ConversationListModel.sort(
                ConversationListModel.excludeArchived(conversationsForSort()),
                ConversationListModel.Sort.PINNED_FIRST);
    }

    public List<Conversation> archivedSorted() {
        return ConversationListModel.sort(
                ConversationListModel.onlyArchived(conversationsForSort()),
                ConversationListModel.Sort.RECENT_FIRST);
    }

    private List<Conversation> conversationsForSort() {
        return new ArrayList<>(conversations.values());
    }

    public int unreadTotal() {
        return ConversationListModel.totalUnread(conversationValues());
    }

    private List<Conversation> conversationValues() {
        return new ArrayList<>(conversations.values());
    }

    public void setPinned(String conversationId, boolean pinned) {
        Conversation c = conversations.get(conversationId);
        if (c != null) upsert(c.pinned(pinned));
    }

    public void setMuted(String conversationId, boolean muted) {
        Conversation c = conversations.get(conversationId);
        if (c != null) upsert(c.muted(muted));
    }

    public void setArchived(String conversationId, boolean archived) {
        Conversation c = conversations.get(conversationId);
        if (c != null) upsert(c.archived(archived));
    }

    public void setBlocked(String conversationId, boolean blocked) {
        Conversation c = conversations.get(conversationId);
        if (c != null) upsert(c.blocked(blocked));
    }

    // ---- contacts ----

    public void putContact(Contact c) {
        contacts.put(c.id, c);
        changed();
    }

    public Contact contact(String id) {
        return contacts.get(id);
    }

    public List<Contact> contacts() {
        List<Contact> out = new ArrayList<>(contacts.values());
        out.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return Collections.unmodifiableList(out);
    }

    public UserProfile profile() {
        return profile;
    }

    public void updateProfile(UserProfile p) {
        profile = p;
        changed();
    }

    // ---- messaging ----

    public Message sendText(String conversationId, String body) {
        String error = MessageRules.validateBody(body);
        if (error != null) return null;
        Message m = Message.text(nextId("msg-"), conversationId, myId, body, now());
        addLocal(m);
        enqueue(OutboxEntry.Op.SEND_MESSAGE, conversationId, m.toJson().toString());
        changed();
        return m;
    }

    public Message sendAttachment(String conversationId, String caption, Attachment a) {
        Message m = Message.attach(nextId("msg-"), conversationId, myId, a.kind,
                caption == null ? "" : caption, a, now());
        addLocal(m);
        enqueue(OutboxEntry.Op.SEND_MESSAGE, conversationId, m.toJson().toString());
        changed();
        return m;
    }

    public Message reply(String conversationId, String replyToId, String body) {
        String error = MessageRules.validateBody(body);
        if (error != null) return null;
        Message m = Message.text(nextId("msg-"), conversationId, myId, body, now())
                .asReply(replyToId);
        addLocal(m);
        enqueue(OutboxEntry.Op.SEND_MESSAGE, conversationId, m.toJson().toString());
        changed();
        return m;
    }

    public Message edit(String messageId, String newBody) {
        Message cur = messages.get(messageId);
        if (!MessageRules.canEdit(cur, myId, now())) return null;
        Message next = cur.edit(newBody, now());
        messages.put(next.id, next);
        search.add(next);
        windowOf(next.conversationId).replace(next);
        enqueue(OutboxEntry.Op.EDIT_MESSAGE, next.conversationId,
                new JsonObject().put("messageId", next.id).put("newBody", next.body).toString());
        changed();
        return next;
    }

    public Message delete(String messageId) {
        Message cur = messages.get(messageId);
        if (cur == null || cur.isDeleted()) return null;
        Message next = cur.markDeleted(now());
        messages.put(next.id, next);
        search.remove(next.id);
        windowOf(next.conversationId).replace(next);
        enqueue(OutboxEntry.Op.DELETE_MESSAGE, next.conversationId,
                new JsonObject().put("messageId", next.id).toString());
        changed();
        return next;
    }

    public Message forwardTo(String messageId, String targetConversationId) {
        Message cur = messages.get(messageId);
        if (!MessageRules.canForward(cur)) return null;
        Message copy = Message.text(nextId("msg-"), targetConversationId, myId,
                cur.body, now());
        if (cur.hasAttachment()) {
            copy = Message.attach(copy.id, targetConversationId, myId, cur.kind,
                    cur.body, cur.attachment, now());
        }
        addLocal(copy);
        enqueue(OutboxEntry.Op.FORWARD, targetConversationId,
                new JsonObject().put("messageId", cur.id)
                        .put("targetConversationId", targetConversationId).toString());
        changed();
        return copy;
    }

    public void react(String messageId, String emoji, String who) {
        Message cur = messages.get(messageId);
        if (!MessageRules.canReact(cur)) return;
        for (Reaction r : reactions) {
            if (r.messageId.equals(messageId) && r.contactId.equals(who)
                    && r.emoji.equals(emoji)) return;
        }
        reactions.add(new Reaction(messageId, who, emoji, now()));
        enqueue(OutboxEntry.Op.REACTION, cur.conversationId,
                new JsonObject().put("messageId", messageId).put("emoji", emoji).toString());
        changed();
    }

    public List<Reaction> reactionsFor(String messageId) {
        List<Reaction> out = new ArrayList<>();
        for (Reaction r : reactions) if (r.messageId.equals(messageId)) out.add(r);
        return out;
    }

    // ---- incoming / sync ----

    public Message incoming(Message m) {
        if (m == null || messages.containsKey(m.id)) return m;
        if (m.conversationId == null) return m;
        if (!conversations.containsKey(m.conversationId)) return m;
        messages.put(m.id, m);
        search.add(m);
        ThreadWindow w = windowOf(m.conversationId);
        w.add(m);
        Conversation c = conversations.get(m.conversationId);
        if (!m.isFrom(myId)) {
            c = c.withLastMessage(m.id, previewOf(m), m.createdAt).withUnread(c.unreadCount + 1);
        } else {
            c = c.withLastMessage(m.id, previewOf(m), m.createdAt);
        }
        conversations.put(c.id, c);
        changed();
        return m;
    }

    public void markRead(String conversationId, String upToMessageId) {
        Conversation c = conversations.get(conversationId);
        if (c == null) return;
        if (c.unreadCount > 0) {
            upsert(c.withUnread(0));
        }
        ThreadWindow w = windows.get(conversationId);
        if (w == null) return;
        int stop = w.indexOf(upToMessageId);
        if (stop < 0) stop = w.size() - 1;
        boolean changedAny = false;
        for (int i = 0; i <= stop && i < w.size(); i++) {
            Message m = w.all().get(i);
            if (!m.isFrom(myId) && m.status != Message.Status.READ && !m.isDeleted()) {
                messages.put(m.id, m.withStatus(Message.Status.READ));
                windows.get(conversationId).replace(m.withStatus(Message.Status.READ));
                changedAny = true;
            }
        }
        if (changedAny) {
            enqueue(OutboxEntry.Op.READ_RECEIPT, conversationId,
                    new JsonObject().put("conversationId", conversationId)
                            .put("upToMessageId", upToMessageId).toString());
            changed();
        }
    }

    public Message updateStatus(String messageId, Message.Status status) {
        Message cur = messages.get(messageId);
        if (cur == null) return null;
        if (cur.status == status) return cur;
        Message next = cur.withStatus(status);
        messages.put(next.id, next);
        windowOf(next.conversationId).replace(next);
        changed();
        return next;
    }

    public void ack(String messageId) {
        updateStatus(messageId, Message.Status.SENT);
    }

    /** Sync-applied remote edit (no outbox enqueue). */
    public void applyRemoteEdit(String messageId, String newBody) {
        Message cur = messages.get(messageId);
        if (cur == null || cur.isDeleted()) return;
        Message next = cur.edit(newBody, now());
        messages.put(next.id, next);
        search.add(next);
        windowOf(next.conversationId).replace(next);
        changed();
    }

    /** Sync-applied remote delete (already tombstoned locally). */
    public void applyRemoteDelete(String messageId) {
        Message cur = messages.get(messageId);
        if (cur == null || cur.isDeleted()) return;
        Message next = cur.markDeleted(now());
        messages.put(next.id, next);
        search.remove(next.id);
        windowOf(next.conversationId).replace(next);
        changed();
    }

    // ---- outbox ----

    public void enqueue(OutboxEntry.Op op, String conversationId, String payload) {
        outbox.add(OutboxEntry.create(nextId("ob-"), op, conversationId, payload, now()));
    }

    public OutboxEntry nextOutbox() {
        return outbox.next();
    }

    public OutboxEntry removeOutbox(String id) {
        return outbox.remove(id);
    }

    public boolean outboxEmpty() {
        return outbox.isEmpty();
    }

    public int outboxSize() {
        return outbox.size();
    }

    public List<OutboxEntry> outboxAll() {
        return outbox.all();
    }

    public void outboxAttempted(String id, String error) {
        OutboxEntry cur = outbox.remove(id);
        if (cur != null) outbox.add(cur.attempted(now(), error));
        changed();
    }

    public void enqueueReport(String contactId, String reason) {
        enqueue(OutboxEntry.Op.REPORT, contactId,
                new JsonObject().put("contactId", contactId)
                        .put("reason", reason == null ? "" : reason).toString());
        changed();
    }

    // ---- search ----

    public List<SearchIndex.Hit> search(String query, int maxResults) {
        return search.search(query, maxResults);
    }

    // ---- thread windows ----

    public ThreadWindow thread(String conversationId) {
        return windowOf(conversationId);
    }

    private ThreadWindow windowOf(String conversationId) {
        ThreadWindow w = windows.get(conversationId);
        if (w == null && messages.size() > 0) {
            for (Message m : messages.values()) {
                if (m.conversationId.equals(conversationId)) {
                    if (w == null) w = new ThreadWindow();
                    w.add(m);
                }
            }
            windows.put(conversationId, w == null ? new ThreadWindow() : w);
        }
        return windows.get(conversationId);
    }

    private void addLocal(Message m) {
        messages.put(m.id, m);
        search.add(m);
        ThreadWindow w = windowOf(m.conversationId);
        w.add(m);
        Conversation c = conversations.get(m.conversationId);
        if (c != null) {
            upsert(c.withLastMessage(m.id, previewOf(m), m.createdAt));
        }
    }

    private String previewOf(Message m) {
        if (m.isDeleted()) return "This message was deleted";
        if (m.hasAttachment()) {
            if (m.body != null && !m.body.isEmpty()) return m.body;
            switch (m.kind) {
                case IMAGE: return "Photo";
                case VIDEO: return "Video";
                case AUDIO: return "Audio";
                case VOICE_NOTE: return "Voice message";
                case DOCUMENT: return "Document";
                default: return "Attachment";
            }
        }
        return m.body;
    }

    // ---- snapshot / restore ----

    public JsonObject snapshot() {
        JsonObject o = new JsonObject();
        o.put("version", 1);
        o.put("myId", myId);
        o.put("profile", profile == null ? new JsonObject() : profile.toJson());
        JsonArray cs = new JsonArray();
        for (Conversation c : conversations.values()) cs.add(c.toJson());
        o.put("conversations", cs);
        JsonArray ms = new JsonArray();
        for (Message m : messages.values()) ms.add(m.toJson());
        o.put("messages", ms);
        JsonArray ct = new JsonArray();
        for (Contact c : contacts.values()) ct.add(c.toJson());
        o.put("contacts", ct);
        JsonArray rs = new JsonArray();
        for (Reaction r : reactions) rs.add(r.toJson());
        o.put("reactions", rs);
        JsonArray ob = new JsonArray();
        for (OutboxEntry e : outbox.all()) ob.add(e.toJson());
        o.put("outbox", ob);
        o.put("seq", seq);
        return o;
    }

    public static MessengerRepository restore(String json, String myId) {
        MessengerRepository repo = new MessengerRepository(myId);
        if (json == null || json.isEmpty()) return repo;
        JsonObject o = Json.parseObject(json);
        if (o.getObject("profile") != null) {
            repo.profile = UserProfile.fromJson(o.getObject("profile"));
        }
        JsonArray cons = o.getArray("conversations");
        for (int i = 0; i < cons.size(); i++) {
            Conversation c = Conversation.fromJson(cons.getObject(i));
            repo.conversations.put(c.id, c);
        }
        JsonArray msgs = o.getArray("messages");
        for (int i = 0; i < msgs.size(); i++) {
            Message m = Message.fromJson(msgs.getObject(i));
            repo.messages.put(m.id, m);
        }
        JsonArray ct = o.getArray("contacts");
        for (int i = 0; i < ct.size(); i++) {
            Contact c = Contact.fromJson(ct.getObject(i));
            repo.contacts.put(c.id, c);
        }
        JsonArray rs = o.getArray("reactions");
        for (int i = 0; i < rs.size(); i++) {
            repo.reactions.add(Reaction.fromJson(rs.getObject(i)));
        }
        JsonArray ob = o.getArray("outbox");
        for (int i = 0; i < ob.size(); i++) {
            repo.outbox.add(OutboxEntry.fromJson(ob.getObject(i)));
        }
        repo.seq = o.getLong("seq", 0);
        return repo;
    }

    private long now() {
        return System.currentTimeMillis();
    }
}