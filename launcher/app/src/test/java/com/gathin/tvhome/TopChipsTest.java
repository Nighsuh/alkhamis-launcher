package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TopChipsTest {
    @Test
    public void defaultsKeepTheShrineChips() {
        List<TopChips.Chip> chips = TopChips.defaults();
        assertTrue(on(chips, TopChips.NETWORK));
        assertTrue(on(chips, TopChips.INPUTS));
        assertTrue(on(chips, TopChips.SETTINGS));
        assertFalse(on(chips, TopChips.WEATHER));
        assertFalse(on(chips, TopChips.BLUETOOTH));
    }

    @Test
    public void aBadLineIsRejected() {
        assertNull(TopChips.decode(Arrays.asList("+not-a-chip")));
        assertNull(TopChips.decode(Arrays.asList("network")));
    }

    @Test
    public void orderAndSwitchesRoundTrip() {
        List<TopChips.Chip> chips = TopChips.decode(Arrays.asList("+settings", "-wifi", "+network"));
        assertEquals(TopChips.SETTINGS, chips.get(0).id);
        assertTrue(chips.get(0).on);
        assertEquals(TopChips.WIFI, chips.get(1).id);
        assertFalse(chips.get(1).on);
        assertEquals(TopChips.NETWORK, chips.get(2).id);
        assertEquals(TopChips.ORDER.length, chips.size());
    }

    private static boolean on(List<TopChips.Chip> chips, String id) {
        for (TopChips.Chip chip : chips) if (chip.id.equals(id)) return chip.on;
        return false;
    }
}
