package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * A scaled Home drawn from a theme's own config and wallpaper. Focusing a row only updates this
 * panel. The theme is not applied until OK.
 */
public final class ThemePreviewView extends View {
    private static final int DEBOUNCE_MS = 90;
    private static final int MAX_EDGE = 720;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Path clip = new Path();
    private ThemeLook look = ThemeLook.fallback();
    private Bitmap wallpaper;
    private String caption = "";
    private boolean waiting;
    private int generation;
    private Runnable pending;
    private Typeface clockFace;

    public ThemePreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        text.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        try {
            clockFace = Typeface.create(getResources().getString(R.string.font_light), Typeface.NORMAL);
        } catch (RuntimeException ignored) {
            clockFace = text.getTypeface();
        }
    }

    /** Focus moved. Wallpaper decode waits a moment so a fast pass does not start one per row. */
    void preview(ThemeCatalog.Item item, boolean shrine) {
        if (pending != null) handler.removeCallbacks(pending);
        final int token = ++generation;
        final String id = shrine || item == null ? "shrine" : item.id;
        final String remote = !shrine && item != null ? item.preview : "";
        final String label = shrine || item == null
                ? getResources().getString(R.string.themes_use_shrine)
                : item.label("ar".equals(getResources().getConfiguration().getLocales().get(0).getLanguage()));
        pending = () -> load(token, id, shrine || item == null, remote, label);
        handler.postDelayed(pending, DEBOUNCE_MS);
    }

    private void load(int token, String id, boolean shrine, String remote, String label) {
        boolean bundled = shrine || hasAsset(id);
        if (!bundled && (remote == null || !remote.startsWith("https://"))) {
            if (token != generation) return;
            swap(token, ThemeLook.fallback(), null, label, true);
            return;
        }
        new Thread(() -> {
            ThemeLook parsed = ThemeLook.fallback();
            Bitmap bitmap = null;
            try {
                if (bundled) {
                    String root = shrine ? "bundled-theme" : ThemeCatalog.BUNDLED_ROOT + "/" + id;
                    parsed = ThemeLook.fromJson(readAsset(root + "/config.json"));
                    if (parsed.wallpaper) bitmap = decodeAsset(root + "/wallpaper.jpg");
                }
                if (bitmap == null && remote != null && remote.startsWith("https://")) {
                    bitmap = decodeUrl(remote);
                }
            } catch (Exception ignored) { }
            ThemeLook look = parsed;
            Bitmap picture = bitmap;
            post(() -> {
                if (token != generation) {
                    if (picture != null) picture.recycle();
                    return;
                }
                swap(token, look, picture, label, false);
            });
        }, "theme-preview").start();
    }

    private void swap(int token, ThemeLook next, Bitmap picture, String label, boolean pendingDownload) {
        if (token != generation) {
            if (picture != null) picture.recycle();
            return;
        }
        Bitmap old = wallpaper;
        wallpaper = picture;
        look = next;
        caption = label;
        waiting = pendingDownload;
        if (old != null && old != picture) old.recycle();
        invalidate();
    }

    private boolean hasAsset(String id) {
        if (!ThemeCatalog.safeId(id)) return false;
        try {
            String[] names = getContext().getAssets().list(ThemeCatalog.BUNDLED_ROOT + "/" + id);
            return names != null && names.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String readAsset(String path) throws Exception {
        try (InputStream in = getContext().getAssets().open(path)) {
            byte[] buf = new byte[4096];
            StringBuilder out = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0 && out.length() < 64 * 1024) {
                out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            return out.toString();
        }
    }

    private Bitmap decodeAsset(String path) {
        try (InputStream in = getContext().getAssets().open(path)) {
            byte[] bytes = readBytes(in, 8 * 1024 * 1024);
            return decode(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decodeUrl(String remote) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(remote).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(8000);
        conn.setInstanceFollowRedirects(false);
        try (InputStream in = conn.getInputStream()) {
            return decode(readBytes(in, 2 * 1024 * 1024));
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readBytes(InputStream in, int max) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > max) break;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static Bitmap decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
    }

    @Override
    protected void onDetachedFromWindow() {
        generation++;
        if (pending != null) handler.removeCallbacks(pending);
        if (wallpaper != null) {
            wallpaper.recycle();
            wallpaper = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float pad = 12 * d;
        float captionRoom = 22 * d;
        box.set(pad, pad, getWidth() - pad, getHeight() - pad - captionRoom);
        float radius = 18 * d;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0C1016);
        canvas.drawRoundRect(box, radius, radius, paint);

        canvas.save();
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        paint.setColor(look.background);
        canvas.drawRect(box, paint);
        if (wallpaper != null && !wallpaper.isRecycled()) {
            float scale = Math.max(box.width() / wallpaper.getWidth(), box.height() / wallpaper.getHeight());
            float w = wallpaper.getWidth() * scale;
            float h = wallpaper.getHeight() * scale;
            canvas.drawBitmap(wallpaper, null, new RectF(
                    box.centerX() - w / 2f, box.centerY() - h / 2f,
                    box.centerX() + w / 2f, box.centerY() + h / 2f), paint);
            int dim = Math.round(255f * look.wallpaperDim / 100f);
            paint.setColor((dim << 24));
            canvas.drawRect(box, paint);
        }

        float clockSize = 28 * d;
        text.setTypeface(clockFace);
        text.setTextSize(clockSize);
        text.setColor(0xFFFFFFFF);
        String clock = "9:41";
        float clockW = text.measureText(clock);
        float clockX = rtl ? box.right - 16 * d - clockW : box.left + 16 * d;
        canvas.drawText(clock, clockX, box.top + 36 * d, text);

        text.setTextSize(9 * d);
        text.setColor(look.headerWhite ? 0xFFFFFFFF : 0xB3FFFFFF);
        String greet = rtl ? "مساء الخير" : "GOOD EVENING";
        float greetW = text.measureText(greet);
        canvas.drawText(greet, rtl ? box.right - 16 * d - greetW : box.left + 16 * d, box.top + 14 * d, text);

        float pillW = 54 * d;
        float pillH = 16 * d;
        float pillY = box.top + 22 * d;
        float pillX = rtl ? box.left + 14 * d : box.right - 14 * d - pillW * 2 - 6 * d;
        drawPill(canvas, pillX, pillY, pillW, pillH, d);
        drawPill(canvas, pillX + pillW + 6 * d, pillY, pillW, pillH, d);

        float tileTop = box.top + box.height() * 0.38f;
        float tileH = box.height() * 0.22f;
        float gap = 8 * d;
        float tileW = (box.width() - 32 * d - gap * 3) / 4f;
        float rowX = box.left + 16 * d;
        for (int i = 0; i < 4; i++) {
            int shown = rtl ? 3 - i : i;
            float left = rowX + i * (tileW + gap);
            RectF tile = new RectF(left, tileTop, left + tileW, tileTop + tileH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC1C2430);
            canvas.drawRoundRect(tile, 10 * d, 10 * d, paint);
            if (shown == 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.5f * d);
                paint.setColor(look.accent);
                canvas.drawRoundRect(tile, 10 * d, 10 * d, paint);
            }
        }

        float bandTop = box.bottom - tileH - 22 * d;
        RectF band = new RectF(box.left + 10 * d, bandTop, box.right - 10 * d, box.bottom - 12 * d);
        paint.setStyle(Paint.Style.FILL);
        int alpha = look.dockGlass ? 0x55 : Math.round(255f * look.dockOpacity / 100f);
        paint.setColor((alpha << 24) | 0x00101822);
        canvas.drawRoundRect(band, 14 * d, 14 * d, paint);
        canvas.restore();

        text.setTextSize(13 * d);
        text.setColor(0xFFE7EEF4);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        String line = waiting ? getResources().getString(R.string.themes_preview_later) : caption;
        float lineW = text.measureText(line);
        float lineX = rtl ? box.right - lineW : box.left;
        if (lineX < pad) lineX = pad;
        canvas.drawText(line, lineX, getHeight() - 4 * d, text);
    }

    private void drawPill(Canvas canvas, float x, float y, float w, float h, float d) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCCF4F7FB);
        canvas.drawRoundRect(new RectF(x, y, x + w, y + h), h / 2f, h / 2f, paint);
    }
}
