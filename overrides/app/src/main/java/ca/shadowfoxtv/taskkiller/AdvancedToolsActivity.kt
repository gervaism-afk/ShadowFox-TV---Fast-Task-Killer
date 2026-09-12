package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

private val ABG = Color(0xFF03111D)
private val APANEL = Color(0xFF0A2637)
private val ACYAN = Color(0xFF00E5FF)
private val AWHITE = Color(0xFFF7FBFF)
private val AMUTED = Color(0xFF9AABB8)
private val AGREEN = Color(0xFF77C943)
private val AORANGE = Color(0xFFFF7A00)

class AdvancedToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = ABG, surface = APANEL)) {
                AdvancedToolsScreen(AdvancedManager(applicationContext)) { finish() }
            }
        }
    }
}

private enum class AdvancedTab { RUNNING, STARTUP, SYSTEM, MODES, SAFETY }

data class AdvancedApp(val packageName: String, val label: String, val system: Boolean, val running: Boolean)
data class BenchmarkResult(val score: Int, val cpuMs: Long, val storageMs: Long, val ramFreeMb: Long)

class AdvancedManager(private val context: Context) {
    private val app = context.applicationContext
    private val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pm = app.packageManager
    private val prefs = app.getSharedPreferences("shadowfox_advanced", Context.MODE_PRIVATE)

    fun rooted() = DeviceCapabilityDetector.detect(app).rooted

