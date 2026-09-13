package com.appfactory.hello;

/** Pure logic class; covered by unit tests. */
public final class Greeting {

    private Greeting() {}

    public static String greeting(String name) {
        String safe = (name == null || name.trim().isEmpty()) ? "Friend" : name.trim();
        return "Hello, " + safe + "!";
    }

    public static String counterLabel(int count) {
        return "Presses: " + count;
    }
}