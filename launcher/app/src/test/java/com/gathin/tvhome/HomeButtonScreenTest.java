package com.gathin.tvhome;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

import java.io.File;
import java.io.FileOutputStream;

/** The Home-button steps, and the failure dialog that offers that button first. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ar-w1280dp-h720dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class HomeButtonScreenTest {
    @Test
    public void stepsAndFailureDialog() throws Exception {
        HomeButtonActivity help = Robolectric.buildActivity(HomeButtonActivity.class).setup().get();
        File dir = new File("/opt/cursor/artifacts/qa/3.6.1");
        dir.mkdirs();
        draw(help.getWindow().getDecorView(), new File(dir, "home-button-steps.png"));
        help.finish();

        Intent fail = new Intent(Intent.ACTION_MAIN);
        fail.setClassName("com.gathin.tvhome", DefaultHomeActivity.class.getName());
        fail.putExtra(DefaultHomeActivity.EXTRA_PREVIEW_FAIL, true);
        DefaultHomeActivity home = Robolectric.buildActivity(DefaultHomeActivity.class, fail).setup().get();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        android.app.AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog == null) throw new AssertionError("no failure dialog");
        CharSequence message = ((android.widget.TextView) dialog.findViewById(android.R.id.message)).getText();
        if (message == null || !message.toString().contains("زر الرئيسية")) {
            throw new AssertionError("dialog did not offer the home button: " + message);
        }
        dialog.show();
        View window = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
        if (window != null) draw(window, new File(dir, "home-fail-dialog.png"));
        home.finish();
    }

    private static void draw(View decor, File file) throws Exception {
        decor.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1280, 720);
        Bitmap bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }
}
