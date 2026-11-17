package com.gathin.tvhome;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lists widget providers. Prayer times is built into this launcher and is listed first. */
public final class WidgetPickerActivity extends Activity {
    private static final int BIND = 4402;
    private static final int CONFIGURE = 4403;
    private AppWidgetHost host;
    private int pending = -1;
    private AppWidgetProviderInfo pendingInfo;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        ((TextView) findViewById(R.id.title)).setText(R.string.widgets_title);
        ((TextView) findViewById(R.id.hint)).setText(R.string.widgets_hint);
        LinearLayout rows = findViewById(R.id.rows);
        host = new AppWidgetHost(getApplicationContext(), WidgetStrip.HOST_ID);
        List<AppWidgetProviderInfo> providers = new ArrayList<>(AppWidgetManager.getInstance(this).getInstalledProviders());
        ComponentName prayer = new ComponentName(this, PrayerWidget.class);
        Collections.sort(providers, (a, b) -> {
            boolean ap = prayer.equals(a.provider);
            boolean bp = prayer.equals(b.provider);
            if (ap != bp) return ap ? -1 : 1;
            String al = String.valueOf(a.loadLabel(getPackageManager()));
            String bl = String.valueOf(b.loadLabel(getPackageManager()));
            return al.compareToIgnoreCase(bl);
        });
        boolean sawPrayer = false;
        for (AppWidgetProviderInfo info : providers) {
            if (isBuiltInPrayer(info)) {
                sawPrayer = true;
                add(rows, getString(R.string.prayer_title), this::addBuiltInPrayer);
            } else {
                CharSequence label = info.loadLabel(getPackageManager());
                String text = label == null ? info.provider.getClassName() : label.toString();
                add(rows, text, () -> choose(info));
            }
        }
        if (!sawPrayer) addFirst(rows, getString(R.string.prayer_title), this::addBuiltInPrayer);
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    /** The launcher draws this itself. No system bind dialog and no second app. */
    private void addBuiltInPrayer() {
        if (HomeWidgets.hasPrayer(this)) {
            say(R.string.widgets_already);
            return;
        }
        HomeWidgets.addPrayer(this);
        HomeEdits.notifyChanged(this);
        say(R.string.widgets_prayer_added);
        finish();
    }

    private boolean isBuiltInPrayer(AppWidgetProviderInfo info) {
        return info != null && info.provider != null
                && getPackageName().equals(info.provider.getPackageName())
                && info.provider.getClassName().endsWith(".PrayerWidget");
    }

    private void choose(AppWidgetProviderInfo info) {
        try {
            int id = host.allocateAppWidgetId();
            pending = id;
            pendingInfo = info;
            boolean ok = AppWidgetManager.getInstance(this).bindAppWidgetIdIfAllowed(id, info.provider);
            if (ok) {
                afterBound(id);
                return;
            }
            Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, Process.myUserHandle());
            // Android 11 hides this system screen from resolveActivity unless it is declared in
            // queries. Try to open it anyway, and say so if this TV does not have one.
            say(R.string.widgets_bind);
            try {
                startActivityForResult(bind, BIND);
            } catch (android.content.ActivityNotFoundException missing) {
                host.deleteAppWidgetId(id);
                pending = -1;
                say(R.string.widgets_bind_missing);
            }
            return;
        } catch (RuntimeException e) {
            if (pending >= 0) host.deleteAppWidgetId(pending);
            pending = -1;
            say(getString(R.string.widgets_failed) + "\n" + (e.getMessage() == null ? "" : e.getMessage()));
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == BIND) {
            int id = data != null ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending) : pending;
            if (result == RESULT_OK && id >= 0) afterBound(id);
            else {
                if (id >= 0) host.deleteAppWidgetId(id);
                pending = -1;
                say(R.string.widgets_bind_denied);
            }
            return;
        }
        if (request == CONFIGURE) {
            int id = pending;
            if (result == RESULT_OK && id >= 0) keep(id);
            else {
                if (id >= 0) host.deleteAppWidgetId(id);
                pending = -1;
                say(R.string.widgets_configure_denied);
            }
        }
    }

    private void afterBound(int id) {
        pending = id;
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) info = pendingInfo;
        if (info != null && info.configure != null) {
            Intent configure = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            configure.setComponent(info.configure);
            configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            try {
                startActivityForResult(configure, CONFIGURE);
            } catch (RuntimeException e) {
                host.deleteAppWidgetId(id);
                pending = -1;
                say(R.string.widgets_configure_failed);
            }
            return;
        }
        keep(id);
    }

    private void keep(int id) {
        int[] have = HomeWidgets.read(this);
        int[] next = new int[have.length + 1];
        System.arraycopy(have, 0, next, 0, have.length);
        next[have.length] = id;
        HomeWidgets.write(this, next);
        HomeEdits.notifyChanged(this);
        pending = -1;
        say(R.string.widgets_added);
        finish();
    }

    private void say(int message) { say(getString(message)); }

    private void say(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void add(LinearLayout rows, String label, Runnable action) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(18);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setClickable(true);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        row.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 3;
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOnClickListener(v -> action.run());
        rows.addView(row, lp);
    }

    private void addFirst(LinearLayout rows, String label, Runnable action) {
        int before = rows.getChildCount();
        add(rows, label, action);
        if (rows.getChildCount() == before + 1 && before > 0) {
            android.view.View row = rows.getChildAt(rows.getChildCount() - 1);
            rows.removeView(row);
            rows.addView(row, 0);
        }
    }
}
