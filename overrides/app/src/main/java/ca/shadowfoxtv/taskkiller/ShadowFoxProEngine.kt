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
        return RootShell.isRootAvailable()
    }

    suspend fun optimize(): ProCleanupResult = withContext(Dispatchers.IO) {
        resetDiagnostic()
        val beforeRam = availableMemoryBytes()
        val beforeStorage = freeStorageBytes()
        val root = rootAvailable()
        val protected = protectedPackages()

        val allEligible = if (root) rootThirdPartyPackages(protected) else nonRootCandidates(protected)
        val runningBefore = if (root) rootProcessSnapshot() else RootProcessSnapshot(emptySet(), "")
        val runningCandidates = if (root) allEligible.filter { it.isRunningIn(runningBefore) } else allEligible

        appendDiagnostic("START root=$root eligible=${allEligible.size} running=${runningCandidates.size}")
        appendDiagnostic("CANDIDATES ${runningCandidates.ifEmpty { listOf("none") }.joinToString(",")}")
        appendDiagnostic("PROTECTED ${protected.sorted().joinToString(",")}")
        if (root) appendDiagnostic("SOURCES proc=${runningBefore.names.size} activityChars=${runningBefore.activityProcesses.length}")

        var verifiedStopped = 0
        var cacheBefore = 0L
        var cacheAfter = 0L

        if (root) {
            cacheBefore = cacheBytes(allEligible)

            for (pkg in runningCandidates) {
                val stop = runRoot("am force-stop --user 0 ${shellQuote(pkg)}")
                var stillRunning = true
                for (attempt in 0 until 6) {
                    Thread.sleep(100)
                    stillRunning = pkg.isRunningIn(rootProcessSnapshot())
                    if (!stillRunning) break
                }
                if (!stillRunning) verifiedStopped++
                appendDiagnostic("STOP pkg=$pkg command=${if (stop.success) "ok" else "failed"} state=${if (stillRunning) "running" else "stopped"} output=${cleanOutput(stop.output)}")
            }

            for (pkg in allEligible) {
                val paths = cachePaths(pkg)
                val joined = paths.joinToString(" ") { shellQuote(it) }
                val clear = runRoot("for d in $joined; do if [ -d \"\$d\" ]; then rm -rf \"\$d\"/* \"\$d\"/.[!.]* \"\$d\"/..?* 2>/dev/null; fi; done")
                if (!clear.success) appendDiagnostic("CACHE pkg=$pkg command=failed output=${cleanOutput(clear.output)}")
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
            "STANDARD MODE • background clean requested • +${formatBytes(ramFreed)} measured RAM • app stops unverified"
        }

        appendDiagnostic("RESULT stopped=$verifiedStopped/${runningCandidates.size} ram=$ramFreed cacheBefore=$cacheBefore cacheAfter=$cacheAfter storage=$storageFreed")

        ProCleanupResult(
            closedApps = if (root) verifiedStopped else 0,
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
        val processes = rootProcessSnapshot()
        val running = eligible.count { it.isRunningIn(processes) }
        return "ROOT ACTIVE • UID 0 • $running running • ${eligible.size} eligible"
    }

    fun runningThirdPartyCount(): Int = runningThirdPartyPackages().size

    fun runningThirdPartyPackages(): List<String> {
        val protected = protectedPackages()
        if (!rootAvailable()) return nonRootCandidates(protected)
        val processes = rootProcessSnapshot()
        return rootThirdPartyPackages(protected).filter { it.isRunningIn(processes) }
    }

    fun isThirdPartyPackageRunning(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!rootAvailable()) return packageName in nonRootCandidates(protectedPackages())
        return packageName.isRunningIn(rootProcessSnapshot())
    }

    fun readRecentDiagnostics(maxLines: Int = 12): List<String> = runCatching {
        val f = diagnosticFile()
        if (!f.exists()) emptyList() else f.readLines().takeLast(maxLines)
    }.getOrDefault(emptyList())

    private fun resetDiagnostic() {
        runCatching { diagnosticFile().writeText("") }
    }

    private fun cleanOutput(value: String): String = value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .take(100)
        .ifBlank { "none" }

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

    /**
     * Reads /proc directly because several rooted Android TV firmwares ship a
     * pidof/ps variant that cannot resolve Android package process names.
     */
    private fun rootProcessSnapshot(): RootProcessSnapshot {
        val proc = runRoot("for f in /proc/[0-9]*/cmdline; do cat \"\$f\" 2>/dev/null; echo; done")
        val names = proc.output.lineSequence()
            .map { it.substringBefore('\u0000').trim() }
            .filter { it.isNotBlank() }
            .toSet()
        // Some H96 Android TV builds isolate /proc even from app-spawned root.
        // ActivityManager's registry still lists active and cached app processes.
        val activity = runRoot("dumpsys activity processes").output
        return RootProcessSnapshot(names, activity)
    }

    private fun String.isRunningIn(snapshot: RootProcessSnapshot): Boolean {
        if (snapshot.names.any { it == this || it.startsWith("${this}:") }) return true
        val packageToken = Regex("(?<![A-Za-z0-9_.])${Regex.escape(this)}(?=[:/}\\s]|$)")
        return packageToken.containsMatchIn(snapshot.activityProcesses)
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
            val paths = cachePaths(pkg)
            val joined = paths.joinToString(" ") { shellQuote(it) }
            val cmd = "du -sk $joined 2>/dev/null | awk '{s+=\$1} END{print s+0}'"
            val out = runRoot(cmd)
            if (out.output.isNotBlank()) totalKb += out.output.trim().lineSequence().lastOrNull()?.toLongOrNull() ?: 0L
        }
        return totalKb * 1024L
    }

    private fun cachePaths(pkg: String): List<String> = listOf(
        "/data/user/0/$pkg/cache",
        "/data/user/0/$pkg/code_cache"
    )

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
        val result = RootShell.exec(command)
        return RootExec(result.success, result.output)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        if (bytes < 1024L * 1024L) return "${(bytes / 1024L).coerceAtLeast(1L)} KB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
    }

    private data class RootExec(val success: Boolean, val output: String)
    private data class RootProcessSnapshot(val names: Set<String>, val activityProcesses: String)
}
