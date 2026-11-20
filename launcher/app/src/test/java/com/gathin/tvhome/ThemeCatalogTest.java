package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThemeCatalogTest {
    @Test
    public void shrineAloneDoesNotUnlockThemes() throws Exception {
        String json = "{\"themes\":[{\"id\":\"shrine\",\"name\":\"Imam Hussain shrine\",\"builtin\":true}]}";
        assertEquals(0, ThemeCatalog.extraCount(ThemeCatalog.parse(json)));
    }

    @Test
    public void aDownloadableThemeUnlocksThemes() throws Exception {
        String json = "{\"themes\":["
                + "{\"id\":\"shrine\",\"builtin\":true},"
                + "{\"id\":\"evening-courtyard\",\"name\":\"Evening courtyard\","
                + "\"download\":\"https://example.com/evening-courtyard.zip\"}"
                + "]}";
        assertEquals(1, ThemeCatalog.extraCount(ThemeCatalog.parse(json)));
        assertTrue(ThemeCatalog.beyondShrine(ThemeCatalog.parse(json).get(1)));
    }

    @Test
    public void httpAndPathTricksAreNotATheme() throws Exception {
        String json = "{\"themes\":[{\"id\":\"sneaky\",\"download\":\"http://example.com/a.zip\"},"
                + "{\"id\":\"../secret\",\"download\":\"https://example.com/a.zip\"}]}";
        assertEquals(0, ThemeCatalog.extraCount(ThemeCatalog.parse(json)));
        assertNull(ThemeInstaller.leaf("../secret/config.json"));
        assertNull(ThemeInstaller.leaf("/tmp/config.json"));
        assertEquals("config.json", ThemeInstaller.leaf("evening/config.json"));
        assertFalse(ThemeCatalog.shrineId("evening-courtyard"));
        assertTrue(ThemeCatalog.safeId("istanbul_v2"));
        assertFalse(ThemeCatalog.safeId("istanbul_v2/../x"));
    }
}
