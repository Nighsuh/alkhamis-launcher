package com.gathin.tvhome;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Packages this launcher removed for the current user, so they can be put back with install-existing. */
final class RemovedApps {
    private static final String MAGIC = "tvhome-removed 1";
    private static final String FILE = "removed_apps.txt";

    static final class Item {
        final String pkg;
        final String label;

        Item(String pkg, String label) {
            this.pkg = pkg;
            this.label = label == null || label.isEmpty() ? pkg : label;
        }
    }

    private RemovedApps() {}

    static List<Item> read(Context ctx) {
        List<Item> out = new ArrayList<>();
        List<String> lines = TextList.read(TextList.file(ctx, FILE), MAGIC);
        if (lines == null) return out;
        for (String line : lines) {
            int tab = line.indexOf('\t');
            String pkg = tab < 0 ? line : line.substring(0, tab);
            String label = tab < 0 ? pkg : line.substring(tab + 1).replace('\t', ' ');
            if (PackageGuard.safeName(pkg)) out.add(new Item(pkg, label));
        }
        return out;
    }

    static void add(Context ctx, String pkg, String label) {
        if (!PackageGuard.safeName(pkg)) return;
        List<Item> all = read(ctx);
        List<String> lines = new ArrayList<>();
        boolean found = false;
        for (Item item : all) {
            if (item.pkg.equals(pkg)) {
                found = true;
                lines.add(pkg + "\t" + clean(label));
            } else {
                lines.add(item.pkg + "\t" + clean(item.label));
            }
        }
        if (!found) lines.add(pkg + "\t" + clean(label));
        TextList.write(TextList.file(ctx, FILE), MAGIC, lines);
    }

    static void remove(Context ctx, String pkg) {
        List<String> lines = new ArrayList<>();
        for (Item item : read(ctx)) if (!item.pkg.equals(pkg)) lines.add(item.pkg + "\t" + clean(item.label));
        TextList.write(TextList.file(ctx, FILE), MAGIC, lines);
    }

    private static String clean(String label) {
        if (label == null) return "";
        return label.replace('\n', ' ').replace('\t', ' ').trim();
    }
}
