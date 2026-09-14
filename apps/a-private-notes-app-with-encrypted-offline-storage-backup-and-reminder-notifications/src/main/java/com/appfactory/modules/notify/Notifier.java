package com.appfactory.modules.notify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;

/** Android glue: notification channels + posting (API 26+ channels). */
public final class Notifier {

    private final Context context;
    private final NotificationManager manager;
    private final String channelId;

    public Notifier(Context context, String channelId) {
        this.context = context.getApplicationContext();
        this.manager = (NotificationManager) this.context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        this.channelId = channelId;
    }

    public void ensureChannel(NotificationSpec.Channel posture) {
        if (android.os.Build.VERSION.SDK_INT < 26) return;
        int importance = posture == NotificationSpec.Channel.IMPORTANT
                ? NotificationManager.IMPORTANCE_HIGH
                : posture == NotificationSpec.Channel.SILENT
                    ? NotificationManager.IMPORTANCE_MIN
                    : NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel c = new NotificationChannel(
                channelId, channelId, importance);
        if (posture == NotificationSpec.Channel.SILENT) {
            c.enableVibration(false);
            c.setLightColor(Color.TRANSPARENT);
        }
        manager.createNotificationChannel(c);
    }

    public void post(NotificationSpec spec) {
        ensureChannel(spec.channel);
        Notification n = new Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(spec.title)
                .setContentText(spec.text)
                .setAutoCancel(true)
                .build();
        manager.notify(spec.id, n);
    }
}