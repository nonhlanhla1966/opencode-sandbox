package com.appfactory.aonebuttonhelloworld;

/** Pure logic class for the greeting messages; covered by unit tests. */
public final class Greeting {

    private static final String[] MESSAGES = {
            "Hello, World!",
            "Hello, again!",
            "Hello, hello!"
    };

    private Greeting() {}

    public static String hello() {
        return MESSAGES[0];
    }

    /** Returns the message for a 0-based press index, cycling forever. */
    public static String pressMessage(int pressIndex) {
        int safe = pressIndex < 0 ? -pressIndex : pressIndex;
        return MESSAGES[safe % MESSAGES.length];
    }
}