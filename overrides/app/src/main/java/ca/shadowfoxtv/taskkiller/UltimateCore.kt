package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.MediaDrm
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class UltimateSnapshot(
    val mode: String,
    val root: Boolean,
    val health: Int,
    val ramUsedPercent: Int,
    val freeRam: Long,
    val freeStorage: Long,
    val runningApps: Int,
    val temperatureStatus: String,
    val network: String
)

data class ManagedApp(
    val packageName: String,
    val label: String,
    val running: Boolean,
    val protected: Boolean,
    val system: Boolean
)

data class NetworkReport(
    val connected: Boolean,
    val transport: String,
    val pingMs: Int,
    val dnsMs: Int,
    val verdict: String
)

data class StorageReport(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val appCacheBytes: Long
)

data class DeviceReport(
    val manufacturer: String,
    val model: String,
    val android: String,
    val sdk: Int,
    val abi: String,
    val rootMode: String,
    val widevine: String,
    val thermal: String
)

class UltimateManager(private val context: Context) {
    private val app = context.applicationContext
    private val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pm = app.packageManager
    private val prefs = app.getSharedPreferences("shadowfox_v6", Context.MODE_PRIVATE)
    @Volatile private var cachedCapabilities: DeviceCapabilities? = null
    @Volatile private var cachedApps: List<ManagedApp>? = null
    @Volatile private var cachedLaunchableApps: List<Pair<String, String>>? = null

    fun capabilities(): DeviceCapabilities = cachedCapabilities ?: DeviceCapabilityDetector.detect(app).also { cachedCapabilities = it }

    fun cachedCapabilities(): DeviceCapabilities? = cachedCapabilities
    fun appContext(): Context = app

    fun protectedPackages(): Set<String> = prefs.getStringSet("protected", emptySet())?.toSet().orEmpty()

    fun setProtected(pkg: String, value: Boolean) {
        val next = protectedPackages().toMutableSet()
        if (value) next += pkg else next -= pkg
        prefs.edit().putStringSet("protected", next).apply()
    }

    suspend fun snapshot(): UltimateSnapshot = withContext(Dispatchers.IO) {
        // First paint must never wait for a root shell probe. Use the cached capability
        // once available; root-aware actions still call capabilities() when required.
        val caps = cachedCapabilities()
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val usedPct = if (mem.totalMem > 0) (((mem.totalMem - mem.availMem) * 100) / mem.totalMem).toInt() else 0
        // Keep the first dashboard paint fast on low-power TV sticks.
        // Root process enumeration can be expensive, so do not block RAM/storage/network
        // telemetry on a shell scan. Active-app detail is refreshed by the Apps/optimizer path.
        val running = runCatching {
            am.runningAppProcesses?.count { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE } ?: 0
        }.getOrDefault(0)
        val storage = storageReport()
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val networkConnected = active != null && cm.getNetworkCapabilities(active)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val networkLabel = if (networkConnected) "ONLINE" else "OFFLINE"
        val storageFreePct = if (storage.totalBytes > 0) (storage.freeBytes * 100 / storage.totalBytes).toInt() else 0
        var score = 100
        score -= ((usedPct - 65).coerceAtLeast(0) * 2).coerceAtMost(28)
        score -= ((20 - storageFreePct).coerceAtLeast(0) * 2).coerceAtMost(24)
        if (!networkConnected) score -= 20
        if (running > 20) score -= ((running - 20) / 2).coerceAtMost(10)
        UltimateSnapshot(
            mode = caps?.modeLabel ?: "DETECTING MODE",
            root = caps?.rooted ?: false,
            health = score.coerceIn(0, 100),
            ramUsedPercent = usedPct,
            freeRam = mem.availMem,
            freeStorage = storage.freeBytes,
            runningApps = running,
            temperatureStatus = thermalStatus(),
            network = networkLabel
        )
    }

    suspend fun smartOptimize(): ProCleanupResult {
        val result = ShadowFoxProEngine(app).optimize()
        appendHistory("SMART OPTIMIZE | ${result.summary}")
        return result
    }

    fun optimizerDiagnostics(): List<String> = ShadowFoxProEngine(app).readRecentDiagnostics(20)

