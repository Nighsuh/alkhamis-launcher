package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Locale;

/** Twenty-two files: Down must reach the last row and scroll it into view. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, qualifiers = "ar-w1920dp-h1080dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TransferListTest {
    @Test
    public void downScrollsToTheLastFile() throws Exception {
        File dir = RuntimeEnvironment.getApplication().getExternalFilesDir("transfer");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new AssertionError("folder");
        File[] old = dir.listFiles();
        if (old != null) for (File file : old) file.delete();
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.NOVEMBER, 23, 22, 16, 0);
        long base = calendar.getTimeInMillis();
        for (int i = 0; i < 21; i++) write(dir, String.format(Locale.US, "clip-%02d.mp4", i + 1), base - i * 60_000L);
        write(dir, "photo.jpg", base - 21 * 60_000L);
        write(dir, "song.mp3", base - 22 * 60_000L);
        write(dir, "note.txt", base - 23 * 60_000L);
        write(dir, "installer.apk", base - 24 * 60_000L);

        TransferActivity activity = Robolectric.buildActivity(TransferActivity.class).setup().get();
        TextView pin = activity.findViewById(R.id.transferPin);
        android.widget.LinearLayout files = activity.findViewById(R.id.transferFiles);
        long deadline = System.currentTimeMillis() + 6000;
        TextView url = activity.findViewById(R.id.transferUrl);
        while (System.currentTimeMillis() < deadline && (files.getChildCount() < 25 || url.getText() == null || !url.getText().toString().startsWith("http"))) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            Thread.sleep(30);
        }
        if (files.getChildCount() < 25) throw new AssertionError("rows " + files.getChildCount());
        CharSequence title = null;
        android.view.ViewGroup root = activity.findViewById(R.id.transferRoot);
        title = findLabel(root, "Alkhamis TV Transfer");
        if (title == null) throw new AssertionError("title missing");
        CharSequence where = ((TextView) activity.findViewById(R.id.transferWhere)).getText();
        if (where == null || !where.toString().contains("Download/AlkhamisTransfer")) {
            throw new AssertionError("path " + where);
        }
        if (!(files.getChildAt(0) instanceof android.view.ViewGroup)
                || !(((android.view.ViewGroup) files.getChildAt(0)).getChildAt(0) instanceof android.widget.ImageView)) {
            throw new AssertionError("file icon");
        }
        TextView found = findDate(files);
        if (found == null) throw new AssertionError("date missing");
        if (!found.getText().toString().contains("\u2066")) throw new AssertionError("date not isolated: " + found.getText());
        if (!found.getText().toString().contains("2026/11/23 22:16")) {
            throw new AssertionError("date text " + found.getText());
        }
        if (files.findViewWithTag("install:installer.apk") == null) throw new AssertionError("install chip");

        View decor = activity.getWindow().getDecorView();
        decor.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, 1920, 1080);
        View focus = activity.findViewById(R.id.transferBackground);
        focus.setFocusableInTouchMode(true);
        if (!focus.requestFocus()) throw new AssertionError("background would not take focus");
        for (int i = 0; i < 30; i++) {
            View next = focus.focusSearch(View.FOCUS_DOWN);
            if (next == null || next == focus) break;
            next.setFocusableInTouchMode(true);
            if (!next.requestFocus()) throw new AssertionError("refused " + next.getTag());
            focus = next;
        }
        if (focus.getTag() == null || !String.valueOf(focus.getTag()).contains("installer.apk")) {
            throw new AssertionError("focus " + focus.getTag() + " id " + focus.getId());
        }
        ScrollView scroll = activity.findViewById(R.id.transferFileScroll);
        if (scroll.getScrollY() < 100) throw new AssertionError("did not scroll " + scroll.getScrollY());
        scroll.scrollTo(0, 0);
        View firstChip = files.findViewWithTag("open:clip-01.mp4");
        firstChip.setFocusableInTouchMode(true);
        firstChip.requestFocus();

        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        decor.draw(new Canvas(bitmap));
        File outDir = new File("/opt/cursor/artifacts/qa/3.6.2");
        outDir.mkdirs();
        try (FileOutputStream out = new FileOutputStream(new File(outDir, "after-tv.png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();

        String address = ((TextView) activity.findViewById(R.id.transferUrl)).getText().toString();
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(address).openConnection();
        try (java.io.InputStream in = conn.getInputStream()) {
            java.nio.file.Files.write(new File(outDir, "after-web.html").toPath(), in.readAllBytes());
        } finally {
            conn.disconnect();
        }
        Process chrome = new ProcessBuilder("google-chrome", "--headless=new", "--disable-gpu", "--no-sandbox",
                "--window-size=390,900", "--virtual-time-budget=2500",
                "--screenshot=" + new File(outDir, "after-web.png").getAbsolutePath(),
                address).start();
        if (!chrome.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) chrome.destroyForcibly();
        activity.finish();
    }

    private static CharSequence findLabel(View view, String text) {
        if (view instanceof TextView) {
            CharSequence value = ((TextView) view).getText();
            if (value != null && value.toString().contains(text)) return value;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                CharSequence found = findLabel(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findDate(View view) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.toString().contains("2026/11/23")) return (TextView) view;
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findDate(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void write(File dir, String name, long when) throws Exception {
        File file = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(new byte[] {1, 2, 3, 4});
        }
        file.setLastModified(when);
    }
}
