package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A small private text file: a magic header, then one entry per line. Missing file is "never set". */
final class TextList {
    private static final String TAG = "TvHome.List";

    private TextList() {}

    static File file(Context ctx, String name) {
        return new File(ctx.getFilesDir(), name);
    }

    /** Entries after a matching header, or null when the file is missing or not ours. */
    static List<String> read(File file, String magic) {
        if (file == null || !file.exists()) return null;
        try {
            String text = new String(new AtomicFile(file).readFully(), StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            if (lines.length == 0 || !lines[0].equals(magic)) return null;
            List<String> out = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                if (!lines[i].isEmpty()) out.add(lines[i]);
            }
            return out;
        } catch (IOException e) {
            Log.w(TAG, "cannot read " + file.getName());
            return null;
        }
    }

    static boolean write(File file, String magic, List<String> lines) {
        StringBuilder sb = new StringBuilder(magic).append('\n');
        for (String line : lines) sb.append(line).append('\n');
        AtomicFile af = new AtomicFile(file);
        FileOutputStream out = null;
        try {
            out = af.startWrite();
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            af.finishWrite(out);
            return true;
        } catch (IOException e) {
            if (out != null) af.failWrite(out);
            Log.w(TAG, "cannot write " + file.getName());
            return false;
        }
    }

    static void delete(File file) {
        if (file != null) file.delete();
    }
}
