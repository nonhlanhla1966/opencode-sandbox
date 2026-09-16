package com.appfactory.aplantidentificationappthattakesaphotoofaleafwiththecameraidentifiestheplantspeciesstorestheidentificationhistoryofflineinalocaldatabaseandshowscaretipsforeachidentifiedplant;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.TypedValue;

public final class SettingsActivity extends Activity {
    SharedPreferences prefs;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        TextView tv = new TextView(this);
        tv.setText("Settings");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setPadding(0, 0, 0, dp(12));
        root.addView(tv);

        scroll.addView(root);
        setContentView(scroll);
    }
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
            v, getResources().getDisplayMetrics());
    }
}
