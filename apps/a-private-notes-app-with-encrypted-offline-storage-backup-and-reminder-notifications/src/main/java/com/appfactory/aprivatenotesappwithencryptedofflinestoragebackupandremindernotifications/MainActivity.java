package com.appfactory.aprivatenotesappwithencryptedofflinestoragebackupandremindernotifications;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.Color;
import android.util.TypedValue;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String[] FEATURES = new String[]{"habit tracker", "notes", "crypto/secure", "offline-first"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("A private notes app with encrypted offline storage, backup and reminder notifications");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setPadding(0, 0, 0, dp(16));
        root.addView(title);

        for (String feature : FEATURES) {
            Button btn = new Button(this);
            btn.setText(feature);
            btn.setOnClickListener(v -> {
                Intent intent = new Intent(this, DetailActivity.class);
                intent.putExtra("title", feature);
                intent.putExtra("body", "A private notes app with encrypted offline storage, backup and reminder notifications — " + feature);
                startActivity(intent);
            });
            root.addView(btn);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        row.setPadding(0, dp(16), 0, 0);
        Button about = new Button(this);
        about.setText("About");
        about.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        row.addView(about);
        Button settings = new Button(this);
        settings.setText("Settings");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        row.addView(settings);
        root.addView(row);

        setContentView(scroll);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            v, getResources().getDisplayMetrics());
    }
}
