package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeSwitchPlanTest {
    @Test
    public void wifiOnWithoutAnOwnerDisablesNobody() {
        List<String> plan = HomeSwitch.disablePlan(
                Arrays.asList(HomeSwitch.LAUNCHERX, HomeSwitch.SETUP), null, true);
        assertTrue(plan.isEmpty());
    }

    @Test
    public void aKnownOwnerStaysEnabled() {
        Set<String> owners = new HashSet<>();
        owners.add(HomeSwitch.SETUP);
        List<String> plan = HomeSwitch.disablePlan(
                Arrays.asList(HomeSwitch.LAUNCHERX, HomeSwitch.SETUP), owners, true);
        assertEquals(Collections.singletonList(HomeSwitch.LAUNCHERX), plan);
    }

    @Test
    public void wifiOffStillDisablesWhenTheDumpNamedNobody() {
        List<String> candidates = Collections.singletonList(HomeSwitch.LAUNCHERX);
        assertEquals(candidates, HomeSwitch.disablePlan(candidates, null, false));
    }
}
