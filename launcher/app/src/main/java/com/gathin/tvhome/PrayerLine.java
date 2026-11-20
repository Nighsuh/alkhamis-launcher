package com.gathin.tvhome;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
/** One line for the dream: the next prayer. The cache is optional; the times are calculated when it is missing. */
final class PrayerLine {
    private static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    private PrayerLine() {}

    /** The line the dream and the old widget show. Computed here when the cache is missing or unreadable. */
    static String line(Context ctx) {
        String cached = cached(ctx);
        if (cached != null && !cached.isEmpty()) return cached;
        return local(ctx);
    }

    static String cached(Context ctx) {
        File file = new File(ctx.getFilesDir(), "prayer-line.json");
        if (!file.isFile()) return "";
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = in.read(buf);
            JSONObject o = new JSONObject(new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8));
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(PrayerTimes.RIYADH);
            int minute = now.getHour() * 60 + now.getMinute();
            int next = -1;
            int nextMin = 0;
            for (int i = 0; i < NAMES.length; i++) {
                int min = minutes(o, NAMES[i]);
                if (min > minute) { next = i; nextMin = min; break; }
            }
            if (next < 0) return ctx.getString(R.string.prayer_tomorrow, ctx.getString(R.string.prayer_fajr)
                    + "  ·  " + ClockStyle.formatMinutes(ctx, minutes(o, NAMES[0])));
            return format(ctx, next, nextMin, nextMin - minute);
        } catch (Exception e) {
            return "";
        }
    }

    /** "04:45", "04:45 (AST)", or a minute count already stored as a number. */
    static int minutes(JSONObject o, String name) {
        if (o == null || !o.has(name)) return -1;
        Object value = o.opt(name);
        if (value instanceof Number) {
            int min = ((Number) value).intValue();
            return min >= 0 && min < 24 * 60 ? min : -1;
        }
        return clockMinutes(String.valueOf(value));
    }

    /** "04:45", "04:45 (AST)", or a minute count stored as text. */
    static int clockMinutes(String raw) {
        if (raw == null) return -1;
        String text = raw.trim();
        int space = text.indexOf(' ');
        if (space > 0) text = text.substring(0, space);
        if (text.length() >= 5 && text.charAt(2) == ':') {
            try {
                int h = Integer.parseInt(text.substring(0, 2));
                int m = Integer.parseInt(text.substring(3, 5));
                if (h >= 0 && h < 24 && m >= 0 && m < 60) return h * 60 + m;
            } catch (NumberFormatException ignored) { }
        }
        try {
            int min = Integer.parseInt(text);
            return min >= 0 && min < 24 * 60 ? min : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String format(Context ctx, int index, int minute, int left) {
        String wait = left >= 60
                ? ctx.getString(R.string.prayer_wait_hm, left / 60, left % 60)
                : ctx.getString(R.string.prayer_wait_m, Math.max(0, left));
        int name = new int[]{R.string.prayer_fajr, R.string.prayer_dhuhr, R.string.prayer_asr,
                R.string.prayer_maghrib, R.string.prayer_isha}[index];
        return ctx.getString(R.string.prayer_next_line, ctx.getString(name),
                ClockStyle.formatMinutes(ctx, minute), wait);
    }

    static String local(Context ctx) {
        try {
            int[] times = PrayerTimes.forDate(java.time.LocalDate.now(PrayerTimes.RIYADH),
                    PrayerData.lat(ctx), PrayerData.lon(ctx), PrayerData.method(ctx));
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(PrayerTimes.RIYADH);
            int minute = now.getHour() * 60 + now.getMinute();
            for (int i = 0; i < times.length; i++) {
                if (times[i] > minute) return format(ctx, i, times[i], times[i] - minute);
            }
            return ctx.getString(R.string.prayer_tomorrow,
                    ctx.getString(R.string.prayer_fajr) + "  ·  " + ClockStyle.formatMinutes(ctx, times[0]));
        } catch (RuntimeException e) {
            return "";
        }
    }

    static void refresh(Context ctx) {
        new Thread(() -> PrayerData.fetch(ctx), "prayer-refresh").start();
    }
}
