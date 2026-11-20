package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Place, method and the Aladhan timings file the dream already reads. */
final class PrayerData {
    static final String PREFS = "prayer";
    static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};
    static final int[] METHODS = {0, 1, 2, 3, 4, 5, 7};

    /** English, Arabic, latitude, longitude. Dammam is the default. */
    static final String[][] PLACES = {
            {"Dammam", "الدمام", "26.4207", "50.0888"},
            {"Khobar", "الخبر", "26.2172", "50.1971"},
            {"Qatif", "القطيف", "26.5654", "49.9965"},
            {"Riyadh", "الرياض", "24.7136", "46.6753"},
            {"Jeddah", "جدة", "21.4858", "39.1925"},
            {"Makkah", "مكة", "21.3891", "39.8579"},
            {"Madinah", "المدينة", "24.5247", "39.5692"},
            {"Kuwait", "الكويت", "29.3759", "47.9774"},
            {"Manama", "المنامة", "26.2285", "50.5860"},
            {"Doha", "الدوحة", "25.2854", "51.5310"},
            {"Abu Dhabi", "أبوظبي", "24.4539", "54.3773"},
            {"Dubai", "دبي", "25.2048", "55.2708"},
            {"Muscat", "مسقط", "23.5880", "58.3829"},
            {"Baghdad", "بغداد", "33.3152", "44.3661"},
            {"Najaf", "النجف", "32.0280", "44.3390"},
            {"Karbala", "كربلاء", "32.6160", "44.0249"},
            {"Basra", "البصرة", "30.5085", "47.7804"},
            {"Tehran", "طهران", "35.6892", "51.3890"},
            {"Qom", "قم", "34.6416", "50.8746"},
            {"Mashhad", "مشهد", "36.2605", "59.6168"},
            {"Beirut", "بيروت", "33.8938", "35.5018"},
            {"Cairo", "القاهرة", "30.0444", "31.2357"},
            {"Istanbul", "إسطنبول", "41.0082", "28.9784"},
            {"London", "لندن", "51.5074", "-0.1278"}
    };

    private PrayerData() {}

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String place(Context ctx) { return prefs(ctx).getString("place", "Dammam"); }
    static double lat(Context ctx) { return prefDouble(ctx, "lat", 26.4207); }
    static double lon(Context ctx) { return prefDouble(ctx, "lon", 50.0888); }
    static int method(Context ctx) {
        int method;
        try {
            method = prefs(ctx).getInt("method", 0);
        } catch (ClassCastException e) {
            try {
                method = Integer.parseInt(prefs(ctx).getString("method", "0").trim());
                prefs(ctx).edit().putInt("method", method).apply();
            } catch (RuntimeException ex) {
                prefs(ctx).edit().remove("method").apply();
                method = 0;
            }
        }
        for (int known : METHODS) if (known == method) return method;
        return 0;
    }

    /** Older builds stored the place as text. A bad type must not take the dream or settings down. */
    private static double prefDouble(Context ctx, String key, double fallback) {
        try {
            return Double.longBitsToDouble(prefs(ctx).getLong(key, Double.doubleToLongBits(fallback)));
        } catch (ClassCastException e) {
            try {
                double value = Double.parseDouble(prefs(ctx).getString(key, Double.toString(fallback)).trim());
                prefs(ctx).edit().putLong(key, Double.doubleToLongBits(value)).apply();
                return value;
            } catch (RuntimeException ex) {
                prefs(ctx).edit().remove(key).apply();
                return fallback;
            }
        }
    }

    static void save(Context ctx, String place, double lat, double lon, int method) {
        prefs(ctx).edit().putString("place", place).putLong("lat", Double.doubleToLongBits(lat))
                .putLong("lon", Double.doubleToLongBits(lon)).putInt("method", method).apply();
    }

    static String timingsUrl(Context ctx) {
        String day = LocalDate.now(ClockStyle.zoneId(ctx)).format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        return "https://api.aladhan.com/v1/timings/" + day
                + "?latitude=" + lat(ctx) + "&longitude=" + lon(ctx) + "&method=" + method(ctx);
    }

    /** Writes prayer-line.json. Returns false when the network call fails. */
    static boolean fetch(Context ctx) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(timingsUrl(ctx)).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "CleanYourTV-Shrine");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = new byte[4096];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            JSONObject timings = new JSONObject(out.toString("UTF-8")).getJSONObject("data").getJSONObject("timings");
            JSONObject saved = new JSONObject();
            for (String name : NAMES) {
                String value = timings.getString(name);
                int space = value.indexOf(' ');
                if (space > 0) value = value.substring(0, space);
                saved.put(name, Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3, 5)));
            }
            File file = new File(ctx.getFilesDir(), "prayer-line.json");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(saved.toString().getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
