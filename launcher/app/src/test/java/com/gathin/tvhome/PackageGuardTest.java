package com.gathin.tvhome;

import android.content.pm.ApplicationInfo;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PackageGuardTest {
    @Test
    public void netflixCanBeNamedAndIsNotProtected() {
        assertTrue(PackageGuard.safeName("com.netflix.ninja"));
        assertFalse(PackageGuard.blocked("com.gathin.tvhome", "com.netflix.ninja"));
    }

    @Test
    public void shellPunctuationIsRejected() {
        assertFalse(PackageGuard.safeName("com.foo;rm"));
        assertFalse(PackageGuard.safeName("com.foo bar"));
        assertFalse(PackageGuard.safeName("com..foo"));
        assertFalse(PackageGuard.safeName(".com.foo"));
    }

    @Test
    public void launcherAndCriticalPackagesAreBlocked() {
        assertTrue(PackageGuard.blocked("com.gathin.tvhome", "com.gathin.tvhome"));
        assertTrue(PackageGuard.blocked("com.gathin.tvhome", "com.android.vending"));
        assertTrue(PackageGuard.blocked("com.gathin.tvhome", "com.google.android.gms"));
        assertTrue(PackageGuard.blocked("com.gathin.tvhome", "com.android.systemui"));
        assertTrue(PackageGuard.blocked("com.gathin.tvhome", "com.android.tv.settings"));
        assertTrue(PackageGuard.blocked("com.gathin.tvhome", "com.android.shell"));
    }

    @Test
    public void commandsAreTheUserScopedOnes() {
        assertEquals("pm uninstall -k --user 0 com.netflix.ninja", PackageGuard.uninstallCommand("com.netflix.ninja"));
        assertEquals("cmd package install-existing --user 0 com.netflix.ninja",
                PackageGuard.restoreCommand("com.netflix.ninja"));
    }

    @Test
    public void kindFollowsTheSystemFlags() {
        assertEquals(PackageGuard.USER, PackageGuard.kind(0));
        assertEquals(PackageGuard.SYSTEM, PackageGuard.kind(ApplicationInfo.FLAG_SYSTEM));
        assertEquals(PackageGuard.UPDATED_SYSTEM,
                PackageGuard.kind(ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP));
    }

    @Test
    public void userDockRoundTrip() {
        assertNull(UserDock.decode(null));
        assertNull(UserDock.decode("not a dock"));
        assertEquals(Arrays.asList("com.a.b", "com.c.d"),
                UserDock.decode("tvhome-user-dock 1\ncom.a.b\ncom.c.d\n"));
        assertEquals(0, UserDock.decode("tvhome-user-dock 1\n").size());
    }
}
