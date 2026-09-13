package com.appfactory.aonebuttonhelloworld;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class GreetingTest {

    @Test
    public void helloIsWorld() {
        assertEquals("Hello, World!", Greeting.hello());
    }

    @Test
    public void pressesCycleThroughMessages() {
        assertEquals("Hello, World!", Greeting.pressMessage(0));
        assertEquals("Hello, again!", Greeting.pressMessage(1));
        assertEquals("Hello, hello!", Greeting.pressMessage(2));
    }

    @Test
    public void pressesWrapAround() {
        assertEquals("Hello, World!", Greeting.pressMessage(3));
        assertEquals("Hello, hello!", Greeting.pressMessage(5));
    }
}