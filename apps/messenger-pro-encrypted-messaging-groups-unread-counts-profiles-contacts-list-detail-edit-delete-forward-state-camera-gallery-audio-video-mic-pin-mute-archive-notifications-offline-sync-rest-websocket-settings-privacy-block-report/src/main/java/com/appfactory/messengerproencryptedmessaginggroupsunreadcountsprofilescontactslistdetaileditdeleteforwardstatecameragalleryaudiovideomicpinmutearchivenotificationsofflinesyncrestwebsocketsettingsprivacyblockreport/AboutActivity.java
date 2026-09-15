package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** About / credits screen. */
public final class AboutActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView version = new TextView(this);
        version.setText("Version " + versionName());
        version.setTextSize(13);
        version.setPadding(0, 0, 0, dp(12));
        root.addView(version);

        TextView body = new TextView(this);
        body.setText(R.string.about_body);
        body.setTextSize(15);
        root.addView(body);

        root.addView(label(getString(R.string.label_features)));

        TextView status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(4), 0, dp(8));
        String backend = settings().apiBaseUrl().isEmpty()
                ? getString(R.string.label_backend_mock)
                : getString(R.string.label_backend_rest);
        status.setText(getString(R.string.label_backend) + "\n" + backend
                + "\n" + getString(R.string.about_security_line));
        root.addView(status);

        Button backBtn = new Button(this);
        backBtn.setText(R.string.action_back);
        backBtn.setOnClickListener(v -> finish());
        root.addView(backBtn);

        scroll.addView(root);
        setContentView(scroll);
    }

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }
}