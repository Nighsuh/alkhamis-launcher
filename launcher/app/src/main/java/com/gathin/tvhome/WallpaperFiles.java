package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** The owner's wallpaper file, next to the shrine picture, plus the dim they picked. */
final class WallpaperFiles {
    static final String USER_NAME = "user.jpg";

    private WallpaperFiles() {}

    static File userFile(Context ctx) {
        File dir = Config.dir(ctx);
        if (dir == null) return null;
        File folder = new File(dir, "wallpapers");
        if (!folder.isDirectory() && !folder.mkdirs()) return null;
        return new File(folder, USER_NAME);
    }

    static boolean copyToUser(Context ctx, InputStream in) throws Exception {
        File dest = userFile(ctx);
        if (dest == null) return false;
        File tmp = new File(dest.getParentFile(), USER_NAME + ".part");
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            int total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 15 * 1024 * 1024) return false;
                out.write(buf, 0, n);
            }
        }
        if (tmp.length() < 32 || !looksLikeImage(tmp)) {
            tmp.delete();
            return false;
        }
        if (dest.exists() && !dest.delete()) return false;
        if (!tmp.renameTo(dest)) return false;
        choose(ctx, USER_NAME);
        return true;
    }

    static boolean copyUri(Context ctx, Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            return copyToUser(ctx, in);
        } catch (Exception e) {
            return false;
        }
    }

    static void useShrine(Context ctx) {
        prefs(ctx).edit().remove(Config.WALLPAPER_CHOICE_KEY).commit();
        HomeEdits.notifyChanged(ctx);
    }

    static void choose(Context ctx, String name) {
        prefs(ctx).edit().putString(Config.WALLPAPER_CHOICE_KEY, name).commit();
        HomeEdits.notifyChanged(ctx);
    }

    static void setDim(Context ctx, int dim) {
        int clamped = Math.max(0, Math.min(90, dim));
        prefs(ctx).edit().putInt(Config.WALLPAPER_DIM_KEY, clamped).commit();
        HomeEdits.notifyChanged(ctx);
    }

    static int dim(Context ctx) {
        return prefs(ctx).getInt(Config.WALLPAPER_DIM_KEY, 45);
    }

    /** Back to the bundled shrine picture and its 45% dim. */
    static void reset(Context ctx) {
        File user = userFile(ctx);
        if (user != null) user.delete();
        prefs(ctx).edit().remove(Config.WALLPAPER_CHOICE_KEY).remove(Config.WALLPAPER_DIM_KEY).commit();
        HomeEdits.notifyChanged(ctx);
    }

    static boolean looksLikeImage(File file) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] magic = new byte[12];
            int n = in.read(magic);
            if (n >= 3 && (magic[0] & 0xFF) == 0xFF && (magic[1] & 0xFF) == 0xD8) return true;
            if (n >= 8 && magic[0] == (byte) 0x89 && magic[1] == 'P' && magic[2] == 'N' && magic[3] == 'G') return true;
            if (n >= 12 && magic[0] == 'R' && magic[1] == 'I' && magic[2] == 'F' && magic[3] == 'F') return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }
}
