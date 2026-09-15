package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.Arrays;
import java.util.List;

/**
 * Pure delivery-state machine: PENDING -> SENT -> DELIVERED -> READ with
 * deterministic policy (read receipts on/off) and a testable clock.
 */
public final class StatusSimulator {

    public interface Clock {
        long now();
    }

    public static final class SystemClock implements Clock {
        @Override public long now() { return System.currentTimeMillis(); }
        public static final SystemClock INSTANCE = new SystemClock();
    }

    private static final List<Message.Status> ORDER = Arrays.asList(
            Message.Status.PENDING, Message.Status.SENT, Message.Status.DELIVERED, Message.Status.READ);

    private final Clock clock;

    public StatusSimulator(Clock clock) {
        this.clock = clock;
    }

    /** Deterministic "tick": advance one step or reflect read-receipt policy. */
    public Message tick(Message m, boolean readReceiptsEnabled) {
        if (m == null || m.isSystem() || m.isDeleted()) return m;
        if (m.status == Message.Status.FAILED || m.status == Message.Status.READ) return m;
        int idx = ORDER.indexOf(m.status);
        if (idx < 0) return m;
        Message.Status next = ORDER.get(Math.min(idx + 1, ORDER.size() - 1));
        if (next == Message.Status.READ && !readReceiptsEnabled && m.isFrom(m.senderId)) {
            return m;
        }
        return m.withStatus(next);
    }

    public Message markFailed(Message m, long now) {
        if (m == null) return m;
        return new com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message(
                m.id, m.conversationId, m.senderId, m.body, m.kind, Message.Status.FAILED,
                m.createdAt, now, m.editedAt, m.replyToId, m.deletedAt, m.attachment);
    }

    public Message ack(Message m) {
        if (m == null) return m;
        return m.withStatus(Message.Status.SENT);
    }

    public static List<Message.Status> order() {
        return ORDER;
    }
}