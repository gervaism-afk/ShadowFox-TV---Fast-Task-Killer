package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StartupDiagnostics {
    private const val PREFS = "shadowfox_diag"

    fun begin(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasActive = p.getBoolean("active", false)
        val oldStage = p.getString("stage", "unknown") ?: "unknown"
        val oldCrash = p.getString("crash", "") ?: ""
        val oldHeartbeat = p.getLong("heartbeat", 0L)
        val exit = processExitHistory(context)
        val report = if (wasActive) buildString {
            append("SHADOWFOX H96 DIAGNOSTIC\n")
            append("Previous session ended unexpectedly\n")
            append("Last stage: $oldStage\n")
            if (oldHeartbeat > 0) append("Last heartbeat: ${formatTime(oldHeartbeat)}\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}\n")
            append("ABI: ${Build.SUPPORTED_ABIS.joinToString()}\n")
            append("\nANDROID EXIT HISTORY\n$exit\n")
            if (oldCrash.isBlank()) append("\nNo Java/Kotlin exception was captured.")
            else append("\nCaptured exception:\n${oldCrash.take(2400)}")
        } else ""
        p.edit().putString("report", report).putBoolean("active", true)
            .putString("stage", "Application.onCreate").putString("crash", "")
            .putLong("heartbeat", System.currentTimeMillis()).commit()
    }

    fun stage(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("stage", value).putLong("heartbeat", System.currentTimeMillis()).commit()
    }

    fun heartbeat(context: Context, value: String = "Dashboard alive") = stage(context, value)

    fun report(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("report", "") ?: ""

    fun installExceptionHandler(context: Context) {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val sw = StringWriter(); error.printStackTrace(PrintWriter(sw))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("stage", "UNCAUGHT_EXCEPTION:${thread.name}")
                .putString("crash", sw.toString()).putLong("heartbeat", System.currentTimeMillis()).commit()
            old?.uncaughtException(thread, error)
        }
    }

    private fun processExitHistory(context: Context): String {
        if (Build.VERSION.SDK_INT < 30) return "Unavailable: Android ${Build.VERSION.RELEASE} is below Android 11 (API 30)."
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            if (exits.isEmpty()) "No exit records returned by Android."
            else exits.joinToString("\n") { e ->
                "${formatTime(e.timestamp)} | reason=${reasonName(e.reason)}(${e.reason}) | status=${e.status} | importance=${e.importance} | ${e.description ?: "no description"}"
            }
        } catch (t: Throwable) {
            "Exit-history query failed: ${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        0 -> "UNKNOWN"; 1 -> "EXIT_SELF"; 2 -> "SIGNALED"; 3 -> "LOW_MEMORY"
        4 -> "CRASH"; 5 -> "CRASH_NATIVE"; 6 -> "ANR"; 7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"; 9 -> "EXCESSIVE_RESOURCE_USAGE"; 10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"; 12 -> "DEPENDENCY_DIED"; 13 -> "OTHER"
        14 -> "FREEZER"; 15 -> "PACKAGE_STATE_CHANGE"; 16 -> "PACKAGE_UPDATED"
        else -> "REASON_$reason"
    }

    private fun formatTime(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(ms))
}
