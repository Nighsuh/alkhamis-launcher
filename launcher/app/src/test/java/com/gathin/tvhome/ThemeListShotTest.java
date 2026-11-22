package com.gathin.tvhome;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
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

/** The fast theme list, then the real Home opened from معاينة. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ar-w1280dp-h720dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ThemeListShotTest {
    @Test
    public void listThenPreview() throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName("com.gathin.tvhome", ThemesActivity.class.getName());
        intent.putExtra(ThemeStage.EXTRA_ID, "istanbul_v2");
        ThemesActivity list = Robolectric.buildActivity(ThemesActivity.class, intent).setup().get();
        Thread.sleep(400);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        TextView name = list.getWindow().getDecorView().findViewWithTag("istanbul_v2");
        if (name == null) throw new AssertionError("istanbul row missing");
        if (!name.getText().toString().contains("إسطنبول")) throw new AssertionError(name.getText().toString());
        TextView preview = (TextView) name.getTag(com.gathin.tvhome.R.id.themeRows);
        if (preview == null || !preview.getText().toString().contains("معاينة")) {
            throw new AssertionError("preview action missing");
        }
        File dir = new File("/opt/cursor/artifacts/qa/3.5.9");
        dir.mkdirs();
        View decor = list.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1280, 720);
        name.requestFocus();
        int y = 0;
        View walk = name;
        android.widget.ScrollView scroll = null;
        while (walk.getParent() instanceof View) {
            View parent = (View) walk.getParent();
            if (parent instanceof android.widget.ScrollView) {
                scroll = (android.widget.ScrollView) parent;
                break;
            }
            y += walk.getTop();
            walk = parent;
        }
        if (scroll != null) scroll.scrollTo(0, Math.max(0, y - 80));
        write(decor, new File(dir, "themes-list.png"));
        preview.performClick();
        Intent next = Shadows.shadowOf(list).getNextStartedActivity();
        if (next == null || !next.getBooleanExtra(ThemeStage.EXTRA, false)
                || !"istanbul_v2".equals(next.getStringExtra(ThemeStage.EXTRA_ID))) {
            throw new AssertionError("preview did not open istanbul_v2");
        }
        next.setClassName("com.gathin.tvhome", MainActivity.class.getName());
        MainActivity home = Robolectric.buildActivity(MainActivity.class, next).setup().get();
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            View root = home.findViewById(R.id.root);
            if (root instanceof android.view.ViewGroup) {
                View first = ((android.view.ViewGroup) root).getChildAt(0);
                if (first instanceof android.widget.ImageView
                        && ((android.widget.ImageView) first).getDrawable() != null) break;
            }
            Thread.sleep(40);
        }
        draw(home.getWindow().getDecorView(), new File(dir, "themes-preview.png"));
        TextView overlay = home.getWindow().getDecorView().findViewWithTag("theme-preview-overlay-title");
        if (overlay == null || !overlay.getText().toString().contains("إسطنبول")) {
            throw new AssertionError("preview overlay missing");
        }
        home.finish();
        list.finish();
    }

    private static void draw(View decor, File file) throws Exception {
        int w = 1280;
        int h = 720;
        decor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, w, h);
        write(decor, file);
    }

    private static void write(View decor, File file) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }
}
