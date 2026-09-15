package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

/**
 * Pure notification policy: when an incoming message should fire a status-bar
 * notification given mute/pin/archive state and user prefs.
 */
public final class NotificationPolicy {

    public enum Channel { ACTIVE, MUTED, ARCHIVED, OFF }

    private final boolean notificationsEnabled;

    public NotificationPolicy(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public Channel channelFor(Conversation c) {
        if (!notificationsEnabled) return Channel.OFF;
        if (c == null) return Channel.ACTIVE;
        if (c.muted) return Channel.MUTED;
        if (c.archived) return Channel.ARCHIVED;
        return Channel.ACTIVE;
    }

    public boolean shouldNotify(Conversation c, Message m) {
        if (m == null || c == null) return false;
        if (m.isFrom("me")) return false;
        if (m.isSystem()) return false;
        Channel ch = channelFor(c);
        return ch == Channel.ACTIVE || ch == Channel.MUTED;
    }

    public boolean shouldVibrate(Channel ch) {
        return ch == Channel.ACTIVE;
    }

    public boolean shouldSound(Channel ch) {
        return ch == Channel.ACTIVE;
    }
}