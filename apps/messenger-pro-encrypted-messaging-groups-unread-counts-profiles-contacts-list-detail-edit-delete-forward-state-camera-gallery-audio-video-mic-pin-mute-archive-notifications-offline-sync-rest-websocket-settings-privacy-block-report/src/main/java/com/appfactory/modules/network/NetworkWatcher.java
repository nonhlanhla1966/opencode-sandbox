package com.appfactory.modules.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;

/** Android glue: feed real connectivity into the pure Network state machine. */
public final class NetworkWatcher {
    private final Network state;
    private final ConnectivityManager cm;

    public NetworkWatcher(Context context, Network state) {
        this.state = state;
        this.cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public boolean isOnlineNow() {
        try {
            android.net.Network active = cm.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(active);
            return nc != null && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Sync one-shot: sets ONLINE/OFFLINE on the state machine. */
    public void refresh() {
        state.update(isOnlineNow());
    }

    /** (Android 7.0+) Register callback; returns false when unsupported. */
    public boolean startWatching() {
        if (Build.VERSION.SDK_INT < 24) return false;
        try {
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(android.net.Network network) { state.update(true); }
                @Override public void onLost(android.net.Network network) { state.update(false); }
            });
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}