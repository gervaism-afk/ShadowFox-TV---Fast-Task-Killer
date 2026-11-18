package ca.shadowfoxtv.taskkiller

/** Discover a shell with harmless identity probes, then execute each command once. */
internal class RootCommandRunner(
    private val candidates: List<String>,
    private val run: (List<String>, String?, Long) -> RootProcess.Result,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private data class Shell(val path: String, val mode: Int) {
        fun args(command: String): List<String> = when (mode) {
            0 -> listOf(path, "-c", command)
            1 -> listOf(path, "0", "sh", "-c", command)
            2 -> listOf(path, "root", "sh", "-c", command)
            else -> listOf(path)
        }

        fun input(command: String): String? = if (mode == 3) "$command\nexit\n" else null
    }

    @Volatile private var shell: Shell? = null
    private val availability = RootAvailabilityCache { discover() }

    fun isAvailable(): Boolean = availability.isAvailable()

    fun exec(command: String, timeoutMillis: Long): RootProcess.Result {
        if (timeoutMillis <= 0) return RootProcess.Result(false, "timeout")
        if (!isAvailable()) return RootProcess.Result(false, "Root unavailable")
        val selected = shell ?: return RootProcess.Result(false, "Root unavailable")
        return run(selected.args(command), selected.input(command), timeoutMillis)
    }

    private fun discover(): Boolean {
        val deadline = nanoTime() + 5_000_000_000L
        for (path in candidates) {
            for (mode in 0..3) {
                val remainingMillis = (deadline - nanoTime()) / 1_000_000
                if (remainingMillis <= 0) return false
                val candidate = Shell(path, mode)
                val result = run(candidate.args("id"), candidate.input("id"), minOf(1000, remainingMillis))
                if (result.success && Regex("\\buid=0\\b").containsMatchIn(result.output)) {
                    shell = candidate
                    return true
                }
            }
        }
        return false
    }
}
