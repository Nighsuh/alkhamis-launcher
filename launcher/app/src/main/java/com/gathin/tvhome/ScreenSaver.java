package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/** What the shrine dream shows, and the idle and sleep times written to the TV when we may. */
final class ScreenSaver {
    static final String PREFS = "screensaver";
    static final String MODE_CLOCK = "clock";
    static final String MODE_SLIDES = "slides";
    static final String MODE_WALLPAPER = "wallpaper";
    static final int SLIDE_DEFAULT = 5;

    private ScreenSaver() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String mode(Context ctx) {
        String mode;
        try {
            mode = prefs(ctx).getString("mode", MODE_CLOCK);
        } catch (ClassCastException e) {
            prefs(ctx).edit().remove("mode").apply();
            mode = MODE_CLOCK;
        }
        if (MODE_SLIDES.equals(mode) || MODE_WALLPAPER.equals(mode)) return mode;
        return MODE_CLOCK;
    }

    /** On unless the owner turned it off. A value saved as text by an older build is read as on. */
    static boolean prayer(Context ctx) {
        try {
            return prefs(ctx).getBoolean("prayer", true);
        } catch (ClassCastException e) {
            prefs(ctx).edit().remove("prayer").apply();
            return true;
        }
    }
    static boolean clockOverlay(Context ctx) { return prefs(ctx).getBoolean("overlay", true); }

    static int slideSeconds(Context ctx) {
        int seconds = prefs(ctx).getInt("slide_seconds", SLIDE_DEFAULT);
        return seconds < 3 || seconds > 60 ? SLIDE_DEFAULT : seconds;
    }

    static String folder(Context ctx) { return prefs(ctx).getString("folder", ""); }

    static int idleMinutes(Context ctx) { return prefs(ctx).getInt("idle", 0); }
    static int sleepMinutes(Context ctx) { return prefs(ctx).getInt("sleep", 0); }

    static boolean canWrite(Context ctx) {
        return ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Component the system should dream. */
    static String component(Context ctx) {
        return ctx.getPackageName() + "/.ShrineDream";
    }

    /**
     * Screensaver idle and sleep timeout. Sleep of 0 is never ({@code sleep_timeout} -1).
     * Screensaver of 0 turns the dream off. Throws if the permission is missing.
     */
    static void apply(Context ctx) {
        android.content.ContentResolver resolver = ctx.getContentResolver();
        int idle = idleMinutes(ctx);
        int sleep = sleepMinutes(ctx);
        Settings.Secure.putString(resolver, "screensaver_components", component(ctx));
        Settings.Secure.putInt(resolver, "screensaver_enabled", idle > 0 ? 1 : 0);
        Settings.Secure.putInt(resolver, "screensaver_activate_on_sleep", 1);
        Settings.Secure.putInt(resolver, "screensaver_activate_on_dock", 1);
        if (idle > 0) {
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, idle * 60_000);
        }
        Settings.Secure.putInt(resolver, "sleep_timeout", sleep <= 0 ? -1 : sleep * 60_000);
    }
}
