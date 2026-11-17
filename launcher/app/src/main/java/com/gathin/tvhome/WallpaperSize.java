package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/**
 * How big a theme's wallpaper may be and what size it is decoded at (#166). A 1 GB TV cannot afford a
 * picture decoded at full size: a 65535x2159 image is about 283 MB even in RGB_565.
 */
final class WallpaperSize {
    private WallpaperSize() { }

    /** 4K UHD (3840x2160, 8.3 M pixels) and a little more; anything bigger is refused. */
    static final long MAX_PIXELS = 8_400_000L;

    static boolean accepted(int width, int height) {
        return width > 0 && height > 0 && (long) width * height <= MAX_PIXELS;
    }

    /** The largest power-of-two sample size that still covers the screen in both directions. */
    static int sampleSize(int width, int height, int screenW, int screenH) {
        int sample = 1;
        while (width / (sample * 2) >= screenW && height / (sample * 2) >= screenH) sample *= 2;
        return sample;
    }

    /**
     * {inDensity, inTargetDensity} that scale a (sampled) picture down to what CENTER_CROP shows: the
     * side that limits the cover lands on the screen size. Null when it is already that small.
     */
    static int[] coverScale(int width, int height, int screenW, int screenH) {
        if ((long) screenW * height >= (long) screenH * width) {   // width limits the cover
            return width > screenW ? new int[]{width, screenW} : null;
        }
        return height > screenH ? new int[]{height, screenH} : null;
    }

    /** RGB_565 picture scaled to cover the screen. Never the file's full pixel size. */
    static Bitmap decodeFile(File file, int screenW, int screenH) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            return decode(bounds.outWidth, bounds.outHeight, screenW, screenH, options ->
                    BitmapFactory.decodeFile(file.getPath(), options));
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    static Bitmap decodeBytes(byte[] data, int screenW, int screenH) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            return decode(bounds.outWidth, bounds.outHeight, screenW, screenH, options ->
                    BitmapFactory.decodeByteArray(data, 0, data.length, options));
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private interface Decoder {
        Bitmap decode(BitmapFactory.Options options);
    }

    private static Bitmap decode(int width, int height, int screenW, int screenH, Decoder decoder) {
        if (width <= 0 || height <= 0 || screenW <= 0 || screenH <= 0) return null;
        int sample = sampleSize(width, height, screenW, screenH);
        if (!accepted(width / sample, height / sample)) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        int[] scale = coverScale(width / sample, height / sample, screenW, screenH);
        if (scale != null) {
            options.inScaled = true;
            options.inDensity = scale[0];
            options.inTargetDensity = scale[1];
        }
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return decoder.decode(options);
    }
}
