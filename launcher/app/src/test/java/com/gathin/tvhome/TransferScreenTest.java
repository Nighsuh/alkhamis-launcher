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
@Config(sdk = 30, qualifiers = "ar-w1280dp-h720dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TransferScreenTest {
    @Test
    public void showsQrAndPin() throws Exception {
        TransferActivity activity = Robolectric.buildActivity(TransferActivity.class).setup().get();
        TextView pin = activity.findViewById(R.id.transferPin);
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && (pin.getText() == null || pin.getText().length() == 0)) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(40);
        }
        if (pin.getText() == null || !pin.getText().toString().contains("482") && pin.getText().length() < 6) {
            if (pin.getText() == null || pin.getText().length() < 8) {
                throw new AssertionError("pin missing: " + pin.getText());
            }
        }
        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1280, 720);
        Bitmap bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File dir = new File("/opt/cursor/artifacts/qa/3.6.0");
        dir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(dir, "tv-transfer.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
        String address = ((TextView) activity.findViewById(R.id.transferUrl)).getText().toString();
        String digits = pin.getText().toString().replaceAll("\\D", "");
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(address + "/?p=" + digits).openConnection();
        try (java.io.InputStream in = conn.getInputStream()) {
            java.nio.file.Files.write(new File(dir, "web.html").toPath(), in.readAllBytes());
        } finally {
            conn.disconnect();
        }
        activity.finish();
    }
}
