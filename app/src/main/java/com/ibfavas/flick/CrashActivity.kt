package com.ibfavas.flick

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView

/*
 * Shows the crash report captured by FlickApp in a plain
 * ScrollView + TextView. Deliberately no Compose, no
 * DataStore and no coroutines, so it cannot crash the same
 * way the main app does. The user can screenshot this
 * screen to share the stack trace. Read order: (a) the
 * Intent extra "report", (b) the static holder, (c) the
 * newest flick-crash-*.txt file in the crashes directory.
 * The static holder is null in this activity's fresh
 * process, statics do not survive process death, so the
 * Intent extra is the primary channel.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = loadReport()
        fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
        val content = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
            addView(TextView(this@CrashActivity).apply {
                this.text = "Crash report"
                textSize = 30f
                typeface = Typeface.SERIF
                setTextColor(android.graphics.Color.WHITE)
                setPadding(0, 0, 0, dp(16))
            })
        }
        val textView = TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xff101011.toInt())
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), 0xff363639.toInt())
            }
        }
        content.addView(textView)
        val scrollView = ScrollView(this).apply {
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        scrollView.fitsSystemWindows = true
        scrollView.setBackgroundColor(android.graphics.Color.BLACK)
        setContentView(scrollView)
    }

    private fun loadReport(): String {
        // (a) Intent extra, the primary channel, survives process death.
        val fromIntent = try {
            intent?.getStringExtra("report")
        } catch (_: Exception) {
            null
        }
        if (!fromIntent.isNullOrBlank()) return fromIntent

        // (b) Static holder, only works if this activity runs in the
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
