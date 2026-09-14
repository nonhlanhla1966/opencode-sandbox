package com.appfactory.dailyhabittrackerwithstreaksofflinehistoryanddarkmode;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;

public final class AboutActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        TextView tv = new TextView(this);
        tv.setText("Daily habit tracker with streaks, offline history and dark mode\n\n"
            + "Built by the OpenCode AppFactory Fast Lane.\n"
            + "Completely open-source.");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        root.addView(tv);
        scroll.addView(root);
        setContentView(scroll);
    }
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            v, getResources().getDisplayMetrics());
    }
}
