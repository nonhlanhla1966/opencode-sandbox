package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, windowed view over a conversation's messages. Messages are held in
 * chronological order; pagination walks older (before cursor) windows.
 */
public final class ThreadWindow {

    public static final int PAGE_SIZE = 30;

    private final List<Message> messages = new ArrayList<>();

    public void add(Message m) {
        if (m == null) return;
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id.equals(m.id)) {
                messages.set(i, m);
                return;
            }
        }
        int pos = insertionIndex(m.createdAt);
        messages.add(pos, m);
    }

    public void addAll(List<Message> in) {
        for (Message m : in) add(m);
    }

    public Message replace(Message m) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id.equals(m.id)) {
                messages.set(i, m);
                return m;
            }
        }
        add(m);
        return m;
    }

    public void remove(String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id.equals(id)) {
                messages.remove(i);
                return;
            }
        }
    }

    private int insertionIndex(long createdAt) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).createdAt > createdAt) return i;
        }
        return messages.size();
    }

    /** Latest page of {@code limit} messages (tail of the thread). */
    public List<Message> latest(int limit) {
        int n = messages.size();
        return slice(Math.max(0, n - limit), n);
    }

    /** Older window ending before {@code beforeId}; empty when exhausted. */
    public List<Message> olderThan(String beforeId, int limit) {
        int idx = indexOf(beforeId);
        int stop = idx < 0 ? messages.size() : idx;
        int start = Math.max(0, stop - limit);
        return slice(start, stop);
    }

    public boolean has(String id) {
        return indexOf(id) >= 0;
    }

    public int indexOf(String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id.equals(id)) return i;
        }
        return -1;
    }

    public int size() {
        return messages.size();
    }

    public boolean hasMoreOlderThan(String id) {
        return indexOf(id) > 0;
    }

    public List<Message> all() {
        return new ArrayList<>(messages);
    }

    private List<Message> slice(int from, int to) {
        List<Message> out = new ArrayList<>();
        for (int i = from; i < to; i++) out.add(messages.get(i));
        return out;
    }
}