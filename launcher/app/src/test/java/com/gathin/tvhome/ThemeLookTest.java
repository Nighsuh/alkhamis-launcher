package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ThemeLookTest {
    @Test
    public void readsTheSameColourKeysHomeUses() {
        ThemeLook look = ThemeLook.fromJson("{\"background_color\":\"#070A14\",\"accent_color\":\"#FF7A1A\","
                + "\"wallpaper_dim\":20,\"dock_opacity\":40,\"dock_glass\":true,\"dock_band\":false,"
                + "\"icon_only_buttons\":true,\"header_white\":true,"
                + "\"wallpaper\":false,\"glass_style\":\"rim\"}");
        assertEquals(0xFF070A14, look.background);
        assertEquals(0xFFFF7A1A, look.accent);
        assertEquals(20, look.wallpaperDim);
        assertEquals(40, look.dockOpacity);
        assertTrue(look.dockGlass);
        assertFalse(look.dockBand);
        assertTrue(look.iconOnly);
        assertTrue(look.headerWhite);
        assertFalse(look.wallpaper);
        assertEquals("rim", look.glassStyle);
    }

    @Test
    public void aBrokenConfigKeepsTheFallback() {
        ThemeLook look = ThemeLook.fromJson("{not json");
        ThemeLook fallback = ThemeLook.fallback();
        assertEquals(fallback.background, look.background);
        assertEquals(fallback.dockOpacity, look.dockOpacity);
    }
}
