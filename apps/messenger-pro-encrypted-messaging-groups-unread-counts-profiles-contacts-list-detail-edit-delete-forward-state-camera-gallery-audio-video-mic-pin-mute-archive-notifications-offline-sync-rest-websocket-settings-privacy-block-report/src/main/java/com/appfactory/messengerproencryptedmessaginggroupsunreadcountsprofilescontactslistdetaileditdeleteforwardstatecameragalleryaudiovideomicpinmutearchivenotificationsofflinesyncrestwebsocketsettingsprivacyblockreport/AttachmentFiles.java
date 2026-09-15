package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data.MessengerFileStore;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.media.MediaGateway;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Attachment;
import com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.model.Message;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Copies picked content / camera output into the app's private attachments dir. */
public final class AttachmentFiles {

    private AttachmentFiles() { }

    public static File newCameraFile(Context context) {
        File dir = new MessengerFileStore(context.getFilesDir()).attachmentsDir();
        return new File(dir, "camera_" + System.currentTimeMillis() + ".jpg");
    }

    public static File newVoiceTarget(Context context) {
        File dir = new MessengerFileStore(context.getFilesDir()).attachmentsDir();
        return new File(dir, "voice_" + System.currentTimeMillis() + ".mp4");
    }

    /** Persist a captured/drawn Bitmap as a JPEG attachment. */
    public static Attachment saveBitmap(Context context, android.graphics.Bitmap bmp) {
        if (bmp == null) return null;
        File dir = new MessengerFileStore(context.getFilesDir()).attachmentsDir();
        File target = new File(dir, "capture_" + System.currentTimeMillis() + ".jpg");
        try (FileOutputStream out = new FileOutputStream(target)) {
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out);
        } catch (Exception e) {
            return null;
        }
        return Attachment.create("att-" + target.getName(), Message.Kind.IMAGE,
                Uri.fromFile(target).toString(), "image/jpeg",
                target.length(), bmp.getWidth(), bmp.getHeight(), 0);
    }

    public static String extensionFor(String mime) {
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime == null ? "" : mime);
        return ext == null ? "bin" : ext;
    }

    /** Import any content Uri into the app dir and build an Attachment. */
    public static Attachment importFromUri(Context context, Uri uri) {
        String mime = context.getContentResolver().getType(uri);
        Message.Kind kind = MediaGateway.classifyMime(mime);
        String ext = extensionFor(mime);
        File dir = new MessengerFileStore(context.getFilesDir()).attachmentsDir();
        File target = new File(dir, "import_" + System.currentTimeMillis() + "." + ext);
        long size = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                size += n;
            }
        } catch (Exception e) {
            return null;
        }
        return Attachment.create("att-" + target.getName(), kind,
                Uri.fromFile(target).toString(), mime == null ? "application/octet-stream" : mime,
                size, 0, 0, 0);
    }
}