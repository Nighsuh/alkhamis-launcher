package com.gathin.tvhome;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;

/** Prayer times on the home widget row. Updated from the home minute tick, not a background service. */
public class PrayerWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        RemoteViews views = views(context);
        for (int id : ids) manager.updateAppWidget(id, views);
    }

    static void push(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerWidget.class));
        if (ids == null || ids.length == 0) return;
        manager.updateAppWidget(ids, views(context));
    }

    private static RemoteViews views(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_prayer);
        views.setTextViewText(R.id.widgetTitle, context.getString(R.string.prayer_title));
        String line = PrayerLine.line(context);
        if (line == null || line.isEmpty()) line = ClockStyle.dateLine(context);
        views.setTextViewText(R.id.widgetLine, line);
        return views;
    }
}
