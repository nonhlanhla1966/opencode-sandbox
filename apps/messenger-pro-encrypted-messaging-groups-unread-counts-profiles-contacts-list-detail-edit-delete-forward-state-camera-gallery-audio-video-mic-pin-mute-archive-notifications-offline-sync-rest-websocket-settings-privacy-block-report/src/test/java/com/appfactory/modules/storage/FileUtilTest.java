package com.appfactory.modules.storage;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class FileUtilTest {
    @Test public void rejectsTraversal() {
        try {
            FileUtil.safeChild(new File("/tmp"), "../evil");
            fail("should reject traversal");
        } catch (java.io.IOException expected) { }
        try {
            FileUtil.safeChild(new File("/tmp"), "/abs/path");
            fail("should reject absolute");
        } catch (java.io.IOException expected) { }
        try {
            assertEquals(new File("/tmp", "ok.txt"),
                    FileUtil.safeChild(new File("/tmp"), "ok.txt"));
        } catch (java.io.IOException unexpected) {
            fail("should accept a safe child path");
        }
    }
    @Test public void naiveContentWrite() throws Exception {
        File dir = new File("build/tmp/futil");
        dir.mkdirs();
        java.nio.file.Files.write(new File(dir, "a.txt").toPath(), "hello".getBytes());
        assertEquals(1, FileUtil.listRecursive(dir).size());
    }
    @Test public void zipRoundTrip() throws Exception {
        File dir = new File("build/tmp/fzip");
        File out = new File("build/tmp/backup.zip");
        out.delete();
        dir.mkdirs();
        java.nio.file.Files.write(new File(dir, "one.txt").toPath(), "1".getBytes());
        java.nio.file.Files.write(new File(dir, "two.txt").toPath(), "2".getBytes());
        FileUtil.zipDir(dir, out);
        assertTrue(out.exists() && out.length() > 0);
        File rest = new File("build/tmp/frestore");
        rest.delete();
        FileUtil.unzip(out, rest);
        assertTrue(new File(rest, "one.txt").exists());
        assertEquals("2", new String(java.nio.file.Files.readAllBytes(
                new File(rest, "two.txt").toPath())));
    }
}