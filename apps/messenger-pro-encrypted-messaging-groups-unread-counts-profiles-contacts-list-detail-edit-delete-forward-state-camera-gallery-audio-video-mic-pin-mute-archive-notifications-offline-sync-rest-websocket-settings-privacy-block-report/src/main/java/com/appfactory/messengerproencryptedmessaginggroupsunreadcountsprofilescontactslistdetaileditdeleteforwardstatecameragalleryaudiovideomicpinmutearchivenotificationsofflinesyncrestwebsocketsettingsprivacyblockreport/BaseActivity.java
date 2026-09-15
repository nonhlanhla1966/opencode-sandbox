package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Toast;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerDbHelper;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerRepository;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.network.SyncEngine;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.settings.MessengerSettings;

/** Common theme + wiring for every screen. */
public abstract class BaseActivity extends Activity {

    private static final int REQ_PERMS = 4001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this, app().settings());
        super.onCreate(savedInstanceState);
    }

    protected App app() {
        return (App) getApplicationContext();
    }

    protected MessengerSettings settings() {
        return app().settings();
    }

    protected MessengerRepository repo() {
        return app().repo();
    }

    protected SyncEngine engine() {
        return app().syncEngine();
    }

    protected MessengerDbHelper db() {
        return app().db();
    }

    protected int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                v, getResources().getDisplayMetrics());
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void requestAudioPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERMS);
        }
    }
}