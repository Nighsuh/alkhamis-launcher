package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Clock, a 5-second slideshow, or the wallpaper, plus idle and sleep timers. */
public final class ScreensaverActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        ((TextView) findViewById(R.id.title)).setText(R.string.screensaver_title);
        ((TextView) findViewById(R.id.hint)).setText(R.string.screensaver_hint);
        LinearLayout rows = findViewById(R.id.rows);
        String mode = ScreenSaver.mode(this);
        add(rows, getString(R.string.screensaver_clock), ScreenSaver.MODE_CLOCK.equals(mode), () -> mode(ScreenSaver.MODE_CLOCK));
        add(rows, getString(R.string.screensaver_slides), ScreenSaver.MODE_SLIDES.equals(mode), () -> mode(ScreenSaver.MODE_SLIDES));
        add(rows, getString(R.string.screensaver_wallpaper), ScreenSaver.MODE_WALLPAPER.equals(mode), () -> mode(ScreenSaver.MODE_WALLPAPER));
        boolean prayer = ScreenSaver.prayer(this);
        add(rows, getString(R.string.screensaver_prayer, getString(prayer ? R.string.on : R.string.off)), prayer, () -> {
            ScreenSaver.prefs(this).edit().putBoolean("prayer", !prayer).apply();
            recreate();
        });
        int idle = ScreenSaver.idleMinutes(this);
        add(rows, getString(R.string.screensaver_idle, getString(R.string.screensaver_off)), idle == 0, () -> idle(0));
        for (int minutes : new int[]{5, 15, 30, 60}) {
            final int value = minutes;
            add(rows, getString(R.string.screensaver_idle, getString(R.string.screensaver_minutes, value)),
                    idle == value, () -> idle(value));
        }
        int sleep = ScreenSaver.sleepMinutes(this);
        add(rows, getString(R.string.screensaver_sleep, getString(R.string.screensaver_never)), sleep == 0, () -> sleep(0));
        for (int minutes : new int[]{15, 30, 60}) {
            final int value = minutes;
            add(rows, getString(R.string.screensaver_sleep, getString(R.string.screensaver_minutes, value)),
                    sleep == value, () -> sleep(value));
        }
        add(rows, getString(R.string.screensaver_preview), false, () ->
                startActivity(new Intent(this, DreamPreviewActivity.class)));
        add(rows, getString(R.string.screensaver_apply), false, this::apply);
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        DebugGate.onResume(this);
    }

    @Override
    protected void onPause() {
        DebugGate.onPause(this);
        super.onPause();
    }

    private void mode(String mode) {
        ScreenSaver.prefs(this).edit().putString("mode", mode).putInt("slide_seconds", 5).apply();
        recreate();
    }

    private void idle(int minutes) {
        ScreenSaver.prefs(this).edit().putInt("idle", minutes).apply();
        recreate();
    }

    private void sleep(int minutes) {
        ScreenSaver.prefs(this).edit().putInt("sleep", minutes).apply();
        recreate();
    }

    private void apply() {
        try {
            ScreenSaver.apply(this);
            Toast.makeText(this, R.string.screensaver_applied, Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            DebugGate.request(this, "sleep", "", getString(R.string.screensaver_title));
        }
    }

    private void add(LinearLayout rows, String label, boolean selected, Runnable action) {
        TextView row = new TextView(this);
        row.setText(selected ? "✓  " + label : label);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(18);
        row.setFocusable(true);
        row.setClickable(true);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad / 2, pad, pad / 2);
        row.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 3;
        row.setOnClickListener(v -> action.run());
        rows.addView(row, lp);
    }
}
