package ca.shadowfoxtv.taskkiller

enum class CleanupAction(val stopsApps: Boolean, val clearsCache: Boolean, val budgetMillis: Long) {
    SCAN(false, false, 4_000),
    RAM(true, false, 7_000),
    CACHE(false, true, 6_000),
    SMART(true, true, 10_000)
}

/** Only complete process reports can verify that an app was stopped. */
internal data class RootProcessSnapshot(val names: Set<String>) {
    fun isRunning(packageName: String): Boolean =
        names.any { it == packageName || it.startsWith("$packageName:") }

    companion object {
        fun fromPs(output: String): RootProcessSnapshot? {
            val lines = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            if (lines.firstOrNull() != "NAME") return null
            val names = lines.drop(1).toSet()
            return names.takeIf { it.isNotEmpty() }?.let(::RootProcessSnapshot)
        }

        fun fromActivity(output: String): RootProcessSnapshot? {
            if (!output.contains("ACTIVITY MANAGER RUNNING PROCESSES")) return null
            val names = Regex("ProcessRecord\\{[^}\\n]*?\\s[1-9][0-9]*:([^/\\s}]+)")
                .findAll(output).map { it.groupValues[1] }.toSet()
            return names.takeIf { it.isNotEmpty() }?.let(::RootProcessSnapshot)
        }
    }
}
