package ca.shadowfoxtv.taskkiller

import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter

object StartupDiagnostics {
    private const val PREFS = "shadowfox_diag"
    fun begin(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasActive = p.getBoolean("active", false)
        val oldStage = p.getString("stage", "unknown") ?: "unknown"
        val oldCrash = p.getString("crash", "") ?: ""
        val report = if (wasActive) "SHADOWFOX H96 DIAGNOSTIC\nPrevious session ended unexpectedly\nLast stage: $oldStage\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}\n" + if (oldCrash.isBlank()) "No Java/Kotlin exception was captured." else "Captured exception:\n${oldCrash.take(3000)}" else ""
        p.edit().putString("report", report).putBoolean("active", true).putString("stage", "Application.onCreate").putString("crash", "").commit()
    }
    fun stage(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("stage", value).commit() }
    fun report(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("report", "") ?: ""
    fun installExceptionHandler(context: Context) {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val sw = StringWriter(); error.printStackTrace(PrintWriter(sw))
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("stage", "UNCAUGHT_EXCEPTION:${thread.name}").putString("crash", sw.toString()).commit()
            old?.uncaughtException(thread, error)
        }
    }
}
