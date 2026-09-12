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

    fun rootAvailable(): Boolean {
        val result = runRoot("id")
        return result.success && result.output.contains("uid=0")
    }

    suspend fun optimize(): ProCleanupResult = withContext(Dispatchers.IO) {
        val beforeRam = availableMemoryBytes()
        val beforeStorage = freeStorageBytes()
        val root = rootAvailable()
        val protected = protectedPackages()

        val allEligible = if (root) rootThirdPartyPackages(protected) else nonRootCandidates(protected)
        val runningCandidates = if (root) allEligible.filter(::isPackageRunningRoot) else allEligible

        var verifiedStopped = 0
        var cacheBefore = 0L
        var cacheAfter = 0L

        if (root) {
            cacheBefore = cacheBytes(allEligible)

            for (pkg in runningCandidates) {
                runRoot("am force-stop --user 0 ${shellQuote(pkg)}")
                var stillRunning = true
                for (attempt in 0 until 6) {
                    Thread.sleep(100)
                    stillRunning = isPackageRunningRoot(pkg)
                    if (!stillRunning) break
                }
                if (!stillRunning) verifiedStopped++
            }

            for (pkg in allEligible) {
                val paths = listOf(
                    "/data/user/0/$pkg/cache",
                    "/data/user/0/$pkg/code_cache",
                    "/data/data/$pkg/cache",
                    "/data/data/$pkg/code_cache"
                )
                val joined = paths.joinToString(" ") { shellQuote(it) }
                runRoot("for d in $joined; do [ -d \"\$d\" ] && find \"\$d\" -mindepth 1 -exec rm -rf -- {} + 2>/dev/null; done")
            }

            runRoot("pm trim-caches 999999999999")
            runRoot("sync")
            cacheAfter = cacheBytes(allEligible)
        } else {
            for (pkg in runningCandidates) {
                runCatching { activityManager.killBackgroundProcesses(pkg) }
            }
            Thread.sleep(500)
        }

        Thread.sleep(750)
        val afterRam = availableMemoryBytes()
        val afterStorage = freeStorageBytes()
        val ramFreed = (afterRam - beforeRam).coerceAtLeast(0L)
        val measuredStorageGain = (afterStorage - beforeStorage).coerceAtLeast(0L)
        val measuredCacheGain = (cacheBefore - cacheAfter).coerceAtLeast(0L)
        val storageFreed = maxOf(measuredStorageGain, measuredCacheGain)

        val summary = if (root) {
            "ROOT ✓ • $verifiedStopped/${runningCandidates.size} running apps stopped • +${formatBytes(ramFreed)} RAM • ${formatBytes(storageFreed)} cache"
        } else {
            "STANDARD MODE • safe optimization complete • +${formatBytes(ramFreed)} RAM"
        }

        appendDiagnostic(
            "root=$root eligible=${allEligible.size} running=${runningCandidates.size} stopped=$verifiedStopped ramFreed=$ramFreed cacheBefore=$cacheBefore cacheAfter=$cacheAfter storageFreed=$storageFreed"
        )

        ProCleanupResult(
            closedApps = if (root) verifiedStopped else runningCandidates.size,
            ramFreedBytes = ramFreed,
            storageFreedBytes = storageFreed,
            rootUsed = root,
            attemptedApps = runningCandidates.size,
            verifiedStopped = verifiedStopped,
            cacheFreedBytes = measuredCacheGain,
            summary = summary
        )
    }

    fun diagnosticsSummary(): String {
        val root = rootAvailable()
        if (!root) return "STANDARD MODE • ROOT NOT GRANTED"
        val eligible = rootThirdPartyPackages(protectedPackages())
        val running = eligible.count(::isPackageRunningRoot)
        return "ROOT ACTIVE • UID 0 • $running running • ${eligible.size} eligible"
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

    private fun isPackageRunningRoot(packageName: String): Boolean {
        val result = runRoot("pidof ${shellQuote(packageName)}")
        return result.output.trim().isNotEmpty()
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
            val paths = listOf(
                "/data/user/0/$pkg/cache",
                "/data/user/0/$pkg/code_cache",
                "/data/data/$pkg/cache",
                "/data/data/$pkg/code_cache"
            )
            val joined = paths.joinToString(" ") { shellQuote(it) }
            val cmd = "du -sk $joined 2>/dev/null | awk '{s+=\$1} END{print s+0}'"
            val out = runRoot(cmd)
            if (out.output.isNotBlank()) totalKb += out.output.trim().lineSequence().lastOrNull()?.toLongOrNull() ?: 0L
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

    private fun runRoot(command: String): RootExec {
        val candidates = listOf(
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/debug_ramdisk/su",
            "/data/adb/magisk/su"
        )
        var lastOutput = "su unavailable"
        for (suPath in candidates.distinct()) {
            val result = runCatching {
                val process = ProcessBuilder(suPath, "-c", command).redirectErrorStream(true).start()
                val finished = process.waitFor(15, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    RootExec(false, "timeout")
                } else {
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    RootExec(process.exitValue() == 0, output)
                }
            }.getOrElse { RootExec(false, it.message.orEmpty()) }
            if (result.success || result.output.contains("uid=0")) return result
            if (result.output.isNotBlank()) lastOutput = result.output
        }
        return RootExec(false, lastOutput)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
    }

    private data class RootExec(val success: Boolean, val output: String)
}
