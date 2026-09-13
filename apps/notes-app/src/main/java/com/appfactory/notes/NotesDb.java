package com.appfactory.notes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Local SQLite persistence for notes. The only storage backend — fully
 * offline, no network, no external dependencies.
 */
public final class NotesDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "notes.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE = "notes";
    private static final String COL_ID = "_id";
    private static final String COL_TITLE = "title";
    private static final String COL_BODY = "body";
    private static final String COL_UPDATED = "updated_at";

    public NotesDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_TITLE + " TEXT NOT NULL,"
                + COL_BODY + " TEXT NOT NULL,"
                + COL_UPDATED + " INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_notes_updated ON " + TABLE + " (" + COL_UPDATED + " DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public synchronized long create(String title, String body, long updatedAt) {
        ContentValues v = new ContentValues();
        v.put(COL_TITLE, title);
        v.put(COL_BODY, body);
        v.put(COL_UPDATED, updatedAt);
        SQLiteDatabase db = getWritableDatabase();
        long id = db.insert(TABLE, null, v);
        db.close();
        return id;
    }

    public synchronized boolean update(long id, String title, String body, long updatedAt) {
        ContentValues v = new ContentValues();
        v.put(COL_TITLE, title);
        v.put(COL_BODY, body);
        v.put(COL_UPDATED, updatedAt);
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.update(TABLE, v, COL_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rows > 0;
    }

    public synchronized boolean delete(long id) {
        SQLiteDatabase db = getWritableDatabase();
        int rows = db.delete(TABLE, COL_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rows > 0;
    }

    public synchronized Note get(long id) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null, "1");
        Note note = null;
        if (c.moveToFirst()) {
            note = readNote(c);
        }
        c.close();
        db.close();
        return note;
    }

    /** All notes, newest first. */
    public synchronized List<Note> listAll() {
        return query(null);
    }

    /** Notes whose title or body contains the query, newest first. */
    public synchronized List<Note> search(String query) {
        return query(query);
    }

    private List<Note> query(String query) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        if (query == null || query.trim().isEmpty()) {
            c = db.query(TABLE, null, null, null, null, null,
                    COL_UPDATED + " DESC");
        } else {
            String like = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
            c = db.query(TABLE, null,
                    "LOWER(" + COL_TITLE + ") LIKE ? OR LOWER(" + COL_BODY + ") LIKE ?",
                    new String[]{like, like}, null, null, COL_UPDATED + " DESC");
        }
        List<Note> notes = new ArrayList<>();
        while (c.moveToNext()) {
            notes.add(readNote(c));
        }
        c.close();
        db.close();
        return notes;
    }

    private Note readNote(Cursor c) {
        return new Note(
                c.getLong(c.getColumnIndexOrThrow(COL_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
                c.getString(c.getColumnIndexOrThrow(COL_BODY)),
                c.getLong(c.getColumnIndexOrThrow(COL_UPDATED)));
    }
}