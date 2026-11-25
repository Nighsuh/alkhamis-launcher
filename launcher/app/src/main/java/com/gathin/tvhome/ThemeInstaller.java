package com.gathin.tvhome;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Copies a theme bundle into the launcher's theme folder. The owner's dock file is not touched. */
final class ThemeInstaller {
    private ThemeInstaller() {}

    static final String ACTIVE_KEY = "active_theme";

    static boolean restoreShrine(Context ctx) {
        File dir = Config.installedDir(ctx);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
        try {
            clearPictures(dir);
            copyAsset(ctx, "bundled-theme/config.json", new File(dir, Config.FILE_NAME));
            copyAsset(ctx, "bundled-theme/wallpaper.jpg", new File(dir, Config.WALLPAPER_NAME));
            stamp(dir);
            WallpaperFiles.reset(ctx);
            remember(ctx, "shrine");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Copies one upstream theme folder out of assets. The owner's dock file is not touched. */
    static boolean applyBundled(Context ctx, String id) {
        if (!ThemeCatalog.safeId(id) || ThemeCatalog.shrineId(id)) return false;
        String root = ThemeCatalog.BUNDLED_ROOT + "/" + id;
        File dir = Config.installedDir(ctx);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
        try {
            String[] top = ctx.getAssets().list(root);
            if (top == null || top.length == 0) return false;
            clearPictures(dir);
            copyAssetTree(ctx, root, dir);
            if (!new File(dir, Config.FILE_NAME).isFile()) return false;
            stamp(dir);
            WallpaperFiles.reset(ctx);
            ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                    .remove(Config.WALLPAPER_CHOICE_KEY).remove(Config.WALLPAPER_DIM_KEY).commit();
            remember(ctx, id);
            HomeEdits.notifyChanged(ctx);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Copies one bundled theme into {@code dest} without touching the installed theme or its saved choices.
     * The browse screen points {@link Config#dir} at that folder, so Home draws it with the normal path.
     */
    static boolean stageInto(Context ctx, String id, File dest) {
        if (dest == null) return false;
        try {
            deleteTree(dest);
            if (!dest.isDirectory() && !dest.mkdirs()) return false;
            if (ThemeCatalog.shrineId(id)) {
                copyAsset(ctx, "bundled-theme/config.json", new File(dest, Config.FILE_NAME));
                copyAsset(ctx, "bundled-theme/wallpaper.jpg", new File(dest, Config.WALLPAPER_NAME));
                return new File(dest, Config.FILE_NAME).isFile();
            }
            if (!ThemeCatalog.safeId(id)) return false;
            String root = ThemeCatalog.BUNDLED_ROOT + "/" + id;
            String[] top = ctx.getAssets().list(root);
            if (top == null || top.length == 0) return false;
            copyAssetTree(ctx, root, dest);
            return new File(dest, Config.FILE_NAME).isFile();
        } catch (Exception e) {
            return false;
        }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] kids = file.listFiles();
        if (kids != null) for (File kid : kids) deleteTree(kid);
        file.delete();
    }

    static String active(Context ctx) {
        String id = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(ACTIVE_KEY, "shrine");
        return id == null || id.isEmpty() ? "shrine" : id;
    }

    private static void remember(Context ctx, String id) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                .putString(ACTIVE_KEY, id).commit();
    }

    /** Drops the previous theme's pictures so a new theme does not keep rotating the old ones. */
    private static void clearPictures(File dir) {
        File wall = new File(dir, "wallpapers");
        File[] kids = wall.listFiles();
        if (kids != null) for (File kid : kids) kid.delete();
        for (String name : new String[]{"wallpaper.jpg", "wallpaper.jpeg", "wallpaper.png", "wallpaper.webp",
                "theme-icon.png"}) {
            new File(dir, name).delete();
        }
    }

    private static void copyAssetTree(Context ctx, String assetPath, File destDir) throws Exception {
        String[] kids = ctx.getAssets().list(assetPath);
        if (kids == null || kids.length == 0) {
            String leaf = assetPath.substring(assetPath.lastIndexOf('/') + 1);
            copyAsset(ctx, assetPath, new File(destDir, leaf));
            return;
        }
        if (!destDir.isDirectory() && !destDir.mkdirs()) throw new IllegalStateException("dir");
        for (String kid : kids) {
            String child = assetPath + "/" + kid;
            String[] nested = ctx.getAssets().list(child);
            if (nested != null && nested.length > 0 && kid.indexOf('/') < 0) {
                copyAssetTree(ctx, child, new File(destDir, kid));
            } else {
                // AssetManager on some hosts lists "wallpapers/evening.jpg" in one level.
                File dest = new File(destDir, kid);
                File parent = dest.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IllegalStateException("dir");
                }
                copyAsset(ctx, child, dest);
            }
        }
    }

    /** Accepts a zip whose config.json is at the root or one folder down. Rejects paths that escape the zip. */
    static boolean applyZip(Context ctx, InputStream zip) {
        File dir = Config.installedDir(ctx);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) return false;
        byte[] config = null;
        byte[] wallpaper = null;
        byte[] icon = null;
        try (ZipInputStream in = new ZipInputStream(zip)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = leaf(entry.getName());
                if (name == null) return false;
                byte[] bytes = readLimited(in, 15 * 1024 * 1024);
                if ("config.json".equals(name) && bytes.length < 64 * 1024) config = bytes;
                else if ("wallpaper.jpg".equals(name) || "wallpaper.jpeg".equals(name)
                        || "wallpaper.png".equals(name) || "wallpaper.webp".equals(name)) wallpaper = bytes;
                else if ("icon.png".equals(name) && bytes.length < 2 * 1024 * 1024) icon = bytes;
            }
        } catch (Exception e) {
            return false;
        }
        if (config == null) return false;
        try {
            write(new File(dir, Config.FILE_NAME), config);
            if (wallpaper != null) write(new File(dir, Config.WALLPAPER_NAME), wallpaper);
            if (icon != null) write(new File(dir, "theme-icon.png"), icon);
            stamp(dir);
            ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).edit()
                    .remove(Config.WALLPAPER_CHOICE_KEY).remove(Config.WALLPAPER_DIM_KEY).commit();
            remember(ctx, "downloaded");
            HomeEdits.notifyChanged(ctx);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The last path segment, or null when the entry tries to leave the zip. */
    static String leaf(String name) {
        if (name == null || name.indexOf('\0') >= 0) return null;
        String folded = name.replace('\\', '/');
        if (folded.startsWith("/") || folded.contains("../") || folded.endsWith("/..")) return null;
        int slash = folded.lastIndexOf('/');
        String leaf = slash < 0 ? folded : folded.substring(slash + 1);
        if (leaf.isEmpty() || leaf.contains("..")) return null;
        return leaf.toLowerCase(Locale.ROOT);
    }

    private static void copyAsset(Context ctx, String asset, File dest) throws Exception {
        try (InputStream in = ctx.getAssets().open(asset)) {
            write(dest, readLimited(in, 15 * 1024 * 1024));
        }
    }

    private static void stamp(File dir) throws Exception {
        String token = Long.toHexString(System.currentTimeMillis());
        write(new File(dir, "theme_revision"), token.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            if (out.size() + n > max) throw new IllegalStateException("too large");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void write(File dest, byte[] bytes) throws Exception {
        File tmp = new File(dest.getParentFile(), dest.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
        }
        if (dest.exists() && !dest.delete()) throw new IllegalStateException("replace");
        if (!tmp.renameTo(dest)) throw new IllegalStateException("rename");
    }
}
