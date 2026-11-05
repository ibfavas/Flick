package com.ibfavas.flick

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView

/**
 * Diagnostic screen (v1.0.2, fixed in v1.0.3): shows the crash report
 * captured by FlickApp in a plain ScrollView + TextView. Deliberately uses
 * no Compose, no DataStore and no coroutines, so it cannot crash the same
 * way the main app does. The user can screenshot this screen to share the
 * stack trace.
 *
 * v1.0.3 fix: v1.0.2 read the report only from the static CrashReportHolder,
 * which is null in this activity's fresh process (statics do not survive
 * process death). The report is now read in order: (a) the Intent extra
 * "report" (survives process death), (b) the static holder, (c) the newest
 * flick-crash-*.txt file in the crashes directory.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = loadReport()
        val textView = TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(24, 24, 24, 24)
        }
        val scrollView = ScrollView(this).apply {
            addView(
                textView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        setContentView(scrollView)
    }

    private fun loadReport(): String {
        // (a) Intent extra — the primary channel, survives process death.
        val fromIntent = try {
            intent?.getStringExtra("report")
        } catch (_: Exception) {
            null
        }
        if (!fromIntent.isNullOrBlank()) return fromIntent

        // (b) Static holder — only works if this activity runs in the
        // same process that captured the crash (not the usual case).
        val fromHolder = CrashReportHolder.report
        if (!fromHolder.isNullOrBlank()) return fromHolder

        // (c) Newest crash file on disk, as a last resort.
        val fromFile = try {
            val dir = getExternalFilesDir("crashes")
            val files = dir?.listFiles { f ->
                f.isFile && f.name.startsWith("flick-crash-") && f.name.endsWith(".txt")
            }
            val newest = files?.maxByOrNull { it.lastModified() }
            newest?.takeIf { it.length() > 0 }?.readText()
        } catch (_: Exception) {
            null
        }
        if (!fromFile.isNullOrBlank()) return fromFile

        return "No crash report was captured."
    }
}
