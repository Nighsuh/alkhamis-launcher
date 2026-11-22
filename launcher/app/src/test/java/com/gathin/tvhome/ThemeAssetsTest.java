package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ThemeAssetsTest {
    @Test
    public void aSingleWallpaperFileIsUsed() {
        assertEquals("wallpaper.jpg", ThemeAssets.pickWallpaper(
                Arrays.asList("config.json", "wallpaper.jpg"), Collections.emptyList()));
    }

    @Test
    public void istanbulV2UsesTheEveningPictureInTheFolder() {
        assertEquals("wallpapers/evening.jpg", ThemeAssets.pickWallpaper(
                Collections.singletonList("config.json"),
                Arrays.asList("morning.jpg", "evening.jpg", "night.jpg")));
    }

    @Test
    public void halloweenUsesTheFirstPictureInTheFolder() {
        assertEquals("wallpapers/001.jpg", ThemeAssets.pickWallpaper(
                Collections.singletonList("config.json"),
                Arrays.asList("captions.txt", "001.jpg", "002.jpg")));
    }

    @Test
    public void noPictureMeansNoWallpaper() {
        assertNull(ThemeAssets.pickWallpaper(
                Collections.singletonList("config.json"), Collections.emptyList()));
    }
}
