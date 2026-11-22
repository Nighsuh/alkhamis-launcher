package com.gathin.tvhome;

import java.util.List;
import java.util.Locale;

/**
 * Where a theme keeps its picture. Some folders have wallpaper.jpg. Istanbul v2, Halloween and
 * Family keep the pictures in wallpapers/ and have no wallpaper.jpg, which is why a preview that
 * only opened wallpaper.jpg stayed black.
 */
final class ThemeAssets {
    private ThemeAssets() {}

    static String pickWallpaper(List<String> rootFiles, List<String> wallpaperFiles) {
        String single = firstImage(rootFiles, new String[]{
                "wallpaper.webp", "wallpaper.png", "wallpaper.jpeg", "wallpaper.jpg"});
        if (single != null) return single;
        if (wallpaperFiles == null) return null;
        String mood = firstImage(wallpaperFiles, new String[]{"evening.jpg", "morning.jpg", "night.jpg"});
        if (mood != null) return "wallpapers/" + mood;
        for (String name : wallpaperFiles) {
            if (isImage(name)) return "wallpapers/" + name;
        }
        return null;
    }

    private static String firstImage(List<String> names, String[] preferred) {
        if (names == null) return null;
        for (String want : preferred) {
            for (String name : names) {
                if (name != null && name.equalsIgnoreCase(want)) return name;
            }
        }
        return null;
    }

    private static boolean isImage(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.US);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".webp");
    }
}
