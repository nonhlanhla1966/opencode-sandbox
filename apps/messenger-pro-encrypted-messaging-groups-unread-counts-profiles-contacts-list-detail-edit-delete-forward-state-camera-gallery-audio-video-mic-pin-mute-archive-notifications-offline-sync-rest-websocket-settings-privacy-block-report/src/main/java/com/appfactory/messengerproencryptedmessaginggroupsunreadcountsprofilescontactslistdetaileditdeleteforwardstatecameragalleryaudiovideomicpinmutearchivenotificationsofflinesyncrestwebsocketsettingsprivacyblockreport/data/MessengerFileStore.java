package com.appfactory.messengerproencryptedmessaginggroupsunreadcountsprofilescontactslistdetaileditdeleteforwardstatecameragalleryaudiovideomicpinmutearchivenotificationsofflinesyncrestwebsocketsettingsprivacyblockreport.data;

import com.appfactory.modules.storage.FileUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * JSON snapshot persistence on the app's internal storage plus ZIP export /
 * import (no storage permissions needed). Deterministic layout:
 * filesDir/snapshot.json and filesDir/attachments/** .
 */
public final class MessengerFileStore implements MessengerRepository.Store {

    public static final String SNAPSHOT_FILE = "snapshot.json";
    private final File dir;

    public MessengerFileStore(File appFilesDir) {
        this.dir = appFilesDir;
        if (!dir.exists()) dir.mkdirs();
    }

    public File file(String name) {
        try {
            return FileUtil.safeChild(dir, name);
        } catch (IOException e) {
            return new File(dir, name);
        }
    }

    @Override
    public void save(String json) {
        try {
            Files.write(file(SNAPSHOT_FILE).toPath(),
                    json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // persistence is best-effort; in-memory state remains usable
        }
    }

    public String readSnapshot() {
        try {
            File f = file(SNAPSHOT_FILE);
            if (!f.exists()) return null;
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public long snapshotBytes() {
        File f = file(SNAPSHOT_FILE);
        return f.exists() ? f.length() : 0;
    }

    public File attachmentsDir() {
        File d = file("attachments");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public File exportZip(File outFile) throws IOException {
        FileUtil.zipDir(dir, outFile);
        return outFile;
    }

    /** Import a zip produced by {@link #exportZip}. */
    public static void importZip(File zipFile, File targetDir) throws IOException {
        FileUtil.unzip(zipFile, targetDir);
    }
}