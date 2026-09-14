package ca.shadowfoxtv.taskkiller

import java.util.concurrent.TimeUnit

/**
 * Shared root shell compatibility layer.
 * Supports standard Magisk-style `su -c` and older Android TV box firmware
 * that exposes an interactive su shell or alternate argument styles.
 */
object RootShell {
    data class Result(val success: Boolean, val output: String)

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
        for (path in candidates) {
            val attempts = listOf(
                listOf(path, "-c", command),
                listOf(path, "0", "sh", "-c", command),
                listOf(path, "root", "sh", "-c", command)
            )
            for (args in attempts) {
                val result = runProcess(args, timeoutSeconds)
                if (result.output.contains("uid=0") || result.success) return result
                if (result.output.isNotBlank()) last = result.output
            }

            // Older pre-rooted TV-box ROMs often expose only an interactive su shell.
            val interactive = runInteractive(path, command, timeoutSeconds)
            if (interactive.output.contains("uid=0") || interactive.success) return interactive
            if (interactive.output.isNotBlank()) last = interactive.output
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
