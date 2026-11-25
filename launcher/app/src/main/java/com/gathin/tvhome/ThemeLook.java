package com.gathin.tvhome;

import org.json.JSONObject;

/**
 * The colours and dock treatment a theme's config.json would give Home.
 * The same keys {@link Config} reads, parsed without copying the theme onto the device.
 */
final class ThemeLook {
    final int background;
    final int accent;
    final int wallpaperDim;
    final int dockOpacity;
    final boolean dockGlass;
    final boolean dockBand;
    final boolean iconOnly;
    final boolean headerWhite;
    final boolean wallpaper;
    final String glassStyle;

    private ThemeLook(int background, int accent, int wallpaperDim, int dockOpacity,
            boolean dockGlass, boolean dockBand, boolean iconOnly, boolean headerWhite,
            boolean wallpaper, String glassStyle) {
        this.background = background;
        this.accent = accent;
        this.wallpaperDim = wallpaperDim;
        this.dockOpacity = dockOpacity;
        this.dockGlass = dockGlass;
        this.dockBand = dockBand;
        this.iconOnly = iconOnly;
        this.headerWhite = headerWhite;
        this.wallpaper = wallpaper;
        this.glassStyle = glassStyle == null ? "" : glassStyle;
    }

    static ThemeLook fallback() {
        return new ThemeLook(0xFF101419, 0xFFFFFFFF, 45, 70, false, true, false, false, true, "rim");
    }

    /** Parses a theme config. Missing keys keep the shrine-like fallback. */
    static ThemeLook fromJson(String json) {
        ThemeLook base = fallback();
        if (json == null || json.isEmpty()) return base;
        try {
            JSONObject o = new JSONObject(json);
            return new ThemeLook(
                    color(o, "background_color", base.background),
                    color(o, "accent_color", base.accent),
                    integer(o, "wallpaper_dim", base.wallpaperDim, 0, 90),
                    integer(o, "dock_opacity", base.dockOpacity, 30, 100),
                    bool(o, "dock_glass", base.dockGlass),
                    bool(o, "dock_band", base.dockBand),
                    bool(o, "icon_only_buttons", base.iconOnly),
                    bool(o, "header_white", base.headerWhite),
                    bool(o, "wallpaper", base.wallpaper),
                    string(o, "glass_style", base.glassStyle));
        } catch (Exception e) {
            return base;
        }
    }

    private static String string(JSONObject o, String key, String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    private static boolean bool(JSONObject o, String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    private static int integer(JSONObject o, String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    /** Same rule as Config: a 6-digit theme colour is opaque. Parsed without android.graphics so tests can run it. */
    private static int color(JSONObject o, String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        String text = ((String) v).trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (text.length() != 6 && text.length() != 8) return fallback;
        try {
            long parsed = Long.parseLong(text, 16);
            if (text.length() == 6) return (int) (0xFF000000L | parsed);
            return (int) parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
