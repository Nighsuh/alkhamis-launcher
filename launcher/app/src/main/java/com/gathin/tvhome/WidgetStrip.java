package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.time.ZonedDateTime;
import java.util.Locale;

/** The home-screen widget row. Constructed only after {@link HomeWidgets#wanted} is true. */
final class WidgetStrip implements HomeWidgets.Life {
    static final int BIND_REQUEST = 4401;
    static final int HOST_ID = 0x53485249;
    private static final int[] PRAYER_NAMES = {
            R.string.prayer_fajr, R.string.prayer_sunrise, R.string.prayer_dhuhr,
            R.string.prayer_asr, R.string.prayer_maghrib, R.string.prayer_isha
    };
    private static final int[] PRAYER_ICONS = {
            R.drawable.ic_prayer_fajr, R.drawable.ic_prayer_sunrise, R.drawable.ic_prayer_dhuhr,
            R.drawable.ic_prayer_asr, R.drawable.ic_prayer_maghrib, R.drawable.ic_prayer_isha
    };
    /** Sunrise is shown, but the highlight follows the next prayer, not sunrise. */
    private static final int[] PRAYER_SLOTS = {0, 2, 3, 4, 5};
    private static final int TEAL = 0xFF3DDCB4;

    private static AppWidgetHost host;
    private static int pendingId = -1;
    private LinearLayout prayerRoot;
    private final GradientDrawable[] cardBg = new GradientDrawable[PRAYER_NAMES.length];
    private final TextView[] timeViews = new TextView[PRAYER_NAMES.length];
    private final TextView[] waitViews = new TextView[PRAYER_NAMES.length];
    private String[] englishNames = new String[PRAYER_NAMES.length];

    private WidgetStrip() {}

