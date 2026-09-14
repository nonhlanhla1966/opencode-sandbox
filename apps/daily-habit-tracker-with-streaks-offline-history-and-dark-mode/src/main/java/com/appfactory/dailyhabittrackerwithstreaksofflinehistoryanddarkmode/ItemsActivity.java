package com.appfactory.dailyhabittrackerwithstreaksofflinehistoryanddarkmode;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;

/** Offline-first demo list backed by ItemStore + SharedPreferences. */
public final class ItemsActivity extends Activity {
    private ItemStore store;
    private LinearLayout list;
    private SharedPreferences prefs;
    private static final String KEY = "item_store.json";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("items", MODE_PRIVATE);
        store = ItemStore.fromJson(prefs.getString(KEY, ""));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        setContentView(scroll);

        TextView h = new TextView(this);
        h.setText("Items (offline first)");
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        root.addView(h);

        final EditText title = new EditText(this);
        title.setHint("Title");
        root.addView(title);
        final EditText detail = new EditText(this);
        detail.setHint("Detail");
        root.addView(detail);
        Button addBtn = new Button(this);
        addBtn.setText("Add item");
        addBtn.setOnClickListener(v -> addItem(title.getText(), detail.getText()));
        root.addView(addBtn);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        refresh();

        scroll.addView(root);
    }
    private void addItem(Editable t, Editable d) {
        store.add(t.toString(), d.toString());
        persist();
        refresh();
    }
    private void removeItem(String id) {
        store.remove(id);
        persist();
        refresh();
    }
    private void persist() {
        prefs.edit().putString(KEY, store.toJson().toString()).apply();
    }
    private void refresh() {
        list.removeAllViews();
        for (Item it : store.items()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView tv = new TextView(this);
            tv.setText(it.title + (it.detail.isEmpty() ? "" : " — " + it.detail));
            tv.setPadding(0, dp(4), dp(12), dp(4));
            row.addView(tv);
            Button del = new Button(this);
            del.setText("Remove");
            del.setOnClickListener(v -> removeItem(it.id));
            row.addView(del);
            list.addView(row);
        }
        if (store.size() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No items yet. Add one above.");
            list.addView(empty);
        }
    }
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            v, getResources().getDisplayMetrics());
    }
}
