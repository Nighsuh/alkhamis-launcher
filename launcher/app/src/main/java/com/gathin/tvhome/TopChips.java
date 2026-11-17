package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.AlarmClock;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The top-right chips. A missing file is the shrine default (network, inputs, settings).
 * A chip whose intent does not resolve on this TV is left out even if it is switched on.
 */
final class TopChips {
    static final String NETWORK = "network";
    static final String WIFI = "wifi";
    static final String INPUTS = "inputs";
    static final String SETTINGS = "settings";
    static final String LAUNCHER = "launcher";
    static final String BLUETOOTH = "bluetooth";
    static final String DISPLAY = "display";
    static final String NOTIFICATIONS = "notifications";
    static final String SEARCH = "search";
    static final String CLOCK = "clock";
    static final String WEATHER = "weather";

    static final String[] ORDER = {
            NETWORK, WIFI, INPUTS, SETTINGS, LAUNCHER, BLUETOOTH, DISPLAY, NOTIFICATIONS, SEARCH, CLOCK, WEATHER
    };

    private static final String MAGIC = "tvhome-top-chips 1";
    private static final String FILE = "top_chips.txt";
    static final String TV_INPUT_SETTINGS = "android.settings.TV_INPUT_SETTINGS";

    private TopChips() {}

    static final class Chip {
        final String id;
        final boolean on;

        Chip(String id, boolean on) {
            this.id = id;
            this.on = on;
        }
    }

    /** Saved order, or the default when the owner has not chosen yet. */
    static List<Chip> stored(Context ctx) {
        List<String> lines = TextList.read(TextList.file(ctx, FILE), MAGIC);
        if (lines == null) return defaults();
        List<Chip> out = decode(lines);
        return out == null ? defaults() : out;
    }

    static boolean write(Context ctx, List<Chip> chips) {
        List<String> lines = new ArrayList<>();
        for (Chip chip : chips) lines.add((chip.on ? "+" : "-") + chip.id);
        return TextList.write(TextList.file(ctx, FILE), MAGIC, lines);
    }

    static List<Chip> defaults() {
        List<Chip> chips = new ArrayList<>();
        for (String id : ORDER) {
            boolean on = NETWORK.equals(id) || INPUTS.equals(id) || SETTINGS.equals(id);
            chips.add(new Chip(id, on));
        }
        return chips;
    }

    /** Null when a line is not one of the known chips. */
    static List<Chip> decode(List<String> lines) {
        List<Chip> out = new ArrayList<>();
        for (String line : lines) {
            if (line.length() < 2 || (line.charAt(0) != '+' && line.charAt(0) != '-')) return null;
            String id = line.substring(1);
            if (!known(id)) return null;
            out.add(new Chip(id, line.charAt(0) == '+'));
        }
        for (String id : ORDER) {
            boolean seen = false;
            for (Chip chip : out) seen |= chip.id.equals(id);
            if (!seen) out.add(new Chip(id, false));
        }
        return out;
    }

    static boolean known(String id) {
        for (String candidate : ORDER) if (candidate.equals(id)) return true;
        return false;
    }

    /** Chips this TV can open, in the saved order. */
    static List<Chip> available(Context ctx) {
        List<Chip> out = new ArrayList<>();
        for (Chip chip : stored(ctx)) if (resolves(ctx, chip.id)) out.add(chip);
        return out;
    }

    /** Enabled chips that also resolve, in order. This is the row Home shows. */
    static List<String> shown(Context ctx) {
        List<String> out = new ArrayList<>();
        for (Chip chip : available(ctx)) if (chip.on) out.add(chip.id);
        return out;
    }

    static boolean resolves(Context ctx, String id) {
        if (INPUTS.equals(id)) return inputsAvailable(ctx);
        if (LAUNCHER.equals(id)) return true;
        if (WEATHER.equals(id)) return weatherIntent(ctx) != null;
        return intent(ctx, id) != null;
    }

    static Intent intent(Context ctx, String id) {
        switch (id) {
            case NETWORK:
                return first(ctx, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS);
            case WIFI:
                return only(ctx, Settings.ACTION_WIFI_SETTINGS);
            case SETTINGS:
                return SystemSettings.intent(ctx, Settings.ACTION_SETTINGS);
            case BLUETOOTH:
                return only(ctx, Settings.ACTION_BLUETOOTH_SETTINGS);
            case DISPLAY:
                return first(ctx, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SOUND_SETTINGS);
            case NOTIFICATIONS:
                return only(ctx, "android.settings.NOTIFICATION_SETTINGS");
            case SEARCH:
                return first(ctx, Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND, "android.intent.action.VOICE_ASSIST");
            case CLOCK:
                return first(ctx, AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM);
            case INPUTS:
                return only(ctx, TV_INPUT_SETTINGS);
            default:
                return null;
        }
    }

    static boolean inputsAvailable(Context ctx) {
        if (only(ctx, TV_INPUT_SETTINGS) != null) return true;
        TvInputManager inputs = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (inputs == null) return false;
        for (TvInputInfo info : inputs.getTvInputList()) {
            if (info.isPassthroughInput() && !info.isHidden(ctx)) return true;
        }
        return false;
    }

    static Intent weatherIntent(Context ctx) {
        String[] packages = {
                "com.google.android.apps.weather",
                "com.google.android.apps.genie.geniewidget",
                "com.android.providers.weather"
        };
        PackageManager pm = ctx.getPackageManager();
        for (String pkg : packages) {
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch != null) return launch;
        }
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(main, 0)) {
            if (info.activityInfo == null) continue;
            CharSequence label = info.loadLabel(pm);
            if (label != null && label.toString().toLowerCase().contains("weather")) {
                return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setClassName(info.activityInfo.packageName, info.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        }
        return null;
    }

    private static Intent first(Context ctx, String... actions) {
        for (String action : actions) {
            Intent intent = only(ctx, action);
            if (intent != null) return intent;
        }
        return null;
    }

    private static Intent only(Context ctx, String action) {
        Intent intent = new Intent(action);
        if (ctx.getPackageManager().resolveActivity(intent, 0) == null) return null;
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static Map<String, Chip> byId(List<Chip> chips) {
        Map<String, Chip> map = new LinkedHashMap<>();
        for (Chip chip : chips) map.put(chip.id, chip);
        return map;
    }

    static List<String> ids(List<Chip> chips) {
        return new ArrayList<>(Arrays.asList(ORDER));
    }
}
