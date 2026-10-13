package ca.shadowfoxtv.taskkiller

import java.util.concurrent.TimeUnit

/**
 * Shared root shell compatibility layer.
 * Supports standard Magisk-style `su -c` and older Android TV box firmware
 * that exposes an interactive su shell or alternate argument styles.
 */
object RootShell {
    data class Result(val success: Boolean, val output: String)

    @Volatile private var preferredPath: String? = null

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
        val r = exec("id", 10)
        return r.output.contains("uid=0")
    }

    fun exec(command: String, timeoutSeconds: Long = 15): Result {
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

    private fun runProcess(args: List<String>, timeoutSeconds: Long): Result = runCatching {
        val p = ProcessBuilder(args).redirectErrorStream(true).start()
        val done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!done) {
            p.destroyForcibly()
            Result(false, "timeout")
        } else {
            val out = p.inputStream.bufferedReader().use { it.readText() }
            Result(p.exitValue() == 0, out)
        }
    }.getOrElse { Result(false, it.message.orEmpty()) }

    private fun runInteractive(path: String, command: String, timeoutSeconds: Long): Result = runCatching {
        val p = ProcessBuilder(path).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { w ->
            w.write(command)
            w.newLine()
            w.write("exit")
            w.newLine()
            w.flush()
        }
        val done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!done) {
            p.destroyForcibly()
            Result(false, "timeout")
        } else {
            val out = p.inputStream.bufferedReader().use { it.readText() }
            Result(p.exitValue() == 0, out)
        }
    }.getOrElse { Result(false, it.message.orEmpty()) }
}
