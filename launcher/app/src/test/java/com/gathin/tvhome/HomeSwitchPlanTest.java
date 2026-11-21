package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeSwitchPlanTest {
    @Test
    public void setupwraithAndLauncherxAreDenied() {
        assertTrue(HomeSwitch.denied(HomeSwitch.SETUP));
        assertTrue(HomeSwitch.denied(HomeSwitch.LAUNCHERX));
        assertTrue(HomeSwitch.denied(HomeSwitch.TV_LAUNCHER));
        assertTrue(HomeSwitch.denied(HomeSwitch.ASSISTANT[0]));
        assertTrue(HomeSwitch.denied("com.google.android.setupwizard"));
        assertTrue(HomeSwitch.denied("com.tcl.tv"));
        assertTrue(HomeSwitch.denied("com.vendor.oobe"));
        assertTrue(HomeSwitch.denied("com.vendor.provision"));
    }

    @Test
    public void googleTvDisablesNobodyEvenAfterHomeIsVerified() {
        List<String> candidates = Arrays.asList(HomeSwitch.SETUP, HomeSwitch.LAUNCHERX, HomeSwitch.TV_LAUNCHER);
        List<String> plan = HomeSwitch.disablePlan(candidates, Collections.emptySet(), false, true, true);
        assertTrue(plan.isEmpty());
    }

    @Test
    public void nothingIsDisabledBeforeHomeIsVerified() {
        List<String> candidates = Collections.singletonList("com.example.stocklauncher");
        List<String> plan = HomeSwitch.disablePlan(candidates, Collections.emptySet(), false, false, false);
        assertTrue(plan.isEmpty());
    }

    @Test
    public void aDeniedNameNeverEntersThePlan() {
        List<String> candidates = Arrays.asList(HomeSwitch.SETUP, "com.example.stocklauncher");
        List<String> plan = HomeSwitch.disablePlan(candidates, Collections.emptySet(), false, true, false);
        assertEquals(Collections.singletonList("com.example.stocklauncher"), plan);
    }

    @Test
    public void wifiOnWithoutAnOwnerDisablesNobody() {
        List<String> plan = HomeSwitch.disablePlan(
                Collections.singletonList("com.example.stocklauncher"), null, true, true, false);
        assertTrue(plan.isEmpty());
    }

    @Test
    public void aKnownOwnerStaysEnabled() {
        Set<String> owners = new HashSet<>();
        owners.add("com.example.stocklauncher");
        List<String> plan = HomeSwitch.disablePlan(
                Arrays.asList("com.example.stocklauncher", "com.example.other"), owners, true, true, false);
        assertEquals(Collections.singletonList("com.example.other"), plan);
        assertFalse(plan.contains(HomeSwitch.SETUP));
    }

    @Test
    public void disableCommandsForTheDenylistAreRefused() {
        String self = "com.gathin.tvhome";
        assertFalse(HomeSwitch.commandAllowed(HomeSwitch.disable(HomeSwitch.SETUP), self));
        assertFalse(HomeSwitch.commandAllowed(HomeSwitch.disable(HomeSwitch.LAUNCHERX), self));
        assertFalse(HomeSwitch.commandAllowed(HomeSwitch.disable(HomeSwitch.TV_LAUNCHER), self));
        assertTrue(HomeSwitch.commandAllowed(HomeSwitch.enable(HomeSwitch.SETUP), self));
        assertTrue(HomeSwitch.commandAllowed(HomeSwitch.enable(HomeSwitch.LAUNCHERX), self));
    }

    @Test
    public void theRecoveryListIncludesThePackages354CouldDisable() {
        List<String> names = HomeDisabled.recoveryDefaults();
        assertTrue(names.contains(HomeSwitch.LAUNCHERX));
        assertTrue(names.contains(HomeSwitch.SETUP));
        assertTrue(names.contains(HomeSwitch.TV_LAUNCHER));
        assertEquals(names, HomeDisabled.split(HomeDisabled.join(names)));
    }
}
