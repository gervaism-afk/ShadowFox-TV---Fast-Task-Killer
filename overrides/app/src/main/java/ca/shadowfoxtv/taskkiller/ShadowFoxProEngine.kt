package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Root-first optimizer for ShadowFox TV.
 * Every reported result is measured or verified after the command completes.
 */
data class ProCleanupResult(
    val closedApps: Int,
    val ramFreedBytes: Long,
    val storageFreedBytes: Long,
    val rootUsed: Boolean,
    val attemptedApps: Int,
    val verifiedStopped: Int,
    val cacheFreedBytes: Long,
    val summary: String
)

class ShadowFoxProEngine(private val context: Context) {
    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = appContext.packageManager

    fun rootAvailable(): Boolean = runRoot("id").let { it.success && it.output.contains("uid=0") }

    suspend fun optimize(): ProCleanupResult = withContext(Dispatchers.IO) {
        val beforeRam = availableMemoryBytes()
        val beforeStorage = freeStorageBytes()
        val root = rootAvailable()
        val protected = protectedPackages()
        val candidates = if (root) rootThirdPartyPackages(protected) else nonRootCandidates(protected)

        var verifiedStopped = 0
        var cacheBefore = 0L
        var cacheAfter = 0L

        if (root) {
            cacheBefore = cacheBytes(candidates)
            for (pkg in candidates) {
                val stop = runRoot("am force-stop --user 0 ${shellQuote(pkg)}")
                if (stop.success) {
                    Thread.sleep(45)
                    val pid = runRoot("pidof ${shellQuote(pkg)}")
                    if (pid.success && pid.output.trim().isEmpty()) verifiedStopped++
                }
            }

            // Clear only cache/code_cache contents for eligible third-party apps.
            // This does not delete app data, accounts, settings, or databases.
            for (pkg in candidates) {
                val base = "/data/user/0/$pkg"
                runRoot("for d in ${shellQuote("$base/cache")} ${shellQuote("$base/code_cache")}; do [ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + 2>/dev/null; done")
            }
            runRoot("pm trim-caches 999999999999")
            runRoot("sync")
            cacheAfter = cacheBytes(candidates)
        } else {
            for (pkg in candidates) {
                runCatching { activityManager.killBackgroundProcesses(pkg) }
            }
            Thread.sleep(350)
        }

        Thread.sleep(650)
        val afterRam = availableMemoryBytes()
        val afterStorage = freeStorageBytes()
        val ramFreed = (afterRam - beforeRam).coerceAtLeast(0L)
        val measuredStorageGain = (afterStorage - beforeStorage).coerceAtLeast(0L)
        val measuredCacheGain = (cacheBefore - cacheAfter).coerceAtLeast(0L)
        val storageFreed = maxOf(measuredStorageGain, measuredCacheGain)

        val summary = if (root) {
            "ROOT ✓ • $verifiedStopped/${candidates.size} stopped • +${formatBytes(ramFreed)} RAM • ${formatBytes(storageFreed)} cache"
        } else {
            "LIMITED MODE • ${candidates.size} apps targeted • +${formatBytes(ramFreed)} RAM"
        }

        appendDiagnostic(
            "root=$root candidates=${candidates.size} stopped=$verifiedStopped ramFreed=$ramFreed cacheBefore=$cacheBefore cacheAfter=$cacheAfter storageFreed=$storageFreed"
        )

        ProCleanupResult(
            closedApps = if (root) verifiedStopped else candidates.size,
            ramFreedBytes = ramFreed,
            storageFreedBytes = storageFreed,
            rootUsed = root,
            attemptedApps = candidates.size,
            verifiedStopped = verifiedStopped,
            cacheFreedBytes = measuredCacheGain,
            summary = summary
        )
    }

    fun diagnosticsSummary(): String {
        val root = rootAvailable()
        val userApps = if (root) rootThirdPartyPackages(protectedPackages()).size else 0
        return if (root) "ROOT ACTIVE • UID 0 • $userApps eligible apps" else "ROOT NOT AVAILABLE"
    }

    fun readRecentDiagnostics(maxLines: Int = 12): List<String> = runCatching {
        val f = diagnosticFile()
        if (!f.exists()) emptyList() else f.readLines().takeLast(maxLines)
    }.getOrDefault(emptyList())

    private fun rootThirdPartyPackages(protected: Set<String>): List<String> {
        val result = runRoot("pm list packages -3")
        if (!result.success) return emptyList()
        return result.output.lineSequence()
            .map { it.trim().removePrefix("package:") }
            .filter { it.isNotBlank() && it != appContext.packageName && it !in protected }
            .distinct()
            .sorted()
            .toList()
    }

    private fun nonRootCandidates(protected: Set<String>): List<String> =
        activityManager.runningAppProcesses.orEmpty()
            .flatMap { it.pkgList?.toList().orEmpty() }
            .distinct()
            .filter { it != appContext.packageName && it !in protected }
            .filterNot(::isSystemPackage)

    private fun protectedPackages(): Set<String> {
        val set = mutableSetOf(
            appContext.packageName,
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.amazon.device.software.ota",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.android.permissioncontroller"
        )

        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNullTo(set) { it.activityInfo?.packageName }
        }

        val prefs = appContext.getSharedPreferences("shadowfox_pro_engine", Context.MODE_PRIVATE)
        set.addAll(prefs.getStringSet("protected_packages", emptySet()) ?: emptySet())
        return set
    }

    private fun cacheBytes(packages: List<String>): Long {
        if (packages.isEmpty()) return 0L
        var totalKb = 0L
        for (pkg in packages) {
            val base = "/data/user/0/$pkg"
            val cmd = "du -sk ${shellQuote("$base/cache")} ${shellQuote("$base/code_cache")} 2>/dev/null | awk '{s+=\$1} END{print s+0}'"
            val out = runRoot(cmd)
            if (out.success) totalKb += out.output.trim().lineSequence().lastOrNull()?.toLongOrNull() ?: 0L
        }
        return totalKb * 1024L
    }

    private fun isSystemPackage(packageName: String): Boolean = runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    private fun availableMemoryBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem
    }

    private fun freeStorageBytes(): Long = runCatching {
        StatFs(Environment.getDataDirectory().absolutePath).availableBytes
    }.getOrDefault(0L)

    private fun appendDiagnostic(message: String) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val f = diagnosticFile()
            if (f.length() > 256_000L) f.writeText("")
            f.appendText("$stamp | $message\n")
        }
    }

    private fun diagnosticFile(): File = File(appContext.filesDir, "shadowfox_pro_engine.log")

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun runRoot(command: String): RootExec = runCatching {
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            RootExec(false, "timeout")
        } else {
            val output = process.inputStream.bufferedReader().use { it.readText() }
            RootExec(process.exitValue() == 0, output)
        }
    }.getOrElse { RootExec(false, it.message.orEmpty()) }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
    }

    private data class RootExec(val success: Boolean, val output: String)
}
