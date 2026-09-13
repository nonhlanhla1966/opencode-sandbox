package com.appfactory.notes;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public final class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView version = findViewById(R.id.versionLine);
        String appName = getString(R.string.app_name);
        String v = "";
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        ((TextView) findViewById(R.id.aboutTitle)).setText(appName);
        ((TextView) findViewById(R.id.aboutBody)).setText(getString(R.string.about_body, appName));
        version.setText(getString(R.string.about_version_format, v));
        findViewById(R.id.aboutBack).setOnClickListener(v1 -> finish());
    }
}