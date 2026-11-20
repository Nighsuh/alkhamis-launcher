package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Online theme index. The Themes row stays grey until this list contains a theme that is not the
 * built-in shrine theme. The default index is configurable after that.
 */
final class ThemeCatalog {
    static final String DEFAULT_INDEX =
            "https://raw.githubusercontent.com/Nighsuh/alkhamis-launcher/main/themes/index.json";
    static final String URL_KEY = "theme_index_url";
    static final String EXTRA_KEY = "theme_extra_count";
    /** Asset listing every upstream theme that ships inside the APK. */
    static final String BUNDLED_CATALOG = "theme-catalog.json";
    static final String BUNDLED_ROOT = "upstream-themes";

    static final class Item {
        final String id;
        final String name;
        final String nameAr;
        final String preview;
        final String download;
        final boolean builtin;

        Item(String id, String name, String preview, String download, boolean builtin) {
            this(id, name, name, preview, download, builtin);
        }

        Item(String id, String name, String nameAr, String preview, String download, boolean builtin) {
            this.id = id == null ? "" : id;
            this.name = name == null || name.isEmpty() ? this.id : name;
            this.nameAr = nameAr == null || nameAr.isEmpty() ? this.name : nameAr;
            this.preview = preview == null ? "" : preview;
            this.download = download == null ? "" : download;
            this.builtin = builtin;
        }

        /** English and Arabic together, with the UI language first. */
        String label(boolean arabic) {
            if (name.equals(nameAr)) return name;
            return arabic ? nameAr + " · " + name : name + " · " + nameAr;
        }
    }

    private ThemeCatalog() {}

    static String indexUrl(Context ctx) {
        String saved = prefs(ctx).getString(URL_KEY, "");
        return saved == null || saved.trim().isEmpty() ? DEFAULT_INDEX : saved.trim();
    }

    static void setIndexUrl(Context ctx, String url) {
        prefs(ctx).edit().putString(URL_KEY, url == null ? "" : url.trim()).apply();
    }

    /** True when a previous fetch saw a downloadable theme other than the shrine. */
    static boolean menuEnabled(Context ctx) {
        return prefs(ctx).getInt(EXTRA_KEY, 0) > 0;
    }

    static void remember(Context ctx, List<Item> items) {
        prefs(ctx).edit().putInt(EXTRA_KEY, extraCount(items)).apply();
    }

    /** Fetches the index off the main thread and remembers whether an extra theme exists. */
    static void refresh(Context ctx, Runnable done) {
        Context app = ctx.getApplicationContext();
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(indexUrl(app)).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("User-Agent", "CleanYourTV-Shrine");
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = conn.getInputStream()) {
                    byte[] buf = new byte[4096];
                    for (int n; (n = in.read(buf)) > 0 && out.size() < 256 * 1024; ) out.write(buf, 0, n);
                } finally {
                    conn.disconnect();
                }
                remember(app, parse(out.toString(java.nio.charset.StandardCharsets.UTF_8.name())));
            } catch (Exception ignored) { }
            if (done != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(done);
        }, "theme-index").start();
    }

    static List<Item> parse(String json) throws org.json.JSONException {
        if (json == null) return Collections.emptyList();
        JSONObject root = new JSONObject(json);
        JSONArray list = root.optJSONArray("themes");
        if (list == null) return Collections.emptyList();
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < list.length() && i < 40; i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            String id = o.optString("id", "").trim();
            if (!safeId(id)) continue;
            boolean builtin = o.optBoolean("builtin", false) || shrineId(id);
            String name = o.optString("name", "").trim();
            if (name.isEmpty()) name = o.optString("name_en", id).trim();
            out.add(new Item(id, name, o.optString("name_ar", "").trim(),
                    o.optString("preview", "").trim(), o.optString("download", "").trim(), builtin));
        }
        return out;
    }

    /** Themes the owner can download. The built-in shrine theme does not count. */
    static int extraCount(List<Item> items) {
        int n = 0;
        for (Item item : items) if (beyondShrine(item)) n++;
        return n;
    }

    static boolean beyondShrine(Item item) {
        if (item == null || item.builtin || shrineId(item.id)) return false;
        return item.download.startsWith("https://") && item.download.length() < 500;
    }

    static boolean safeId(String id) {
        if (id == null || id.isEmpty() || id.length() > 64) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    static boolean shrineId(String id) {
        if (id == null) return false;
        String folded = id.trim().toLowerCase(Locale.ROOT);
        return folded.equals("shrine") || folded.equals("imam-hussain") || folded.equals("imam-hussain-inside");
    }

    /** The 29 upstream themes copied into the APK. Empty when the asset is missing. */
    static List<Item> bundled(Context ctx) {
        try (java.io.InputStream in = ctx.getAssets().open(BUNDLED_CATALOG)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0 && out.size() < 256 * 1024; ) out.write(buf, 0, n);
            return parse(out.toString(java.nio.charset.StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
