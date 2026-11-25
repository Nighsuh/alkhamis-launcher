package com.gathin.tvhome;

/**
 * Whether the Home-button helper should bring this launcher forward.
 * It never decides to turn another app off. If this launcher is already Home, it stays quiet.
 */
final class HomeButton {
    private HomeButton() {}

    /** Stock Google TV and Android TV home packages that take the Home button when the role will not move. */
    static boolean stockHome(String pkg) {
        return "com.google.android.apps.tv.launcherx".equals(pkg)
                || "com.google.android.tvlauncher".equals(pkg)
                || "com.google.android.tungsten.setupwraith".equals(pkg);
    }

    static boolean shouldOpen(boolean weAreHome, String foreground) {
        return !weAreHome && stockHome(foreground);
    }
}
