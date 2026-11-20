package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PrayerLineTest {
    @Test
    public void clockMinutesAcceptsACountAClockAndAnOldString() {
        assertEquals(284, PrayerLine.clockMinutes("284"));
        assertEquals(11 * 60 + 23, PrayerLine.clockMinutes("11:23"));
        assertEquals(14 * 60 + 30, PrayerLine.clockMinutes("14:30 (AST)"));
        assertEquals(-1, PrayerLine.clockMinutes("nope"));
        assertEquals(-1, PrayerLine.clockMinutes(null));
    }
}