    suspend fun runningApps(): List<AdvancedApp> = withContext(Dispatchers.IO) {
        val running = if (rooted()) rootRunningPackages() else am.runningAppProcesses.orEmpty().flatMap { it.pkgList?.toList().orEmpty() }.distinct()
        running.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                AdvancedApp(pkg, pm.getApplicationLabel(info).toString(), isSystem(info), true)
            }.getOrNull()
        }.filter { !isCritical(it.packageName) }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    suspend fun launchableApps(systemOnly: Boolean? = null): List<AdvancedApp> = withContext(Dispatchers.IO) {
        val running = runningApps().map { it.packageName }.toSet()
        pm.getInstalledApplications(0).asSequence().mapNotNull { info ->
            val sys = isSystem(info)
            if (systemOnly != null && sys != systemOnly) return@mapNotNull null
            if (pm.getLaunchIntentForPackage(info.packageName) == null) return@mapNotNull null
            AdvancedApp(info.packageName, pm.getApplicationLabel(info).toString(), sys, info.packageName in running)
        }.filter { !isCritical(it.packageName) }.sortedBy { it.label.lowercase(Locale.getDefault()) }.toList()
    }

    suspend fun stop(pkg: String): String = withContext(Dispatchers.IO) {
        if (isCritical(pkg)) return@withContext "Protected system component"
        if (rooted()) {
            val ok = root("am force-stop --user 0 ${q(pkg)}").first
            if (ok) "Stopped ${label(pkg)}" else "Root stop failed"
        } else {
            runCatching { am.killBackgroundProcesses(pkg) }
            "Standard background clean requested for ${label(pkg)}"
        }
    }

    fun launch(pkg: String) { pm.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(app::startActivity) }
    fun appSettings(pkg: String) { app.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    fun startupBlocked(): Set<String> = prefs.getStringSet("startup_blocked", emptySet())?.toSet().orEmpty()
    suspend fun setStartupBlocked(pkg: String, blocked: Boolean): String = withContext(Dispatchers.IO) {
        if (!rooted()) {
            appSettings(pkg)
            return@withContext "Standard mode: opened app settings"
        }
        if (isCritical(pkg)) return@withContext "Protected system component"
        val mode = if (blocked) "ignore" else "allow"
        val a = root("cmd appops set ${q(pkg)} RUN_IN_BACKGROUND $mode").first
        val b = root("cmd appops set ${q(pkg)} RUN_ANY_IN_BACKGROUND $mode").first
        if (a || b) {
            val next = startupBlocked().toMutableSet().apply { if (blocked) add(pkg) else remove(pkg) }
            prefs.edit().putStringSet("startup_blocked", next).putString("undo_type", "startup").putString("undo_pkg", pkg).putBoolean("undo_state", blocked).apply()
            if (blocked) "Startup/background restricted" else "Startup/background restored"
        } else "Startup control unavailable on this ROM"
    }

    suspend fun setFrozen(pkg: String, frozen: Boolean): String = withContext(Dispatchers.IO) {
        if (!rooted()) { appSettings(pkg); return@withContext "Standard mode: opened app settings" }
        if (isCritical(pkg)) return@withContext "Protected system component"
        val cmd = if (frozen) "pm disable-user --user 0 ${q(pkg)}" else "pm enable ${q(pkg)}"
        val ok = root(cmd).first
        if (ok) {
            prefs.edit().putString("undo_type", "freeze").putString("undo_pkg", pkg).putBoolean("undo_state", frozen).apply()
            if (frozen) "Frozen ${label(pkg)}" else "Enabled ${label(pkg)}"
        } else "System app action failed"
    }

    suspend fun gamingMode(pkg: String?): String {
        val result = UltimateManager(app).streamingOptimize(pkg)
        return "GAMING MODE • ${result.closedApps} stopped • RAM optimized"
    }

    suspend fun resetConnection(): String = withContext(Dispatchers.IO) {
        if (rooted()) {
            val off = root("svc wifi disable").first
            Thread.sleep(900)
            val on = root("svc wifi enable").first
            if (off && on) "Wi-Fi radio reset complete" else "Root connection reset not supported"
        } else {
            val intent = if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { app.startActivity(intent) }
            "Opened Android connection controls"
        }
    }

    suspend fun benchmark(): BenchmarkResult = withContext(Dispatchers.Default) {
        val cpuStart = System.nanoTime()
        var x = 0L
        for (i in 1..1_500_000) x = (x * 31 + i) xor (i.toLong() shl 1)
        val cpuMs = (System.nanoTime() - cpuStart) / 1_000_000L
        val temp = File(app.cacheDir, "sf_bench.tmp")
        val data = ByteArray(512 * 1024) { (it and 0x7f).toByte() }
        val storageStart = System.nanoTime()
        temp.outputStream().use { it.write(data); it.flush() }
        temp.inputStream().use { it.readBytes() }
        temp.delete()
        val storageMs = (System.nanoTime() - storageStart) / 1_000_000L
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val freeMb = mem.availMem / 1024 / 1024
        val score = (1000 - cpuMs.coerceAtMost(700) - storageMs.coerceAtMost(250)).toInt().coerceIn(100, 1000)
        if (x == Long.MIN_VALUE) prefs.edit().putLong("bench_guard", x).apply()
        BenchmarkResult(score, cpuMs, storageMs, freeMb)
    }

    fun selfHealEnabled() = prefs.getBoolean("self_heal", false)
    fun setSelfHeal(enabled: Boolean) {
        prefs.edit().putBoolean("self_heal", enabled).apply()
        UltimateManager(app).scheduleMaintenance(enabled)
    }

    suspend fun runSelfHealNow(): String {
        val snap = UltimateManager(app).snapshot()
        return if (snap.health < 85 || snap.ramUsedPercent > 75) {
            val r = UltimateManager(app).smartOptimize(); "SELF-HEAL • ${r.summary}"
        } else "SELF-HEAL • Device healthy (${snap.health}/100)"
    }

    suspend fun undoLast(): String = withContext(Dispatchers.IO) {
        val type = prefs.getString("undo_type", null) ?: return@withContext "Nothing to undo"
        val pkg = prefs.getString("undo_pkg", null) ?: return@withContext "Nothing to undo"
        val state = prefs.getBoolean("undo_state", false)
        val result = when (type) {
            "freeze" -> setFrozen(pkg, !state)
            "startup" -> setStartupBlocked(pkg, !state)
            else -> "Nothing to undo"
        }
        prefs.edit().remove("undo_type").remove("undo_pkg").remove("undo_state").apply()
        "UNDO • $result"
    }

    fun checkUpdate() { GitHubReleaseUpdater.start(app) }

    private fun label(pkg: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    private fun isSystem(info: ApplicationInfo) = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 || (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    private fun isCritical(pkg: String): Boolean {
        if (pkg == app.packageName || pkg == "android" || pkg == "com.android.systemui" || pkg.contains("launcher", true)) return true
        return pkg in setOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending", "com.android.permissioncontroller", "com.amazon.device.software.ota")
    }
    private fun rootRunningPackages(): List<String> {
        val r = root("ps -A -o NAME")
        if (!r.first) return emptyList()
        return r.second.lineSequence().map { it.trim().substringBefore(':') }.filter { it.contains('.') }.distinct().toList()
    }
    private fun root(cmd: String): Pair<Boolean,String> = runCatching {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val done = p.waitFor(12, TimeUnit.SECONDS)
        if (!done) { p.destroyForcibly(); false to "timeout" } else (p.exitValue() == 0) to p.inputStream.bufferedReader().use { it.readText() }
    }.getOrElse { false to it.message.orEmpty() }
    private fun q(v: String) = "'" + v.replace("'", "'\\''") + "'"
}

@Composable
private fun AdvancedToolsScreen(manager: AdvancedManager, onBack: () -> Unit) {
    var tab by remember { mutableStateOf(AdvancedTab.RUNNING) }
    var message by remember { mutableStateOf(if (manager.rooted()) "ROOT MODE ACTIVE" else "STANDARD MODE ACTIVE") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(ABG).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ShadowFox ADVANCED", color = AWHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text("v${BuildConfig.VERSION_NAME} • ${if (manager.rooted()) "ROOT" else "STANDARD"}", color = if (manager.rooted()) AGREEN else ACYAN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Action("BACK") { onBack() }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdvancedTab.entries.forEach { t -> TabButton(t.name, t == tab, Modifier.weight(1f)) { tab = t } }
        }
        Spacer(Modifier.height(8.dp))
        Text(message, color = AMUTED, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        when (tab) {
            AdvancedTab.RUNNING -> RunningPane(manager) { message = it }
            AdvancedTab.STARTUP -> StartupPane(manager) { message = it }
            AdvancedTab.SYSTEM -> SystemPane(manager) { message = it }
            AdvancedTab.MODES -> ModesPane(manager) { message = it }
            AdvancedTab.SAFETY -> SafetyPane(manager) { message = it }
        }
    }
}

@Composable
private fun RunningPane(manager: AdvancedManager, status: (String)->Unit) {
    val scope = rememberCoroutineScope(); var apps by remember { mutableStateOf<List<AdvancedApp>>(emptyList()) }
    fun load() { scope.launch { apps = manager.runningApps() } }
    LaunchedEffect(Unit) { load() }
    ScrollPane {
        apps.forEach { a -> ToolCard(a.label, a.packageName) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Action("LAUNCH") { manager.launch(a.packageName) }
                Action("STOP") { scope.launch { status(manager.stop(a.packageName)); load() } }
                Action("SETTINGS") { manager.appSettings(a.packageName) }
            }
        } }
    }
}

