package com.gathin.tvhome;

import android.content.Context;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Public Downloads: MediaStore on Android 10, and the move out of the private folder. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30)
public class TransferFilesTest {
    @Test
    public void namesDoNotCollide() {
        Set<String> taken = new HashSet<>();
        taken.add("clip.apk");
        assertEquals("clip (1).apk", TransferFiles.uniqueName("clip.apk", taken));
        taken.add("clip (1).apk");
        assertEquals("clip (2).apk", TransferFiles.uniqueName("clip.apk", taken));
    }

    @Test
    public void migrationMovesPrivateFilesIntoDownloads() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        File old = ctx.getExternalFilesDir("transfer");
        assertNotNull(old);
        if (!old.isDirectory()) old.mkdirs();
        File[] leftover = old.listFiles();
        if (leftover != null) for (File file : leftover) file.delete();
        File clip = new File(old, "kept.mp4");
        try (FileOutputStream out = new FileOutputStream(clip)) {
            out.write(new byte[] {1, 2, 3, 4, 5});
        }
        clip.setLastModified(1_763_000_000_000L);
        File note = new File(old, ".notes.txt");
        try (FileOutputStream out = new FileOutputStream(note)) {
            out.write("hi\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        TransferFiles.migrate(ctx);
        assertFalse(clip.exists());
        assertTrue(note.isFile());
        List<TransferFiles.Item> items = TransferFiles.open(ctx).list();
        TransferFiles.Item found = null;
        for (TransferFiles.Item item : items) if ("kept.mp4".equals(item.name)) found = item;
        assertNotNull(found);
        assertEquals(5, found.size);
        assertNotNull(found.uri);
        assertEquals("content", found.uri.getScheme());
        assertTrue(found.uri.toString().startsWith(Uri.parse("content://media/external/downloads").toString())
                || found.uri.toString().startsWith("content:"));
        TransferFiles.Dir dir = TransferFiles.open(ctx);
        TransferFiles.Out again = dir.begin("kept.mp4");
        again.stream.write(new byte[] {9});
        again.commit();
        assertEquals("kept (1).mp4", again.name);
        assertTrue(dir.delete("kept (1).mp4"));
        assertNull(dir.find("kept (1).mp4"));
        assertNull(dir.find("../secret.txt"));
    }
}
