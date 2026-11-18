package ca.shadowfoxtv.taskkiller

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Bounded shell I/O, including Android 6/7 where timed Process.waitFor is unavailable. */
internal object RootProcess {
    data class Result(val success: Boolean, val output: String)

    fun run(args: List<String>, input: String?, timeoutSeconds: Long): Result =
        runMillis(args, input, TimeUnit.SECONDS.toMillis(timeoutSeconds))

    fun runMillis(args: List<String>, input: String?, timeoutMillis: Long): Result = runCatching {
        if (timeoutMillis <= 0) return Result(false, "timeout")
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val process = ProcessBuilder(args).redirectErrorStream(true).start()
        val output = StringBuffer()
        // Drain while the process runs: dumpsys and /proc output can fill the pipe.
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { stream ->
                    val buffer = CharArray(4096)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        output.append(buffer, 0, count)
                    }
                }
            }
        }.apply { isDaemon = true; start() }
        val writeError = AtomicReference<Throwable?>()
        val writer = Thread {
            runCatching {
                process.outputStream.bufferedWriter().use { stream ->
                    if (input != null) stream.write(input)
                }
            }.exceptionOrNull()?.let(writeError::set)
        }.apply { isDaemon = true; start() }
        try {
            var exit: Int? = null
            while (exit == null && System.nanoTime() < deadline) {
                exit = try { process.exitValue() } catch (_: IllegalThreadStateException) { null }
                if (exit == null) Thread.sleep(10)
            }
            if (exit == null) {
                process.destroy()
                Result(false, "timeout")
            } else {
                // Thread.join(0) waits forever, so skip joining after the deadline.
                val readerWait = minOf(500, (deadline - System.nanoTime()) / 1_000_000)
                if (readerWait > 0) reader.join(readerWait)
                val writerWait = minOf(500, (deadline - System.nanoTime()) / 1_000_000)
                if (writerWait > 0) writer.join(writerWait)
                // Incomplete process snapshots must not be treated as successful results.
                Result(exit == 0 && !reader.isAlive && !writer.isAlive && writeError.get() == null, output.toString())
            }
        } finally {
            process.destroy()
        }
    }.getOrElse { Result(false, it.message.orEmpty()) }
}
