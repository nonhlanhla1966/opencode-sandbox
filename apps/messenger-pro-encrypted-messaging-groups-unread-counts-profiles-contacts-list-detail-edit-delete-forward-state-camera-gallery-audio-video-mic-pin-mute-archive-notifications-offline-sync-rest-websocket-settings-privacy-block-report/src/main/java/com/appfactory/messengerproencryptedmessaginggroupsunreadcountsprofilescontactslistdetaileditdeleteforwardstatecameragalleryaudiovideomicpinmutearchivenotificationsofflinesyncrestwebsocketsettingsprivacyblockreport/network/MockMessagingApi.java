package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic in-memory backend used by tests and as the offline fallback.
 * Mirrors "the server": buffers messages per conversation, simulates offline
 * failures, server-side ids and echo delivery.
 */
public final class MockMessagingApi implements MessagingApi {

    private final Map<String, List<Message>> mirror = new LinkedHashMap<>();
    private long serverSeq = 5000;
    private boolean offline = false;
    private boolean echoIncoming = true;   // sent messages become incoming at the peer

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    public boolean offline() {
        return offline;
    }

    public void setEchoIncoming(boolean echoIncoming) {
        this.echoIncoming = echoIncoming;
    }

    /** Seed a stored server-side message (acts as pre-existing history). */
    public void seed(Message m) {
        mirror.computeIfAbsent(m.conversationId, k -> new ArrayList<>()).add(m);
    }

    public List<Message> mirrorFor(String conversationId) {
        List<Message> list = mirror.get(conversationId);
        return list == null ? new ArrayList<>() : list;
    }

    private void guard() throws ApiException {
        if (offline) throw new ApiException("backend offline", true, 503);
    }

    @Override
    public List<Message> fetchMessages(String conversationId, String beforeMessageId, int limit)
            throws ApiException {
        guard();
        List<Message> all = mirrorFor(conversationId);
        List<Message> out = new ArrayList<>();
        for (Message m : all) {
            if (beforeMessageId != null && m.id.equals(beforeMessageId)) break;
            out.add(m);
        }
        if (out.size() > limit) out = out.subList(out.size() - limit, out.size());
        return out;
    }

    @Override
    public Message send(Message out) throws ApiException {
        guard();
        Message server = new Message("srv-" + (++serverSeq), out.conversationId,
                out.senderId, out.body, out.kind, Message.Status.SENT,
                out.createdAt, System.currentTimeMillis(), -1, out.replyToId, -1, out.attachment);
        mirror.computeIfAbsent(out.conversationId, k -> new ArrayList<>()).add(server);
        return server;
    }

    @Override
    public Message edit(String messageId, String newBody) throws ApiException {
        guard();
        for (List<Message> list : mirror.values()) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(messageId)) {
                    Message updated = list.get(i).edit(newBody, System.currentTimeMillis());
                    list.set(i, updated);
                    return updated;
                }
            }
        }
        throw new ApiException("message not found", false, 404);
    }

    @Override
    public void delete(String messageId) throws ApiException {
        guard();
        for (List<Message> list : mirror.values()) {
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id.equals(messageId)) {
                    list.set(i, list.get(i).markDeleted(System.currentTimeMillis()));
                    return;
                }
            }
        }
    }

    @Override
    public void markRead(String conversationId, String upToMessageId) throws ApiException {
        guard();
        // mark all prior messages READ in the mirror
        List<Message> list = mirrorFor(conversationId);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(upToMessageId)) break;
            if (list.get(i).status != Message.Status.READ) {
                list.set(i, list.get(i).withStatus(Message.Status.READ));
            }
        }
    }

    @Override
    public void react(String messageId, String emoji) throws ApiException {
        guard();
        for (List<Message> list : mirror.values()) {
            for (Message m : list) {
                if (m.id.equals(messageId)) return; // accepted
            }
        }
        throw new ApiException("message not found", false, 404);
    }

    @Override
    public void forward(String messageId, String targetConversationId) throws ApiException {
        guard();
        Message src = null;
        for (List<Message> list : mirror.values()) {
            for (Message m : list) if (m.id.equals(messageId)) src = m;
        }
        if (src == null) throw new ApiException("message not found", false, 404);
        Message copy = new Message("srv-" + (++serverSeq), targetConversationId, src.senderId,
                src.body, src.kind, Message.Status.SENT, System.currentTimeMillis(),
                System.currentTimeMillis(), -1, "", -1, src.attachment);
        mirror.computeIfAbsent(targetConversationId, k -> new ArrayList<>()).add(copy);
    }

    @Override
    public void report(String ref, String reason) throws ApiException {
        guard();
    }

    @Override
    public List<Message> fetchIncoming(String conversationId, long sinceCreatedAt)
            throws ApiException {
        guard();
        List<Message> out = new ArrayList<>();
        for (Message m : mirrorFor(conversationId)) {
            if (m.createdAt > sinceCreatedAt) out.add(m);
        }
        return out;
    }

    @Override
    public boolean isReachable() {
        return !offline;
    }

    public boolean echoIncoming() {
        return echoIncoming;
    }
}