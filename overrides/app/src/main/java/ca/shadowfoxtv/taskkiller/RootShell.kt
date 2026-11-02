package ca.shadowfoxtv.taskkiller

import android.os.Build

/**
 * Shared root shell compatibility layer.
 * Supports standard Magisk-style `su -c` and older Android TV box firmware
 * that exposes an interactive su shell or alternate argument styles.
 */
object RootShell {
    data class Result(val success: Boolean, val output: String)

    @Volatile private var preferredPath: String? = null

    private val availability = RootAvailabilityCache {
        val result = exec("id", 10)
        result.success && Regex("\\buid=0\\b").containsMatchIn(result.output)
    }

    private fun skipRoot(): Boolean = isGoogleTvStreamer(Build.MANUFACTURER, Build.MODEL)

    private val candidates = listOf(
        "su",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/su/bin/su",
        "/debug_ramdisk/su",
        "/data/adb/magisk/su"
    ).distinct()

    fun isRootAvailable(): Boolean {
        // Google TV Streamer hardware is stock/non-rooted; skip costly su probing.
        if (skipRoot()) return false
        return availability.isAvailable()
    }

    fun exec(command: String, timeoutSeconds: Long = 15): Result {
        // Enforce the same policy for direct callers as for capability detection.
        if (skipRoot()) return Result(false, "Root disabled on Google TV Streamer")
        var last = "su unavailable"

        // Once a working su binary is found, reuse it. This is critical on older TV-box
        // firmware: probing every possible su path/mode can consume the timeout repeatedly.
        preferredPath?.let { path ->
            val direct = runProcess(listOf(path, "-c", command), timeoutSeconds)
            if (direct.success || direct.output.contains("uid=0")) return direct
            preferredPath = null
        }

        for (path in candidates) {
            // Fast path used by Magisk and nearly all pre-rooted Android boxes.
            val direct = runProcess(listOf(path, "-c", command), timeoutSeconds)
            if (direct.success || direct.output.contains("uid=0")) {
                preferredPath = path
                return direct
            }
            if (direct.output.isNotBlank()) last = direct.output

            // Compatibility fallbacks get a short probe budget so a broken su variant
            // cannot multiply one command into minutes of retries.
            val probeSeconds = minOf(timeoutSeconds, 1L)
            val legacy = runProcess(listOf(path, "0", "sh", "-c", command), probeSeconds)
            if (legacy.success || legacy.output.contains("uid=0")) {
                preferredPath = path
                return legacy
            }
            val rooted = runProcess(listOf(path, "root", "sh", "-c", command), probeSeconds)
            if (rooted.success || rooted.output.contains("uid=0")) {
                preferredPath = path
                return rooted
            }
            val interactive = runInteractive(path, command, probeSeconds)
            if (interactive.success || interactive.output.contains("uid=0")) {
                preferredPath = path
                return interactive
            }
        }
        return Result(false, last)
    }

    private fun runProcess(args: List<String>, timeoutSeconds: Long): Result =
        RootProcess.run(args, null, timeoutSeconds).let { Result(it.success, it.output) }

    private fun runInteractive(path: String, command: String, timeoutSeconds: Long): Result =
        RootProcess.run(listOf(path), "$command\nexit\n", timeoutSeconds).let { Result(it.success, it.output) }
}
