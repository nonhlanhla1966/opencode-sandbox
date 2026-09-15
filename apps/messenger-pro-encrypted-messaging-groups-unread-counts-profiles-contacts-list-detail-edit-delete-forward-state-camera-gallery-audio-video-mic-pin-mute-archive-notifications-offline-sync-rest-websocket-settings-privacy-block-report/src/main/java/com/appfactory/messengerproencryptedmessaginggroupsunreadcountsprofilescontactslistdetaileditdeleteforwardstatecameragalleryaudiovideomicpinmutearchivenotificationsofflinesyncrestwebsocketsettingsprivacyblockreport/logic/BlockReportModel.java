package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Contact;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure privacy policy: what a blocked contact may/cannot do plus a local
 * report audit trail (block, report, lastSeen privacy).
 */
public final class BlockReportModel {

    public static final class Report {
        public final String reportedContactId;
        public final String reason;
        public final long at;

        Report(String reportedContactId, String reason, long at) {
            this.reportedContactId = reportedContactId;
            this.reason = reason;
            this.at = at;
        }
    }

    private final List<Report> reports = new ArrayList<>();

    public List<Report> reports() {
        return new ArrayList<>(reports);
    }

    /** A blocked contact cannot message us; server is expected to enforce. */
    public boolean blockContact(Contact contact) {
        return !contact.blocked;
    }

    public boolean canSendTo(Conversation c, Contact peer) {
        return c == null
                || peer == null
                || !peer.blocked
                || !c.peerId.equals(peer.id);
    }

    public Report reportContact(Contact contact, String reason, long at) {
        Report r = new Report(contact.id, reason == null ? "" : reason, at);
        reports.add(r);
        return r;
    }

    /** Privacy: show "last seen" only if both peers share the setting. */
    public boolean canSeeLastSeen(boolean myLastSeenPublic, boolean theirLastSeenPublic) {
        return myLastSeenPublic && theirLastSeenPublic;
    }

    public boolean allowedReactionForBlocked(Message m, Contact peer) {
        return peer == null || !peer.blocked || m == null || !m.isFrom(peer.id);
    }
}