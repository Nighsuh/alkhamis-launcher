package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-screen Home with the focused theme's wallpaper and colours. Left and Right move between
 * themes. OK applies the focused one. Back leaves the installed theme alone.
 */
public final class ThemeBrowseActivity extends Activity {
    static final String EXTRA_ID = "theme_id";
    private static final String TAG = "TvHome.ThemePreview";
    private static final int MAX_EDGE = 1280;

    private final List<ThemeCatalog.Item> themes = new ArrayList<>();
    private final Map<String, Bitmap> cache = new LinkedHashMap<>(6, 0.75f, true);
    private FrameLayout root;
    private ImageView wallpaper;
    private TextView overlay;
    private LinearLayout dock;
    private int index;
    private int generation;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(wallpaper, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        findViewById(R.id.grid).setVisibility(View.GONE);
        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dockLp.gravity = android.view.Gravity.BOTTOM;
        dockLp.bottomMargin = (int) (48 * getResources().getDisplayMetrics().density);
        root.addView(dock, dockLp);
        overlay = new TextView(this);
        overlay.setTextColor(0xFFFFFFFF);
        overlay.setTextSize(16);
        overlay.setGravity(android.view.Gravity.CENTER);
        overlay.setBackgroundColor(0x99000000);
        overlay.setPadding(28, 14, 28, 14);
        FrameLayout.LayoutParams overlayLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overlayLp.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        overlayLp.topMargin = (int) (18 * getResources().getDisplayMetrics().density);
        root.addView(overlay, overlayLp);
        paintGreeting();
        fillApps();
        themes.add(new ThemeCatalog.Item("shrine", "Shrine", "الضريح", "", "", true));
        for (ThemeCatalog.Item item : ThemeCatalog.bundled(this)) {
            if (!ThemeCatalog.shrineId(item.id)) themes.add(item);
        }
        String requested = getIntent().getStringExtra(EXTRA_ID);
        index = 0;
        if (requested != null) {
            for (int i = 0; i < themes.size(); i++) {
                if (requested.equals(themes.get(i).id)) index = i;
            }
        }
        show(index);
        root.setFocusable(true);
        root.requestFocus();
        loadOnline();
    }

