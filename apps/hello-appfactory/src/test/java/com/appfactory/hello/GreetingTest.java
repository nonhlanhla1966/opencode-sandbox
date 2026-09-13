package com.appfactory.hello;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GreetingTest {

    @Test
    public void greetingUsesName() {
        assertEquals("Hello, Ada!", Greeting.greeting("Ada"));
    }

    @Test
    public void greetingTrimsAndDefaults() {
        assertEquals("Hello, Friend!", Greeting.greeting("  "));
        assertEquals("Hello, Friend!", Greeting.greeting(null));
    }

    @Test
    public void counterLabelFormats() {
        assertEquals("Presses: 3", Greeting.counterLabel(3));
    }
}