    static HomeWidgets.Life attach(Activity activity) {
        if (host == null) host = new AppWidgetHost(activity.getApplicationContext(), HOST_ID);
        int[] ids = HomeWidgets.read(activity);
        if (ids.length == 0 && activity.getIntent().getBooleanExtra("measure_widgets", false)) {
            int id = bindClock(activity);
            if (id >= 0) {
                ids = new int[]{id};
                HomeWidgets.write(activity, ids);
            }
        }
        boolean prayer = HomeWidgets.hasPrayer(activity);
        if (ids.length == 0 && !prayer) return new WidgetStrip();
        View grid = activity.findViewById(R.id.grid);
        if (!(grid != null && grid.getParent() instanceof FrameLayout)) return new WidgetStrip();
        FrameLayout frame = (FrameLayout) grid.getParent();
        WidgetStrip life = new WidgetStrip();
        LinearLayout slot = new LinearLayout(activity);
        slot.setId(R.id.widgetStrip);
        slot.setOrientation(prayer ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(12 * density);
        slot.setPadding(pad, 0, pad, 0);
        if (prayer) slot.addView(life.prayerCards(activity, density));
        LinearLayout hostRow = slot;
        if (prayer && ids.length > 0) {
            hostRow = new LinearLayout(activity);
            hostRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = pad / 2;
            slot.addView(hostRow, rowLp);
        }
        try { host.startListening(); } catch (RuntimeException ignored) { }
        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        for (int id : ids) {
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            if (info == null) continue;
            try {
                AppWidgetHostView view = host.createView(activity, id, info);
                view.setAppWidget(id, info);
                int width = Math.round(300 * density);
                int height = Math.round(96 * density);
                view.setMinimumWidth(width);
                view.setMinimumHeight(height);
                view.updateAppWidgetSize(null, 300, 96, 300, 96);
                prepareFocus(view);
                view.setOnClickListener(v -> confirmRemove(activity, slot, () -> {
                    HomeWidgets.removeAppWidget(activity, id);
                    host.deleteAppWidgetId(id);
                }));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
                lp.setMarginEnd(pad);
                hostRow.addView(view, lp);
            } catch (RuntimeException e) {
                Toast.makeText(activity, activity.getString(R.string.widgets_failed), Toast.LENGTH_LONG).show();
            }
        }
        if (slot.getChildCount() == 0) return life;
        // Inside the grid's frame, not above it: the frame clips, and the grid's scroll moves this row
        // with the dock. A row added to the column above the grid stays pinned while the apps scroll.
        FrameLayout.LayoutParams slotLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slotLp.gravity = Gravity.TOP | Gravity.START;
        frame.setClipChildren(true);
        frame.addView(slot, slotLp);
        slot.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) grid.requestLayout();
        });
        return life;
    }

    private static int bindClock(Activity activity) {
        int id = host.allocateAppWidgetId();
        ComponentName provider = new ComponentName(activity, ClockWidget.class);
        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        if (manager.bindAppWidgetIdIfAllowed(id, provider)) return id;
        // The system asks before a normal app may host a widget. The id stays allocated for that dialog.
        Intent bind = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider);
        pendingId = id;
        activity.startActivityForResult(bind, BIND_REQUEST);
        return -1;
    }

    static int takePending() {
        int id = pendingId;
        pendingId = -1;
        return id;
    }

    static void drop(int id) {
        if (host != null && id >= 0) host.deleteAppWidgetId(id);
    }

    @Override public void start() {
        if (host == null) return;
        try {
            host.startListening();
        } catch (RuntimeException ignored) { }
    }

    @Override public void stop() {
        if (host == null) return;
        try {
            host.stopListening();
        } catch (RuntimeException ignored) { }
    }

    @Override public void tick(android.content.Context ctx) {
        PrayerWidget.push(ctx);
        if (prayerRoot != null && ctx instanceof Activity) refreshPrayer((Activity) ctx);
    }

    private View prayerCards(Activity activity, float density) {
        prayerRoot = new LinearLayout(activity);
        prayerRoot.setOrientation(LinearLayout.VERTICAL);
        int dir = activity.getResources().getConfiguration().getLayoutDirection();
        prayerRoot.setLayoutDirection(dir);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(dir);
        int gap = Math.round(6 * density);
        String[] arabic = texts(activity, "ar");
        englishNames = texts(activity, "en");
        String[] english = englishNames;
        for (int i = 0; i < PRAYER_NAMES.length; i++) {
            row.addView(oneCard(activity, density, i, arabic[i], english[i], i == PRAYER_NAMES.length - 1 ? 0 : gap));
        }
        prayerRoot.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView footer = label(activity, "", 12, 0x99B7C4D0);
        footer.setTag("footer");
        LinearLayout.LayoutParams footLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footLp.topMargin = Math.round(4 * density);
        prayerRoot.addView(footer, footLp);
        prepareFocus(prayerRoot);
        prayerRoot.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        prayerRoot.setContentDescription(activity.getString(R.string.prayer_title));
        prayerRoot.setOnClickListener(v -> confirmRemove(activity, (LinearLayout) prayerRoot.getParent(),
                () -> HomeWidgets.removePrayer(activity)));
        prayerRoot.setOnFocusChangeListener((v, focused) -> refreshPrayer(activity));
        refreshPrayer(activity);
        return prayerRoot;
    }

    private View oneCard(Activity activity, float density, int index, String arabic, String english, int gap) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = Math.round(6 * density);
        card.setPadding(pad, pad, pad, Math.round(5 * density));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(12 * density);
        cardBg[index] = bg;
        card.setBackground(bg);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(PRAYER_ICONS[index]);
        int iconPx = Math.round(18 * density);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconPx, iconPx);
        iconLp.bottomMargin = Math.round(2 * density);
        card.addView(icon, iconLp);
        TextView ar = label(activity, arabic, 13, 0xFFFFFFFF);
        ar.setGravity(Gravity.CENTER);
        ar.setTypeface(Typeface.create(ar.getTypeface(), Typeface.BOLD));
        card.addView(ar);
        TextView en = label(activity, english, 10, 0xFFC5D0DE);
        en.setGravity(Gravity.CENTER);
        card.addView(en);
        TextView time = label(activity, "", 15, 0xFFFFFFFF);
        time.setGravity(Gravity.CENTER);
        time.setTextDirection(View.TEXT_DIRECTION_LTR);
        time.setTypeface(Typeface.create(time.getTypeface(), Typeface.BOLD));
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeLp.topMargin = Math.round(2 * density);
        card.addView(time, timeLp);
        timeViews[index] = time;
        TextView wait = label(activity, " ", 10, 0x00000000);
        wait.setGravity(Gravity.CENTER);
        wait.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        wait.setTextDirection(View.TEXT_DIRECTION_LTR);
        card.addView(wait);
        waitViews[index] = wait;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (gap > 0) lp.setMarginEnd(gap);
        card.setLayoutParams(lp);
        return card;
    }

    private void refreshPrayer(Activity activity) {
        if (prayerRoot == null) return;
        int[] times = PrayerTimes.day(java.time.LocalDate.now(PrayerTimes.RIYADH),
                PrayerData.lat(activity), PrayerData.lon(activity), PrayerData.method(activity));
        ZonedDateTime now = ZonedDateTime.now(PrayerTimes.RIYADH);
        int minute = now.getHour() * 60 + now.getMinute();
        int next = nextPrayer(times, minute);
        boolean focused = prayerRoot.isFocused();
        float density = activity.getResources().getDisplayMetrics().density;
        int stroke = Math.max(2, Math.round(1.5f * density));
        StringBuilder spoken = new StringBuilder();
        for (int i = 0; i < times.length; i++) {
            String clock = cardTime(activity, times[i]);
            timeViews[i].setText(clock);
            boolean on = i == next;
            waitViews[i].setText(on ? countdown(activity, times[i], minute) : " ");
            waitViews[i].setTextColor(on ? TEAL : 0x00000000);
            cardBg[i].setColor(on ? 0xE0283A52 : 0xB3182436);
            cardBg[i].setStroke(on ? stroke : 0, focused && on ? 0xFFFFFFFF : TEAL);
            spoken.append(englishNames[i]).append(' ').append(clock).append(' ');
        }
        TextView footer = prayerRoot.findViewWithTag("footer");
        if (footer != null) {
            footer.setText(placeName(activity) + "  ·  " + activity.getString(methodName(PrayerData.method(activity))));
        }
        prayerRoot.setContentDescription(spoken.toString().trim());
    }

    private static int nextPrayer(int[] times, int minute) {
        for (int slot : PRAYER_SLOTS) {
            if (times[slot] > minute) return slot;
        }
        return PRAYER_SLOTS[0];
    }

    private static String countdown(Activity activity, int target, int minute) {
        int left = target - minute;
        if (left <= 0) left += 24 * 60;
        boolean ar = arabic(activity);
        // Left-to-right override keeps "1 س 40 د" from swapping the two Arabic letters.
        if (left >= 60) {
            String body = String.format(Locale.US, ar ? "%d س %d د" : "%dh %dm", left / 60, left % 60);
            return ar ? "\u202D" + body + "\u202C" : body;
        }
        String body = String.format(Locale.US, ar ? "%d د" : "%dm", left);
        return ar ? "\u202D" + body + "\u202C" : body;
    }

    /** 12-hour or 24-hour, with Western digits. Arabic uses ص and م. */
    private static String cardTime(Activity activity, int minutes) {
        int h = Math.floorMod(minutes / 60, 24);
        int m = Math.floorMod(minutes, 60);
        if (ClockStyle.is24(activity)) return String.format(Locale.US, "%02d:%02d", h, m);
        int h12 = h % 12;
        if (h12 == 0) h12 = 12;
        String marker = arabic(activity) ? (h < 12 ? "ص" : "م") : (h < 12 ? "AM" : "PM");
        return String.format(Locale.US, "%d:%02d %s", h12, m, marker);
    }

    private static String[] texts(Activity activity, String lang) {
        android.content.res.Configuration config = new android.content.res.Configuration(
                activity.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(lang));
        android.content.Context localized = activity.createConfigurationContext(config);
        String[] out = new String[PRAYER_NAMES.length];
        for (int i = 0; i < PRAYER_NAMES.length; i++) out[i] = localized.getString(PRAYER_NAMES[i]);
        return out;
    }

    private static boolean arabic(Activity activity) {
        return "ar".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    private static String placeName(Activity activity) {
        String place = PrayerData.place(activity);
        boolean ar = "ar".equals(activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
        for (String[] row : PrayerData.PLACES) {
            if (row[0].equals(place)) return ar ? row[1] : row[0];
        }
        return place;
    }

    private static int methodName(int method) {
        switch (method) {
            case 1: return R.string.method_1;
            case 2: return R.string.method_2;
            case 3: return R.string.method_3;
            case 4: return R.string.method_4;
            case 5: return R.string.method_5;
            case 7: return R.string.method_7;
            default: return R.string.method_0;
        }
    }

    private static TextView label(Activity activity, String text, int sp, int color) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static void prepareFocus(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);
        view.setNextFocusDownId(R.id.grid);
        view.setNextFocusUpId(R.id.btnSettings);
    }

    private static void confirmRemove(Activity activity, LinearLayout slot, Runnable remove) {
        new AlertDialog.Builder(activity)
                .setMessage(R.string.widgets_remove)
                .setPositiveButton(R.string.widgets_remove_yes, (dialog, which) -> {
                    remove.run();
                    HomeEdits.notifyChanged(activity);
                    Toast.makeText(activity, R.string.widgets_removed, Toast.LENGTH_LONG).show();
                    activity.recreate();
                })
                .setNegativeButton(R.string.widgets_remove_no, null)
                .show();
    }
}
