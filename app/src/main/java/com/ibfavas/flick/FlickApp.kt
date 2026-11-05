package com.ibfavas.flick

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Holds the latest crash report in memory so CrashActivity can show it
 * even though the app process is restarted for that activity.
 */
object CrashReportHolder {
    @Volatile
    var report: String? = null
}

/**
 * Diagnostic Application class (v1.0.2, fixed in v1.0.3): installs a global uncaught-exception
 * handler first thing in onCreate, so that when the app crashes on launch
 * the real stack trace is captured, saved to a file, and shown on screen
 * in CrashActivity instead of Android's generic "keeps stopping" dialog.
 *
 * v1.0.3 fixes: the crash file path is computed once per crash (v1.0.2
 * computed it twice with different timestamps) and the report is passed
 * to CrashActivity via an Intent extra, because the static holder does
 * not survive the process kill/restart.
 */
class FlickApp : Application() {

    override fun onCreate() {
        installCrashHandler()
        super.onCreate()
    }

    private fun installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                // Compute the crash file path ONCE per crash and reuse it
                // for both the report header and the actual write. (v1.0.2
                // computed it twice, so the header named a file that was
                // never written.)
                val path = crashFilePath()
                val report = buildReport(throwable, path)
                CrashReportHolder.report = report
                writeReportFile(report, path)
                val intent = Intent(this, CrashActivity::class.java).apply {
                    // Intent extras survive process death; the static holder
                    // does not (v1.0.2 bug: CrashActivity ran in a fresh
                    // process where the holder was null). Skip the extra if
                    // the report is huge (Binder limit) — CrashActivity then
                    // falls back to the file written above.
                    if (report.length < 200_000) putExtra("report", report)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                }
                startActivity(intent)
                // Give ActivityManager a moment to record the start before
                // this process is killed; the new activity runs in a fresh
                // process afterwards.
                try {
                    Thread.sleep(400)
                } catch (_: InterruptedException) {
                }
            } catch (_: Throwable) {
                // The handler itself must never throw.
            } finally {
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun buildReport(throwable: Throwable, filePath: String?): String {
        val out = StringBuilder()
        val file = filePath
        out.append("Flick crash report\n")
        out.append("Report file: ").append(file ?: "(could not write file)").append('\n')
        out.append("App version: ").append(appVersion()).append('\n')
        out.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        out.append("Android: ").append(Build.VERSION.RELEASE)
            .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        out.append("\n")
        var current: Throwable? = throwable
        var depth = 0
        while (current != null) {
            out.append(if (depth == 0) "Exception: " else "Caused by: ")
                .append(current.javaClass.name)
                .append(": ").append(current.message).append('\n')
            val sw = StringWriter()
            current.printStackTrace(PrintWriter(sw))
            out.append(sw.toString()).append('\n')
            current = current.cause
            depth++
        }
        return out.toString()
    }

    private fun appVersion(): String {
        return try {
            @Suppress("DEPRECATION")
            val info = packageManager.getPackageInfo(packageName, 0)
            "${info.versionName} (code ${info.versionCode})"
        } catch (_: Exception) {
            "unknown"
        }
    }

    /** Path the report file will be written to (used in the report header). */
    private fun crashFilePath(): String? {
        return try {
            val dir = getExternalFilesDir("crashes") ?: return null
            File(dir, "flick-crash-${System.currentTimeMillis()}.txt").absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun writeReportFile(report: String, path: String?) {
        try {
            val filePath = path ?: return
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(report)
        } catch (_: Exception) {
            // Never let the handler throw.
        }
    }
}
