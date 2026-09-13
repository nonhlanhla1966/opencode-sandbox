package com.appfactory.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public final class NoteEditActivity extends Activity {

    static final String EXTRA_NOTE_ID = "com.appfactory.notes.NOTE_ID";

    private NotesDb store;
    private long noteId = -1L;
    private EditText titleField;
    private EditText bodyField;
    private Button deleteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);

        store = new NotesDb(this);
        titleField = findViewById(R.id.noteTitleInput);
        bodyField = findViewById(R.id.noteBodyInput);
        deleteButton = findViewById(R.id.deleteButton);

        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1L);
        if (noteId < 0) {
            noteId = -1L;
            deleteButton.setVisibility(View.GONE);
        } else {
            Note existing = store.get(noteId);
            if (existing == null) {
                toast(R.string.note_missing);
                finish();
                return;
            }
            titleField.setText(existing.title);
            bodyField.setText(existing.body);
        }

        deleteButton.setOnClickListener(v -> {
            if (noteId < 0) {
                return;
            }
            new AlertDialog.Builder(NoteEditActivity.this)
                    .setTitle(R.string.delete_title)
                    .setMessage(R.string.delete_message)
                    .setPositiveButton(R.string.delete_confirm, (d, w) -> {
                        store.delete(noteId);
                        toast(R.string.deleted_toast);
                        finish();
                    })
                    .setNegativeButton(R.string.delete_cancel, null)
                    .show();
        });
    }

    @Override
    public void onBackPressed() {
        saveAndFinish();
    }

    private void saveAndFinish() {
        String title = titleField.getText() == null
                ? "" : titleField.getText().toString();
        String body = bodyField.getText() == null
                ? "" : bodyField.getText().toString();

        if (NoteText.isBlank(title, body)) {
            toast(R.string.empty_note_ignored);
            finish();
            return;
        }

        long now = System.currentTimeMillis();
        String cleanTitle = NoteText.titleOf(title);
        String cleanBody = NoteText.bodyOf(body);

        if (noteId < 0) {
            store.create(cleanTitle, cleanBody, now);
        } else {
            store.update(noteId, cleanTitle, cleanBody, now);
        }
        finish();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}