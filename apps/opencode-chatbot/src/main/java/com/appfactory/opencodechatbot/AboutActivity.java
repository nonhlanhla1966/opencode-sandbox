package com.appfactory.opencodechatbot;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import com.appfactory.opencodechatbot.data.ChatSettings;
import com.appfactory.opencodechatbot.util.AppTheme;

/** About / credits screen. */
public final class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ChatSettings settings = new ChatSettings(this);
        AppTheme.apply(this, settings);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
}