    suspend fun streamingOptimize(targetPackage: String?): ProCleanupResult = withContext(Dispatchers.IO) {
        val caps = capabilities()
        val protected = protectedPackages().toMutableSet().apply {
            add(app.packageName)
            if (!targetPackage.isNullOrBlank()) add(targetPackage)
        }
        val before = ActivityManager.MemoryInfo().also(am::getMemoryInfo).availMem
        var stopped = 0
        var attempted = 0

        if (caps.rooted) {
            val engine = ShadowFoxProEngine(app)
            val running = engine.runningThirdPartyPackages().filter { it !in protected && !isCritical(it) }
            attempted = running.size
            for (pkg in running) {
                val stop = runRoot("am force-stop --user 0 ${shellQuote(pkg)}")
                if (stop.first) {
                    Thread.sleep(35)
                    if (!engine.isThirdPartyPackageRunning(pkg)) stopped++
                }
            }
            runRoot("pm trim-caches 999999999999")
            runRoot("sync")
        } else {
            for (pkg in runningPackages().filter { it !in protected && !isSystem(it) }) {
                runCatching { am.killBackgroundProcesses(pkg) }
            }
            Thread.sleep(350)
        }

        Thread.sleep(300)
        val after = ActivityManager.MemoryInfo().also(am::getMemoryInfo).availMem
        val freed = (after - before).coerceAtLeast(0)
        val result = ProCleanupResult(
            closedApps = stopped,
            ramFreedBytes = freed,
            storageFreedBytes = 0,
            rootUsed = caps.rooted,
            attemptedApps = attempted,
            verifiedStopped = stopped,
            cacheFreedBytes = 0,
            summary = if (caps.rooted) "STREAMING MODE • $stopped/$attempted verified stopped • +${formatBytes(freed)} measured RAM" else "STREAMING MODE • standard background clean requested • +${formatBytes(freed)} measured RAM • app stops unverified"
        )
        appendHistory(result.summary)
        if (!targetPackage.isNullOrBlank()) {
            pm.getLaunchIntentForPackage(targetPackage)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(app::startActivity)
        }
        result
    }

    suspend fun apps(forceRefresh: Boolean = false): List<ManagedApp> = withContext(Dispatchers.IO) {
        if (!forceRefresh) cachedApps?.let { return@withContext it }
        val protected = protectedPackages()
        val running = am.runningAppProcesses
            ?.asSequence()
            ?.filter { it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE }
            ?.flatMap { it.pkgList.asSequence() }
            ?.toSet()
            .orEmpty()

        // Build the launchable-app metadata once. Opening APPS after the first load only
        // recomputes cheap running/protected state instead of querying and relabelling packages.
        val launchable: List<Pair<String, String>> = (if (!forceRefresh) cachedLaunchableApps else null) ?: run {
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            (pm.queryIntentActivities(launcherIntent, 0) + pm.queryIntentActivities(leanbackIntent, 0))
                .asSequence()
                .mapNotNull { ri ->
                    val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
                    val pkg = ai.packageName
                    pkg to runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(pkg)
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase(Locale.getDefault()) }
                .toList()
                .also { cachedLaunchableApps = it }
        }
        val result = launchable.map { (pkg, label) ->
            val ai = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
            ManagedApp(
                packageName = pkg,
                label = label,
                running = pkg in running,
                protected = pkg in protected,
                system = ai?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
            )
        }
        cachedApps = result
        result
    }

    fun launch(pkg: String) {
        pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(app::startActivity)
    }

