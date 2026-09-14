package com.appfactory.aprivatenotesappwithencryptedofflinestoragebackupandremindernotifications;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ItemTest {
    @Test public void itemEqualityById() {
        Item a = new Item("x","a","b",1);
        Item b = new Item("x","c","d",2);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
    @Test public void storeAddAndSearch() {
        ItemStore s = new ItemStore();
        s.add("Grocery", "milk and eggs");
        s.add("Gym", "leg day");
        assertEquals(2, s.size());
        assertEquals(1, s.search("milk").size());
        assertEquals(0, s.search("zzz").size());
    }
    @Test public void storeRemove() {
        ItemStore s = new ItemStore();
        s.add("A", "1");
        s.add("B", "2");
        assertTrue(s.remove("1"));
        assertFalse(s.remove("1"));
        assertEquals(1, s.size());
    }
    @Test public void storeRoundTripsThroughJson() {
        ItemStore s = new ItemStore();
        s.add("Title", "Detail");
        s.add("Two", "");
        String text = s.toJson().toString();
        ItemStore back = ItemStore.fromJson(text);
        assertEquals(2, back.size());
        assertEquals("Title", back.items().get(0).title);
        assertEquals("Detail", back.items().get(0).detail);
    }
}
