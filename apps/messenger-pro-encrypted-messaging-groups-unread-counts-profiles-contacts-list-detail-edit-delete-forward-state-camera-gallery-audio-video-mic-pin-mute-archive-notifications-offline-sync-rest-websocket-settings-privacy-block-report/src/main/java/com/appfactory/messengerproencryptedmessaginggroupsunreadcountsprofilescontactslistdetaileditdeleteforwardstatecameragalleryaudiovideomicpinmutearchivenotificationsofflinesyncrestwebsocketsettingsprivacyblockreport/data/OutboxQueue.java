package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.OutboxEntry;

import java.util.ArrayList;
import java.util.List;

/** Pure FIFO outbox queue (offline draft ops awaiting sync). */
public final class OutboxQueue {

    private final List<OutboxEntry> entries = new ArrayList<>();

    public void add(OutboxEntry e) {
        if (e != null) entries.add(e);
    }

    /** Oldest queued (attempt-ordered) entry, or null when empty. */
    public OutboxEntry next() {
        return entries.isEmpty() ? null : entries.get(0);
    }

    public OutboxEntry remove(String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(id)) {
                return entries.remove(i);
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public boolean has(String id) {
        for (OutboxEntry e : entries) if (e.id.equals(id)) return true;
        return false;
    }

    public List<OutboxEntry> all() {
        return new ArrayList<>(entries);
    }
}