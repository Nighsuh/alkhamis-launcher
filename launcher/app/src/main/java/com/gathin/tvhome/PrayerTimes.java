package com.gathin.tvhome;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Prayer times for one day. Method 0 is Jafari: Fajr 16°, Maghrib 4°, Isha 14°, Asr when the
 * shadow equals the object (factor 1). The clock is Asia/Riyadh, which does not use daylight time.
 */
final class PrayerTimes {
    static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");

    private PrayerTimes() {}

    /** Minutes from local midnight, in order: Fajr, Dhuhr, Asr, Maghrib, Isha. */
    static int[] forDate(LocalDate date, double lat, double lng, int method) {
        int[] all = day(date, lat, lng, method);
        return new int[] {all[0], all[2], all[3], all[4], all[5]};
    }

    /** Minutes from local midnight: Fajr, sunrise, Dhuhr, Asr, Maghrib, Isha. */
    static int[] day(LocalDate date, double lat, double lng, int method) {
        Angles angles = angles(method);
        double jDate = julian(date.getYear(), date.getMonthValue(), date.getDayOfMonth()) - lng / 360.0;
        double fajr = 5, sunrise = 6, dhuhr = 12, asr = 13, maghrib = 18, isha = 18;
        for (int pass = 0; pass < 2; pass++) {
            dhuhr = midDay(jDate, dhuhr);
            fajr = sunAngleTime(jDate, angles.fajr, lat, fajr, true);
            sunrise = sunAngleTime(jDate, 0.833, lat, sunrise, true);
            maghrib = angles.maghrib >= 0
                    ? sunAngleTime(jDate, angles.maghrib, lat, maghrib, false)
                    : sunAngleTime(jDate, 0.833, lat, maghrib, false);
            isha = angles.ishaMinutes > 0
                    ? maghrib + angles.ishaMinutes / 60.0
                    : sunAngleTime(jDate, angles.isha, lat, isha, false);
            asr = asrTime(jDate, angles.asrFactor, lat, asr);
        }
        double shift = 3.0 - lng / 15.0;
        return new int[] {
                toMinutes(fajr + shift), toMinutes(sunrise + shift), toMinutes(dhuhr + shift),
                toMinutes(asr + shift), toMinutes(maghrib + shift), toMinutes(isha + shift)
        };
    }

    private static double midDay(double jDate, double time) {
        return fixHour(12 - sunPosition(jDate + time / 24.0)[1]);
    }

    private static double sunAngleTime(double jDate, double angle, double lat, double time, boolean beforeNoon) {
        double decl = sunPosition(jDate + time / 24.0)[0];
        double noon = midDay(jDate, time);
        double offset = hourAngle(angle, lat, decl);
        return noon + (beforeNoon ? -offset : offset);
    }

    private static double asrTime(double jDate, double factor, double lat, double time) {
        double decl = sunPosition(jDate + time / 24.0)[0];
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return sunAngleTime(jDate, angle, lat, time, false);
    }

    private static double hourAngle(double angle, double lat, double decl) {
        double top = -Math.sin(Math.toRadians(angle))
                - Math.sin(Math.toRadians(lat)) * Math.sin(Math.toRadians(decl));
        double bot = Math.cos(Math.toRadians(decl)) * Math.cos(Math.toRadians(lat));
        if (bot == 0) return 0;
        double cos = Math.max(-1, Math.min(1, top / bot));
        return Math.toDegrees(Math.acos(cos)) / 15.0;
    }

    /** Declination in degrees, equation of time in hours. */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15.0;
        return new double[] {Math.toDegrees(Math.asin(sin(e) * sin(l))), q / 15.0 - fixHour(ra)};
    }

    private static double julian(int year, int month, int day) {
        int y = year;
        int m = month;
        if (m <= 2) { y -= 1; m += 12; }
        int a = y / 100;
        int b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + day + b - 1524.5;
    }

    private static int toMinutes(double hours) {
        double fixed = fixHour(hours);
        int minutes = (int) Math.round(fixed * 60);
        if (minutes >= 24 * 60) minutes -= 24 * 60;
        if (minutes < 0) minutes += 24 * 60;
        return minutes;
    }

    private static double fixHour(double hour) {
        double wrapped = hour % 24;
        return wrapped < 0 ? wrapped + 24 : wrapped;
    }

    private static double fixAngle(double angle) {
        double wrapped = angle % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped;
    }

    private static double sin(double degrees) { return Math.sin(Math.toRadians(degrees)); }
    private static double cos(double degrees) { return Math.cos(Math.toRadians(degrees)); }

    /** Fajr angle, Maghrib angle (-1 means sunset), Isha angle, Isha minutes after Maghrib, Asr factor. */
    private static Angles angles(int method) {
        switch (method) {
            case 1: return new Angles(18, -1, 18, 0, 1);
            case 2: return new Angles(15, -1, 15, 0, 1);
            case 3: return new Angles(18, -1, 17, 0, 1);
            case 4: return new Angles(18.5, -1, 0, 90, 1);
            case 5: return new Angles(19.5, -1, 17.5, 0, 1);
            case 7: return new Angles(17.7, 4.5, 14, 0, 1);
            default: return new Angles(16, 4, 14, 0, 1);
        }
    }

    private static final class Angles {
        final double fajr, maghrib, isha;
        final int ishaMinutes;
        final double asrFactor;
        Angles(double fajr, double maghrib, double isha, int ishaMinutes, double asrFactor) {
            this.fajr = fajr;
            this.maghrib = maghrib;
            this.isha = isha;
            this.ishaMinutes = ishaMinutes;
            this.asrFactor = asrFactor;
        }
    }
}
