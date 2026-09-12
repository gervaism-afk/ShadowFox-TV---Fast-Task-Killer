package ca.shadowfoxtv.taskkiller

import android.content.Context
import android.os.Build
import java.util.concurrent.TimeUnit

data class DeviceCapabilities(
    val rooted: Boolean,
    val androidVersion: Int,
    val canKillOtherApps: Boolean,
    val canClearOtherAppCaches: Boolean,
    val canManagePackages: Boolean,
    val canUseStandardOptimizer: Boolean
) {
    val modeLabel: String
        get() = if (rooted) "ROOTED PRO MODE" else "STANDARD MODE"
}

object DeviceCapabilityDetector {
    fun detect(context: Context): DeviceCapabilities {
        val rooted = hasWorkingRoot()
        return DeviceCapabilities(
            rooted = rooted,
            androidVersion = Build.VERSION.SDK_INT,
            canKillOtherApps = rooted,
            canClearOtherAppCaches = rooted,
            canManagePackages = rooted,
            canUseStandardOptimizer = true
        )
    }

    private fun hasWorkingRoot(): Boolean {
        val candidates = listOf(
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/debug_ramdisk/su",
            "/data/adb/magisk/su"
        )
        for (suPath in candidates.distinct()) {
            val rooted = runCatching {
                val process = ProcessBuilder(suPath, "-c", "id").redirectErrorStream(true).start()
                val finished = process.waitFor(8, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    false
                } else {
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    process.exitValue() == 0 && output.contains("uid=0")
                }
            }.getOrDefault(false)
            if (rooted) return true
        }
        return false
    }
}
