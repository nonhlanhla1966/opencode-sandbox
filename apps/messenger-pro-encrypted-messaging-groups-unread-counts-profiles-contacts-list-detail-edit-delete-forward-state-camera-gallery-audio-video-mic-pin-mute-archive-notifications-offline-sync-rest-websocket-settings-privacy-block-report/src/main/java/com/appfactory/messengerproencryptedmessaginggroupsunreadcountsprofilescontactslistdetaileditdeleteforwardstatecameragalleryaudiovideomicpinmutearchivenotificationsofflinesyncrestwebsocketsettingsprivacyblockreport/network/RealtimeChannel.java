package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network;

import java.util.List;

/**
 * Realtime channel (WebSocket) abstraction. Deterministic queue drains make it
 * trivially testable; the mock ships with the app for offline operation.
 */
public interface RealtimeChannel {

    void open();

    void close();

    boolean isOpen();

    void setListener(Listener l);

    /** Drain pending events since the last drain. */
    List<Event> drain();

    interface Listener {
        void onEvent(Event e);
        void onStateChanged(boolean open);
    }

    final class Event {
        public final String type;         // NEW_MESSAGE | EDITED | DELETED | READ | REACTION
        public final String conversationId;
        public final String messageId;
        public final String payload;      // json string, per-type

        public Event(String type, String conversationId, String messageId, String payload) {
            this.type = type;
            this.conversationId = conversationId;
            this.messageId = messageId;
            this.payload = payload;
        }
    }
}