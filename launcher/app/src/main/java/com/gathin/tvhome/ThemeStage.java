package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Browse themes by pointing Home at a copy of the theme and drawing Home again.
 * The installed theme is untouched until OK. Back drops the copy.
 */
final class ThemeStage {
    static final String EXTRA = "theme_preview";
    static final String EXTRA_ID = "theme_id";
    private static final String OVERLAY = "theme-preview-overlay";

    private static volatile File root;
    private static String current = "shrine";
    /** Home was showing a browse copy. Rebuild it with the installed theme once the list covers it. */
    private static boolean homeDirty;

    private ThemeStage() {}

    /** The folder Home should read while browsing, or null when the installed theme is showing. */
    static File folder() {
        return root;
    }

    static boolean active() {
        return root != null;
    }

    /** Called from {@link MainActivity#onCreate} before {@link Config#load}. */
    static void prepare(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null || !intent.getBooleanExtra(EXTRA, false)) {
            root = null;
            return;
        }
        String id = intent.getStringExtra(EXTRA_ID);
        if (id == null || id.isEmpty()) id = "shrine";
        if (!stage(activity, id)) {
            root = null;
            return;
        }
        current = id;
    }

    static boolean wants(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA, false);
    }

    /** Left, Right, Up and Down move. OK installs the theme. Back leaves the installed theme. */
    static boolean consume(Activity activity, KeyEvent event) {
        if (!active() || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_UP) {
            step(activity, -1);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            step(activity, 1);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            apply(activity);
            return true;
        }
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
            returnToList(activity);
            return true;
        }
        return false;
    }

    /** The small line over Home: name, place in the list, and the picture credit when there is one. */
    static void attach(Activity activity, FrameLayout parent, WallpaperPlayer player) {
        if (!active() || parent == null) return;
        if (parent.findViewWithTag(OVERLAY) != null) return;
        float density = activity.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(activity);
        box.setTag(OVERLAY);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackgroundColor(0xB3000000);
        int pad = Math.round(16 * density);
        box.setPadding(pad, Math.round(8 * density), pad, Math.round(8 * density));
        box.setFocusable(false);
        TextView title = new TextView(activity);
        title.setTag(OVERLAY + "-title");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER);
        title.setTextDirection(View.TEXT_DIRECTION_LTR);
        title.setFocusable(false);
        TextView credit = new TextView(activity);
        credit.setTag(OVERLAY + "-credit");
        credit.setTextColor(0xE6FFFFFF);
        credit.setTextSize(12);
        credit.setGravity(Gravity.CENTER);
        credit.setTextDirection(View.TEXT_DIRECTION_LTR);
        credit.setFocusable(false);
        credit.setVisibility(View.GONE);
        box.addView(title);
        box.addView(credit);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Math.round(12 * density);
        parent.addView(box, lp);
        paint(activity);
        if (player != null) player.setCreditListener(text -> credit(activity, text));
        preload(activity);
    }

    static void credit(Activity activity, String text) {
        activity.runOnUiThread(() -> {
            TextView credit = activity.findViewById(android.R.id.content) == null
                    ? null : activity.getWindow().getDecorView().findViewWithTag(OVERLAY + "-credit");
            if (credit == null) return;
            if (text == null || text.isEmpty()) {
                credit.setVisibility(View.GONE);
                return;
            }
            credit.setText(text);
            credit.setVisibility(View.VISIBLE);
        });
    }

    private static void paint(Activity activity) {
        TextView title = activity.getWindow().getDecorView().findViewWithTag(OVERLAY + "-title");
        if (title == null) return;
        List<ThemeCatalog.Item> themes = themes(activity);
        int index = indexOf(themes, current);
        if (index < 0) index = 0;
        ThemeCatalog.Item item = themes.get(index);
        boolean arabic = "ar".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
        title.setText(item.label(arabic) + "\n" + (index + 1) + "/" + themes.size()
                + "    " + activity.getString(R.string.theme_preview_keys));
    }

    private static void step(Activity activity, int delta) {
        List<ThemeCatalog.Item> themes = themes(activity);
        if (themes.isEmpty()) return;
        int index = indexOf(themes, current);
        if (index < 0) index = 0;
        int next = (index + delta) % themes.size();
        if (next < 0) next += themes.size();
        ThemeCatalog.Item item = themes.get(next);
        Intent intent = activity.getIntent();
        if (intent == null) intent = new Intent(activity, MainActivity.class);
        intent.putExtra(EXTRA, true);
        intent.putExtra(EXTRA_ID, item.id);
        activity.setIntent(intent);
        if (!stage(activity, item.id)) {
            current = item.id;
            root = null;
            paint(activity);
            TextView title = activity.getWindow().getDecorView().findViewWithTag(OVERLAY + "-title");
            if (title != null) title.append("\n" + activity.getString(R.string.themes_preview_later));
            return;
        }
        current = item.id;
        activity.recreate();
    }

    private static void apply(Activity activity) {
        String id = current;
        root = null;
        if (ThemeCatalog.shrineId(id)) {
            ThemeInstaller.restoreShrine(activity);
            leave(activity);
            return;
        }
        if (bundled(activity, id)) {
            ThemeInstaller.applyBundled(activity, id);
            leave(activity);
            return;
        }
        ThemeCatalog.Item item = find(themes(activity), id);
        if (item == null || !item.download.startsWith("https://")) {
            leave(activity);
            return;
        }
        TextView title = activity.getWindow().getDecorView().findViewWithTag(OVERLAY + "-title");
        if (title != null) title.append("\n…");
        new Thread(() -> {
            boolean ok = false;
            try {
                java.net.HttpURLConnection conn =
                        (java.net.HttpURLConnection) new java.net.URL(item.download).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(false);
                try (java.io.InputStream in = conn.getInputStream()) {
                    ok = ThemeInstaller.applyZip(activity, in);
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) { }
            boolean applied = ok;
            activity.runOnUiThread(() -> {
                if (applied) leave(activity);
            });
        }, "theme-download").start();
    }

    /** True once, after Back from the preview. Home rebuilds with the installed theme. */
    static boolean takeHomeDirty() {
        boolean dirty = homeDirty;
        homeDirty = false;
        return dirty;
    }

    private static void leave(Activity activity) {
        root = null;
        homeDirty = false;
        Intent home = new Intent(activity, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.setIntent(home);
        activity.recreate();
    }

    /** Back from the preview: the list, on the theme that was open. The installed theme is unchanged. */
    private static void returnToList(Activity activity) {
        String id = current;
        root = null;
        homeDirty = true;
        activity.setIntent(new Intent(activity, MainActivity.class));
        Intent list = new Intent(activity, ThemesActivity.class);
        list.putExtra(EXTRA_ID, id);
        activity.startActivity(list);
    }

    private static boolean stage(Activity activity, String id) {
        if (!bundled(activity, id) && !ThemeCatalog.shrineId(id)) return false;
        File dest = new File(activity.getFilesDir(), "theme-preview/" + id);
        if (!ThemeInstaller.stageInto(activity, id, dest)) return false;
        root = dest;
        return true;
    }

    private static boolean bundled(Activity activity, String id) {
        if (ThemeCatalog.shrineId(id)) return true;
        if (!ThemeCatalog.safeId(id)) return false;
        try {
            String[] names = activity.getAssets().list(ThemeCatalog.BUNDLED_ROOT + "/" + id);
            return names != null && names.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void preload(Activity activity) {
        List<ThemeCatalog.Item> themes = themes(activity);
        int index = indexOf(themes, current);
        Thread t = new Thread(() -> {
            for (int delta : new int[]{-1, 1}) {
                int at = index + delta;
                if (at < 0 || at >= themes.size()) continue;
                String id = themes.get(at).id;
                File dest = new File(activity.getFilesDir(), "theme-preview/" + id);
                if (!new File(dest, Config.FILE_NAME).isFile()) ThemeInstaller.stageInto(activity, id, dest);
            }
        }, "theme-preload");
        t.setDaemon(true);
        t.start();
    }

    private static List<ThemeCatalog.Item> themes(Activity activity) {
        List<ThemeCatalog.Item> out = new ArrayList<>();
        out.add(new ThemeCatalog.Item("shrine", "Shrine", "الضريح", "", "", true));
        for (ThemeCatalog.Item item : ThemeCatalog.bundled(activity)) {
            if (!ThemeCatalog.shrineId(item.id)) out.add(item);
        }
        return out;
    }

    private static int indexOf(List<ThemeCatalog.Item> themes, String id) {
        for (int i = 0; i < themes.size(); i++) if (themes.get(i).id.equals(id)) return i;
        return -1;
    }

    private static ThemeCatalog.Item find(List<ThemeCatalog.Item> themes, String id) {
        int i = indexOf(themes, id);
        return i < 0 ? null : themes.get(i);
    }
}
