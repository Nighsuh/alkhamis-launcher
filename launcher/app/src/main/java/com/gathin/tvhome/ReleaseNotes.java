package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** GitHub release list for Nighsuh/alkhamis-launcher, and how a tag compares with this install. */
final class ReleaseNotes {
    static final String API = "https://api.github.com/repos/Nighsuh/alkhamis-launcher/releases";

    final String tag;
    final String title;
    final String body;

    private ReleaseNotes(String tag, String title, String body) {
        this.tag = tag;
        this.title = title;
        this.body = body;
    }

    static List<ReleaseNotes> parse(String json) throws org.json.JSONException {
        JSONArray array = new JSONArray(json);
        List<ReleaseNotes> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            if (item.optBoolean("draft")) continue;
            String tag = item.optString("tag_name", "");
            if (!safeTag(tag)) continue;
            String name = item.optString("name", "").trim();
            String body = item.optString("body", "").trim();
            out.add(new ReleaseNotes(tag, name.isEmpty() ? tag : name, body));
        }
        return out;
    }

    /** Tags we are willing to put in a download URL. */
    static boolean safeTag(String tag) {
        return tag != null && tag.matches("v[0-9][A-Za-z0-9._-]*") && tag.length() <= 64;
    }

    static String assetUrl(String tag) {
        if (!safeTag(tag)) throw new IllegalArgumentException(tag);
        return "https://github.com/Nighsuh/alkhamis-launcher/releases/download/"
                + tag + "/CleanYourTV-Shrine.apk";
    }

    /**
     * Positive when {@code tag} is newer than the installed versionName, zero when they match,
     * negative when the tag is older. "v3.2-shrine" and "3.2-shrine" are the same release.
     */
    static int compare(String installed, String tag) {
        int[] have = numbers(installed);
        int[] next = numbers(tag);
        int n = Math.max(have.length, next.length);
        for (int i = 0; i < n; i++) {
            int a = i < have.length ? have[i] : 0;
            int b = i < next.length ? next[i] : 0;
            if (a != b) return b - a;
        }
        return 0;
    }

    private static int[] numbers(String text) {
        if (text == null) return new int[0];
        int start = 0;
        if (text.startsWith("v") || text.startsWith("V")) start = 1;
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            if ((c >= '0' && c <= '9') || c == '.') end++;
            else break;
        }
        String[] parts = text.substring(start, end).split("\\.");
        int count = 0;
        int[] values = new int[parts.length];
        for (String part : parts) {
            if (part.isEmpty()) continue;
            try { values[count++] = Integer.parseInt(part); }
            catch (NumberFormatException ignored) { }
        }
        if (count == values.length) return values;
        int[] trimmed = new int[count];
        System.arraycopy(values, 0, trimmed, 0, count);
        return trimmed;
    }
}
