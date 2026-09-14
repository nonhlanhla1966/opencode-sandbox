package com.appfactory.modules.text;

import org.junit.Test;
import static org.junit.Assert.*;

public class TextTest {
    @Test public void slugify() {
        assertEquals("a-stop-watch-with-lap-times",
                Text.slugify("A Stop watch! With Lap Times"));
        assertEquals("flash-light", Text.slugify("FlAsH-ligHT"));
        assertEquals("app", Text.slugify("   "));
        assertFalse(Text.slugify("../etc").contains("."));
    }
    @Test public void titleCase() {
        assertEquals("Hello World", Text.titleCase("hello world"));
        assertEquals("My App", Text.titleCase("my-app"));
    }
    @Test public void truncate() {
        assertEquals("ab…", Text.truncate("abcdef", 3));
        assertEquals("abc", Text.truncate("abc", 3));
    }
    @Test public void durations() {
        assertEquals("5s", Text.formatDuration(5));
        assertEquals("1m 05s", Text.formatDuration(65));
        assertEquals("1h 00m 01s", Text.formatDuration(3601));
    }
    @Test public void bytes() {
        assertEquals("512 B", Text.formatBytes(512));
        assertEquals("1.0 KB", Text.formatBytes(1024));
    }
}