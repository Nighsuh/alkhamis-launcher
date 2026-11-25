package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The theme list. Up and Down move between names with no Home draw. OK applies the focused theme.
 * Preview opens the real Home for that theme.
 */
public final class ThemesActivity extends Activity {
    private static final LruCache<String, Bitmap> THUMBS = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private final List<ThemeCatalog.Item> themes = new ArrayList<>();
    private LinearLayout rows;

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_themes);
        rows = findViewById(R.id.themeRows);
        themes.add(new ThemeCatalog.Item("shrine", "Alkhamis", "الخميس", "", "", true));
        for (ThemeCatalog.Item item : ThemeCatalog.bundled(this)) {
            if (!ThemeCatalog.shrineId(item.id)) themes.add(item);
        }
        String focusId = getIntent().getStringExtra(ThemeStage.EXTRA_ID);
        if (focusId == null || focusId.isEmpty()) focusId = "shrine";
        TextView focus = null;
        TextView previousName = null;
        float density = getResources().getDisplayMetrics().density;
        boolean arabic = "ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
        for (int i = 0; i < themes.size(); i++) {
            ThemeCatalog.Item item = themes.get(i);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lineLp.topMargin = Math.round(8 * density);
            line.setLayoutParams(lineLp);

            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setBackgroundColor(0xFF1C242C);
            LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(
                    Math.round(128 * density), Math.round(72 * density));
            thumbLp.setMarginEnd(Math.round(12 * density));
            thumb.setLayoutParams(thumbLp);
            line.addView(thumb);
            loadThumb(item, thumb);

            TextView name = new TextView(this);
            name.setText(item.label(arabic));
            name.setTag(item.id);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(18);
            name.setGravity(android.view.Gravity.CENTER_VERTICAL);
            name.setPadding(Math.round(18 * density), 0, Math.round(18 * density), 0);
            name.setMinHeight(Math.round(52 * density));
            name.setBackgroundResource(R.drawable.settings_choice_bg);
            name.setFocusable(true);
            name.setClickable(true);
            name.setOnClickListener(v -> apply(item));
            LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nameLp.setMarginEnd(Math.round(10 * density));
            name.setLayoutParams(nameLp);
            line.addView(name);

            TextView preview = new TextView(this);
            preview.setText(R.string.themes_preview_open);
            preview.setTextColor(0xFFFFFFFF);
            preview.setTextSize(18);
            preview.setGravity(android.view.Gravity.CENTER);
            preview.setPadding(Math.round(18 * density), 0, Math.round(18 * density), 0);
            preview.setMinHeight(Math.round(52 * density));
            preview.setBackgroundResource(R.drawable.settings_choice_bg);
            preview.setFocusable(true);
            preview.setClickable(true);
            preview.setOnClickListener(v -> openPreview(item.id));
            line.addView(preview);

            if (previousName != null) {
                previousName.setNextFocusDownId(name.getId());
                name.setNextFocusUpId(previousName.getId());
            }
            name.setNextFocusRightId(preview.getId());
            name.setNextFocusLeftId(preview.getId());
            preview.setNextFocusLeftId(name.getId());
            preview.setNextFocusRightId(name.getId());
            preview.setNextFocusDownId(View.NO_ID);
            rows.addView(line);
            // Ids exist only after the views are attached.
            name.setId(View.generateViewId());
            preview.setId(View.generateViewId());
            if (previousName != null) {
                previousName.setNextFocusDownId(name.getId());
                name.setNextFocusUpId(previousName.getId());
                TextView previousPreview = (TextView) previousName.getTag(R.id.themeRows);
                if (previousPreview != null) previousPreview.setNextFocusDownId(name.getId());
            }
            name.setNextFocusRightId(preview.getId());
            name.setNextFocusLeftId(preview.getId());
            preview.setNextFocusLeftId(name.getId());
            preview.setNextFocusRightId(name.getId());
            name.setTag(R.id.themeRows, preview);
            previousName = name;
            if (item.id.equals(focusId)) focus = name;
        }
        TextView first = focus != null ? focus : previousName;
        if (first != null) first.post(first::requestFocus);
    }

    private void openPreview(String id) {
        Intent home = new Intent(this, MainActivity.class);
        home.putExtra(ThemeStage.EXTRA, true);
        home.putExtra(ThemeStage.EXTRA_ID, id);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(home);
    }

    private void apply(ThemeCatalog.Item item) {
        if (ThemeCatalog.shrineId(item.id)) {
            ThemeInstaller.restoreShrine(this);
            finish();
            return;
        }
        if (bundled(item.id)) {
            ThemeInstaller.applyBundled(this, item.id);
            finish();
            return;
        }
        if (!item.download.startsWith("https://")) return;
        new Thread(() -> {
            boolean ok = false;
            try {
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(item.download).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(false);
                try (InputStream in = conn.getInputStream()) {
                    ok = ThemeInstaller.applyZip(this, in);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) { }
            boolean applied = ok;
            runOnUiThread(() -> {
                if (applied) finish();
            });
        }, "theme-apply").start();
    }

    private boolean bundled(String id) {
        if (!ThemeCatalog.safeId(id)) return false;
        try {
            String[] names = getAssets().list(ThemeCatalog.BUNDLED_ROOT + "/" + id);
            return names != null && names.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void loadThumb(ThemeCatalog.Item item, ImageView thumb) {
        Bitmap cached = THUMBS.get(item.id);
        if (cached != null && !cached.isRecycled()) {
            thumb.setImageBitmap(cached);
            return;
        }
        Thread t = new Thread(() -> {
            Bitmap bitmap = decodeThumb(item);
            if (bitmap == null) return;
            THUMBS.put(item.id, bitmap);
            thumb.post(() -> {
                if (!bitmap.isRecycled()) thumb.setImageBitmap(bitmap);
            });
        }, "theme-thumb");
        t.setDaemon(true);
        t.start();
    }

    private Bitmap decodeThumb(ThemeCatalog.Item item) {
        String asset = thumbAsset(item.id);
        if (asset == null) return null;
        try (InputStream in = getAssets().open(asset)) {
            byte[] bytes = read(in, 8 * 1024 * 1024);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 320 || bounds.outHeight / sample > 180) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        } catch (Exception e) {
            return null;
        }
    }

    private String thumbAsset(String id) {
        String root = ThemeCatalog.shrineId(id) ? "bundled-theme" : ThemeCatalog.BUNDLED_ROOT + "/" + id;
        List<String> top = list(root);
        List<String> papers = new ArrayList<>();
        for (String name : top) {
            int slash = name.lastIndexOf('/');
            if (slash > 0) papers.add(name.substring(slash + 1));
        }
        papers.addAll(list(root + "/wallpapers"));
        String relative = ThemeAssets.pickWallpaper(top, papers);
        return relative == null ? null : root + "/" + relative;
    }

    private List<String> list(String path) {
        List<String> out = new ArrayList<>();
        try {
            String[] names = getAssets().list(path);
            if (names != null) for (String name : names) out.add(name);
        } catch (Exception ignored) { }
        return out;
    }

    private static byte[] read(InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < max) out.write(buf, 0, Math.min(n, max - out.size()));
        return out.toByteArray();
    }
}
