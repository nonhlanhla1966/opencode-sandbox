package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.SearchIndex;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Conversation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Search conversations + messages with tappable live results. */
public final class SearchActivity extends BaseActivity {

    private final List<SearchIndex.Hit> hits = new ArrayList<>();
    private HitAdapter adapter;
    private TextView emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EditText input = new EditText(this);
        input.setHint(R.string.hint_search);
        input.setSingleLine(true);

        adapter = new HitAdapter(this, hits);
        Map<String, String> titles = new HashMap<>();
        for (Conversation c : repo().conversations()) titles.put(c.id, c.title);
        adapter.setTitles(titles);

        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            SearchIndex.Hit h = hits.get(position);
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("conversationId", h.conversationId);
            startActivity(i);
        });

        emptyState = new TextView(this);
        emptyState.setText(R.string.hint_search);
        emptyState.setPadding(dp(16), dp(20), dp(16), dp(16));
        emptyState.setGravity(android.view.Gravity.CENTER);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { runQuery(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(input);
        root.addView(emptyState);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        runQuery("");
    }

    private void runQuery(String q) {
        hits.clear();
        if (q.trim().isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            emptyState.setText(R.string.hint_search);
        } else {
            emptyState.setVisibility(View.GONE);
        }
        hits.addAll(repo().search(q, 50));
        adapter.notifyDataSetChanged();
    }

    private static final class HitAdapter extends BaseAdapter {
        private final Context context;
        private final List<SearchIndex.Hit> items;
        private final Map<String, String> titles = new HashMap<>();

        HitAdapter(Context context, List<SearchIndex.Hit> items) {
            this.context = context;
            this.items = items;
        }

        void setTitles(Map<String, String> titles) {
            this.titles.clear();
            this.titles.putAll(titles);
        }

        @Override public int getCount() { return items.size(); }
        @Override public SearchIndex.Hit getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = View.inflate(context, android.R.layout.simple_list_item_2, null);
            }
            TextView line1 = v.findViewById(android.R.id.text1);
            TextView line2 = v.findViewById(android.R.id.text2);
            SearchIndex.Hit h = getItem(position);
            String t = titles.get(h.conversationId);
            line1.setText(t == null ? h.conversationId : t);
            line2.setText(h.preview);
            return v;
        }
    }
}