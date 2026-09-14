package com.appfactory.modules.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkTest {
    @Test public void stateTransitions() {
        Network n = new Network();
        assertEquals(Network.Status.UNKNOWN, n.status());
        n.update(true);
        assertTrue(n.isOnline());
        n.update(true);
        assertEquals(1, n.status().ordinal());  // still ONLINE, no repeat event
        n.update(false);
        assertFalse(n.isOnline());
        assertEquals(Network.Status.OFFLINE, n.status());
    }
    @Test public void listenersNotifiedOncePerRealChange() {
        final int[] events = {0};
        Network n = new Network();
        n.addListener((prev, cur) -> events[0]++);
        n.update(true);
        n.update(true);
        n.update(false);
        assertEquals(2, events[0]);
    }
    @Test public void offlinePolicy() {
        assertFalse(Network.shouldRunNow(false, Network.OfflinePolicy.BLOCK_WHEN_OFFLINE));
        assertTrue(Network.shouldRunNow(false, Network.OfflinePolicy.QUEUE_WHEN_OFFLINE));
        assertTrue(Network.shouldRunNow(true, Network.OfflinePolicy.BLOCK_WHEN_OFFLINE));
    }
}