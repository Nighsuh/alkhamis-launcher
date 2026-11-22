package com.gathin.tvhome;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Draws the full-screen theme preview. The TV emulator on this host never reaches adb, so these
 * PNGs are the check that Istanbul v2, Halloween, Glass and Family each use their own picture.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ar-w1280dp-h720dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThemeBrowseScreenshotTest {
    @Test
    public void fourThemesLookDifferent() throws Exception {
        int[] istanbul = shot("istanbul_v2", "theme-preview-istanbul-v2.png", "إسطنبول");
        int[] halloween = shot("halloween", "theme-preview-halloween.png", "الهالوين");
        int[] glass = shot("glass", "theme-preview-glass.png", "زجاج");
        int[] family = shot("family", "theme-preview-family.png", "العائلة");
        assertDifferent(istanbul, halloween);
        assertDifferent(istanbul, glass);
        assertDifferent(istanbul, family);
        assertDifferent(halloween, glass);
        assertDifferent(halloween, family);
        assertDifferent(glass, family);
    }

    private static int[] shot(String id, String file, String overlayPiece) throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.gathin.tvhome", ThemeBrowseActivity.class.getName());
        intent.putExtra(ThemeBrowseActivity.EXTRA_ID, id);
        ThemeBrowseActivity activity = Robolectric.buildActivity(ThemeBrowseActivity.class, intent)
                .setup().get();
        ImageView wallpaper = findWallpaper(activity);
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (wallpaper.getDrawable() != null) break;
            Thread.sleep(40);
        }
        TextView overlay = findOverlay(activity);
        String caption = overlay.getText().toString();
        if (!caption.contains(overlayPiece) || !caption.contains("تطبيق")) {
            throw new AssertionError(id + " overlay was: " + caption);
        }
        if (wallpaper.getDrawable() == null) {
            throw new AssertionError(id + " drew no wallpaper");
        }
        View decor = activity.getWindow().getDecorView();
        int w = 1280;
        int h = 720;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File out = new File("/opt/cursor/artifacts", file);
        out.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(out)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new AssertionError("png compress failed for " + id);
            }
        }
        int[] sample = sample(bitmap);
        activity.finish();
        return sample;
    }

    private static ImageView findWallpaper(ThemeBrowseActivity activity) {
        return (ImageView) find(activity.findViewById(android.R.id.content), ImageView.class, 0);
    }

    private static TextView findOverlay(ThemeBrowseActivity activity) {
        TextView found = null;
        View content = activity.findViewById(android.R.id.content);
        found = (TextView) findLast(content, TextView.class);
        if (found == null) throw new AssertionError("no overlay");
        return found;
    }

    private static View find(View root, Class<?> type, int which) {
        int[] seen = new int[]{0};
        View found = walk(root, type, which, seen);
        if (found == null) throw new AssertionError("missing " + type.getSimpleName());
        return found;
    }

    private static View findLast(View root, Class<?> type) {
        View[] last = new View[1];
        walkLast(root, type, last);
        return last[0];
    }

    private static View walk(View root, Class<?> type, int which, int[] seen) {
        if (type.isInstance(root)) {
            if (seen[0] == which) return root;
            seen[0]++;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = walk(group.getChildAt(i), type, which, seen);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void walkLast(View root, Class<?> type, View[] last) {
        if (type.isInstance(root) && root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && text.toString().contains("تطبيق")) last[0] = root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) walkLast(group.getChildAt(i), type, last);
        }
    }

    /** A few pixels from the middle of the wallpaper, away from the clock and the caption bar. */
    private static int[] sample(Bitmap bitmap) {
        int[] px = new int[20];
        int n = 0;
        for (int y = 280; y <= 430; y += 50) {
            for (int x = 200; x <= 1000; x += 200) {
                px[n++] = bitmap.getPixel(x, y);
            }
        }
        int distinct = 0;
        for (int i = 0; i < px.length; i++) {
            boolean fresh = true;
            for (int j = 0; j < i; j++) if (px[j] == px[i]) fresh = false;
            if (fresh) distinct++;
        }
        if (distinct < 2) throw new AssertionError("wallpaper looks flat");
        return px;
    }

    private static void assertDifferent(int[] a, int[] b) {
        int same = 0;
        for (int i = 0; i < a.length; i++) if (a[i] == b[i]) same++;
        if (same == a.length) throw new AssertionError("two themes sampled the same pixels");
    }
}
