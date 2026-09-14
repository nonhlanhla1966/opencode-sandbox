package com.appfactory.opencodechatbot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.appfactory.opencodechatbot.data.ChatSettings;
import com.appfactory.opencodechatbot.data.ConversationStore;
import com.appfactory.opencodechatbot.model.Conversation;
import com.appfactory.opencodechatbot.util.AppTheme;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Conversation list: open, search, rename, delete, delete-all. */
public final class ConversationsActivity extends Activity {

    public static final String EXTRA_CONVERSATION_ID = "conversation_id";
    public static final String EXTRA_NEW = "new";

    private ConversationStore store;
    private ConversationAdapter adapter;
    private List<Conversation> all = new ArrayList<>();
    private ListView list;
    private EditText search;

    private final List<Conversation> filtered = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ChatSettings settings = new ChatSettings(this);
        AppTheme.apply(this, settings);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        store = new ConversationStore(this);
        list = findViewById(R.id.list);
        search = findViewById(R.id.et_search);
        adapter = new ConversationAdapter();
        list.setAdapter(adapter);
        list.setEmptyView(findViewById(R.id.txt_empty));
        registerForContextMenu(list);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation c = filtered.get(position);
                Intent data = new Intent();
                data.putExtra(EXTRA_CONVERSATION_ID, c.getId());
                setResult(RESULT_OK, data);
                finish();
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Button deleteAll = findViewById(R.id.btn_delete_all);
        deleteAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteAll();
            }
        });
        deleteAll.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                confirmDeleteAll();
                return true;
            }
        });

        reload();
    }

    private void reload() {
        all = store.loadAll();
        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        String q = search.getText().toString().trim().toLowerCase(Locale.US);
        for (Conversation c : all) {
            if (q.isEmpty()
                    || c.getTitle().toLowerCase(Locale.US).contains(q)
                    || c.preview().toLowerCase(Locale.US).contains(q)) {
                filtered.add(c);
            }
        }
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------- context menu

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        if (menuInfo instanceof AdapterView.AdapterContextMenuInfo) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            Conversation c = filtered.get(info.position);
            menu.setHeaderTitle(c.getTitle());
            menu.add(1, 101, 0, R.string.action_rename);
            menu.add(1, 102, 1, R.string.action_delete);
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() != 1
                || !(item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo)) {
            return super.onContextItemSelected(item);
        }
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Conversation c = filtered.get(info.position);
        if (item.getItemId() == 101) {
            rename(c);
        } else if (item.getItemId() == 102) {
            confirmDelete(c);
        }
        return true;
    }

    private void rename(final Conversation c) {
        final EditText input = new EditText(this);
        input.setText(c.getTitle());
        input.setSelectAllOnFocus(true);
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename_title)
                .setView(input)
                .setPositiveButton(R.string.action_rename, (d, w) -> {
                    String title = input.getText().toString().trim();
                    if (!title.isEmpty()) {
                        c.setTitle(title);
                        store.save(c);
                        reload();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDelete(final Conversation c) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_message, c.getTitle()))
                .setPositiveButton(R.string.action_confirmed_yes, (d, w) -> {
                    store.delete(c.getId());
                    reload();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmDeleteAll() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_all_confirm_title)
                .setMessage(getString(R.string.delete_all_confirm_message, all.size()))
                .setPositiveButton(R.string.action_confirmed_yes, (d, w) -> {
                    store.deleteAll();
                    reload();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- adapter

    private final class ConversationAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return filtered.size();
        }

        @Override
        public Object getItem(int position) {
            return filtered.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = getLayoutInflater().inflate(R.layout.row_conversation, parent, false);
            }
            Conversation c = filtered.get(position);
            ((TextView) row.findViewById(R.id.txt_title)).setText(c.getTitle());
            ((TextView) row.findViewById(R.id.txt_preview)).setText(c.preview());

            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            ((TextView) row.findViewById(R.id.txt_date)).setText(
                    df.format(new Date(c.getUpdatedAt())));
            return row;
        }
    }
}