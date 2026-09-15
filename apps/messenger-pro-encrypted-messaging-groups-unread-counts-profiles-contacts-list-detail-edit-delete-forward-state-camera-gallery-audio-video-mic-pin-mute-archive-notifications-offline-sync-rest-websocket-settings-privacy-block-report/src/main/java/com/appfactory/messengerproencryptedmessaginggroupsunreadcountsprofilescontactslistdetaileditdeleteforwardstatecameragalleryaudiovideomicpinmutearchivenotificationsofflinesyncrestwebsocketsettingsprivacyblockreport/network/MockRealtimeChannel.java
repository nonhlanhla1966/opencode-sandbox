package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic in-memory realtime channel: open/close state plus a FIFO
 * event buffer that producers push to (simulating the server) and that
 * {@link #drain()} releases to listeners in order.
 */
public final class MockRealtimeChannel implements RealtimeChannel {

    private final List<Event> buffer = new ArrayList<>();
    private Listener listener;
    private boolean open;

    @Override
    public void open() {
        open = true;
        if (listener != null) listener.onStateChanged(true);
    }

    @Override
    public void close() {
        open = false;
        if (listener != null) listener.onStateChanged(false);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void setListener(Listener l) {
        this.listener = l;
    }

    /** Producer-side: simulate remote event arriving. */
    public void push(Event e) {
        buffer.add(e);
        if (open && listener != null) listener.onEvent(e);
    }

    @Override
    public List<Event> drain() {
        if (buffer.isEmpty()) return Collections.emptyList();
        List<Event> out = new ArrayList<>(buffer);
        buffer.clear();
        return out;
    }

    public int pending() {
        return buffer.size();
    }
}