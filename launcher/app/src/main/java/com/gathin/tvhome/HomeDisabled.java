package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Packages this launcher disabled while switching Home. The list is kept so a failed switch can
 * turn them back on, and so Settings can do it again after a reinstall once debugging is connected.
 * The default list is the Google TV packages 3.5.4 could disable. Enabling one that is already on
 * does not change it.
 */
final class HomeDisabled {
    private static final String PREF = "home_disabled_packages";
    private static final String FILE = "home-disabled";

    private HomeDisabled() {}

    /** What a restore turns back on when this install has no record of its own. */
    static List<String> recoveryDefaults() {
        List<String> names = new ArrayList<>();
        names.add(HomeSwitch.LAUNCHERX);
        names.add(HomeSwitch.TV_LAUNCHER);
        names.add(HomeSwitch.SETUP);
        names.add(HomeSwitch.RECOMMENDATIONS);
        for (String assistant : HomeSwitch.ASSISTANT) names.add(assistant);
        return names;
    }

    static void remember(Context ctx, List<String> packages) {
        if (ctx == null || packages == null || packages.isEmpty()) return;
        LinkedHashSet<String> all = new LinkedHashSet<>(read(ctx));
        for (String pkg : packages) {
            if (pkg != null && !pkg.isEmpty()) all.add(pkg);
        }
        prefs(ctx).edit().putString(PREF, join(new ArrayList<>(all))).apply();
    }

    static void forget(Context ctx) {
        if (ctx == null) return;
        prefs(ctx).edit().remove(PREF).apply();
    }

    static List<String> read(Context ctx) {
        String raw = ctx == null ? "" : prefs(ctx).getString(PREF, "");
        return split(raw);
    }

    /** Saved packages, or the 3.5.4 set when this install has none. */
    static List<String> toRestore(Context ctx) {
        List<String> saved = read(ctx);
        return saved.isEmpty() ? recoveryDefaults() : saved;
    }

    static String join(List<String> packages) {
        StringBuilder out = new StringBuilder();
        for (String pkg : packages) {
            if (pkg == null || pkg.isEmpty()) continue;
            if (out.length() > 0) out.append(',');
            out.append(pkg);
        }
        return out.toString();
    }

    static List<String> split(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String part : raw.split(",")) {
            String pkg = part.trim();
            if (!pkg.isEmpty()) out.add(pkg);
        }
        return out;
    }

    /** Turn the saved packages back on. Asks for debugging when it is not already connected. */
    static void restore(Context ctx) {
        if (!(ctx instanceof android.app.Activity)) return;
        android.app.Activity activity = (android.app.Activity) ctx;
        if (!LocalAdb.get(activity).isConnected()) {
            DebugGate.request(activity, "restore-home", activity.getPackageName(),
                    activity.getString(R.string.restore_home));
            return;
        }
        enableSaved(activity, null);
    }

    /** Used once debugging is connected. {@code done} clears the debugging dialog. */
    static void enableSaved(Context ctx, LocalAdb.Result done) {
        enableAt(ctx, toRestore(ctx), 0, done);
    }

    /** First start, only when debugging is already connected. Never opens the debugging dialog. */
    static void restoreIfConnected(Context ctx) {
        try {
            if (ctx == null || !LocalAdb.get(ctx).isConnected()) return;
            List<String> saved = read(ctx);
            if (!saved.isEmpty()) enableSaved(ctx, null);
        } catch (RuntimeException ignored) {
            // A bad debugging connection must not take Home down with it.
        }
    }

    private static void enableAt(Context ctx, List<String> packages, int index, LocalAdb.Result done) {
        if (index >= packages.size()) {
            forget(ctx);
            if (done != null) done.onResult(true, ctx.getString(R.string.restore_home_done));
            else if (ctx instanceof android.app.Activity) {
                Toast.makeText(ctx, R.string.restore_home_done, Toast.LENGTH_LONG).show();
            }
            return;
        }
        String pkg = packages.get(index);
        if (!PackageGuard.safeName(pkg)) {
            enableAt(ctx, packages, index + 1, done);
            return;
        }
        LocalAdb.get(ctx).runRaw(HomeSwitch.enable(pkg), (ok, message) ->
                enableAt(ctx, packages, index + 1, done));
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
