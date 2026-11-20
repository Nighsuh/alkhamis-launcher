package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The shrine theme shipped inside the APK (assets/bundled-theme/).
 *
 * CleanYourTV reads a theme from its external files directory, which is normally filled later by
 * tools/theme.sh. This copies that folder's config.json and wallpaper.jpg into place when no
 * config.json is there yet, so the first launch already has the theme. A config.json that is
 * already present — a theme pushed afterwards, or this copy from an earlier launch — is not
 * replaced. There is no wallpapers/ collection, and the bundled config sets
 * wallpaper_rotation_minutes to 0, so the single picture stays put.
 *
 * Dock packages that are not installed are not special-cased here. The grid only lists apps that
 * resolve, and fill_dock (on in config.xml, and not turned off by this theme) fills the free slots.
 */
final class BundledTheme {
    private static final String TAG = "TvHome.Theme";
    private static final String ASSET = "bundled-theme/";

    private BundledTheme() {}

    /** Copies the bundled theme into {@link Config#dir} when that folder has no config.json yet. */
    static void installIfAbsent(Context ctx) {
        File dir = Config.dir(ctx);
        if (dir == null) return;
        File config = new File(dir, Config.FILE_NAME);
        if (config.isFile() && config.length() > 0) return;
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "theme directory not created");
            return;
        }
        File wallpaper = new File(dir, Config.WALLPAPER_NAME);
        File configTmp = new File(dir, Config.FILE_NAME + ".part");
        File wallpaperTmp = new File(dir, Config.WALLPAPER_NAME + ".part");
        try {
            // Picture first. config.json is what makes the overlay "present", so a launch that
            // stops halfway retries instead of sticking on a theme with no picture.
            copyAsset(ctx, ASSET + Config.WALLPAPER_NAME, wallpaperTmp);
            copyAsset(ctx, ASSET + Config.FILE_NAME, configTmp);
            if (wallpaperTmp.length() == 0 || configTmp.length() == 0) {
                throw new IOException("bundled theme asset was empty");
            }
            replace(wallpaperTmp, wallpaper);
            replace(configTmp, config);
        } catch (IOException e) {
            Log.w(TAG, "bundled theme not installed: " + e.getMessage());
            configTmp.delete();
            wallpaperTmp.delete();
            if (!wallpaper.isFile() || wallpaper.length() == 0) config.delete();
        }
    }

    private static void copyAsset(Context ctx, String name, File dest) throws IOException {
        try (InputStream in = ctx.getAssets().open(name);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
            out.getFD().sync();
        }
    }

    /** Replaces dest with tmp. Both are in the theme directory, so rename stays on one filesystem. */
    private static void replace(File tmp, File dest) throws IOException {
        if (dest.exists() && !dest.delete()) throw new IOException("could not replace " + dest.getName());
        if (!tmp.renameTo(dest)) {
            try (InputStream in = new FileInputStream(tmp);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                out.flush();
                out.getFD().sync();
            }
            tmp.delete();
        }
    }
}
