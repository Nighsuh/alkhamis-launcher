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
        File dir = new File("/opt/cursor/artifacts/qa/3.6.6");
        dir.mkdirs();
        LauncherSettingsActivity settings = Robolectric.buildActivity(LauncherSettingsActivity.class).setup().get();
        TextView language = settings.findViewById(R.id.languageChoice);
        String shown = language.getText().toString();
        if ("ar".equals(lang) && !shown.contains("العربية")) throw new AssertionError(shown);
        if ("en".equals(lang) && !shown.contains("English")) throw new AssertionError(shown);
        if ("en".equals(lang)) drawTvRename(new File(dir, "tv-rename.png"));
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
        TextView url = transfer.findViewById(R.id.transferUrl);
        deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && (url.getText() == null || !url.getText().toString().startsWith("http"))) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(40);
        }
        if (url.getText() == null || !url.getText().toString().startsWith("http")) throw new AssertionError("address missing");
        String tvName = ((TextView) transfer.findViewById(R.id.transferName)).getText().toString();
        if (tvName.toLowerCase().contains("pin")) throw new AssertionError(tvName);
        draw(transfer.getWindow().getDecorView(), new File(dir, "transfer-" + lang + ".png"));
        String address = url.getText().toString();
        savePage(address + (address.contains("?") ? "&" : "?") + "lang=ar", new File(dir, "web-ar.html"));
        savePage(address + (address.contains("?") ? "&" : "?") + "lang=en", new File(dir, "web-en.html"));
        if ("en".equals(lang)) {
            byte[] jpeg = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 1, 2, 3, (byte) 0xd9};
            uploadJpeg(address, jpeg);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            try { Thread.sleep(1100); } catch (InterruptedException ignored) { }
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            draw(transfer.getWindow().getDecorView(), new File(dir, "transfer-file-en.png"));
        }
        transfer.finish();
    }

    private static void uploadJpeg(String address, byte[] jpeg) throws Exception {
        String boundary = "shotBoundary";
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"image1000424971\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(address + "/upload").openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setFixedLengthStreamingMode(head.length + jpeg.length + tail.length);
        try (java.io.OutputStream out = conn.getOutputStream()) {
            out.write(head);
            out.write(jpeg);
            out.write(tail);
        }
        conn.getResponseCode();
        conn.disconnect();
    }

    private static void savePage(String url, File file) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try (InputStream in = conn.getInputStream()) {
            java.nio.file.Files.write(file.toPath(), in.readAllBytes());
        } finally {
            conn.disconnect();
        }
    }

    private static void drawTvRename(File file) throws Exception {
        android.content.Context ctx = org.robolectric.RuntimeEnvironment.getApplication();
        android.view.View view = android.view.LayoutInflater.from(ctx).inflate(R.layout.dialog_tv_rename, null);
        android.widget.EditText field = view.findViewById(R.id.renameField);
        field.setText("Living room");
        field.setSelection(field.getText().length());
        int width = 960;
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, view.getMeasuredHeight());
        android.widget.TextView save = view.findViewById(R.id.renameSave);
        android.widget.TextView cancel = view.findViewById(R.id.renameCancel);
        if (!save.isFocusable() || !cancel.isFocusable()) throw new AssertionError("rename buttons are not focusable");
        if (save.getText().length() == 0 || cancel.getText().length() == 0) throw new AssertionError("rename buttons are blank");
        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF101419);
        canvas.translate(480, 280);
        view.draw(canvas);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
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
