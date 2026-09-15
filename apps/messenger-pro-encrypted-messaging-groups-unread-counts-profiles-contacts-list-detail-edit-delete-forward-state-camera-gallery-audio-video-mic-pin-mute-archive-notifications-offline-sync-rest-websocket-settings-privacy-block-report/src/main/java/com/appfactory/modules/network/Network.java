package com.appfactory.modules.network;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/** Connectivity state machine (JVM-testable, no Android deps). */
public final class Network {

    public enum Status { UNKNOWN, ONLINE, OFFLINE }

    public interface Listener {
        void onStatusChanged(Status previous, Status current);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Status status = Status.UNKNOWN;

    public synchronized void update(boolean connected) {
        Status next = connected ? Status.ONLINE : Status.OFFLINE;
        if (next == status) return;
        Status prev = status;
        status = next;
        for (Listener l : listeners) l.onStatusChanged(prev, next);
    }

    public synchronized Status status() { return status; }
    public synchronized boolean isOnline() { return status == Status.ONLINE; }

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    public int listenerCount() { return listeners.size(); }

    /** Offline-first policy: decide whether to run an action now or defer. */
    public static boolean shouldRunNow(boolean isOnline, OfflinePolicy policy) {
        switch (policy) {
            case BLOCK_WHEN_OFFLINE: return isOnline;
            case QUEUE_WHEN_OFFLINE: return true;  // caller queues for sync
            case ALWAYS:             return true;
            default:                 return isOnline;
        }
    }

    public enum OfflinePolicy { BLOCK_WHEN_OFFLINE, QUEUE_WHEN_OFFLINE, ALWAYS }
}