package com.appfactory.modules.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Safe filesystem helpers + ZIP backup/restore (pure JVM). */
public final class FileUtil {

    private FileUtil() { }

    /** Join root+child with traversal protection. */
    public static File safeChild(File root, String child) throws IOException {
        if (child.startsWith("/") || child.contains("..")) {
            throw new IOException("unsafe child path: " + child);
        }
        return new File(root, child);
    }

    public static List<File> listRecursive(File dir) {
        List<File> out = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return out;
        for (File c : children) {
            if (c.isDirectory()) out.addAll(listRecursive(c));
            else out.add(c);
        }
        return out;
    }

    /** Zip a directory tree (relative entries). Entries are deterministic. */
    public static void zipDir(File dir, File zipFile) throws IOException {
        List<File> files = listRecursive(dir);
        Collections.sort(files, (a, b) -> a.getPath().compareTo(b.getPath()));
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File f : files) {
                ZipEntry e = new ZipEntry(dir.toPath().relativize(f.toPath()).toString());
                zos.putNextEntry(e);
                zos.write(Files.readAllBytes(f.toPath()));
                zos.closeEntry();
            }
        }
    }

    /** Restore a zip created by zipDir into target. Never writes outside. */
    public static void unzip(File zipFile, File target) throws IOException {
        if (!target.exists() && !target.mkdirs()) {
            throw new IOException("cannot create target dir");
        }
        try (ZipInputStream zis = new ZipInputStream(
                     new java.io.FileInputStream(zipFile))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                File out = safeChild(target, e.getName());
                out.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
                }
            }
        }
    }
}