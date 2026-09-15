package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerRepository;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.OutboxEntry;
import com.appfactory.modules.json.JsonObject;
import com.appfactory.modules.network.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Offline-first sync engine. Drains the outbox, polls incoming per
 * conversation when online, and processes realtime channel events. All logic
 * is deterministic and unit-testable against the mock API/channel.
 */
public final class SyncEngine {

    public enum State { OFFLINE, IDLE, SYNCING }

    public interface StateListener {
        void onStateChanged(State state);
        void onSyncCompleted(int processed);
    }

    static final int MAX_ATTEMPTS = 3;

    private final MessengerRepository repo;
    private final MessagingApi api;
    private final RealtimeChannel channel;
    private final Network network;
    private final List<StateListener> listeners = new ArrayList<>();
    private final Map<String, Long> cursors = new LinkedHashMap<>();

    private State state = State.IDLE;

    public SyncEngine(MessengerRepository repo, MessagingApi api,
                      RealtimeChannel channel, Network network) {
        this.repo = repo;
        this.api = api;
        this.channel = channel;
        this.network = network;
    }

    public void addListener(StateListener l) {
        listeners.add(l);
    }

    public State state() {
        return state;
    }

    /** One synchronous sync pass; safe to call from tests. */
    public synchronized void sync() {
        boolean online = network.status() == Network.Status.ONLINE;
        if (!online) {
            setState(State.OFFLINE);
            for (StateListener l : listeners) l.onSyncCompleted(0);
            return;
        }
        setState(State.SYNCING);
        int processed = drainOutbox();
        processed += pollIncoming();
        if (channel.isOpen()) processed += drainChannel();
        setState(State.IDLE);
        for (StateListener l : listeners) l.onSyncCompleted(processed);
    }

    /** Realtime events only (no outbox/poll). */
    public synchronized void processChannelEvents() {
        if (channel.isOpen() && state != State.SYNCING) {
            int processed = drainChannel();
            for (StateListener l : listeners) l.onSyncCompleted(processed);
        }
    }

    private int drainOutbox() {
        int processed = 0;
        while (true) {
            OutboxEntry e = repo.nextOutbox();
            if (e == null) break;
            try {
                execute(e);
                repo.removeOutbox(e.id);
                processed++;
            } catch (MessagingApi.ApiException ex) {
                repo.outboxAttempted(e.id, ex.getMessage() == null ? ex.getClass().getName()
                        : ex.getMessage());
                if (e.attempts + 1 >= MAX_ATTEMPTS) {
                    markFailedIfSend(e);
                }
                break;
            }
        }
        return processed;
    }

    private void markFailedIfSend(OutboxEntry e) {
        if (e.op == OutboxEntry.Op.SEND_MESSAGE) {
            JsonObject p = safeParse(e.payload);
            if (p != null) {
                Message m = Message.fromJson(p);
                repo.updateStatus(m.id, Message.Status.FAILED);
            }
        }
    }

    private JsonObject safeParse(String json) {
        try {
            JsonObject o = com.appfactory.modules.json.Json.parseObject(json);
            return o;
        } catch (RuntimeException err) {
            return null;
        }
    }

    private void execute(OutboxEntry e) throws MessagingApi.ApiException {
        JsonObject p = safeParse(e.payload);
        if (p == null) throw new MessagingApi.ApiException("bad outbox payload");
        switch (e.op) {
            case SEND_MESSAGE: {
                Message m = Message.fromJson(p);
                api.send(m);
                repo.ack(m.id);
                break;
            }
            case EDIT_MESSAGE: {
                String id = p.getString("messageId", "");
                String nb = p.getString("newBody", "");
                api.edit(id, nb);
                repo.applyRemoteEdit(id, nb);
                break;
            }
            case DELETE_MESSAGE: {
                String id = p.getString("messageId", "");
                api.delete(id);
                repo.applyRemoteDelete(id);
                break;
            }
            case READ_RECEIPT: {
                String conv = p.getString("conversationId", "");
                String up = p.getString("upToMessageId", "");
                api.markRead(conv, up);
                break;
            }
            case REACTION: {
                api.react(p.getString("messageId", ""), p.getString("emoji", ""));
                break;
            }
            case FORWARD: {
                api.forward(p.getString("messageId", ""), p.getString("targetConversationId", ""));
                break;
            }
            case REPORT: {
                api.report(p.getString("contactId", ""), p.getString("reason", ""));
                break;
            }
            default:
                // unknown op: drop silently to avoid wedging the queue
        }
    }

    private int pollIncoming() {
        int processed = 0;
        for (Conversation c : repo.conversations()) {
            long since = cursors.containsKey(c.id) ? cursors.get(c.id) : 0L;
            try {
                List<Message> incoming = api.fetchIncoming(c.id, since);
                long newest = since;
                for (Message m : incoming) {
                    if (m.createdAt > newest) newest = m.createdAt;
                    repo.incoming(m);
                    processed++;
                }
                cursors.put(c.id, newest);
            } catch (MessagingApi.ApiException ex) {
                // transient failures are tolerated; cursor keeps old position
            }
        }
        return processed;
    }

    private int drainChannel() {
        int processed = 0;
        for (RealtimeChannel.Event ev : channel.drain()) {
            processEvent(ev);
            processed++;
        }
        return processed;
    }

    private void processEvent(RealtimeChannel.Event ev) {
        JsonObject p = safeParse(ev.payload);
        switch (ev.type) {
            case "NEW_MESSAGE": {
                if (p != null) repo.incoming(Message.fromJson(p));
                break;
            }
            case "EDITED": {
                if (p != null) repo.applyRemoteEdit(ev.messageId, p.getString("newBody", ""));
                break;
            }
            case "DELETED": {
                repo.applyRemoteDelete(ev.messageId);
                break;
            }
            case "READ": {
                // peer read confirmation; nothing local to change
                break;
            }
            default:
                // ignore unknown types
        }
    }

    private void setState(State next) {
        if (state == next) return;
        state = next;
        for (StateListener l : listeners) l.onStateChanged(state);
    }
}