package ca.shadowfoxtv.taskkiller

import android.os.Build
import java.util.concurrent.TimeUnit

/** Shared, bounded root discovery with support for legacy TV-box shell variants. */
object RootShell {
    data class Result(val success: Boolean, val output: String)

    private val commands = RootCommandRunner(
        listOf(
            "su", "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/vendor/bin/su", "/su/bin/su", "/debug_ramdisk/su", "/data/adb/magisk/su"
        ),
        RootProcess::runMillis
    )

    private fun skipRoot(): Boolean = isGoogleTvStreamer(Build.MANUFACTURER, Build.MODEL)

    fun isRootAvailable(): Boolean = !skipRoot() && commands.isAvailable()

    fun exec(command: String, timeoutSeconds: Long = 15): Result =
        execMillis(command, TimeUnit.SECONDS.toMillis(timeoutSeconds))

    internal fun execMillis(command: String, timeoutMillis: Long): Result {
        if (skipRoot()) return Result(false, "Root disabled on Google TV Streamer")
        val result = commands.exec(command, timeoutMillis)
        return Result(result.success, result.output)
    }
}
