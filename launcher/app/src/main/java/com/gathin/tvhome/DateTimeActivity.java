package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 12/24, date shape, Gregorian/Hijri/Both, Hijri ±2, and time zone. */
public final class DateTimeActivity extends Activity {
    private static final String[] PINNED = {
            "Asia/Riyadh", "Asia/Kuwait", "Asia/Qatar", "Asia/Bahrain", "Asia/Dubai",
            "Asia/Muscat", "Asia/Baghdad", "Asia/Tehran", "Asia/Beirut"
    };

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        if ("zones".equals(getIntent().getStringExtra("screen"))) showZones();
        else showMain();
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

    private void showMain() {
        ((TextView) findViewById(R.id.title)).setText(R.string.datetime_title);
        ((TextView) findViewById(R.id.hint)).setText(ClockStyle.dateLine(this) + "\n" + getString(R.string.datetime_hint));
        LinearLayout rows = findViewById(R.id.rows);
        boolean h24 = ClockStyle.is24(this);
        add(rows, getString(R.string.hour_12), !h24, () -> hour(false));
        add(rows, getString(R.string.hour_24), h24, () -> hour(true));
        boolean seconds = ClockStyle.seconds(this);
        add(rows, getString(seconds ? R.string.hide_seconds : R.string.show_seconds), false, () -> {
            ClockStyle.prefs(this).edit().putBoolean("seconds", !seconds).apply();
            changed();
        });
        String kind = ClockStyle.dateKind(this);
        java.time.LocalDate today = java.time.LocalDate.now(ClockStyle.zoneId(this));
        java.util.Locale locale = getResources().getConfiguration().getLocales().get(0);
        add(rows, ClockStyle.dateLine(locale, today, "gregorian", "long", 0), "long".equals(kind), () -> date("long"));
        add(rows, ClockStyle.dateLine(locale, today, "gregorian", "dmy", 0), "dmy".equals(kind), () -> date("dmy"));
        add(rows, ClockStyle.dateLine(locale, today, "gregorian", "iso", 0), "iso".equals(kind), () -> date("iso"));
        String cal = ClockStyle.calendar(this);
        add(rows, getString(R.string.cal_gregorian), "gregorian".equals(cal), () -> calendar("gregorian"));
        add(rows, getString(R.string.cal_hijri), "hijri".equals(cal), () -> calendar("hijri"));
        add(rows, getString(R.string.cal_both), "both".equals(cal), () -> calendar("both"));
        if (!"gregorian".equals(cal)) {
            for (int offset = -2; offset <= 2; offset++) {
                final int value = offset;
                String mark = value > 0 ? "+" + value : String.valueOf(value);
                add(rows, getString(R.string.hijri_offset, mark), ClockStyle.hijriOffset(this) == value, () -> offset(value));
            }
        }
        String zone = ClockStyle.zone(this);
        add(rows, getString(R.string.zone_tv), zone.isEmpty(), () -> {
            ClockStyle.prefs(this).edit().remove("zone").apply();
            changed();
        });
        add(rows, getString(R.string.zone_pick) + (zone.isEmpty() ? "" : "  ·  " + zone), false, () ->
                startActivity(new Intent(this, DateTimeActivity.class).putExtra("screen", "zones")));
        add(rows, getString(R.string.zone_system), false, this::setSystemZone);
        int auto = 0;
        try { auto = Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME_ZONE); }
        catch (Exception ignored) { }
        int automatic = auto;
        add(rows, getString(automatic == 1 ? R.string.zone_auto_on : R.string.zone_auto_off), false, () -> setAuto(automatic != 1));
        add(rows, getString(R.string.zone_date_settings), false, () ->
                startActivity(new Intent(Settings.ACTION_DATE_SETTINGS)));
        focus(rows);
    }

    private void showZones() {
        ((TextView) findViewById(R.id.title)).setText(R.string.zone_pick);
        ((TextView) findViewById(R.id.hint)).setText(R.string.zone_search);
        LinearLayout rows = findViewById(R.id.rows);
        EditText filter = new EditText(this);
        filter.setHint(R.string.zone_search);
        filter.setTextColor(0xFFFFFFFF);
        filter.setHintTextColor(0x8CFFFFFF);
        filter.setSingleLine(true);
        TvFocus.vertical(filter);
        rows.addView(filter);
        LinearLayout matches = new LinearLayout(this);
        matches.setOrientation(LinearLayout.VERTICAL);
        rows.addView(matches);
        List<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        Collections.sort(zones);
        Runnable fill = () -> {
            matches.removeAllViews();
            String query = filter.getText() == null ? "" : filter.getText().toString().trim().toLowerCase();
            add(matches, getString(R.string.zone_tv), ClockStyle.zone(this).isEmpty(), () -> {
                ClockStyle.prefs(this).edit().remove("zone").apply();
                changed();
            });
            int shown = 0;
            List<String> ordered = new ArrayList<>();
            if (query.isEmpty()) Collections.addAll(ordered, PINNED);
            for (String zone : zones) {
                if (!query.isEmpty() && !zone.toLowerCase().contains(query)) continue;
                if (!ordered.contains(zone)) ordered.add(zone);
            }
            for (String zone : ordered) {
                if (shown++ > 40 && !query.isEmpty()) break;
                if (shown > 80) break;
                add(matches, zone, zone.equals(ClockStyle.zone(this)), () -> {
                    ClockStyle.prefs(this).edit().putString("zone", zone).apply();
                    changed();
                });
            }
        };
        filter.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { fill.run(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        fill.run();
        filter.requestFocus();
    }

    private void hour(boolean h24) {
        ClockStyle.prefs(this).edit().putString("hour", h24 ? "24" : "12").apply();
        changed();
    }

    private void date(String kind) {
        ClockStyle.prefs(this).edit().putString("date", kind).apply();
        changed();
    }

    private void calendar(String mode) {
        ClockStyle.prefs(this).edit().putString("calendar", mode).apply();
        changed();
    }

    private void offset(int offset) {
        ClockStyle.prefs(this).edit().putInt("hijri_offset", offset).apply();
        changed();
    }

    private void setSystemZone() {
        String zone = ClockStyle.zone(this);
        if (zone.isEmpty()) {
            Toast.makeText(this, R.string.zone_tv, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ((AlarmManager) getSystemService(ALARM_SERVICE)).setTimeZone(zone);
            Toast.makeText(this, R.string.zone_system_done, Toast.LENGTH_LONG).show();
            return;
        } catch (SecurityException ignored) { }
        DebugGate.request(this, "set-timezone", zone, zone);
    }

    private void setAuto(boolean on) {
        try {
            Settings.Global.putInt(getContentResolver(), Settings.Global.AUTO_TIME_ZONE, on ? 1 : 0);
            recreate();
            return;
        } catch (SecurityException ignored) { }
        DebugGate.request(this, "auto-zone", on ? "1" : "0", on ? "1" : "0");
    }

    private void changed() {
        HomeEdits.notifyChanged(this);
        recreate();
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

    private void focus(LinearLayout rows) {
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }
}
