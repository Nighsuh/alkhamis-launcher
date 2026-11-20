package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Download a theme from the index and apply it. The shrine theme is always listed. */
public final class ThemesActivity extends Activity {
    private LinearLayout rows;
    private TextView status;
    private final List<ThemeCatalog.Item> items = new ArrayList<>();
    private boolean busy;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_themes);
        rows = findViewById(R.id.themeRows);
        status = findViewById(R.id.status);
        EditText url = findViewById(R.id.indexUrl);
        url.setText(ThemeCatalog.indexUrl(this));
        findViewById(R.id.saveUrl).setOnClickListener(v -> {
            String text = url.getText().toString().trim();
            if (!text.startsWith("https://")) {
                status.setText(R.string.themes_url_bad);
                return;
            }
            ThemeCatalog.setIndexUrl(this, text);
            load();
        });
        findViewById(R.id.useShrine).setOnClickListener(v -> {
            boolean ok = ThemeInstaller.restoreShrine(this);
            status.setText(ok ? R.string.themes_applied : R.string.themes_failed);
            if (ok) show();
        });
        findViewById(R.id.useShrine).requestFocus();
        show();
        load();
    }

    private void load() {
        if (busy) return;
        busy = true;
        status.setText(R.string.themes_loading);
        String index = ThemeCatalog.indexUrl(this);
        new Thread(() -> {
            String body = null;
            try {
                body = fetch(index);
            } catch (Exception ignored) { }
            String json = body;
            runOnUiThread(() -> {
                busy = false;
                items.clear();
                try {
                    if (json != null) items.addAll(ThemeCatalog.parse(json));
                } catch (Exception ignored) { }
                ThemeCatalog.remember(this, items);
                show();
            });
        }).start();
    }

    private void show() {
        rows.removeAllViews();
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        boolean arabic = "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        String active = ThemeInstaller.active(this);
        TextView shrine = (TextView) findViewById(R.id.useShrine);
        shrine.setText(("shrine".equals(active) ? "✓ " : "") + getString(R.string.themes_use_shrine));
        java.util.LinkedHashMap<String, ThemeCatalog.Item> listed = new java.util.LinkedHashMap<>();
        for (ThemeCatalog.Item item : ThemeCatalog.bundled(this)) {
            if (ThemeCatalog.shrineId(item.id)) continue;
            listed.put(item.id, item);
        }
        for (ThemeCatalog.Item item : items) {
            if (!ThemeCatalog.beyondShrine(item) || listed.containsKey(item.id)) continue;
            listed.put(item.id, item);
        }
        for (ThemeCatalog.Item item : listed.values()) {
            boolean on = item.id.equals(active);
            TextView row = row((on ? "✓ " : "") + item.label(arabic), pad);
            row.setOnClickListener(v -> apply(item));
            rows.addView(row);
        }
        if (!busy) status.setText(listed.isEmpty() ? R.string.themes_none_yet : R.string.themes_pick);
    }

    private void apply(ThemeCatalog.Item item) {
        if (busy) return;
        String root = ThemeCatalog.BUNDLED_ROOT + "/" + item.id;
        try {
            String[] have = getAssets().list(root);
            if (have != null && have.length > 0) {
                boolean ok = ThemeInstaller.applyBundled(this, item.id);
                status.setText(ok ? R.string.themes_applied : R.string.themes_failed);
                if (ok) show();
                return;
            }
        } catch (Exception ignored) { }
        download(item);
    }

    private void download(ThemeCatalog.Item item) {
        if (busy) return;
        busy = true;
        status.setText(R.string.themes_downloading);
        new Thread(() -> {
            boolean ok = false;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(item.download).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(false);
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (out.size() + n > 20 * 1024 * 1024) throw new IllegalStateException("too large");
                        out.write(buf, 0, n);
                    }
                    ok = ThemeInstaller.applyZip(this, new ByteArrayInputStream(out.toByteArray()));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) { }
            boolean applied = ok;
            runOnUiThread(() -> {
                busy = false;
                status.setText(applied ? R.string.themes_applied : R.string.themes_failed);
                if (applied) show();
            });
        }).start();
    }

    private TextView row(String label, int pad) {
        TextView row = new TextView(this);
        row.setText(label);
        row.setBackgroundResource(R.drawable.settings_choice_bg);
        row.setTextColor(0xFFFFFFFF);
        row.setTextSize(18);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(pad, pad / 2, pad, pad / 2);
        row.setMinHeight((int) (52 * getResources().getDisplayMetrics().density));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 2;
        row.setLayoutParams(lp);
        return row;
    }

    private static String fetch(String url) throws Exception {
        if (!url.startsWith("https://")) throw new IllegalArgumentException("url");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setInstanceFollowRedirects(false);
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (out.size() + n > 256 * 1024) break;
                out.write(buf, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            conn.disconnect();
        }
    }
}
