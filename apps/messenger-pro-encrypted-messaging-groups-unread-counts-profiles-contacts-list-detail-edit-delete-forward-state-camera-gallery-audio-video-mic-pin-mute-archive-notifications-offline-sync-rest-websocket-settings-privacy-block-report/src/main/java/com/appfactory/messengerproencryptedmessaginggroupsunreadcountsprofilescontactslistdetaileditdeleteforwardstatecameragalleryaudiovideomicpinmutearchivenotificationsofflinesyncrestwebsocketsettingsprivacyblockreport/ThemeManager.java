package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.app.Activity;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.settings.MessengerSettings;
import com.appfactory.modules.theme.Theme;

/** Deterministic theme resolution + seeded avatar colors. */
public final class ThemeManager {

    private ThemeManager() { }

    public static void apply(Activity activity, MessengerSettings settings) {
        switch (settings.themeMode()) {
            case LIGHT:
                activity.setTheme(R.style.Theme_AppFactory_Light);
                break;
            case DARK:
                activity.setTheme(R.style.Theme_AppFactory_Dark);
                break;
            default:
                activity.setTheme(R.style.Theme_AppFactory);
                break;
        }
    }

    public static int avatarColor(String seedId) {
        return Theme.colorFromSeed(seedId == null ? "x" : seedId, 0xFF128C7E);
    }
}