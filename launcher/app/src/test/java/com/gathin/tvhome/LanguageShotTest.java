package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Home, settings, the language list, transfer, and the browser page in Arabic and English. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class LanguageShotTest {
    @Test
    @org.robolectric.annotation.Config(sdk = 30, qualifiers = "ar-w1920dp-h1080dp-land")
    public void arabicScreens() throws Exception {
        shoot("ar");
    }

    @Test
    @org.robolectric.annotation.Config(sdk = 30, qualifiers = "en-w1920dp-h1080dp-land")
    public void englishScreens() throws Exception {
        shoot("en");
    }

    @After
    public void resetTransferState() {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .remove(LanguageActivity.LANG_KEY)
                .remove("transfer_public_362")
                .putLong(LauncherLocale.GENERATION, 0)
                .commit();
        TransferHub.onScreenClosed();
    }

    private static void shoot(String lang) throws Exception {
        Context app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(LanguageActivity.LANG_KEY, lang)
                .putLong(LauncherLocale.GENERATION, 1)
                .commit();
        File dir = new File("/opt/cursor/artifacts/qa/3.6.4");
        dir.mkdirs();
        LauncherSettingsActivity settings = Robolectric.buildActivity(LauncherSettingsActivity.class).setup().get();
        TextView language = settings.findViewById(R.id.languageChoice);
        String shown = language.getText().toString();
        if ("ar".equals(lang) && !shown.contains("العربية")) throw new AssertionError(shown);
        if ("en".equals(lang) && !shown.contains("English")) throw new AssertionError(shown);
        draw(settings.getWindow().getDecorView(), new File(dir, "settings-" + lang + ".png"));
        settings.finish();

        LanguageActivity list = Robolectric.buildActivity(LanguageActivity.class).setup().get();
        draw(list.getWindow().getDecorView(), new File(dir, "language-" + lang + ".png"));
        list.finish();

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.setClassName("com.gathin.tvhome", MainActivity.class.getName());
        MainActivity home = Robolectric.buildActivity(MainActivity.class, homeIntent).setup().get();
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(30);
        }
        draw(home.getWindow().getDecorView(), new File(dir, "home-" + lang + ".png"));
        home.finish();

        TransferActivity transfer = Robolectric.buildActivity(TransferActivity.class).setup().get();
        TextView pin = transfer.findViewById(R.id.transferPin);
        deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && (pin.getText() == null || pin.getText().length() < 6)) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(40);
        }
        if (pin.getText() == null || pin.getText().length() < 6) throw new AssertionError("pin missing");
        draw(transfer.getWindow().getDecorView(), new File(dir, "transfer-" + lang + ".png"));
        String address = ((TextView) transfer.findViewById(R.id.transferUrl)).getText().toString();
        String digits = pin.getText().toString().replaceAll("\\D", "");
        savePage(address + "/?p=" + digits + "&lang=ar", new File(dir, "web-ar.html"));
        savePage(address + "/?p=" + digits + "&lang=en", new File(dir, "web-en.html"));
        transfer.finish();
    }

    private static void savePage(String url, File file) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try (InputStream in = conn.getInputStream()) {
            java.nio.file.Files.write(file.toPath(), in.readAllBytes());
        } finally {
            conn.disconnect();
        }
    }

    private static void draw(View decor, File file) throws Exception {
        decor.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }
}
