package com.gathin.tvhome;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

/** Recreates an open screen when Language changes, so the new locale is not stuck on the old one. */
public final class LocaleApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new Recreate());
    }

    private static long generation(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getLong(LauncherLocale.GENERATION, 0);
    }

    private static final class Recreate implements ActivityLifecycleCallbacks {
        @Override
        public void onActivityStarted(Activity activity) {
            View decor = activity.getWindow().getDecorView();
            if (decor.getTag(R.id.localeGeneration) == null) {
                decor.setTag(R.id.localeGeneration, generation(activity));
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            View decor = activity.getWindow().getDecorView();
            long now = generation(activity);
            Object born = decor.getTag(R.id.localeGeneration);
            if (born instanceof Long && ((Long) born) != now) {
                decor.setTag(R.id.localeGeneration, now);
                activity.recreate();
            }
        }

        @Override public void onActivityCreated(Activity activity, Bundle state) {}
        @Override public void onActivityPaused(Activity activity) {}
        @Override public void onActivityStopped(Activity activity) {}
        @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
        @Override public void onActivityDestroyed(Activity activity) {}
    }
}
