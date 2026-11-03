package ca.shadowfoxtv.taskkiller

import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HealthSettings(val lowStorage: Boolean, val thresholdMb: Int, val heat: Boolean)

class UserToolsManager(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("shadowfox_user_tools", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()

    fun setFavorite(packageName: String, favorite: Boolean) {
        val next = favorites().toMutableSet()
        if (favorite) next.add(packageName) else next.remove(packageName)
        prefs.edit().putStringSet("favorites", next).apply()
    }

    fun healthSettings(): HealthSettings = HealthSettings(
        prefs.getBoolean("low_storage", false), prefs.getInt("threshold_mb", 1024),
        prefs.getBoolean("heat", false) && Build.VERSION.SDK_INT >= 29
    )

    fun saveHealthSettings(value: HealthSettings) {
        val previous = healthSettings()
        prefs.edit().putBoolean("low_storage", value.lowStorage)
            .putInt("threshold_mb", value.thresholdMb.coerceIn(256, 4096))
            .putBoolean("heat", value.heat && Build.VERSION.SDK_INT >= 29).apply()
        if (!value.lowStorage || previous.thresholdMb != value.thresholdMb) {
            prefs.edit().putBoolean("storage_active", false).apply()
        }
        if (!value.heat) prefs.edit().putBoolean("heat_active", false).apply()
        HealthAlerts.settingsChanged(app)
    }

    fun openStorageSettings(): Boolean = runCatching {
        app.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrElse {
        runCatching {
            app.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun lastStabilityReport(): String = prefs.getString("last_stability", "No stability test recorded.").orEmpty()

    @RequiresApi(29)
    suspend fun saveReportToDownloads(contents: String): Boolean = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "ShadowFox-support-v${BuildConfig.VERSION_NAME}-${System.currentTimeMillis()}.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ShadowFox")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(contents) } ?: error("No output stream")
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) > 0)
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    }

    suspend fun testStability(onProgress: (Int, Int) -> Unit): StabilityResult = withContext(Dispatchers.IO) {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val startingNetwork = cm.activeNetwork
        var changed = false
        val samples = mutableListOf<Int>()
        repeat(12) { index ->
            currentCoroutineContext().ensureActive()
            if (cm.activeNetwork != startingNetwork) changed = true
            val time = if (cm.activeNetwork == null) -1 else runCatching {
                val start = System.nanoTime()
                Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 1500) }
                ((System.nanoTime() - start) / 1_000_000).toInt()
            }.getOrDefault(-1)
            samples.add(time)
            withContext(Dispatchers.Main) { onProgress(index + 1, 12) }
            if (index < 11) delay(500)
        }
        if (cm.activeNetwork != startingNetwork) changed = true
        currentCoroutineContext().ensureActive()
        StabilityAnalysis.summarize(samples, changed).also { result ->
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            prefs.edit().putString("last_stability", "$stamp | ${stabilityText(result)}").apply()
        }
    }

    suspend fun supportReport(manager: UltimateManager): String = withContext(Dispatchers.IO) {
        val storage = StatFs(Environment.getDataDirectory().absolutePath)
        val settings = healthSettings()
        val report = buildString {
            appendLine("SHADOWFOX SUPPORT REPORT")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm z", Locale.US).format(Date())}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
            appendLine("Mode: ${if (isGoogleTvStreamer(Build.MANUFACTURER, Build.MODEL)) "STANDARD MODE" else manager.cachedCapabilities()?.modeLabel ?: "Not checked this session"}")
            appendLine("Storage: ${storage.availableBytes / 1048576} MB free / ${storage.totalBytes / 1048576} MB total")
            appendLine("Thermal: ${HealthAlerts.thermalLabel(app)} (Android-reported)")
            appendLine("Storage alerts: ${settings.lowStorage}; threshold: ${settings.thresholdMb} MB")
            appendLine("Heat alerts: ${settings.heat}")
            appendLine("\nLAST CONNECTION STABILITY TEST\n${lastStabilityReport()}")
            appendLine("\nRECENT OPTIMIZER DIAGNOSTICS")
            val diagnostics = manager.optimizerDiagnostics().takeLast(12)
            if (diagnostics.isEmpty()) appendLine("No optimizer diagnostics recorded.")
            else diagnostics.forEach { appendLine(it.take(500)) }
            appendLine("\nRECENT MAINTENANCE HISTORY")
            manager.history().take(5).forEach { appendLine(it.take(500)) }
            appendLine("\nNo device serial, account, Wi-Fi name, or installed-app inventory is collected.")
            appendLine("Diagnostic entries may include app package names. Review before sharing.")
        }
        SupportRedaction.clean(report)
    }

    companion object {
        fun stabilityText(result: StabilityResult): String = buildString {
            appendLine(result.verdict)
            appendLine("${result.attempts} connection attempts • ${result.failed} failed")
            appendLine("Typical: ${result.medianMs?.let { "$it ms" } ?: "--"} • Variation: ${result.variationMs?.let { "$it ms" } ?: "--"} • Slowest: ${result.maximumMs?.let { "$it ms" } ?: "--"}")
            if (result.networkChanged) appendLine("Connection changed during the test. Run again on one network.")
            append("Tests TCP connections to 1.1.1.1:443. This is not a speed or packet-loss test, and does not test your streaming provider.")
        }
    }
}
