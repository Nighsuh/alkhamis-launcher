package com.gathin.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** System language, English, or Arabic, plus Western or Arabic-Indic digits. */
public final class LanguageActivity extends Activity {
    static final String LANG_KEY = "app_locale";
    static final String DIGITS_KEY = "app_digits";

    private SharedPreferences prefs;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        ((TextView) findViewById(R.id.title)).setText(R.string.language);
        ((TextView) findViewById(R.id.hint)).setText(R.string.language_hint);
        LinearLayout rows = findViewById(R.id.rows);
        String lang = prefs.getString(LANG_KEY, "");
        String digits = prefs.getString(DIGITS_KEY, "latn");
        add(rows, getString(R.string.language_system), lang.isEmpty(), () -> choose("", digits, false));
        add(rows, getString(R.string.language_english), "en".equals(lang), () -> choose("en", digits, false));
        add(rows, getString(R.string.language_arabic), "ar".equals(lang), () -> choose("ar", digits, false));
        if ("ar".equals(lang)) {
            add(rows, getString(R.string.digits_western), "latn".equals(digits), () -> choose("ar", "latn", true));
            add(rows, getString(R.string.digits_arabic), !"latn".equals(digits), () -> choose("ar", "arab", true));
        }
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    private void choose(String lang, String digits, boolean digitsExplicit) {
        long next = prefs.getLong(LauncherLocale.GENERATION, 0) + 1;
        android.content.SharedPreferences.Editor editor = prefs.edit()
                .putString(LANG_KEY, lang).putString(DIGITS_KEY, digits)
                .putLong(LauncherLocale.GENERATION, next);
        if (digitsExplicit) editor.putBoolean(LauncherLocale.DIGITS_EXPLICIT, true);
        editor.commit();
        LauncherLocale.applySystem(this, lang);
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
        row.setSelected(selected);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad / 2, pad, pad / 2);
        row.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setTextDirection(android.view.View.TEXT_DIRECTION_LOCALE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        row.setOnClickListener(v -> action.run());
        rows.addView(row, lp);
    }
}
