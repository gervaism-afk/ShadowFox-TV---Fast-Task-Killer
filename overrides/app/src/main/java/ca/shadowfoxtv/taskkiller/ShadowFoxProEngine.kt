package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val summary: String,
    val scannedApps: Int = 0
)

class ShadowFoxProEngine(private val context: Context) {
    companion object {
        private val actionMutex = Mutex()
    }
    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = appContext.packageManager

    fun rootAvailable(): Boolean {
        return RootShell.isRootAvailable()
    }

    suspend fun optimize(): ProCleanupResult = perform(CleanupAction.SMART)

    suspend fun perform(action: CleanupAction, additionalProtected: Set<String> = emptySet()): ProCleanupResult =
        actionMutex.withLock {
            withContext(Dispatchers.IO) {
                val started = System.nanoTime()
                resetDiagnostic()
                val beforeRam = availableMemoryBytes()
                val root = rootAvailable()
                val deadline = System.nanoTime() + action.budgetMillis * 1_000_000
                val protected = protectedPackages() + additionalProtected
                currentCoroutineContext().ensureActive()
                val listedApps = if (root) rootThirdPartyPackages(protected, deadline) else nonRootCandidates(protected)
                val allEligible = listedApps.orEmpty()
                val beforeProcesses = if (root && action != CleanupAction.CACHE) rootProcessSnapshot(deadline) else null
                val runningCandidates = if (root) allEligible.filter { beforeProcesses?.isRunning(it) == true } else allEligible
                appendDiagnostic("START action=$action root=$root eligible=${allEligible.size} running=${runningCandidates.size} discoveryMs=${(System.nanoTime() - started) / 1_000_000}")
                var verifiedStopped = 0
                var cacheFreed = 0L
                var verified = beforeProcesses != null
                var cacheComplete = true
                currentCoroutineContext().ensureActive()
                if (action.stopsApps && runningCandidates.isNotEmpty()) {
                    if (root) {
                        val stopCommand = runningCandidates.joinToString("; ") { pkg ->
                            "am force-stop --user 0 ${shellQuote(pkg)}"
                        }
                        val stop = runRoot(stopCommand, deadline, 3500)
                        appendDiagnostic("STOP_BATCH count=${runningCandidates.size} command=${if (stop.success) "ok" else "partial/failed"} output=${cleanOutput(stop.output)}")
                        currentCoroutineContext().ensureActive()
                        val afterProcesses = rootProcessSnapshot(deadline)
                        verified = afterProcesses != null
                        verifiedStopped = runningCandidates.count { afterProcesses?.isRunning(it) == false }
                    } else {
                        for (pkg in runningCandidates) {
                            currentCoroutineContext().ensureActive()
                            if (System.nanoTime() >= deadline) break
                            runCatching { activityManager.killBackgroundProcesses(pkg) }
                        }
                    }
                    delay(150)
                }
                currentCoroutineContext().ensureActive()
                if (action.clearsCache) {
                    val cache = clearCache(root, allEligible, deadline)
                    cacheFreed = cache.freedBytes
                    cacheComplete = cache.complete
                }
                val afterRam = availableMemoryBytes()
                val ramFreed = if (action.stopsApps) (afterRam - beforeRam).coerceAtLeast(0L) else 0L
                val storageFreed = cacheFreed
                val summary = if (action == CleanupAction.SCAN) {
                    "SCAN COMPLETE • ${runningCandidates.size} detected eligible apps • ${if (root) "ROOT" else "STANDARD MODE"}"
                } else if (action == CleanupAction.CACHE) {
                    "CACHE CLEANER • ${formatBytes(storageFreed)} reclaimed • ${if (root) "ROOT" else "SHADOWFOX CACHE"}"
                } else if (root) {
                    "ROOT ✓ • $verifiedStopped/${runningCandidates.size} running apps stopped • +${formatBytes(ramFreed)} RAM • ${formatBytes(storageFreed)} cache"
                } else {
                    "STANDARD MODE • background clean requested • +${formatBytes(ramFreed)} measured RAM"
                }
                val verificationMissing = root && action != CleanupAction.CACHE && !verified
                val finalSummary = summary +
                    (if (listedApps == null) " • app discovery unavailable" else "") +
                    (if (verificationMissing) " • process verification unavailable" else "") +
                    (if (!cacheComplete) " • cache cleanup incomplete" else "") +
                    (if (System.nanoTime() >= deadline) " • time limit reached; partial results" else "")
                appendDiagnostic("RESULT action=$action stopped=$verifiedStopped/${runningCandidates.size} ram=$ramFreed storage=$storageFreed elapsedMs=${(System.nanoTime() - started) / 1_000_000}")

                ProCleanupResult(
                    closedApps = if (root) verifiedStopped else 0,
                    ramFreedBytes = ramFreed,
                    storageFreedBytes = storageFreed,
                    rootUsed = root,
                    attemptedApps = if (action.stopsApps) runningCandidates.size else 0,
                    verifiedStopped = verifiedStopped,
                    cacheFreedBytes = storageFreed,
                    summary = finalSummary,
                    scannedApps = runningCandidates.size
                )
            }
        }

