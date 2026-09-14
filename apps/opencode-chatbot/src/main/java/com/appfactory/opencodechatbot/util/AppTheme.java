package com.appfactory.opencodechatbot.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import com.appfactory.opencodechatbot.R;
import com.appfactory.opencodechatbot.data.ChatSettings;

/** Applies the user-selected light/dark/system theme before content is set. */
public final class AppTheme {

    public static final int THEME_LIGHT = R.style.Theme_OpencodeChatbot;
    public static final int THEME_DARK = R.style.Theme_OpencodeChatbot_Dark;

    private AppTheme() {
    }

    public static void apply(Activity activity, ChatSettings settings) {
        String theme = settings.getTheme();
        int style;
        if (ChatSettings.THEME_DARK.equals(theme)) {
            style = THEME_DARK;
        } else if (ChatSettings.THEME_LIGHT.equals(theme)) {
            style = THEME_LIGHT;
        } else {
            style = isNight(activity) ? THEME_DARK : THEME_LIGHT;
        }
        activity.setTheme(style);
    }

    public static boolean isNight(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Resolve a theme attribute color to its int value. */
    public static int resolveColor(Context context, int attrRes) {
        android.util.TypedValue tv = new android.util.TypedValue();
        context.getTheme().resolveAttribute(attrRes, tv, true);
        return tv.data;
    }
}