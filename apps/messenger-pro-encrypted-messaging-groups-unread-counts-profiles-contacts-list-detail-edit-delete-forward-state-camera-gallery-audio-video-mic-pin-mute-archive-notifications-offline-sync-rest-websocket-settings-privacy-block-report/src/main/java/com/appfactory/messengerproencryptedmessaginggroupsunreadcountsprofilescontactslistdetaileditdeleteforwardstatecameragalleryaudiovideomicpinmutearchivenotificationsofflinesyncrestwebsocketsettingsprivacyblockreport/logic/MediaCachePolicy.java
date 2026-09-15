package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Pure media cache eviction policy: keep total cached bytes under a ceiling,
 * evict least-recently-accessed first. Also a data-meter policy for
 * auto-download (Wi-Fi only / on data too).
 */
public final class MediaCachePolicy {

    public static final long DEFAULT_CAPACITY_BYTES = 64L * 1024L * 1024L; // 64 MB

    public static final class Entry {
        public final Attachment attachment;
        public final long lastAccess;

        Entry(Attachment attachment, long lastAccess) {
            this.attachment = attachment;
            this.lastAccess = lastAccess;
        }
    }

    public enum Meter { WIFI_ONLY, DATA_OK }

    private final long capacityBytes;
    private final List<Entry> entries = new ArrayList<>();

    public MediaCachePolicy() {
        this(DEFAULT_CAPACITY_BYTES);
    }

    public MediaCachePolicy(long capacityBytes) {
        this.capacityBytes = capacityBytes;
    }

    public void touch(String attachmentId, long at) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).attachment.id.equals(attachmentId)) {
                entries.set(i, new Entry(entries.get(i).attachment, at));
                return;
            }
        }
    }

    public void add(Attachment a, long at) {
        touch(a.id, at);
        if (!contains(a.id)) entries.add(new Entry(a, at));
    }

    private boolean contains(String id) {
        for (Entry e : entries) if (e.attachment.id.equals(id)) return true;
        return false;
    }

    /** Evict LRU until total size fits the ceiling. Returns evicted ids. */
    public List<String> evict() {
        List<String> evicted = new ArrayList<>();
        long total = totalBytes();
        if (total <= capacityBytes) return evicted;
        entries.sort(Comparator.comparingLong((Entry e) -> e.lastAccess));
        for (int i = 0; i < entries.size() && total > capacityBytes; i++) {
            evicted.add(entries.get(i).attachment.id);
            total -= entries.get(i).attachment.sizeBytes;
        }
        return evicted;
    }

    public long totalBytes() {
        long total = 0;
        for (Entry e : entries) total += e.attachment.sizeBytes;
        return total;
    }

    /** Should a media message auto-download under the current meter policy? */
    public boolean autoDownloadAllowed(Message m, Meter meter, boolean onMetered) {
        if (m == null || !m.hasAttachment()) return false;
        if (meter == Meter.WIFI_ONLY) return !onMetered;
        return true;
    }

    public List<Entry> entriesSnapshot() {
        return Collections.unmodifiableList(entries);
    }
}