    fun clearEligibleCache(additionalProtected: Set<String> = emptySet()): Long {
        if (!rootAvailable()) return 0L
        val deadline = System.nanoTime() + CleanupAction.CACHE.budgetMillis * 1_000_000
        val packages = rootThirdPartyPackages(protectedPackages() + additionalProtected, deadline) ?: return 0L
        return clearCache(true, packages, deadline).freedBytes
    }

    private data class CacheCleanup(val freedBytes: Long, val complete: Boolean)

    private fun clearCache(root: Boolean, packages: List<String>, deadline: Long): CacheCleanup {
        val paths = if (root) packages.flatMap(::cachePaths) else listOfNotNull(
            appContext.cacheDir?.absolutePath, appContext.externalCacheDir?.absolutePath
        )
        if (System.nanoTime() >= deadline) return CacheCleanup(0, false)
        if (paths.isEmpty()) return CacheCleanup(0, true)
        val before = freeStorageBytes()
        // A single rm process handles all eligible cache contents, preserving cache directories.
        val command = "exec rm -rf " + paths.joinToString(" ") { path ->
            val quoted = shellQuote(path)
            "$quoted/* $quoted/.[!.]* $quoted/..?*"
        }
        val timeout = minOf(4000, ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
        val result = if (root) RootShell.execMillis(command, timeout) else {
            RootProcess.runMillis(listOf("sh", "-c", command), null, timeout).let { RootShell.Result(it.success, it.output) }
        }
        appendDiagnostic("CACHE command=${if (result.success) "ok" else "partial/failed"} output=${cleanOutput(result.output)}")
        return CacheCleanup((freeStorageBytes() - before).coerceAtLeast(0L), result.success)
    }

    fun diagnosticsSummary(): String {
        val root = rootAvailable()
        if (!root) return "STANDARD MODE • ROOT NOT GRANTED"
        val eligible = rootThirdPartyPackages(protectedPackages()) ?: return "ROOT ACTIVE • app discovery unavailable"
        val processes = rootProcessSnapshot()
        val running = eligible.count { processes?.isRunning(it) == true }
        return "ROOT ACTIVE • UID 0 • $running running • ${eligible.size} eligible"
    }

    fun runningThirdPartyCount(): Int = runningThirdPartyPackages().size

    fun runningThirdPartyPackages(): List<String> {
        val protected = protectedPackages()
        if (!rootAvailable()) return nonRootCandidates(protected)
        val processes = rootProcessSnapshot()
        return rootThirdPartyPackages(protected).orEmpty().filter { processes?.isRunning(it) == true }
    }

    fun isThirdPartyPackageRunning(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!rootAvailable()) return packageName in nonRootCandidates(protectedPackages())
        // Missing verification is treated as still running.
        return rootProcessSnapshot()?.isRunning(packageName) ?: true
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

    private fun rootThirdPartyPackages(protected: Set<String>, deadline: Long = System.nanoTime() + 2_000_000_000L): List<String>? {
        val result = runRoot("pm list packages -3", deadline, 2000)
        if (!result.success) return null
        return result.output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotBlank() && it != appContext.packageName && it !in protected }
            .filterNot { it.contains("launcher", ignoreCase = true) }
            .distinct()
            .sorted()
            .toList()
    }

    private fun rootProcessSnapshot(deadline: Long = System.nanoTime() + 2_000_000_000L): RootProcessSnapshot? {
        // The registry also works on TV firmware that isolates /proc from app-spawned root.
        val activity = runRoot("dumpsys activity processes", deadline, 1500)
        if (activity.success) RootProcessSnapshot.fromActivity(activity.output)?.let { return it }
        // One wide ps process replaces a separate cat process for every /proc entry.
        val processes = runRoot("ps -A -w -o NAME", deadline, 1000)
        return if (processes.success) RootProcessSnapshot.fromPs(processes.output) else null
    }

    private fun nonRootCandidates(protected: Set<String>): List<String> =
        activityManager.runningAppProcesses.orEmpty()
            .flatMap { it.pkgList?.toList().orEmpty() }
            .distinct()
            .filter { it != appContext.packageName && it !in protected }
            .filterNot { it.contains("launcher", ignoreCase = true) }
            .filterNot(::isSystemPackage)

    private fun protectedPackages(): Set<String> {
        val set = mutableSetOf(
            appContext.packageName,
            "com.shadowfoxtv",
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
        // Ultimate Center's protected-app selections also apply to Smart Optimize.
        val userPrefs = appContext.getSharedPreferences("shadowfox_v6", Context.MODE_PRIVATE)
        set.addAll(userPrefs.getStringSet("protected", emptySet()) ?: emptySet())
        return set
    }

    private fun cachePaths(pkg: String): List<String> = listOf(
        "/data/user/0/$pkg/cache",
        "/data/user/0/$pkg/code_cache"
    )

    private fun isSystemPackage(packageName: String): Boolean = runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(true)

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

    private fun runRoot(command: String, deadline: Long, maximumMillis: Long): RootExec {
        val remaining = ((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(0)
        if (remaining == 0L) return RootExec(false, "timeout")
        val result = RootShell.execMillis(command, minOf(remaining, maximumMillis))
        return RootExec(result.success, result.output)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        if (bytes < 1024L * 1024L) return "${(bytes / 1024L).coerceAtLeast(1L)} KB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
    }

    private data class RootExec(val success: Boolean, val output: String)
}
