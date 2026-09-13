package com.appfactory.notes;

/** Immutable in-memory representation of a stored note. */
public final class Note {

    public final long id;
    public final String title;
    public final String body;
    public final long updatedAt;

    public Note(long id, String title, String body, long updatedAt) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.updatedAt = updatedAt;
    }
}