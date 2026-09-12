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

    fun capabilities(): DeviceCapabilities = DeviceCapabilityDetector.detect(app)

    fun protectedPackages(): Set<String> = prefs.getStringSet("protected", emptySet())?.toSet().orEmpty()

    fun setProtected(pkg: String, value: Boolean) {
        val next = protectedPackages().toMutableSet()
        if (value) next += pkg else next -= pkg
        prefs.edit().putStringSet("protected", next).apply()
    }

    suspend fun snapshot(): UltimateSnapshot = withContext(Dispatchers.IO) {
        val caps = capabilities()
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val usedPct = if (mem.totalMem > 0) (((mem.totalMem - mem.availMem) * 100) / mem.totalMem).toInt() else 0
        val running = runningPackages().size
        val storage = storageReport()
        val network = networkReport()
        val storageFreePct = if (storage.totalBytes > 0) (storage.freeBytes * 100 / storage.totalBytes).toInt() else 0
        var score = 100
        score -= ((usedPct - 65).coerceAtLeast(0) * 2).coerceAtMost(28)
        score -= ((20 - storageFreePct).coerceAtLeast(0) * 2).coerceAtMost(24)
        if (!network.connected) score -= 20 else if (network.pingMs > 120) score -= 10
        if (running > 20) score -= ((running - 20) / 2).coerceAtMost(10)
        UltimateSnapshot(
            mode = caps.modeLabel,
            root = caps.rooted,
            health = score.coerceIn(0, 100),
            ramUsedPercent = usedPct,
            freeRam = mem.availMem,
            freeStorage = storage.freeBytes,
            runningApps = running,
            temperatureStatus = thermalStatus(),
            network = network.verdict
        )
    }

    suspend fun smartOptimize(): ProCleanupResult {
        val result = ShadowFoxProEngine(app).optimize()
        appendHistory("SMART OPTIMIZE | ${result.summary}")
        return result
    }

    suspend fun streamingOptimize(targetPackage: String?): ProCleanupResult = withContext(Dispatchers.IO) {
        val caps = capabilities()
        val protected = protectedPackages().toMutableSet().apply {
            add(app.packageName)
            if (!targetPackage.isNullOrBlank()) add(targetPackage)
        }
        val before = ActivityManager.MemoryInfo().also(am::getMemoryInfo).availMem
        var stopped = 0

        if (caps.rooted) {
            val running = rootRunningPackages().filter { it !in protected && !isCritical(it) }
            for (pkg in running) {
                val stop = runRoot("am force-stop --user 0 ${shellQuote(pkg)}")
                if (stop.first) {
                    Thread.sleep(35)
                    if (!isRunningRoot(pkg)) stopped++
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
            attemptedApps = if (caps.rooted) stopped else 0,
            verifiedStopped = stopped,
            cacheFreedBytes = 0,
            summary = if (caps.rooted) "STREAMING MODE • $stopped stopped • +${formatBytes(freed)} RAM" else "STREAMING MODE READY • STANDARD SAFE CLEAN"
        )
        appendHistory(result.summary)
        if (!targetPackage.isNullOrBlank()) {
            pm.getLaunchIntentForPackage(targetPackage)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(app::startActivity)
        }
        result
    }

    suspend fun apps(): List<ManagedApp> = withContext(Dispatchers.IO) {
        val protected = protectedPackages()
        val running = if (capabilities().rooted) rootRunningPackages().toSet() else runningPackages().toSet()
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                ManagedApp(
                    packageName = it.packageName,
                    label = pm.getApplicationLabel(it).toString(),
                    running = it.packageName in running,
                    protected = it.packageName in protected,
                    system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
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
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            else -> if (connected) "Connected" else "Offline"
        }
        val ping = if (connected) socketLatency("1.1.1.1", 443) else -1
        val dns = if (connected) socketLatency("8.8.8.8", 53) else -1
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

    private fun appendHistory(message: String) {
        val stamp = SimpleDateFormat("MMM d HH:mm", Locale.US).format(Date())
        val old = prefs.getString("history", "").orEmpty().lineSequence().filter { it.isNotBlank() }.takeLast(29).toList()
        prefs.edit().putString("history", (old + "$stamp • $message").joinToString("\n")).apply()
    }

    private fun runningPackages(): List<String> = am.runningAppProcesses.orEmpty().flatMap { it.pkgList?.toList().orEmpty() }.distinct()

    private fun rootRunningPackages(): List<String> {
        val result = runRoot("ps -A -o NAME")
        if (!result.first) return emptyList()
        val installed = runRoot("pm list packages -3").second.lineSequence().map { it.removePrefix("package:").trim() }.filter { it.isNotBlank() }.toSet()
        return result.second.lineSequence().map { it.trim() }.filter { it in installed }.distinct().toList()
    }

    private fun isRunningRoot(pkg: String): Boolean {
        val out = runRoot("pidof ${shellQuote(pkg)}")
        return out.second.trim().isNotEmpty()
    }

    private fun isSystem(pkg: String): Boolean = runCatching {
        val info = pm.getApplicationInfo(pkg, 0)
        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

    private fun isCritical(pkg: String): Boolean {
        if (pkg == app.packageName || pkg == "android" || pkg == "com.android.systemui" || pkg.contains("launcher", true)) return true
        return pkg in setOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.android.permissioncontroller")
    }

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
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        val finished = process.waitFor(12, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly(); false to "timeout"
        } else {
            val out = process.inputStream.bufferedReader().use { it.readText() }
            (process.exitValue() == 0) to out
        }
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
