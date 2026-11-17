package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Toggle and reorder the top-right chips. Left and right move a row. OK switches it on or off. */
public final class TopBarActivity extends Activity {
    private final List<TopChips.Chip> chips = new ArrayList<>();
    private LinearLayout rows;
    private int focusIndex;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        ((TextView) findViewById(R.id.title)).setText(R.string.top_bar_title);
        ((TextView) findViewById(R.id.hint)).setText(R.string.top_bar_hint);
        rows = findViewById(R.id.rows);
        chips.clear();
        chips.addAll(TopChips.available(this));
        if (state != null) focusIndex = state.getInt("focus");
        show();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("focus", focusIndex);
    }

    private void show() {
        rows.removeAllViews();
        if (chips.isEmpty()) {
            TextView note = new TextView(this);
            note.setText(R.string.top_bar_none);
            note.setTextColor(0xFFACB8C1);
            note.setTextSize(16);
            rows.addView(note);
            return;
        }
        for (int i = 0; i < chips.size(); i++) {
            TopChips.Chip chip = chips.get(i);
            TextView row = new TextView(this);
            row.setText(label(chip));
            row.setBackgroundResource(R.drawable.settings_choice_bg);
            row.setTextColor(0xFFFFFFFF);
            row.setTextSize(18);
            row.setFocusable(true);
            row.setClickable(true);
            row.setSelected(chip.on);
            int pad = (int) (18 * getResources().getDisplayMetrics().density);
            row.setPadding(pad, pad / 2, pad, pad / 2);
            row.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = pad / 2;
            final int index = i;
            row.setOnClickListener(v -> toggle(index));
            row.setOnKeyListener((v, key, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (key == KeyEvent.KEYCODE_DPAD_LEFT) { move(index, -1); return true; }
                if (key == KeyEvent.KEYCODE_DPAD_RIGHT) { move(index, 1); return true; }
                return false;
            });
            rows.addView(row, lp);
        }
        rows.getChildAt(Math.max(0, Math.min(focusIndex, rows.getChildCount() - 1))).requestFocus();
    }

    private void toggle(int index) {
        TopChips.Chip chip = chips.get(index);
        if (TopChips.SETTINGS.equals(chip.id) && chip.on) {
            Toast.makeText(this, R.string.chip_settings_locked, Toast.LENGTH_SHORT).show();
            return;
        }
        chips.set(index, new TopChips.Chip(chip.id, !chip.on));
        save();
        focusIndex = index;
        show();
    }

    private void move(int index, int delta) {
        int to = index + delta;
        if (to < 0 || to >= chips.size()) return;
        Collections.swap(chips, index, to);
        save();
        focusIndex = to;
        show();
    }

    private void save() {
        List<TopChips.Chip> all = new ArrayList<>(chips);
        for (TopChips.Chip extra : TopChips.stored(this)) {
            boolean seen = false;
            for (TopChips.Chip chip : all) seen |= chip.id.equals(extra.id);
            if (!seen) all.add(extra);
        }
        if (!TopChips.write(this, all)) {
            Toast.makeText(this, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show();
            return;
        }
        HomeEdits.notifyChanged(this);
    }

    private String label(TopChips.Chip chip) {
        int name;
        switch (chip.id) {
            case TopChips.NETWORK: name = R.string.chip_network; break;
            case TopChips.WIFI: name = R.string.chip_wifi; break;
            case TopChips.INPUTS: name = R.string.chip_inputs; break;
            case TopChips.SETTINGS: name = R.string.chip_settings; break;
            case TopChips.LAUNCHER: name = R.string.chip_launcher; break;
            case TopChips.BLUETOOTH: name = R.string.chip_bluetooth; break;
            case TopChips.DISPLAY: name = R.string.chip_display; break;
            case TopChips.NOTIFICATIONS: name = R.string.chip_notifications; break;
            case TopChips.SEARCH: name = R.string.chip_search; break;
            case TopChips.CLOCK: name = R.string.chip_clock; break;
            default: name = R.string.chip_weather; break;
        }
        return getString(chip.on ? R.string.chip_on : R.string.chip_off, getString(name));
    }
}
