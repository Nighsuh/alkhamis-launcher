package com.gathin.tvhome;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

/** A clock the home screen can host. It does not schedule its own alarms; TextClock ticks in the host. */
public class ClockWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, new RemoteViews(context.getPackageName(), R.layout.widget_clock));
        }
    }
}
