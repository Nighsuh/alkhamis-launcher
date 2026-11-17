package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReleaseNotesTest {
    @Test
    public void newerOlderAndSame() {
        assertTrue(ReleaseNotes.compare("3.2-shrine", "v3.3-shrine") > 0);
        assertTrue(ReleaseNotes.compare("3.2-shrine", "v3.1-shrine") < 0);
        assertEquals(0, ReleaseNotes.compare("3.2-shrine", "v3.2-shrine"));
    }

    @Test
    public void assetUrlUsesTheTagAndFileName() {
        assertEquals(
                "https://github.com/Nighsuh/alkhamis-launcher/releases/download/v3.2-shrine/CleanYourTV-Shrine.apk",
                ReleaseNotes.assetUrl("v3.2-shrine"));
        assertFalse(ReleaseNotes.safeTag("v3.2-shrine/../../etc"));
        assertTrue(ReleaseNotes.safeTag("v3.1-shrine"));
    }
}
