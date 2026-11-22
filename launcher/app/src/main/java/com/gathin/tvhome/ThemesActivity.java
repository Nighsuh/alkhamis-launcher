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
        Intent home = new Intent(this, MainActivity.class);
        home.putExtra(ThemeStage.EXTRA, true);
        String id = getIntent().getStringExtra(ThemeStage.EXTRA_ID);
        if (id != null) home.putExtra(ThemeStage.EXTRA_ID, id);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }
}
