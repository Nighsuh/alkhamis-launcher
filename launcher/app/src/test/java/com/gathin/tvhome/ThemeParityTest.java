package com.gathin.tvhome;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Every bundled theme: the browse screen and Home after that theme is applied are the same
 * picture, apart from the browse overlay and the clock.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ar-w1280dp-h720dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThemeParityTest {
    private static final int W = 1280;
    private static final int H = 720;

    @Before
    public void apps() {
        ShadowPackageManager pm = Shadows.shadowOf(RuntimeEnvironment.getApplication().getPackageManager());
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (int i = 0; i < 6; i++) {
            ResolveInfo info = new ResolveInfo();
            ActivityInfo activity = new ActivityInfo();
            activity.name = "com.example.app" + i + ".Main";
            activity.packageName = "com.example.app" + i;
            activity.exported = true;
            activity.enabled = true;
            ApplicationInfo app = new ApplicationInfo();
            app.packageName = activity.packageName;
            app.flags = ApplicationInfo.FLAG_INSTALLED;
            activity.applicationInfo = app;
            info.activityInfo = activity;
            info.nonLocalizedLabel = "App " + i;
            pm.addResolveInfoForIntent(leanback, info);
        }
    }

    @Test
    public void stageKeepsWallpapers() {
        android.content.Context app = RuntimeEnvironment.getApplication();
        String[] top;
        try {
            top = app.getAssets().list("upstream-themes/istanbul_v2");
        } catch (Exception e) {
            throw new AssertionError("list " + e);
        }
        File dest = new File(app.getFilesDir(), "stage-check");
        if (!ThemeInstaller.stageInto(app, "istanbul_v2", dest)) {
            throw new AssertionError("stage failed, list=" + java.util.Arrays.toString(top));
        }
        StringBuilder tree = new StringBuilder();
        listTree(dest, tree);
        if (!tree.toString().contains("evening.jpg")) throw new AssertionError(tree.toString());
    }

    private static void listTree(File file, StringBuilder out) {
        out.append(file.getName()).append(file.isDirectory() ? "/" : " " + file.length()).append('\n');
        File[] kids = file.listFiles();
        if (kids != null) for (File kid : kids) listTree(kid, out);
    }

    @Test
    public void everyThemeMatchesHome() throws Exception {
        List<String> ids = new ArrayList<>();
        ids.add("shrine");
        for (ThemeCatalog.Item item : ThemeCatalog.bundled(RuntimeEnvironment.getApplication())) {
            if (!ThemeCatalog.shrineId(item.id)) ids.add(item.id);
        }
        java.util.Collections.sort(ids.subList(1, ids.size()));
        File dir = new File("/opt/cursor/artifacts/qa/theme-parity");
        dir.mkdirs();
        List<String> notes = new ArrayList<>();
        List<Bitmap> thumbs = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String id : ids) {
            Bitmap preview = null;
            Bitmap applied = null;
            try {
                RuntimeEnvironment.getApplication()
                        .getSharedPreferences("wallpaper_rotation", 0).edit().clear().commit();
                preview = shoot(id, true);
                applied = shoot(id, false);
                String note = compare(id, preview, applied);
                if (hasPicture(id) && mean(preview) < 4 && mean(applied) < 4) {
                    note = id + " FAIL blank wallpaper";
                }
                notes.add(note);
                if (note.contains("FAIL")) failures.add(note);
                thumbs.add(thumb(preview, applied));
                writePair(new File(dir, id + ".png"), preview, applied);
            } catch (Throwable e) {
                String note = id + " FAIL " + e.getClass().getSimpleName() + ": " + e.getMessage();
                notes.add(note);
                failures.add(note);
            } finally {
                if (preview != null) preview.recycle();
                if (applied != null) applied.recycle();
            }
        }
        writeOverview(new File(dir, "overview.png"), thumbs);
        for (Bitmap thumb : thumbs) thumb.recycle();
        StringBuilder summary = new StringBuilder();
        for (String note : notes) summary.append(note).append('\n');
        File summaryFile = new File(dir, "summary.txt");
        try (FileOutputStream out = new FileOutputStream(summaryFile)) {
            out.write(summary.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (!failures.isEmpty()) throw new AssertionError(summary.toString());
    }

    private static Bitmap shoot(String id, boolean preview) throws Exception {
        android.content.Context app = RuntimeEnvironment.getApplication();
        if (!preview) {
            if ("shrine".equals(id)) ThemeInstaller.restoreShrine(app);
            else ThemeInstaller.applyBundled(app, id);
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.gathin.tvhome", MainActivity.class.getName());
        if (preview) {
            intent.putExtra(ThemeStage.EXTRA, true);
            intent.putExtra(ThemeStage.EXTRA_ID, id);
        }
        MainActivity activity = Robolectric.buildActivity(MainActivity.class, intent).setup().get();
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (painted(activity) || !expectsPicture(activity, id)) break;
            Thread.sleep(40);
        }
        if (preview) {
            TextView title = activity.getWindow().getDecorView().findViewWithTag("theme-preview-overlay-title");
            if (title == null || !title.getText().toString().contains("تطبيق")) {
                activity.finish();
                throw new AssertionError(id + " overlay missing");
            }
        } else if (textOf(activity.getWindow().getDecorView()).contains("CC BY")) {
            activity.finish();
            throw new AssertionError(id + " still draws a picture credit on Home");
        }
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, W, H);
        Bitmap bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        activity.finish();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return bitmap;
    }

    private static boolean hasPicture(String id) {
        android.content.Context app = RuntimeEnvironment.getApplication();
        try {
            if ("shrine".equals(id)) {
                return app.getAssets().open("bundled-theme/wallpaper.jpg") != null;
            }
            String[] names = app.getAssets().list("upstream-themes/" + id);
            if (names == null) return false;
            for (String name : names) {
                String lower = name.toLowerCase(java.util.Locale.US);
                if (lower.startsWith("wallpaper.") || lower.contains("wallpapers/")) return true;
                if ("wallpapers".equals(name)) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private static double mean(Bitmap bitmap) {
        long sum = 0;
        int n = 0;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int x1 = Math.min(w, 1100);
        for (int y = 200; y < Math.min(h, 600); y += 8) {
            for (int x = 80; x < x1; x += 8) {
                int p = bitmap.getPixel(x, y);
                sum += Color.red(p) + Color.green(p) + Color.blue(p);
                n++;
            }
        }
        return n == 0 ? 0 : sum / (double) n;
    }

    private static boolean expectsPicture(MainActivity activity, String id) {
        File dir = new File(activity.getFilesDir(), "theme-preview/" + id);
        if (!previewFiles(dir)) {
            File live = activity.getExternalFilesDir(null);
            return live != null && previewFiles(live);
        }
        return true;
    }

    private static boolean previewFiles(File dir) {
        if (dir == null) return false;
        File one = new File(dir, "wallpaper.jpg");
        if (one.isFile() && one.length() > 0) return true;
        File[] kids = new File(dir, "wallpapers").listFiles();
        if (kids == null) return false;
        for (File kid : kids) {
            String name = kid.getName().toLowerCase(java.util.Locale.US);
            if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".webp")) return true;
        }
        return false;
    }

    private static boolean painted(MainActivity activity) {
        View root = activity.findViewById(R.id.root);
        if (!(root instanceof ViewGroup)) return false;
        View first = ((ViewGroup) root).getChildAt(0);
        return first instanceof ImageView && ((ImageView) first).getDrawable() != null;
    }

    private static String textOf(View root) {
        StringBuilder out = new StringBuilder();
        walk(root, out);
        return out.toString();
    }

    private static void walk(View view, StringBuilder out) {
        if (view instanceof TextView) out.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) walk(group.getChildAt(i), out);
        }
    }

    private static String compare(String id, Bitmap preview, Bitmap applied) {
        int diff = 0;
        int compared = 0;
        for (int y = 0; y < H; y += 2) {
            if (y < 200) continue; // browse overlay, credit line and clock
            for (int x = 0; x < W; x += 2) {
                if (inClock(x, y)) continue;
                compared++;
                int a = preview.getPixel(x, y);
                int b = applied.getPixel(x, y);
                if (Math.abs(Color.red(a) - Color.red(b)) > 24
                        || Math.abs(Color.green(a) - Color.green(b)) > 24
                        || Math.abs(Color.blue(a) - Color.blue(b)) > 24) diff++;
            }
        }
        double ratio = compared == 0 ? 1 : diff / (double) compared;
        String status = ratio <= 0.02 ? "OK" : "FAIL";
        return id + " " + status + " differ=" + String.format(java.util.Locale.US, "%.2f%%", ratio * 100);
    }

    /** Clock block sits at the top. Arabic lays it on the right, English on the left. */
    private static boolean inClock(int x, int y) {
        return y < 180 && (x < 520 || x > 760);
    }

    private static void writePair(File file, Bitmap preview, Bitmap applied) throws Exception {
        Bitmap sheet = Bitmap.createBitmap(W * 2, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawBitmap(preview, 0, 0, null);
        canvas.drawBitmap(applied, W, 0, null);
        try (FileOutputStream out = new FileOutputStream(file)) {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        sheet.recycle();
    }

    private static Bitmap thumb(Bitmap preview, Bitmap applied) {
        Bitmap pair = Bitmap.createBitmap(320, 90, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(pair);
        canvas.scale(320f / (W * 2), 90f / H);
        canvas.drawBitmap(preview, 0, 0, null);
        canvas.drawBitmap(applied, W, 0, null);
        return pair;
    }

    private static void writeOverview(File file, List<Bitmap> thumbs) throws Exception {
        int cols = 6;
        int rows = (thumbs.size() + cols - 1) / cols;
        Bitmap grid = Bitmap.createBitmap(cols * 320, Math.max(1, rows) * 90, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grid);
        canvas.drawColor(Color.BLACK);
        for (int i = 0; i < thumbs.size(); i++) {
            canvas.drawBitmap(thumbs.get(i), (i % cols) * 320, (i / cols) * 90, null);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            grid.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        grid.recycle();
    }
}
