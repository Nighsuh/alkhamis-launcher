package com.gathin.tvhome;

import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Today's Hijri date in the launcher language, including its digits. */
final class HijriDate {
    private HijriDate() {}

    static String today(Locale locale) {
        String text = DateTimeFormatter.ofPattern("d MMMM y", locale).format(HijrahDate.now());
        return text + " هـ";
    }
}
