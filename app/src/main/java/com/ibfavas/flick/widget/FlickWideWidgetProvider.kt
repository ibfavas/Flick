package com.ibfavas.flick.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ibfavas.flick.DrawActivity
import com.ibfavas.flick.R

/**
 * The wide widget (3x1): the flick wordmark — rounded lowercase white
 * letters with a cyan dot on the i — on a pitch-black background. The
 * whole widget is the button: tapping it opens the drawing pad. The
 * request code is offset so its PendingIntent can never collide with
 * the other widgets' (same target activity, same intent).
 */
class FlickWideWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, DrawActivity::class.java).apply {
                // EXCLUDE_FROM_RECENTS (v1.2.1): the pad must not
                // appear in the recents list.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                200_000 + appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_flick_wide).apply {
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
