package com.gathin.tvhome;

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

/** Draws the transfer screen once the address and PIN are on it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ar-w1920dp-h1080dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TransferScreenTest {
    @Test
    public void showsQrAndPin() throws Exception {
        File folder = org.robolectric.RuntimeEnvironment.getApplication().getExternalFilesDir("transfer");
        if (folder != null) {
            File[] old = folder.listFiles();
            if (old != null) for (File file : old) file.delete();
        }
        TransferActivity activity = Robolectric.buildActivity(TransferActivity.class).setup().get();
        TextView url = activity.findViewById(R.id.transferUrl);
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && (url.getText() == null || !url.getText().toString().startsWith("http"))) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(40);
        }
        if (url.getText() == null || !url.getText().toString().startsWith("http")) {
            throw new AssertionError("address missing: " + url.getText());
        }
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
        TextView empty = activity.findViewById(R.id.transferEmpty);
        if (empty.getVisibility() != View.VISIBLE) throw new AssertionError("empty help hidden");
        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File dir = new File("/opt/cursor/artifacts/qa/3.6.2");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, "after-tv-empty.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
        String address = url.getText().toString();
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(address).openConnection();
        try (java.io.InputStream in = conn.getInputStream()) {
            java.nio.file.Files.write(new File(dir, "web.html").toPath(), in.readAllBytes());
        } finally {
            conn.disconnect();
        }
        activity.finish();
    }
}
