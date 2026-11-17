package com.gathin.tvhome;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * The featured dock the owner set on the TV. Stored beside the theme, not inside it, so a later
 * launch that still has the bundled config.json does not put the shrine dock back.
 * A missing file means "use the theme". An empty list means the owner cleared it.
 */
final class UserDock {
    static final String MAGIC = "tvhome-user-dock 1";
    private static final String FILE = "user_dock.txt";

    private UserDock() {}

    /** Null when the owner has never set a dock. */
    static List<String> read(Context ctx) {
        List<String> lines = TextList.read(TextList.file(ctx, FILE), MAGIC);
        if (lines == null) return null;
        List<String> out = new ArrayList<>();
        for (String line : lines) if (PackageGuard.safeName(line)) out.add(line);
        return out;
    }

    static boolean write(Context ctx, List<String> packages) {
        List<String> clean = new ArrayList<>();
        for (String pkg : packages) if (PackageGuard.safeName(pkg) && !clean.contains(pkg)) clean.add(pkg);
        return TextList.write(TextList.file(ctx, FILE), MAGIC, clean);
    }

    static void clear(Context ctx) {
        TextList.delete(TextList.file(ctx, FILE));
    }

    /** For tests: null when the text is not a dock file. */
    static List<String> decode(String text) {
        if (text == null) return null;
        String[] lines = text.split("\n", -1);
        if (lines.length == 0 || !MAGIC.equals(lines[0])) return null;
        List<String> out = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            if (!PackageGuard.safeName(lines[i])) return null;
            out.add(lines[i]);
        }
        return out;
    }
}
