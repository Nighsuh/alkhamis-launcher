package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

/** How to turn the Home button helper on, and a switch that turns it off. */
public final class HomeButtonActivity extends Activity {
    private TextView state;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_home_button);
        state = findViewById(R.id.homeButtonState);
        findViewById(R.id.homeButtonOpen).setOnClickListener(v -> openAccessibility());
        findViewById(R.id.homeButtonOff).setOnClickListener(v -> turnOff());
        findViewById(R.id.homeButtonOpen).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        state.setText(enabled() ? R.string.home_button_on_state : R.string.home_button_off_state);
    }

    private void openAccessibility() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, R.string.home_button_settings_missing, Toast.LENGTH_LONG).show();
        }
    }

    private void turnOff() {
        if (HomeButtonService.running != null) {
            HomeButtonService.running.disableSelf();
            Toast.makeText(this, R.string.home_button_off_state, Toast.LENGTH_SHORT).show();
        } else if (!enabled()) {
            Toast.makeText(this, R.string.home_button_off_state, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.home_button_off_hint, Toast.LENGTH_LONG).show();
        }
        state.setText(R.string.home_button_off_state);
    }

    private boolean enabled() {
        String list = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return list != null && list.contains(getPackageName() + "/.HomeButtonService");
    }
}
