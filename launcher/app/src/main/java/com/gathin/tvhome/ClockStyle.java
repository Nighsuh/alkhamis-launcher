package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.TextClock;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.chrono.HijrahDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Launcher clock, date and Hijri choices. Umm al-Qura via {@link HijrahDate}. */
final class ClockStyle {
    static final String PREFS = "clock_style";
    private static final String[] HIJRI_EN = {
            "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Awwal", "Jumada al-Thani",
            "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhu al-Qidah", "Dhu al-Hijjah"
    };
    private static final String[] HIJRI_AR = {
            "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    };

    private ClockStyle() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean is24(Context ctx) {
        String hour = prefs(ctx).getString("hour", "");
        if ("24".equals(hour)) return true;
        if ("12".equals(hour)) return false;
        return DateFormat.is24HourFormat(ctx);
    }

    static boolean seconds(Context ctx) { return prefs(ctx).getBoolean("seconds", false); }

    static String dateKind(Context ctx) {
        String kind = prefs(ctx).getString("date", "long");
        if ("dmy".equals(kind) || "iso".equals(kind)) return kind;
        return "long";
    }

    static String calendar(Context ctx) {
        String mode = prefs(ctx).getString("calendar", "gregorian");
        if ("hijri".equals(mode) || "both".equals(mode)) return mode;
        return "gregorian";
    }

    static boolean gregorianOnly(Context ctx) { return "gregorian".equals(calendar(ctx)); }

    static int hijriOffset(Context ctx) {
        int offset = prefs(ctx).getInt("hijri_offset", 0);
        return Math.max(-2, Math.min(2, offset));
    }

    static String zone(Context ctx) {
        String zone = prefs(ctx).getString("zone", "");
        if (zone == null || zone.isEmpty()) return "";
        try {
            ZoneId.of(zone);
            return zone;
        } catch (Exception e) {
            return "";
        }
    }

    static ZoneId zoneId(Context ctx) {
        String zone = zone(ctx);
        if (zone.isEmpty()) return ZoneId.systemDefault();
        return ZoneId.of(zone);
    }

    static void apply(TextClock time, TextClock ampm, Context ctx) {
        boolean h24 = is24(ctx);
        // A clock with no separate AM/PM view has to carry the marker itself, or 12-hour time is just "7:54".
        String pattern = h24 ? (seconds(ctx) ? "HH:mm:ss" : "HH:mm")
                : ampm == null ? (seconds(ctx) ? "h:mm:ss a" : "h:mm a")
                : (seconds(ctx) ? "h:mm:ss" : "h:mm");
        time.setFormat12Hour(pattern);
        time.setFormat24Hour(pattern);
        String zone = zone(ctx);
        time.setTimeZone(zone.isEmpty() ? null : zone);
        if (ampm == null) return;
        if (h24) {
            ampm.setFormat12Hour("");
            ampm.setFormat24Hour("");
            ampm.setVisibility(View.GONE);
        } else {
            ampm.setFormat12Hour("a");
            ampm.setFormat24Hour("a");
            ampm.setVisibility(View.VISIBLE);
            ampm.setTimeZone(zone.isEmpty() ? null : zone);
        }
    }

    static String gregorianPattern(Context ctx) {
        String kind = dateKind(ctx);
        if ("dmy".equals(kind)) return "dd/MM/yyyy";
        if ("iso".equals(kind)) return "yyyy-MM-dd";
        return "EEEE d MMMM";
    }

    static String dateLine(Context ctx) {
        Locale locale = ctx.getResources().getConfiguration().getLocales().get(0);
        return dateLine(locale, LocalDate.now(zoneId(ctx)), calendar(ctx), dateKind(ctx), hijriOffset(ctx));
    }

    static String dateLine(Locale locale, LocalDate day, String calendar, String kind, int offset) {
        String gregorian = gregorian(locale, day, kind);
        String hijri = hijri(locale, day, offset);
        if ("hijri".equals(calendar)) return hijri;
        if ("both".equals(calendar)) {
            boolean ar = locale != null && "ar".equals(locale.getLanguage());
            String left = ar ? gregorian(locale, day, "long").replaceAll(" \\d{4}$", "") : gregorian;
            return left + "  ·  " + hijri;
        }
        return gregorian;
    }

    private static String gregorian(Locale locale, LocalDate day, String kind) {
        String pattern = "dmy".equals(kind) ? "dd/MM/yyyy" : "iso".equals(kind) ? "yyyy-MM-dd" : "EEEE d MMMM yyyy";
        return DateTimeFormatter.ofPattern(pattern, locale).format(day);
    }

    private static String hijri(Locale locale, LocalDate day, int offset) {
        HijrahDate date = HijrahDate.from(day);
        if (offset != 0) date = date.plus(offset, ChronoUnit.DAYS);
        boolean ar = locale != null && "ar".equals(locale.getLanguage());
        int month = date.get(java.time.temporal.ChronoField.MONTH_OF_YEAR);
        String name = (ar ? HIJRI_AR : HIJRI_EN)[Math.max(0, Math.min(11, month - 1))];
        String dayText = DateTimeFormatter.ofPattern("d", locale).format(date);
        String yearText = DateTimeFormatter.ofPattern("y", locale).format(date);
        return ar ? dayText + " " + name + " " + yearText + " هـ" : dayText + " " + name + " " + yearText;
    }

    static String formatMinutes(Context ctx, int minutes) {
        int h = ((minutes / 60) % 24 + 24) % 24;
        int m = ((minutes % 60) + 60) % 60;
        Locale locale = ctx.getResources().getConfiguration().getLocales().get(0);
        String pattern = is24(ctx) ? "HH:mm" : "h:mm a";
        return DateTimeFormatter.ofPattern(pattern, locale).format(java.time.LocalTime.of(h, m));
    }
}
