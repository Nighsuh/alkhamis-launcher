package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Installed version, and the shrine releases the user can download. */
public final class AboutActivity extends Activity {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout rows;
    private TextView status;
    private String installed = "";
    private boolean busy;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_remote_list);
        try {
            installed = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) { }
        ((TextView) findViewById(R.id.title)).setText(R.string.app_name);
        ((TextView) findViewById(R.id.hint)).setText(getString(R.string.about_installed, installed)
                + "\n\n" + getString(R.string.about_freemytv));
        rows = findViewById(R.id.rows);
        status = new TextView(this);
        status.setTextColor(0xFFACB8C1);
        status.setTextSize(16);
        status.setText(R.string.about_checking);
        rows.addView(status);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            findViewById(android.R.id.content).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
        load();
    }

    private void load() {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(ReleaseNotes.API).openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("User-Agent", "CleanYourTV-Shrine");
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                int code = conn.getResponseCode();
                InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String body = read(in, 512 * 1024);
                conn.disconnect();
                if (code < 200 || code >= 300) {
                    postStatus(getString(R.string.about_error));
                    return;
                }
                List<ReleaseNotes> releases = ReleaseNotes.parse(body);
                main.post(() -> {
                    if (isFinishing()) return;
                    show(releases);
                });
            } catch (Exception e) {
                postStatus(getString(R.string.about_error));
            }
        }, "about-releases").start();
    }

    private void show(List<ReleaseNotes> releases) {
        rows.removeAllViews();
        if (releases.isEmpty()) {
            status.setText(R.string.about_none);
            rows.addView(status);
            return;
        }
        boolean anyOlder = false;
        for (ReleaseNotes release : releases) {
            if (ReleaseNotes.compare(installed, release.tag) < 0) anyOlder = true;
        }
        if (anyOlder) {
            TextView warn = note(getString(R.string.about_downgrade));
            rows.addView(warn);
            addRow(getString(R.string.about_uninstall), () -> startActivity(
                    new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()))));
        }
        for (ReleaseNotes release : releases) {
            int relation = ReleaseNotes.compare(installed, release.tag);
            String mark = relation > 0 ? getString(R.string.about_newer)
                    : relation < 0 ? getString(R.string.about_older)
                    : getString(R.string.about_this);
            rows.addView(note(release.title + "  ·  " + mark));
            if (!release.body.isEmpty()) rows.addView(note(release.body));
            if (relation < 0) {
                addRow(getString(R.string.about_download_older, release.tag), () -> download(release, false));
            } else if (relation > 0) {
                addRow(getString(R.string.about_download, release.tag), () -> download(release, true));
            }
        }
        String uninstall = getString(R.string.about_uninstall);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            // Uninstall is the first focusable row when older releases exist. Opening About on that
            // button means one OK removes the launcher.
            if (child.isFocusable() && (!(child instanceof TextView)
                    || !uninstall.contentEquals(((TextView) child).getText()))) {
                child.requestFocus();
                break;
            }
        }
    }

    private void download(ReleaseNotes release, boolean install) {
        if (busy) return;
        if (!install) {
            statusLine(getString(R.string.about_downgrade));
        }
        if (install && Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent sources = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            if (sources.resolveActivity(getPackageManager()) != null) startActivity(sources);
            statusLine(getString(R.string.about_allow_sources));
            return;
        }
        busy = true;
        statusLine(getString(R.string.about_downloading, release.tag));
        new Thread(() -> {
            File file = new File(getCacheDir(), "CleanYourTV-Shrine-" + release.tag + ".apk");
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(ReleaseNotes.assetUrl(release.tag)).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "CleanYourTV-Shrine");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new java.io.IOException("HTTP " + code);
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buf = new byte[8192];
                    int total = 0;
                    for (int n; (n = in.read(buf)) > 0; ) {
                        total += n;
                        if (total > MAX_BYTES) throw new java.io.IOException("too large");
                        out.write(buf, 0, n);
                    }
                } finally {
                    conn.disconnect();
                }
                main.post(() -> {
                    busy = false;
                    if (isFinishing()) return;
                    if (!install) {
                        statusLine(getString(R.string.about_downgrade_saved));
                        return;
                    }
                    installApk(file);
                });
            } catch (Exception e) {
                main.post(() -> {
                    busy = false;
                    if (!isFinishing()) statusLine(getString(R.string.about_download_failed));
                });
            }
        }, "about-download").start();
    }

    private void installApk(File file) {
        Uri uri = ApkProvider.uri(this, file.getName());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(intent, 0);
        for (ResolveInfo info : handlers) {
            grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        if (handlers.isEmpty()) {
            statusLine(getString(R.string.about_download_failed));
            return;
        }
        startActivity(intent);
    }

    private void postStatus(String text) {
        main.post(() -> {
            if (!isFinishing()) status.setText(text);
        });
    }

    private void statusLine(String text) {
        if (status.getParent() == null) rows.addView(status, 0);
        status.setText(text);
    }

    private TextView note(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(0xFFE7E0D4);
        view.setTextSize(16);
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        view.setPadding(0, pad, 0, 0);
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
        row.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        row.setOnClickListener(v -> action.run());
        rows.addView(row, lp);
    }

    private static String read(InputStream in, int max) throws java.io.IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int total = 0;
        for (int n; (n = in.read(buf)) > 0 && total < max; ) {
            int take = Math.min(n, max - total);
            out.write(buf, 0, take);
            total += take;
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
