package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/** The name Settings, recents, and the leanback banner show. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NameShotTest {
    @Test
    @Config(sdk = 30, qualifiers = "en")
    public void englishLabelAndBanner() throws Exception {
        shoot("en", "Settings  ·  Apps");
    }

    @Test
    @Config(sdk = 30, qualifiers = "ar")
    public void arabicLabelAndBanner() throws Exception {
        shoot("ar", "الإعدادات  ·  التطبيقات");
    }

    private static void shoot(String lang, String heading) throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        String label = ctx.getApplicationInfo().loadLabel(ctx.getPackageManager()).toString();
        if (!"Alkhamis Launcher".equals(label)) throw new AssertionError(label);
        Bitmap banner = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.banner);
        Bitmap icon = BitmapFactory.decodeResource(ctx.getResources(), R.mipmap.ic_launcher);
        if (banner == null || icon == null) throw new AssertionError("missing art");
        Bitmap page = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(page);
        canvas.drawColor(0xFF101419);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFE6C57A);
        paint.setTextSize(42f);
        canvas.drawText(heading, 80, 90, paint);
        paint.setColor(0xFF1A2129);
        canvas.drawRoundRect(80, 140, 1100, 320, 24, 24, paint);
        Bitmap iconRow = Bitmap.createScaledBitmap(icon, 96, 96, true);
        canvas.drawBitmap(iconRow, 110, 180, null);
        paint.setColor(0xFFFFFFFF);
        paint.setTextSize(36f);
        canvas.drawText(label, 230, 240, paint);
        paint.setColor(0xFFACB8C1);
        paint.setTextSize(22f);
        canvas.drawText("com.gathin.tvhome", 230, 278, paint);
        paint.setColor(0xFFE6C57A);
        paint.setTextSize(32f);
        canvas.drawText("Banner", 80, 400, paint);
        Bitmap wide = Bitmap.createScaledBitmap(banner, 960, 540, true);
        canvas.drawBitmap(wide, 80, 440, null);
        File dir = new File("/opt/cursor/artifacts/qa/3.6.7");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new AssertionError(dir);
        try (FileOutputStream out = new FileOutputStream(new File(dir, "settings-apps-" + lang + ".png"))) {
            page.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, "banner-" + lang + ".png"))) {
            banner.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        page.recycle();
        wide.recycle();
        iconRow.recycle();
    }
}
