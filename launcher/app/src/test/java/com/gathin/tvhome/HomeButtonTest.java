package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Home button helper only moves forward, and only when this launcher is not already Home. */
public class HomeButtonTest {
    @Test
    public void quietWhenWeAreHome() {
        assertFalse(HomeButton.shouldOpen(true, "com.google.android.apps.tv.launcherx"));
    }

    @Test
    public void opensOverGoogleTvHomes() {
        assertTrue(HomeButton.shouldOpen(false, "com.google.android.apps.tv.launcherx"));
        assertTrue(HomeButton.shouldOpen(false, "com.google.android.tvlauncher"));
        assertTrue(HomeButton.shouldOpen(false, "com.google.android.tungsten.setupwraith"));
    }

    @Test
    public void leavesOtherAppsAlone() {
        assertFalse(HomeButton.shouldOpen(false, "com.google.android.youtube.tv"));
        assertFalse(HomeButton.shouldOpen(false, "com.tcl.guard"));
        assertFalse(HomeButton.shouldOpen(false, null));
    }
}
