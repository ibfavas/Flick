package com.ibfavas.flick.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ibfavas.flick.MainActivity
import android.widget.RemoteViews
import com.ibfavas.flick.DrawActivity
import com.ibfavas.flick.R
import com.ibfavas.flick.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

abstract class VisiblePadWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val updates = Mutex()

        suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
            updates.withLock {
                val manager = AppWidgetManager.getInstance(context)
                val accent = SettingsStore(context).accentColor.first()
                for (wide in listOf(false, true)) {
                    val component = ComponentName(context, if (wide) FlickWideWidgetProvider::class.java else FlickRoundWidgetProvider::class.java)
                    val layout = if (wide) R.layout.widget_flick_wide else R.layout.widget_flick_round
                    fun artwork() = RemoteViews(context.packageName, layout).apply {
                        val tinted = if (wide) intArrayOf(R.id.wordmark_dot,
                            R.id.widget_divider, R.id.widget_glyph, R.id.widget_expand_icon, R.id.widget_edge)
                            else intArrayOf(R.id.widget_logo, R.id.widget_edge)
                        for (viewId in tinted) setInt(viewId, "setColorFilter", accent)
                    }
                    for (id in manager.getAppWidgetIds(component)) {
                        val intent = Intent(context, DrawActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        }
                        val requestCode = (if (wide) 200_000 else 100_000) + id
                        val views = artwork()
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        val draw = PendingIntent.getActivity(context, requestCode, intent, flags)
                        views.setOnClickPendingIntent(R.id.widget_root, draw)
                        if (wide) {
                            views.setOnClickPendingIntent(R.id.widget_draw, draw)
                            val openApp = Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                            views.setOnClickPendingIntent(R.id.widget_expand,
                                PendingIntent.getActivity(context, requestCode, openApp, flags))
                        }
                        manager.updateAppWidget(id, views)
                    }
                    if (Build.VERSION.SDK_INT >= 35) {
                        // Launcher preview throttling must not interrupt live widget updates.
                        try {
                            manager.setWidgetPreview(component, android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, artwork())
                        } catch (e: RuntimeException) {
                            android.util.Log.w("Flick", "Widget preview unavailable", e)
                        }
                    }
                }
            }
        }
    }
}
