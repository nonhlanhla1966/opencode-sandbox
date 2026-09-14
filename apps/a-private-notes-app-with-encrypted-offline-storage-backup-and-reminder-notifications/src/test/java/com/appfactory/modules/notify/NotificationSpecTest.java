package com.appfactory.modules.notify;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

public class NotificationSpecTest {
    @Test public void buildsValidSpec() {
        NotificationSpec s = new NotificationSpec.Builder()
                .title("Reminder").text("Drink water").channel(NotificationSpec.Channel.IMPORTANT)
                .build();
        assertTrue(s.isActionable());
        assertEquals("Reminder", s.title);
        assertEquals("Drink water", s.text);
    }
    @Test(expected = IllegalArgumentException.class)
    public void requiresText() {
        new NotificationSpec.Builder().title("x").build();
    }
    @Test public void groupProduced() {
        Map<Integer, NotificationSpec> g = NotificationSpec.group("T", "m", 3);
        assertEquals(3, g.size());
    }
}