    fun openSettings(pkg: String) {
        app.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun uninstall(pkg: String) {
        app.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun freeze(pkg: String, freeze: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!capabilities().rooted || isCritical(pkg)) return@withContext false
        runRoot(if (freeze) "pm disable-user --user 0 ${shellQuote(pkg)}" else "pm enable ${shellQuote(pkg)}").first
    }

    suspend fun networkReport(): NetworkReport = withContext(Dispatchers.IO) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let(cm::getNetworkCapabilities)
        val connected = network != null && caps != null
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            else -> if (connected) "Connected" else "Offline"
        }
        val ping = if (connected) socketLatency("1.1.1.1", 443) else -1
        val dns = if (connected) dnsLatency("cloudflare.com") else -1
        val verdict = when {
            !connected -> "NO INTERNET"
            ping < 0 -> "CONNECTION ISSUE"
            ping <= 45 -> "EXCELLENT"
            ping <= 90 -> "GOOD"
            ping <= 150 -> "FAIR"
            else -> "HIGH LATENCY"
        }
        NetworkReport(connected, transport, ping, dns, verdict)
    }

    fun storageReport(): StorageReport {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val ownCache = dirSize(app.cacheDir) + (app.externalCacheDir?.let(::dirSize) ?: 0L)
        return StorageReport(total, free, (total - free).coerceAtLeast(0), ownCache)
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val beforeFree = storageReport().freeBytes
        if (RootShell.isRootAvailable()) {
            RootShell.exec("pm trim-caches 999999999999", 15)
            RootShell.exec("sync", 15)
        } else {
            clearOwnCache()
        }
        val freed = (storageReport().freeBytes - beforeFree).coerceAtLeast(0)
        appendHistory("CACHE CLEANER • ${formatBytes(freed)} cleared")
        freed
    }

    fun clearOwnCache(): Long {
        val before = storageReport().appCacheBytes
        app.cacheDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        app.externalCacheDir?.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
        val after = storageReport().appCacheBytes
        val freed = (before - after).coerceAtLeast(0)
        appendHistory("APP CACHE • ${formatBytes(freed)} cleared")
        return freed
    }

    fun deviceReport(): DeviceReport = DeviceReport(
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        android = Build.VERSION.RELEASE.orEmpty(),
        sdk = Build.VERSION.SDK_INT,
        abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
        rootMode = capabilities().modeLabel,
        widevine = widevineLevel(),
        thermal = thermalStatus()
    )

    fun history(): List<String> = prefs.getString("history", "")
        .orEmpty().lineSequence().filter { it.isNotBlank() }.toList().takeLast(20).reversed()

    fun scheduleMaintenance(enabled: Boolean) {
        prefs.edit().putBoolean("maintenance", enabled).apply()
        val alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(app, 610, Intent(app, MaintenanceReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (!enabled) {
            alarms.cancel(pi)
        } else {
            alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, android.os.SystemClock.elapsedRealtime() + 6 * 60 * 60 * 1000L, 24 * 60 * 60 * 1000L, pi)
        }
    }

    fun maintenanceEnabled(): Boolean = prefs.getBoolean("maintenance", false)

    fun disableAutomaticMaintenance() {
        if (maintenanceEnabled()) scheduleMaintenance(false)
    }

    private fun appendHistory(message: String) {
        val stamp = SimpleDateFormat("MMM d HH:mm", Locale.US).format(Date())
        val old = prefs.getString("history", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList().takeLast(29)
        prefs.edit().putString("history", (old + "$stamp • $message").joinToString("\n")).apply()
    }

    private fun runningPackages(): List<String> = am.runningAppProcesses.orEmpty().flatMap { it.pkgList?.toList().orEmpty() }.distinct()

    private fun isSystem(pkg: String): Boolean = runCatching {
        val info = pm.getApplicationInfo(pkg, 0)
        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    private fun isCritical(pkg: String): Boolean {
        if (pkg == app.packageName || pkg == "android" || pkg == "com.android.systemui" || pkg.contains("launcher", true)) return true
        return pkg in setOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.android.permissioncontroller")
    }

    private fun dnsLatency(host: String): Int = runCatching {
        val start = System.nanoTime()
        java.net.InetAddress.getByName(host)
        ((System.nanoTime() - start) / 1_000_000L).toInt()
    }.getOrDefault(-1)

    private fun socketLatency(host: String, port: Int): Int = runCatching {
        val start = System.nanoTime()
        Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
        ((System.nanoTime() - start) / 1_000_000L).toInt()
    }.getOrDefault(-1)

    private fun thermalStatus(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val p = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        when (p.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
            PowerManager.THERMAL_STATUS_LIGHT -> "WARM"
            PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
            PowerManager.THERMAL_STATUS_SEVERE -> "HOT"
            PowerManager.THERMAL_STATUS_CRITICAL, PowerManager.THERMAL_STATUS_EMERGENCY, PowerManager.THERMAL_STATUS_SHUTDOWN -> "CRITICAL"
            else -> "UNKNOWN"
        }
    } else "N/A"

    private fun widevineLevel(): String = runCatching {
        val uuid = UUID(-1301668207276963122L, -6645017420763422227L)
        MediaDrm(uuid).use { drm -> String(drm.getPropertyByteArray("securityLevel")) }
    }.getOrDefault("Unknown")

    private fun dirSize(file: java.io.File): Long = if (!file.exists()) 0L else if (file.isFile) file.length() else file.listFiles()?.sumOf(::dirSize) ?: 0L

    private fun runRoot(command: String): Pair<Boolean, String> = runCatching {
        val result = RootShell.exec(command, 12)
        result.success to result.output
    }.getOrElse { false to it.message.orEmpty() }

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
    }
}

class MaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        Thread {
            runCatching {
                val manager = UltimateManager(context)
                if (manager.maintenanceEnabled()) kotlinx.coroutines.runBlocking { manager.smartOptimize() }
            }
            pending.finish()
        }.start()
    }
}
