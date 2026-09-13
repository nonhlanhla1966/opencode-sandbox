package com.appfactory.notes;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class HomeActivity extends Activity {

    private static final int REQUEST_EDIT = 11;
    private static final DateFormat DATE_FMT = DateFormat.getDateInstance(DateFormat.MEDIUM);

    private NotesDb store;
    private String query = "";
    private TextView emptyText;
    private TextView notesCount;
    private EditText searchField;
    private ListView noteList;
    private final List<Note> notes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        store = new NotesDb(this);

        noteList = findViewById(R.id.noteList);
        emptyText = findViewById(R.id.emptyText);
        notesCount = findViewById(R.id.notesCount);
        searchField = findViewById(R.id.searchFieldapse);

        adapter = new ArrayAdapter<Note>(this, R.layout.item_note, notes) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView != null ? convertView
                        : LayoutInflater.from(getContext()).inflate(R.layout.item_note, parent, false);
                Note n = getItem(position);
                TextView title = v.findViewById(R.id.noteTitle);
                TextView snippet = v.findViewById(R.id.noteSnippet);
                TextView date = v.findViewById(R.id.noteDate);
                title.setText(NoteText.titleOf(n.title));
                snippet.setText(NoteText.snippet(n.body, 80));
                date.setText(DATE_FMT.format(new Date(n.updatedAt)));
                return v;
            }
        };
        noteList.setAdapter(adapter);

        noteList.setOnItemClickListener((parent, v, position, id) ->
                openNote(adapter.getItem(position).id));

        findViewById(R.id.addButton).setOnClickListener(v -> openNote());
        findViewById(R.id.aboutButton).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s == null ? "" : s.toString();
                reload();
            }
        });
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void openNote() {
        openNote(-1L);
    }

    private void openNote(long noteId) {
        Intent i = new Intent(this, NoteEditActivity.class);
        i.putExtra(NoteEditActivity.EXTRA_NOTE_ID, noteId);
        startActivity(i);
    }

    private void reload() {
        List<Note> found = query.trim().isEmpty()
                ? store.listAll()
                : store.search(query);
        notes.clear();
        notes.addAll(found);
        adapter.notifyDataSetChanged();

        boolean hasMatches = query.trim().isEmpty()
                ? !store.listAll().isEmpty()
                : !found.isEmpty() || true;
        boolean noQuery = query.trim().isEmpty();
        boolean empty = noQuery ? notes.isEmpty() : found.isEmpty();
        emptyText.setText(empty
                ? (noQuery ? R.string.empty_home : R.string.empty_search)
                : 0);
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        notesCount.setText(getString(R.string.notes_count_format, notes.size()));
    }

    private ArrayAdapter<Note> adapter;

    private static final class NoteAdapter extends ArrayAdapter<Note> {
        NoteAdapter(Activity a) {
            super(a, 0);
        }
    }
}