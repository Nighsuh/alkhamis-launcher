package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/** Prayer times, place list and calculation method. Also the leanback card. */
public final class PrayerActivity extends Activity {
    private static final String SCREEN = "screen";
    private LinearLayout rows;
    private TextView status;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        ((TextView) findViewById(R.id.title)).setText(R.string.prayer_title);
        ((TextView) findViewById(R.id.hint)).setText(R.string.prayer_hint);
        rows = findViewById(R.id.rows);
        status = new TextView(this);
        status.setTextColor(0xFFE6C57A);
        status.setTextSize(16);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        String screen = getIntent().getStringExtra(SCREEN);
        if ("place".equals(screen)) showPlaces();
        else if ("method".equals(screen)) showMethods();
        else showTimes();
    }

    private void showTimes() {
        rows.addView(note(ClockStyle.dateLine(this)));
        rows.addView(note(PrayerData.place(this) + "  ·  " + methodLabel(PrayerData.method(this))));
        addRow(getString(R.string.prayer_place), () -> open("place"));
        addRow(getString(R.string.prayer_method), () -> open("method"));
        status.setText(R.string.prayer_loading);
        rows.addView(status);
        paintCached();
        new Thread(() -> {
            boolean ok = PrayerData.fetch(this);
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (!ok && status.getText().length() > 0 && rows.indexOfChild(status) >= 0 && !hasTimes()) {
                    status.setText(R.string.prayer_failed);
                } else {
                    paintCached();
                }
            });
        }, "prayer-page").start();
        focusFirst();
    }

    private boolean hasTimes() {
        return new File(getFilesDir(), "prayer-line.json").isFile();
    }

    private void paintCached() {
        File file = new File(getFilesDir(), "prayer-line.json");
        if (!file.isFile()) return;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = in.read(buf);
            JSONObject o = new JSONObject(new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8));
            if (rows.indexOfChild(status) >= 0) rows.removeView(status);
            for (int i = rows.getChildCount() - 1; i >= 0; i--) {
                if ("time".equals(rows.getChildAt(i).getTag())) rows.removeViewAt(i);
            }
            int[] labels = {R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_asr,
                    R.string.prayer_maghrib, R.string.prayer_isha};
            for (int i = 0; i < PrayerData.NAMES.length; i++) {
                int minutes = o.optInt(PrayerData.NAMES[i], -1);
                if (minutes < 0) continue;
                TextView line = note(getString(labels[i]) + "   " + ClockStyle.formatMinutes(this, minutes));
                line.setTag("time");
                line.setTextSize(22);
                rows.addView(line);
            }
        } catch (Exception ignored) { }
    }

    private void showPlaces() {
        boolean ar = "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        String current = PrayerData.place(this);
        for (String[] place : PrayerData.PLACES) {
            String label = ar ? place[1] + "  ·  " + place[0] : place[0] + "  ·  " + place[1];
            boolean selected = place[0].equals(current);
            addRow((selected ? "✓  " : "") + label, () -> {
                PrayerData.save(this, place[0], Double.parseDouble(place[2]), Double.parseDouble(place[3]), PrayerData.method(this));
                finish();
            });
        }
        focusFirst();
    }

    private void showMethods() {
        int current = PrayerData.method(this);
        for (int method : PrayerData.METHODS) {
            String label = (method == current ? "✓  " : "") + methodLabel(method);
            addRow(label, () -> {
                PrayerData.save(this, PrayerData.place(this), PrayerData.lat(this), PrayerData.lon(this), method);
                finish();
            });
        }
        focusFirst();
    }

    private String methodLabel(int method) {
        switch (method) {
            case 1: return getString(R.string.method_1);
            case 2: return getString(R.string.method_2);
            case 3: return getString(R.string.method_3);
            case 4: return getString(R.string.method_4);
            case 5: return getString(R.string.method_5);
            case 7: return getString(R.string.method_7);
            default: return getString(R.string.method_0);
        }
    }

    private void open(String screen) {
        startActivity(new Intent(this, PrayerActivity.class).putExtra(SCREEN, screen));
    }

    private TextView note(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFFE7E0D4);
        view.setTextSize(16);
        view.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0, 0);
        return view;
    }

    private void addRow(String label, Runnable action) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(18);
        row.setFocusable(true);
        row.setClickable(true);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad / 2, pad, pad / 2);
        row.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        row.setOnClickListener(v -> action.run());
        rows.addView(row, lp);
    }

    private void focusFirst() {
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).isFocusable()) {
                rows.getChildAt(i).requestFocus();
                return;
            }
        }
    }
}