    private void loadOnline() {
        String url = ThemeCatalog.indexUrl(this);
        Thread t = new Thread(() -> {
            List<ThemeCatalog.Item> extra = new ArrayList<>();
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(false);
                try (InputStream in = conn.getInputStream()) {
                    extra.addAll(ThemeCatalog.parse(new String(readBytes(in, 256 * 1024), StandardCharsets.UTF_8)));
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.i(TAG, "index: " + e.getMessage());
            }
            List<ThemeCatalog.Item> found = extra;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                ThemeCatalog.remember(this, found);
                int before = themes.size();
                for (ThemeCatalog.Item item : found) {
                    boolean have = false;
                    for (ThemeCatalog.Item existing : themes) if (existing.id.equals(item.id)) have = true;
                    if (!have && ThemeCatalog.beyondShrine(item)) themes.add(item);
                }
                if (themes.size() != before) show(index);
            });
        }, "theme-index");
        t.setDaemon(true);
        t.start();
    }

    /** The same day-part line Home paints, so the preview is the real header and not an empty clock. */
    private void paintGreeting() {
        TextView greeting = findViewById(R.id.greeting);
        if (greeting == null) return;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int res = hour < 5 ? R.string.good_night
                : hour < 12 ? R.string.good_morning
                : hour < 17 ? R.string.good_afternoon
                : hour < 22 ? R.string.good_evening
                : R.string.good_night;
        boolean arabic = "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        greeting.setLetterSpacing(arabic ? 0f : 0.22f);
        greeting.setText(getString(res));
        TextView date = findViewById(R.id.date);
        if (date != null) date.setLetterSpacing(arabic ? 0.02f : 0.14f);
    }

    private void fillApps() {
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> infos = getPackageManager().queryIntentActivities(leanback, 0);
        float density = getResources().getDisplayMetrics().density;
        int shown = 0;
        for (ResolveInfo info : infos) {
            if (info.activityInfo == null) continue;
            if (getPackageName().equals(info.activityInfo.packageName)) continue;
            View tile = getLayoutInflater().inflate(R.layout.item_app, dock, false);
            tile.findViewById(R.id.card).setFocusable(false);
            ImageView banner = tile.findViewById(R.id.banner);
            ImageView icon = tile.findViewById(R.id.icon);
            TextView name = tile.findViewById(R.id.name);
            Drawable art = null;
            try {
                art = info.activityInfo.loadBanner(getPackageManager());
            } catch (Exception ignored) { }
            if (art == null) {
                try {
                    art = info.loadIcon(getPackageManager());
                } catch (Exception ignored) { }
            }
            if (art != null) banner.setImageDrawable(art);
            else icon.setVisibility(View.VISIBLE);
            name.setAlpha(1f);
            name.setText(info.loadLabel(getPackageManager()));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (200 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart((int) (8 * density));
            tile.setLayoutParams(lp);
            dock.addView(tile);
            if (++shown >= 5) break;
        }
    }

    private void show(int at) {
        if (themes.isEmpty()) return;
        index = (at % themes.size() + themes.size()) % themes.size();
        ThemeCatalog.Item item = themes.get(index);
        boolean arabic = "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        overlay.setText(item.label(arabic) + "\n" + (index + 1) + "/" + themes.size()
                + "    OK  تطبيق    ·    Back  رجوع");
        int token = ++generation;
        Bitmap ready = cache.get(item.id);
        loadLook(item, token, ready != null && !ready.isRecycled() ? ready : null);
        preload(index - 1);
        preload(index + 1);
    }

    private void loadLook(ThemeCatalog.Item item, int token, Bitmap ready) {
        boolean shrine = ThemeCatalog.shrineId(item.id);
        Thread t = new Thread(() -> {
            String root = shrine ? "bundled-theme" : ThemeCatalog.BUNDLED_ROOT + "/" + item.id;
            ThemeLook look = ThemeLook.fallback();
            String relative = null;
            boolean have = shrine || assetDir(root);
            if (have) {
                try {
                    look = ThemeLook.fromJson(readText(root + "/config.json"));
                } catch (Exception e) {
                    Log.i(TAG, item.id + " config unreadable: " + e.getMessage());
                }
                relative = ThemeAssets.pickWallpaper(list(root), list(root + "/wallpapers"));
                Log.i(TAG, item.id + " wallpaper=" + relative);
            } else {
                Log.i(TAG, item.id + " not bundled, preview=" + item.preview);
            }
            Bitmap bitmap = ready;
            try {
                if (bitmap == null && relative != null) bitmap = decodeAsset(root + "/" + relative);
                if (bitmap == null && item.preview.startsWith("https://")) bitmap = decodeUrl(item.preview);
            } catch (Exception e) {
                Log.i(TAG, item.id + " picture failed: " + e.getMessage());
            }
            ThemeLook parsed = look;
            Bitmap picture = bitmap;
            boolean missing = picture == null && relative == null && !item.preview.startsWith("https://");
            runOnUiThread(() -> apply(token, item.id, parsed, picture, missing));
        }, "theme-browse");
        t.setDaemon(true);
        t.start();
    }

    private void apply(int token, String id, ThemeLook look, Bitmap picture, boolean missing) {
        if (token != generation) {
            if (picture != null && !cache.containsValue(picture)) picture.recycle();
            return;
        }
        root.setBackgroundColor(look.background);
        TextView greeting = findViewById(R.id.greeting);
        if (greeting != null) greeting.setTextColor(look.headerWhite ? 0xFFFFFFFF : 0x8CFFFFFF);
        TextView date = findViewById(R.id.date);
        if (date != null) date.setTextColor(look.headerWhite ? 0xFFFFFFFF : 0xA6FFFFFF);
        if (look.iconOnly) {
            TextView wifi = findViewById(R.id.btnWifi);
            TextView settings = findViewById(R.id.btnSettings);
            if (wifi != null) wifi.setText("");
            if (settings != null) settings.setText("");
        }
        int alpha = look.dockGlass ? 0x66 : Math.round(255f * look.dockOpacity / 100f);
        dock.setBackgroundColor(look.dockBand || look.dockGlass ? (alpha << 24) | 0x00101822 : 0x00000000);
        wallpaper.setImageBitmap(picture);
        wallpaper.setTag(picture);
        if (look.wallpaperDim > 0 && picture != null) {
            wallpaper.setColorFilter((look.wallpaperDim * 255 / 100) << 24, PorterDuff.Mode.SRC_ATOP);
        } else {
            wallpaper.setColorFilter(null);
        }
        wallpaper.setAlpha(0f);
        wallpaper.animate().alpha(1f).setDuration(180).start();
        if (picture != null) cache.put(id, picture);
        trimCache();
        if (missing) {
            overlay.append("\n" + getString(R.string.themes_preview_later));
        }
    }

    private void preload(int at) {
        if (themes.isEmpty()) return;
        int i = (at % themes.size() + themes.size()) % themes.size();
        ThemeCatalog.Item item = themes.get(i);
        if (cache.containsKey(item.id)) return;
        Thread t = new Thread(() -> {
            boolean shrine = ThemeCatalog.shrineId(item.id);
            String root = shrine ? "bundled-theme" : ThemeCatalog.BUNDLED_ROOT + "/" + item.id;
            String relative = ThemeAssets.pickWallpaper(list(root), list(root + "/wallpapers"));
            Bitmap bitmap = null;
            try {
                if (relative != null) bitmap = decodeAsset(root + "/" + relative);
            } catch (Exception ignored) { }
            Bitmap picture = bitmap;
            runOnUiThread(() -> {
                if (picture == null) return;
                Bitmap old = cache.put(item.id, picture);
                if (old != null && old != wallpaper.getTag()) old.recycle();
                trimCache();
            });
        }, "theme-preload");
        t.setDaemon(true);
        t.start();
    }

    private void trimCache() {
        while (cache.size() > 4) {
            String eldest = cache.keySet().iterator().next();
            Bitmap bitmap = cache.remove(eldest);
            if (bitmap != null && bitmap != wallpaper.getTag()) bitmap.recycle();
        }
    }

    private void applyFocused() {
        if (themes.isEmpty()) return;
        ThemeCatalog.Item item = themes.get(index);
        if (ThemeCatalog.shrineId(item.id)) {
            ThemeInstaller.restoreShrine(this);
            goHome();
            return;
        }
        try {
            String[] have = getAssets().list(ThemeCatalog.BUNDLED_ROOT + "/" + item.id);
            if (have != null && have.length > 0) {
                ThemeInstaller.applyBundled(this, item.id);
                goHome();
                return;
            }
        } catch (Exception ignored) { }
        // An online theme is applied by the existing download path, then Home opens.
        download(item);
    }

    private void download(ThemeCatalog.Item item) {
        overlay.append("\n…");
        Thread t = new Thread(() -> {
            boolean ok = false;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(item.download).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(false);
                try (InputStream in = conn.getInputStream()) {
                    ok = ThemeInstaller.applyZip(this, in);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                Log.i(TAG, "download " + item.id + ": " + e.getMessage());
            }
            boolean applied = ok;
            runOnUiThread(() -> {
                if (applied) goHome();
            });
        }, "theme-download");
        t.setDaemon(true);
        t.start();
    }

    private void goHome() {
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event);
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) {
            show(index - 1);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            show(index + 1);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            applyFocused();
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean assetDir(String root) {
        try {
            String[] names = getAssets().list(root);
            return names != null && names.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> list(String path) {
        List<String> out = new ArrayList<>();
        try {
            String[] names = getAssets().list(path);
            if (names != null) for (String name : names) out.add(name);
        } catch (Exception e) {
            Log.i(TAG, "list " + path + ": " + e.getMessage());
        }
        return out;
    }

    private String readText(String path) throws Exception {
        try (InputStream in = getAssets().open(path)) {
            byte[] buf = new byte[4096];
            StringBuilder out = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0 && out.length() < 64 * 1024) {
                out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            return out.toString();
        }
    }

    private Bitmap decodeAsset(String path) throws Exception {
        try (InputStream in = getAssets().open(path)) {
            return decode(readBytes(in, 12 * 1024 * 1024));
        }
    }

    private static Bitmap decodeUrl(String remote) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(remote).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        conn.setInstanceFollowRedirects(false);
        try (InputStream in = conn.getInputStream()) {
            return decode(readBytes(in, 3 * 1024 * 1024));
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readBytes(InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > max) break;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static Bitmap decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
    }

    @Override
    protected void onDestroy() {
        generation++;
        for (Bitmap bitmap : cache.values()) if (bitmap != null) bitmap.recycle();
        cache.clear();
        super.onDestroy();
    }
}
