package com.gathin.tvhome;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Packages the owner hid from Home. Separate from the theme's hidden_apps, and reversible. */
final class UserHidden {
    private static final String MAGIC = "tvhome-user-hidden 1";
    private static final String FILE = "user_hidden.txt";

    private UserHidden() {}

    static List<String> read(Context ctx) {
        List<String> lines = TextList.read(TextList.file(ctx, FILE), MAGIC);
        if (lines == null) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String line : lines) if (PackageGuard.safeName(line)) out.add(line);
        return out;
    }

    static boolean add(Context ctx, String pkg) {
        if (!PackageGuard.safeName(pkg)) return false;
        List<String> all = read(ctx);
        if (!all.contains(pkg)) all.add(pkg);
        return TextList.write(TextList.file(ctx, FILE), MAGIC, all);
    }

    static boolean remove(Context ctx, String pkg) {
        List<String> all = read(ctx);
        all.remove(pkg);
        return TextList.write(TextList.file(ctx, FILE), MAGIC, all);
    }
}
