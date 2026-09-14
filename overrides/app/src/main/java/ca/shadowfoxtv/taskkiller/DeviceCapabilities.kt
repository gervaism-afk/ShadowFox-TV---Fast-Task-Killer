package ca.shadowfoxtv.taskkiller

import android.content.Context
import android.os.Build

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
        val rooted = RootShell.isRootAvailable()
        return DeviceCapabilities(
            rooted = rooted,
            androidVersion = Build.VERSION.SDK_INT,
            canKillOtherApps = rooted,
            canClearOtherAppCaches = rooted,
            canManagePackages = rooted,
            canUseStandardOptimizer = true
        )
    }
}
