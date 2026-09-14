package com.appfactory.aprivatenotesappwithencryptedofflinestoragebackupandremindernotifications;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;

public final class DetailActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String title = getIntent().getStringExtra("title");
        String body  = getIntent().getStringExtra("body");
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView tv = new TextView(this);
        tv.setText(title != null ? title : "Detail");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setPadding(0, 0, 0, dp(12));
        root.addView(tv);

        TextView bodyTv = new TextView(this);
        bodyTv.setText(body != null ? body : "");
        bodyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        root.addView(bodyTv);

        scroll.addView(root);
        setContentView(scroll);
    }
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            v, getResources().getDisplayMetrics());
    }
}
