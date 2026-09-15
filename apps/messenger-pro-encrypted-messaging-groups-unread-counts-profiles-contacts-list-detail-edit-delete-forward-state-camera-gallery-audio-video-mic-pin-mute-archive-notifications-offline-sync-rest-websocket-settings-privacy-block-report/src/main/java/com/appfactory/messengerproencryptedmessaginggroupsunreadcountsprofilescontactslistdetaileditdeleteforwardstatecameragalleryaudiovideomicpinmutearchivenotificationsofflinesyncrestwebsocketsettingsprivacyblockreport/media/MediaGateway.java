package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.media;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.logic.MediaCachePolicy;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;
import com.appfactory.modules.media.Image;

/** Pure media classification + sizing math over the vendored media module. */
public final class MediaGateway {

    private MediaGateway() { }

    public static Message.Kind classifyMime(String mime) {
        if (mime == null) return Message.Kind.DOCUMENT;
        String m = mime.toLowerCase();
        if (m.startsWith("image/")) return Message.Kind.IMAGE;
        if (m.startsWith("video/")) return Message.Kind.VIDEO;
        if (m.startsWith("audio/")) return Message.Kind.AUDIO;
        if ("text/plain".equals(m)) return Message.Kind.DOCUMENT;
        return Message.Kind.DOCUMENT;
    }

    public static int sampleSizeFor(int width, int height) {
        return Image.sampleSize(width, height, 2048);
    }

    public static String aspectRatio(int width, int height) {
        return Image.aspectRatio(width, height);
    }

    public static boolean validDimension(int width, int height) {
        return Image.isValidDimension(width, height);
    }

    /** Meter-adjusted auto-download guard. */
    public static boolean canAutoDownload(Message m,
                                          MessengerDownloadSetting setting,
                                          boolean onMetered) {
        boolean wifiOnly = setting == MessengerDownloadSetting.WIFI_ONLY;
        return new MediaCachePolicy(0).autoDownloadAllowed(m,
                wifiOnly ? MediaCachePolicy.Meter.WIFI_ONLY : MediaCachePolicy.Meter.DATA_OK,
                onMetered);
    }

    public enum MessengerDownloadSetting {
        WIFI_ONLY, ALWAYS
    }
}