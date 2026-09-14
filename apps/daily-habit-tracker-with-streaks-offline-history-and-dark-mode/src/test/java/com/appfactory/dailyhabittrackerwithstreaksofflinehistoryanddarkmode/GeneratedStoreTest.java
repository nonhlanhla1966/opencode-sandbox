package com.appfactory.dailyhabittrackerwithstreaksofflinehistoryanddarkmode;

import org.junit.Test;
import static org.junit.Assert.*;

/** Store invariants that keep the offline-first data layer honest. */
public final class GeneratedStoreTest {
    @Test public void searchIsCaseInsensitive() {
        ItemStore s = new ItemStore();
        s.add("Milk", "dairy");
        s.add("Bread", "grains");
        assertEquals(1, s.search("m").size());
        assertEquals(1, s.search("M").size());
        assertEquals(0, s.search("cheese").size());
    }
    @Test public void removeIsIdempotent() {
        ItemStore s = new ItemStore();
        s.add("a", "1");
        assertTrue(s.remove("1"));
        assertEquals(0, s.size());
    }
    @Test public void jsonRoundTripKeepsOrder() {
        ItemStore s = new ItemStore();
        s.add("first", "f");
        s.add("second", "s");
        ItemStore back = ItemStore.fromJson(s.toJson().toString());
        assertEquals("first", back.items().get(0).title);
        assertEquals("second", back.items().get(1).title);
    }
}
