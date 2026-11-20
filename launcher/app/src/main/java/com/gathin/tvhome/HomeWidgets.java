package com.gathin.tvhome;

import android.app.Activity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether Home should build an {@code AppWidgetHost}. The host class is not touched until
 * {@link #mount} runs, which is only when a widget id is saved or a measurement asks for one.
 * A line {@code native:prayer} is the built-in prayer card. It is not an app-widget id.
 */
final class HomeWidgets {
    static final String NATIVE_PRAYER = "native:prayer";

    interface Life {
        void start();
        void stop();
        void tick(android.content.Context ctx);
    }

    private HomeWidgets() {}

    static boolean wanted(Activity activity) {
        return activity.getIntent().getBooleanExtra("measure_widgets", false) || hasIds(activity);
    }

    static Life mount(Activity activity) {
        return WidgetStrip.attach(activity);
    }

    static boolean hasIds(Activity activity) {
        return !lines(activity).isEmpty();
    }

    static boolean hasPrayer(Activity activity) {
        return lines(activity).contains(NATIVE_PRAYER);
    }

    static void addPrayer(Activity activity) {
        List<String> next = lines(activity);
        if (!next.contains(NATIVE_PRAYER)) next.add(0, NATIVE_PRAYER);
        writeLines(activity, next);
    }

    static void removePrayer(Activity activity) {
        List<String> next = lines(activity);
        next.remove(NATIVE_PRAYER);
        writeLines(activity, next);
    }

    /** App-widget ids only. Native cards are kept when this list is written back. */
    static int[] read(Activity activity) {
        List<Integer> ids = new ArrayList<>();
        for (String line : lines(activity)) {
            try { ids.add(Integer.parseInt(line)); }
            catch (NumberFormatException ignored) { }
        }
        int[] out = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) out[i] = ids.get(i);
        return out;
    }

    static void write(Activity activity, int[] ids) {
        List<String> next = new ArrayList<>();
        for (String line : lines(activity)) if (line.startsWith("native:")) next.add(line);
        for (int id : ids) next.add(Integer.toString(id));
        writeLines(activity, next);
    }

    static void removeAppWidget(Activity activity, int id) {
        List<String> next = lines(activity);
        next.remove(Integer.toString(id));
        writeLines(activity, next);
    }

    private static List<String> lines(Activity activity) {
        List<String> out = new ArrayList<>();
        File file = new File(activity.getFilesDir(), "widget-ids.txt");
        if (!file.isFile()) return out;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = in.read(buf);
            String text = new String(buf, 0, Math.max(0, n), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return out;
            if (text.startsWith("{")) {
                try {
                    org.json.JSONObject o = new org.json.JSONObject(text);
                    if (o.optBoolean("prayer", false) || "prayer".equals(o.optString("card"))) out.add(NATIVE_PRAYER);
                    org.json.JSONArray ids = o.optJSONArray("ids");
                    if (ids != null) {
                        for (int i = 0; i < ids.length(); i++) out.add(Integer.toString(ids.optInt(i, -1)));
                    }
                } catch (org.json.JSONException ignored) { }
                out.removeIf(line -> "-1".equals(line));
                if (!out.isEmpty()) writeLines(activity, out);
                return out;
            }
            boolean migrate = false;
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if ("prayer".equals(trimmed)) {
                    trimmed = NATIVE_PRAYER;
                    migrate = true;
                }
                out.add(trimmed);
            }
            if (migrate) writeLines(activity, out);
        } catch (Exception ignored) { }
        return out;
    }

    private static void writeLines(Activity activity, List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (String line : lines) text.append(line).append('\n');
        File file = new File(activity.getFilesDir(), "widget-ids.txt");
        if (lines.isEmpty()) {
            if (file.isFile() && !file.delete()) writeBytes(file, new byte[0]);
            return;
        }
        writeBytes(file, text.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(File file, byte[] bytes) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        } catch (Exception ignored) { }
    }
}
