package com.gathin.tvhome;

import android.content.pm.ApplicationInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * What this launcher is willing to do to a package. Names that are not a plain package id never
 * reach a shell command. Packages the TV needs in order to boot, change settings, or undo a
 * deletion are refused even when debugging is on.
 */
final class PackageGuard {
    static final int USER = 0, UPDATED_SYSTEM = 1, SYSTEM = 2;

    private static final Set<String> PROTECTED = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.tv.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.android.shell",
            "com.android.providers.settings"
    )));

    private PackageGuard() {}

    /** A package id with no spaces, quotes, or shell punctuation. */
    static boolean safeName(String pkg) {
        if (pkg == null || pkg.length() > 200) return false;
        int dots = 0;
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '.';
            if (!ok) return false;
            if (c == '.') {
                if (i == 0 || i == pkg.length() - 1 || pkg.charAt(i - 1) == '.') return false;
                dots++;
            }
        }
        return dots >= 1 && pkg.charAt(0) != '.';
    }

    /** The launcher itself, or a package that would brick settings, the store, or a later restore. */
    static boolean blocked(String self, String pkg) {
        return pkg == null || pkg.equals(self) || PROTECTED.contains(pkg);
    }

    static int kind(int flags) {
        boolean system = (flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean updated = (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        if (!system) return USER;
        return updated ? UPDATED_SYSTEM : SYSTEM;
    }

    /** {@code pm uninstall -k --user 0}. Keeps the app's data so a later restore can bring it back. */
    static String uninstallCommand(String pkg) {
        if (!safeName(pkg)) throw new IllegalArgumentException("package");
        return "pm uninstall -k --user 0 " + pkg;
    }

    /** {@code pm disable-user --user 0}. The name has already passed {@link #safeName}. */
    static String disableCommand(String pkg) {
        if (!safeName(pkg)) throw new IllegalArgumentException("package");
        return "pm disable-user --user 0 " + pkg;
    }

    /** {@code cmd package install-existing}. The name has already passed {@link #safeName}. */
    static String restoreCommand(String pkg) {
        if (!safeName(pkg)) throw new IllegalArgumentException("package");
        return "cmd package install-existing --user 0 " + pkg;
    }
}
