package com.appfactory.modules.settings;

import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsTest {
    @Test public void typedStorage() {
        Settings s = new Settings(new Settings.MemoryProvider());
        s.putInt("count", 3);
        s.putBoolean("dark", true);
        s.putLong("ts", 1234567890123L);
        s.put("name", "alice");
        assertEquals(3, s.getInt("count", -1));
        assertTrue(s.getBoolean("dark", false));
        assertEquals(1234567890123L, s.getLong("ts", -1));
        assertEquals("alice", s.getString("name"));
        assertFalse(s.has("missing"));
        s.remove("name");
        assertFalse(s.has("name"));
    }
    @Test public void listenerFiresOnChange() {
        final boolean[] fired = {false};
        Settings s = new Settings(new Settings.MemoryProvider());
        s.addListener("light", k -> fired[0] = true);
        s.putBoolean("light", true);
        assertTrue(fired[0]);
    }
    @Test public void defaultsAreSafe() {
        Settings s = new Settings(new Settings.MemoryProvider());
        assertEquals("d", s.getString("x", "d"));
        assertEquals(9, s.getInt("x", 9));
        assertFalse(s.getBoolean("x", false));
    }
}