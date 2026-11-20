package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The shrine dream's picture and clock. Shared by the system dream and Preview. */
final class DreamBoard {
    private final Context ctx;
    private final ImageView photo;
    private final View shade;
    private final View clock;
    private final TextView prayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> files = new ArrayList<>();
    private int index;
    private boolean running;
    private Bitmap current;

    DreamBoard(Context ctx, View root) {
        this.ctx = ctx;
        photo = root.findViewById(R.id.dreamPhoto);
        shade = root.findViewById(R.id.dreamShade);
        clock = root.findViewById(R.id.dreamClock);
        prayer = root.findViewById(R.id.dreamPrayer);
    }

    void start() {
        running = true;
        String mode = ScreenSaver.mode(ctx);
        boolean showClock = !ScreenSaver.MODE_SLIDES.equals(mode) || ScreenSaver.clockOverlay(ctx);
        clock.setVisibility(showClock ? View.VISIBLE : View.GONE);
        if (ScreenSaver.MODE_CLOCK.equals(mode)) {
            photo.setImageDrawable(null);
            shade.setVisibility(View.GONE);
        } else {
            shade.setVisibility(View.VISIBLE);
            files.clear();
            files.addAll(pictures(ctx));
            show(0);
            if (ScreenSaver.MODE_SLIDES.equals(mode) && files.size() > 1) schedule();
        }
        paintDate();
        // The prayer line stays when the clock overlay is off, so a photo dream still shows the next time.
        if (ScreenSaver.prayer(ctx)) showPrayer();
        else if (prayer != null) prayer.setVisibility(View.GONE);
        armClock();
    }

    /** Date, and the prayer line, only while this dream is on screen. */
    private void armClock() {
        if (!running) return;
        long delay = 60_000L - (System.currentTimeMillis() % 60_000L);
        if (delay < 5_000L) delay += 60_000L;
        handler.postDelayed(() -> {
            if (!running) return;
            paintDate();
            if (ScreenSaver.prayer(ctx)) {
                PrayerLine.refresh(ctx);
                showPrayer();
            }
            armClock();
        }, delay);
    }

    private void paintDate() {
        TextView date = clock.findViewById(R.id.dreamDate);
        TextClock time = clock.findViewById(R.id.dreamTime);
        if (time != null) ClockStyle.apply(time, null, ctx);
        if (date != null) date.setText(ClockStyle.dateLine(ctx));
    }

    void stop() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        photo.setImageDrawable(null);
        recycleLater(current);
        current = null;
    }

    /** Recycling a full-screen bitmap on the UI thread stalls input, including the focus-loss event when this
     *  screen closes, long enough for the system to report the window as not responding. */
    private static void recycleLater(Bitmap bitmap) {
        if (bitmap == null) return;
        new Thread(bitmap::recycle, "dream-recycle").start();
    }

    private void schedule() {
        if (!running) return;
        handler.postDelayed(() -> {
            if (!running || files.isEmpty()) return;
            show((index + 1) % files.size());
            schedule();
        }, ScreenSaver.slideSeconds(ctx) * 1000L);
    }

    private void show(int at) {
        if (files.isEmpty()) {
            decodeAsset();
            return;
        }
        index = at;
        File file = files.get(at);
        int[] screen = screen();
        new Thread(() -> {
            Bitmap bitmap = WallpaperSize.decodeFile(file, screen[0], screen[1]);
            handler.post(() -> {
                if (!running || bitmap == null) {
                    if (bitmap != null) bitmap.recycle();
                    return;
                }
                Bitmap previous = current;
                current = bitmap;
                photo.setImageBitmap(bitmap);
                recycleLater(previous);
            });
        }).start();
    }

    private void decodeAsset() {
        int[] screen = screen();
        new Thread(() -> {
            Bitmap bitmap = null;
            try (InputStream in = ctx.getAssets().open("bundled-theme/wallpaper.jpg")) {
                ByteArrayOutputStream raw = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) raw.write(buf, 0, n);
                bitmap = WallpaperSize.decodeBytes(raw.toByteArray(), screen[0], screen[1]);
            } catch (Exception ignored) { }
            Bitmap ready = bitmap;
            handler.post(() -> {
                if (!running || ready == null) {
                    recycleLater(ready);
                    return;
                }
                recycleLater(current);
                current = ready;
                photo.setImageBitmap(ready);
            });
        }).start();
    }

    private void showPrayer() {
        if (prayer == null) return;
        String line = PrayerLine.line(ctx);
        if (line == null || line.isEmpty()) {
            prayer.setVisibility(View.GONE);
            return;
        }
        prayer.setText(line);
        prayer.setVisibility(View.VISIBLE);
    }

    /** Shrine picture, theme pictures, and an optional folder. */
    static List<File> pictures(Context ctx) {
        List<File> out = new ArrayList<>();
        File dir = Config.dir(ctx);
        if (dir != null) {
            File single = new File(dir, Config.WALLPAPER_NAME);
            if (single.isFile()) out.add(single);
            out.addAll(WallpaperRotation.images(new File(dir, "wallpapers")));
        }
        String folder = ScreenSaver.folder(ctx);
        if (folder != null && !folder.isEmpty()) {
            File dirFolder = new File(folder);
            File[] files = dirFolder.listFiles();
            if (files != null) {
                Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File file : files) {
                    if (!file.isFile()) continue;
                    String name = file.getName().toLowerCase(Locale.ROOT);
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                        out.add(file);
                    }
                    if (out.size() > 40) break;
                }
            }
        }
        return out;
    }

    private int[] screen() {
        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }
}
