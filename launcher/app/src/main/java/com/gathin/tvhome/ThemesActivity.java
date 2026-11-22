package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** The Themes row opens the full-screen preview. Applying still happens there, on OK. */
public final class ThemesActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivity(new Intent(this, ThemeBrowseActivity.class));
        finish();
    }
}