@Composable
private fun StartupPane(manager: AdvancedManager, status: (String)->Unit) {
    val scope = rememberCoroutineScope(); var apps by remember { mutableStateOf<List<AdvancedApp>>(emptyList()) }; var blocked by remember { mutableStateOf(manager.startupBlocked()) }
    LaunchedEffect(Unit) { apps = manager.launchableApps(false) }
    ScrollPane {
        Text(if (manager.rooted()) "Root mode can restrict background auto-start. Critical apps are protected." else "Standard mode opens Android app settings because Android blocks direct startup control.", color = AMUTED, fontSize = 10.sp)
        apps.forEach { a -> ToolCard(a.label, a.packageName) {
            Action(if (a.packageName in blocked) "ALLOW STARTUP" else "BLOCK STARTUP") { scope.launch { status(manager.setStartupBlocked(a.packageName, a.packageName !in blocked)); blocked = manager.startupBlocked() } }
        } }
    }
}

@Composable
private fun SystemPane(manager: AdvancedManager, status: (String)->Unit) {
    val scope = rememberCoroutineScope(); var apps by remember { mutableStateOf<List<AdvancedApp>>(emptyList()) }
    LaunchedEffect(Unit) { apps = manager.launchableApps(true) }
    ScrollPane {
        Text("Advanced system-app controls are root-only. Core Android, launcher, Play/Amazon services and ShadowFox are protected.", color = AORANGE, fontSize = 10.sp)
        apps.forEach { a -> ToolCard(a.label, a.packageName) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Action("FREEZE") { scope.launch { status(manager.setFrozen(a.packageName, true)) } }
                Action("ENABLE") { scope.launch { status(manager.setFrozen(a.packageName, false)) } }
                Action("SETTINGS") { manager.appSettings(a.packageName) }
            }
        } }
    }
}

