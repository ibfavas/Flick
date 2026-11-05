package com.ibfavas.flick

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView

/**
 * Diagnostic screen (v1.0.2): shows the crash report captured by FlickApp
 * in a plain ScrollView + TextView. Deliberately uses no Compose, no
 * DataStore and no coroutines, so it cannot crash the same way the main
 * app does. The user can screenshot this screen to share the stack trace.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = CrashReportHolder.report
            ?: "No crash report was captured."
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
}
