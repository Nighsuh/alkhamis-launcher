package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** Bumps the counter Home already watches, so the grid rebuilds after a dock or hide change. */
final class HomeEdits {
    private HomeEdits() {}

    static void notifyChanged(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        long next = prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1;
        prefs.edit().putLong(Config.SETTINGS_REVISION_KEY, next).commit();
    }
}