@Composable
private fun ModesPane(manager: AdvancedManager, status: (String)->Unit) {
    val scope = rememberCoroutineScope(); var games by remember { mutableStateOf<List<AdvancedApp>>(emptyList()) }; var bench by remember { mutableStateOf<BenchmarkResult?>(null) }
    LaunchedEffect(Unit) { games = manager.launchableApps(false) }
    ScrollPane {
        ToolCard("GAMING MODE", "Optimize memory/background load, then launch a game or app.") {
            games.take(20).forEach { a -> Action("PLAY • ${a.label.take(18)}") { scope.launch { status(manager.gamingMode(a.packageName)) } }; Spacer(Modifier.height(4.dp)) }
        }
        ToolCard("CONNECTION RESET", "Root: resets Wi-Fi radio. Standard: opens Android connection controls.") { Action("RESET CONNECTION") { scope.launch { status(manager.resetConnection()) } } }
        ToolCard("LIGHTWEIGHT BENCHMARK", "Quick CPU, cache-storage and free-memory check.") {
            Action("RUN BENCHMARK") { scope.launch { bench = manager.benchmark(); status("Benchmark complete") } }
            bench?.let { Text("Score ${it.score}/1000 • CPU ${it.cpuMs} ms • Storage ${it.storageMs} ms • Free RAM ${it.ramFreeMb} MB", color = AWHITE, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun SafetyPane(manager: AdvancedManager, status: (String)->Unit) {
    val scope = rememberCoroutineScope(); var selfHeal by remember { mutableStateOf(manager.selfHealEnabled()) }
    ScrollPane {
        ToolCard("AUTOMATIC SELF-HEAL", "Daily health-aware maintenance. Cleanup only runs when health/RAM conditions justify it.") {
            Action(if (selfHeal) "SELF-HEAL: ON" else "SELF-HEAL: OFF") { selfHeal = !selfHeal; manager.setSelfHeal(selfHeal); status(if (selfHeal) "Self-heal enabled" else "Self-heal disabled") }
            Spacer(Modifier.height(6.dp)); Action("RUN HEALTH CHECK NOW") { scope.launch { status(manager.runSelfHealNow()) } }
        }
        ToolCard("UNDO / SAFETY CENTER", "Reverses the most recent startup restriction or system-app freeze action.") { Action("UNDO LAST ADVANCED ACTION") { scope.launch { status(manager.undoLast()) } } }
        ToolCard("UPDATE CENTER", "Uses the same verified GitHub release updater and signature checks as the main app.") { Action("CHECK FOR UPDATE") { manager.checkUpdate(); status("Update check started") } }
    }
}

@Composable private fun ScrollPane(content: @Composable ColumnScope.()->Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
@Composable private fun ToolCard(title:String, subtitle:String, content:@Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxWidth().background(APANEL, RoundedCornerShape(12.dp)).padding(12.dp)) { Text(title, color=AWHITE, fontSize=14.sp, fontWeight=FontWeight.Black); Text(subtitle,color=AMUTED,fontSize=9.sp,maxLines=2,overflow=TextOverflow.Ellipsis); Spacer(Modifier.height(7.dp)); content() } }
@Composable private fun Action(text:String,onClick:()->Unit) { Box(Modifier.height(38.dp).background(ACYAN, RoundedCornerShape(20.dp)).clickable(onClick=onClick).focusable().padding(horizontal=13.dp), contentAlignment=Alignment.Center) { Text(text,color=ABG,fontSize=9.sp,fontWeight=FontWeight.Black,maxLines=1,overflow=TextOverflow.Ellipsis) } }
@Composable private fun TabButton(text:String, selected:Boolean, modifier:Modifier,onClick:()->Unit) { Box(modifier.height(40.dp).background(if(selected) ACYAN else APANEL,RoundedCornerShape(10.dp)).clickable(onClick=onClick).focusable(),contentAlignment=Alignment.Center) { Text(text,color=if(selected) ABG else AWHITE,fontSize=8.sp,fontWeight=FontWeight.Black,maxLines=1) } }
