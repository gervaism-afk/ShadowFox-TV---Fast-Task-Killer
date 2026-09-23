package ca.shadowfoxtv.taskkiller

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import java.util.Locale

private val UBG = Color(0xFF03111D)
private val UPANEL = Color(0xE60A2030)
private val UCYAN = Color(0xFF00E5FF)
private val UORANGE = Color(0xFFFF7A00)
private val UWHITE = Color(0xFFF7FBFF)
private val UMUTED = Color(0xFF9AABB8)
private val UGREEN = Color(0xFF77C943)
private val UMETAL_TOP = Color(0xFF12334A)
private val UMETAL_MID = Color(0xFF0A2030)
private val UMETAL_BOTTOM = Color(0xFF04131F)
private val UMETAL_EDGE = Color(0xFF00E5FF)

class UltimateCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideBars()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = UBG, surface = UPANEL)) {
                UltimateCenter(UltimateManager(applicationContext), intent.getStringExtra("shadowfox_tab"), onClose = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    private fun hideBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

// TV_V6_ULTIMATE_REBUILD_693
private enum class UltimateTab { OPTIMIZE, APPS, NETWORK, SYSTEM }

@Composable
private fun UltimateCenter(manager: UltimateManager, requestedTab: String?, onClose: () -> Unit) {
    val initialTab = remember(requestedTab) { runCatching { UltimateTab.valueOf(requestedTab ?: "OPTIMIZE") }.getOrDefault(UltimateTab.OPTIMIZE) }
    var tab by remember { mutableStateOf(initialTab) }
    var snapshot by remember { mutableStateOf<UltimateSnapshot?>(null) }
    val scope = rememberCoroutineScope()
    val firstTabFocus = remember { FocusRequester() }
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val hasTelevisionUi = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val isPhone = !hasTelevisionUi && configuration.smallestScreenWidthDp < 600

    fun refresh() { scope.launch { snapshot = manager.snapshot() } }
    LaunchedEffect(Unit) {
        // Paint cheap local telemetry immediately; root detection runs independently
        // so slow su probes on TV sticks cannot hold the whole dashboard hostage.
        manager.disableAutomaticMaintenance()
        snapshot = manager.snapshot()
        if (hasTelevisionUi) firstTabFocus.requestFocus()
        launch(kotlinx.coroutines.Dispatchers.IO) {
            manager.capabilities()
            snapshot = manager.snapshot()
        }
        // Pre-warm launchable app metadata while the Optimize tab is visible so APPS opens immediately.
        launch(kotlinx.coroutines.Dispatchers.IO) { manager.apps() }
        while (true) {
            kotlinx.coroutines.delay(5000)
            snapshot = manager.snapshot()
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF0B3550), Color(0xFF052238), UBG)))
    ) {
        val side = if (landscape && !isPhone) 18.dp else if (landscape) 18.dp else 20.dp
        val top = if (landscape && !isPhone) 14.dp else if (landscape) 8.dp else 12.dp
        val contentMax = if (landscape && !isPhone) 920.dp else if (landscape) 1180.dp else 620.dp
        val contentWidth = minOf(maxWidth - (side * 2), contentMax)

        // Keep the entire command surface centered as one bounded canvas. Using
        // fillMaxSize after width() allowed the child to reclaim the parent width on TV.
        Column(
            Modifier.width(contentWidth).align(Alignment.TopCenter).padding(top = top, bottom = 10.dp)
        ) {
            if (landscape) {
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(UMETAL_TOP, UMETAL_MID, UMETAL_BOTTOM)), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandHeader(snapshot, Modifier.weight(1f), compact = true, isPhone = isPhone)
                    Box(Modifier.weight(.72f), contentAlignment = Alignment.Center) {
                        UltimateWeatherBadge(manager.appContext())
                    }
                    UltimateButton("REFRESH") { refresh() }
                    Spacer(Modifier.width(8.dp))
                    UltimateButton("BACK") { onClose() }
                }
            } else {
                BrandHeader(snapshot, Modifier.fillMaxWidth(), compact = false, isPhone = isPhone)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    UltimateButton("REFRESH") { refresh() }
                    Spacer(Modifier.width(8.dp))
                    UltimateButton("BACK") { onClose() }
                }
            }

            Spacer(Modifier.height(if (landscape) 10.dp else 10.dp))
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC0B2A3D), Color(0xDD061722), Color(0xEE020B11))), RoundedCornerShape(10.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UltimateTab.entries.forEachIndexed { index, item ->
                    UltimateTabButton(
                        item.name,
                        item == tab,
                        Modifier.weight(1f).then(if (index == 0 && hasTelevisionUi) Modifier.focusRequester(firstTabFocus) else Modifier)
                    ) { tab = item }
                }
            }
            Spacer(Modifier.height(if (landscape) 8.dp else 10.dp))

            when (tab) {
                UltimateTab.OPTIMIZE -> OptimizeScreen(manager, snapshot, ::refresh, landscape)
                UltimateTab.APPS -> AppsScreen(manager, landscape, isPhone)
                UltimateTab.NETWORK -> NetworkScreen(manager, landscape)
                UltimateTab.SYSTEM -> SystemScreen(manager, snapshot, ::refresh, landscape)
            }
        }
    }
}

@Composable
private fun UltimateWeatherBadge(context: android.content.Context) {
    val prefs = remember { context.getSharedPreferences("shadowfox_weather", android.content.Context.MODE_PRIVATE) }
    var city by remember { mutableStateOf(prefs.getString("city", "WEATHER") ?: "WEATHER") }
    var temp by remember { mutableStateOf(if (prefs.contains("temp")) "${prefs.getInt("temp", 0)}°C" else "--°C") }
    var code by remember { mutableStateOf(prefs.getInt("code", -1)) }

    // The landing page owns the network refresh. Ultimate Center reads the same
    // cache immediately so opening this screen never adds another network delay.
    LaunchedEffect(Unit) {
        while (true) {
            city = prefs.getString("city", "WEATHER") ?: "WEATHER"
            temp = if (prefs.contains("temp")) "${prefs.getInt("temp", 0)}°C" else "--°C"
            code = prefs.getInt("code", -1)
            kotlinx.coroutines.delay(30_000)
        }
    }

    val symbol = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "≋"
        in 51..67, in 80..82 -> "☂"
        in 71..77, in 85..86 -> "❄"
        in 95..99 -> "ϟ"
        else -> "•"
    }
    Row(
        Modifier.background(Color(0xAA071A28), RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = if (code in 95..99) UORANGE else UCYAN, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(temp, color = UWHITE, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(city, color = UMUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun BrandHeader(snapshot: UltimateSnapshot?, modifier: Modifier, compact: Boolean, isPhone: Boolean) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.shadowfox_logo),
            contentDescription = "ShadowFox TV",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(if (isPhone) { if (compact) 120.dp else 100.dp } else 190.dp).height(if (isPhone) { if (compact) 44.dp else 46.dp } else 60.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("ULTIMATE CENTER", color = Color(0xFFEAFBFF), fontSize = if (isPhone) { if (compact) 16.sp else 18.sp } else 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
                Spacer(Modifier.width(7.dp))
                Text("v${BuildConfig.VERSION_NAME}", color = UCYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Text(if (isPhone) "ADVANCED CONTROL • MOBILE" else "SHADOWFOX PERFORMANCE COMMAND", color = Color(0xFFC6DCE8), fontSize = if (isPhone) 8.sp else 10.sp, fontWeight = FontWeight.Bold)
            Text(snapshot?.mode ?: "DETECTING DEVICE...", color = if (snapshot?.root == true) UCYAN else UMUTED, fontSize = if (isPhone) 9.sp else 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun OptimizeScreen(manager: UltimateManager, snapshot: UltimateSnapshot?, refresh: () -> Unit, landscape: Boolean) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("READY") }
    var busy by remember { mutableStateOf(false) }

    val optimizeScroll = remember(landscape) { ScrollState(0) }
    Column(Modifier.fillMaxSize().verticalScroll(optimizeScroll)) {
        MetricGrid(
            listOf(
                Triple("SYSTEM HEALTH", snapshot?.let { "${it.health}/100" } ?: "SCANNING", if ((snapshot?.health ?: 0) >= 75) UGREEN else UORANGE),
                Triple("RAM USED", snapshot?.let { "${it.ramUsedPercent}%" } ?: "SCANNING", UCYAN),
                Triple("ACTIVE APPS", snapshot?.let { "${it.runningApps}" } ?: "SCANNING", UCYAN),
                Triple("NETWORK", snapshot?.network ?: "SCANNING", UCYAN)
            ), landscape
        )
        Spacer(Modifier.height(10.dp))
        if (landscape) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(146.dp)) { SmartOptimizePanel(manager, snapshot?.root == true, status, busy, onBusy = { busy = it }, onStatus = { status = it }, refresh = refresh) }
                    Box(Modifier.fillMaxWidth().height(112.dp)) { ThermalPanel(snapshot) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(146.dp)) { StreamingPanel() }
                    Box(Modifier.fillMaxWidth().height(112.dp)) { SystemStatusPanel(manager, snapshot) }
                }
            }
        } else {
            SmartOptimizePanel(manager, snapshot?.root == true, status, busy, onBusy = { busy = it }, onStatus = { status = it }, refresh = refresh)
            Spacer(Modifier.height(10.dp))
            StreamingPanel()
            Spacer(Modifier.height(10.dp))
            ThermalPanel(snapshot)
        }
    }
}

@Composable
private fun SmartOptimizePanel(manager: UltimateManager, rootActive: Boolean, status: String, busy: Boolean, onBusy: (Boolean) -> Unit, onStatus: (String) -> Unit, refresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    UltimatePanel("SMART OPTIMIZE", "Root-aware cleanup with verified results — never simulated.", Modifier.height(146.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (rootActive) "ROOT ENGINE ACTIVE" else "STANDARD ENGINE", color = if (rootActive) UGREEN else UCYAN, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(status, color = if (status.contains("ROOT") || status.contains("STOPPED")) UGREEN else UMUTED, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            UltimateButton(if (busy) "OPTIMIZING..." else "OPTIMIZE NOW") {
                if (busy) return@UltimateButton
                scope.launch {
                    onBusy(true)
                    onStatus("SCANNING • VERIFYING APPS • CLEANING CACHE...")
                    val result = manager.smartOptimize()
                    onStatus("${result.verifiedStopped}/${result.attemptedApps} STOPPED • ${formatUiBytes(result.ramFreedBytes)} RAM • ${formatUiBytes(result.storageFreedBytes)} CACHE")
                    onBusy(false)
                    refresh()
                }
            }
        }
    }
}

@Composable
private fun StreamingPanel() {
    UltimatePanel("STREAMING MODE", "Prepare the device for IPTV, movies and high-bitrate playback.", Modifier.height(146.dp)) {
        Text("Open APPS and press STREAM beside your IPTV, VLC, Kodi or movie player.", color = UMUTED, fontSize = 10.sp)
    }
}

@Composable
private fun ThermalPanel(snapshot: UltimateSnapshot?) {
    UltimatePanel("DEVICE PERFORMANCE", "Live memory, storage and thermal condition.") {
        Text("Thermal: ${snapshot?.temperatureStatus ?: "..."}  •  Free RAM: ${formatUiBytes(snapshot?.freeRam ?: 0)}  •  Free Storage: ${formatUiBytes(snapshot?.freeStorage ?: 0)}", color = UWHITE, fontSize = 10.sp)
    }
}

@Composable
private fun SystemStatusPanel(manager: UltimateManager, snapshot: UltimateSnapshot?) {
    val device = remember { manager.deviceReport() }
    UltimatePanel("SYSTEM STATUS", "Live device and optimizer environment.") {
        Text("Android ${device.android} • SDK ${device.sdk}  •  ${if (snapshot?.root == true) "ROOT ACTIVE" else snapshot?.mode ?: "DETECTING MODE"}", color = if (snapshot?.root == true) UGREEN else UWHITE, fontSize = 10.sp)
        Spacer(Modifier.height(4.dp))
        Text("${device.manufacturer} ${device.model} • ${device.abi}", color = UMUTED, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AppsScreen(manager: UltimateManager, landscape: Boolean, isPhone: Boolean) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<ManagedApp>>(emptyList()) }
    var message by remember { mutableStateOf("Loading apps...") }
    var showSystem by remember { mutableStateOf(!isPhone) }
    fun load() { scope.launch {
        val all = manager.apps()
        apps = all.sortedWith(compareBy<ManagedApp> { it.system }.thenBy { it.label.lowercase(Locale.getDefault()) })
        val userCount = all.count { !it.system }
        message = if (isPhone) "$userCount user apps • ${all.size - userCount} system apps" else "${all.size} launchable apps"
    } }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(message, color = UMUTED, fontSize = 10.sp, modifier = Modifier.weight(1f))
                if (isPhone && apps.any { it.system }) CompactAction(if (showSystem) "HIDE SYSTEM" else "SHOW SYSTEM", Modifier.width(112.dp)) { showSystem = !showSystem }
            }
            Spacer(Modifier.height(6.dp))
        }
        val appsScroll = remember(landscape) { ScrollState(0) }
        Column(Modifier.fillMaxSize().verticalScroll(appsScroll), horizontalAlignment = Alignment.CenterHorizontally) {
            apps.filter { showSystem || !it.system }.forEach { item ->
                Column(Modifier.fillMaxWidth()) {
                UltimatePanel(item.label, "${if (item.system) "SYSTEM • " else ""}${if (item.running) "RUNNING" else "IDLE"} • ${item.packageName}") {
                    if (landscape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppActions(manager, item, { load() }, { message = it }, scope)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactAction("LAUNCH", Modifier.weight(1f)) { manager.launch(item.packageName) }
                            CompactAction(if (item.protected) "UNPROTECT" else "PROTECT", Modifier.weight(1f)) { manager.setProtected(item.packageName, !item.protected); load() }
                            CompactAction("STREAM", Modifier.weight(1f)) { scope.launch { message = manager.streamingOptimize(item.packageName).summary; load() } }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactAction("SETTINGS", Modifier.weight(1f)) { manager.openSettings(item.packageName) }
                            if (!item.system) CompactAction("UNINSTALL", Modifier.weight(1f)) { manager.uninstall(item.packageName) }
                        }
                    }
                }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun AppActions(manager: UltimateManager, item: ManagedApp, load: () -> Unit, setMessage: (String) -> Unit, scope: kotlinx.coroutines.CoroutineScope) {
    UltimateButton("LAUNCH") { manager.launch(item.packageName) }
    UltimateButton(if (item.protected) "UNPROTECT" else "PROTECT") { manager.setProtected(item.packageName, !item.protected); load() }
    UltimateButton("STREAM") { scope.launch { setMessage(manager.streamingOptimize(item.packageName).summary); load() } }
    UltimateButton("SETTINGS") { manager.openSettings(item.packageName) }
    if (!item.system) UltimateButton("UNINSTALL") { manager.uninstall(item.packageName) }
}

@Composable
private fun NetworkScreen(manager: UltimateManager, landscape: Boolean) {
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<NetworkReport?>(null) }
    var testing by remember { mutableStateOf(false) }
    fun test() { scope.launch { testing = true; report = manager.networkReport(); testing = false } }
    LaunchedEffect(Unit) { test() }

    val networkScroll = remember(landscape) { ScrollState(0) }
    Column(Modifier.fillMaxSize().verticalScroll(networkScroll)) {
        MetricGrid(
            listOf(
                Triple("STATUS", report?.verdict ?: "TESTING", if (report?.connected == true) UGREEN else UORANGE),
                Triple("TYPE", report?.transport ?: "...", UCYAN),
                Triple("PING", if ((report?.pingMs ?: -1) >= 0) "${report?.pingMs} ms" else "--", UCYAN),
                Triple("DNS", if ((report?.dnsMs ?: -1) >= 0) "${report?.dnsMs} ms" else "--", UCYAN)
            ), landscape
        )
        Spacer(Modifier.height(10.dp))
        UltimatePanel("CONNECTION DIAGNOSTICS", "Live connectivity, DNS and latency checks using ShadowFox streaming thresholds.") {
            Text(when (report?.verdict) {
                "EXCELLENT" -> "Connection looks excellent for streaming."
                "GOOD" -> "Connection looks healthy."
                "FAIR" -> "Streaming should work, but latency is elevated."
                "HIGH LATENCY" -> "High latency detected. Check Wi-Fi signal, router load or ISP."
                "NO INTERNET" -> "No usable internet connection detected."
                else -> "Run the test to diagnose the connection."
            }, color = UWHITE, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            UltimateButton(if (testing) "TESTING..." else "RUN NETWORK TEST") { if (!testing) test() }
        }
    }
}

@Composable
private fun SystemScreen(manager: UltimateManager, snapshot: UltimateSnapshot?, refresh: () -> Unit, landscape: Boolean) {
    val device = remember { manager.deviceReport() }
    var storage by remember { mutableStateOf(manager.storageReport()) }
    var maintenance by remember { mutableStateOf(manager.maintenanceEnabled()) }
    var message by remember { mutableStateOf("SYSTEM READY") }
    val diagnostics = remember(message) { manager.optimizerDiagnostics() }

    Column(Modifier.fillMaxSize()) {
        // Keep the System summary fixed. Only the lower detail cards scroll.
        MetricGrid(
            listOf(
                Triple("MODE", device.rootMode, if (snapshot?.root == true) UGREEN else UCYAN),
                Triple("ANDROID", "${device.android} / SDK ${device.sdk}", UCYAN),
                Triple("WIDEVINE", device.widevine, UCYAN),
                Triple("THERMAL", device.thermal, if (device.thermal == "NORMAL") UGREEN else UORANGE)
            ), landscape
        )
        Spacer(Modifier.height(10.dp))
        UltimatePanel("DEVICE CENTER", "${device.manufacturer} ${device.model} • ${device.abi}") {
            Text("Storage: ${formatUiBytes(storage.usedBytes)} used / ${formatUiBytes(storage.totalBytes)} total • ${formatUiBytes(storage.freeBytes)} free", color = UWHITE, fontSize = 10.sp)
            Text("ShadowFox cache: ${formatUiBytes(storage.appCacheBytes)}", color = UMUTED, fontSize = 9.sp)
            Text("Android ${device.android} • SDK ${device.sdk} • Thermal ${device.thermal}", color = UMUTED, fontSize = 9.sp)
            Spacer(Modifier.height(8.dp))
            if (landscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UltimateButton("CLEAR SHADOWFOX CACHE") { val freed = manager.clearOwnCache(); storage = manager.storageReport(); message = "${formatUiBytes(freed)} cleared" }
                    UltimateButton(if (maintenance) "AUTO MAINTENANCE: ON" else "AUTO MAINTENANCE: OFF") { maintenance = !maintenance; manager.scheduleMaintenance(maintenance) }
                    Column(Modifier.width(145.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    UltimateButton("CHECK FOR UPDATES") { message = "CHECKING FOR UPDATE…"; GitHubReleaseUpdater.start(manager.appContext()) { status -> message = status.uppercase(Locale.getDefault()) } }
                    Spacer(Modifier.height(5.dp))
                    Text(message, color = if (message.contains("UP TO DATE") || message.contains("INSTALLED")) UGREEN else UCYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                }
            } else {
                CompactAction("CLEAR SHADOWFOX CACHE", Modifier.fillMaxWidth()) { val freed = manager.clearOwnCache(); storage = manager.storageReport(); message = "${formatUiBytes(freed)} cleared" }
                Spacer(Modifier.height(6.dp))
                CompactAction(if (maintenance) "AUTO MAINTENANCE: ON" else "AUTO MAINTENANCE: OFF", Modifier.fillMaxWidth()) { maintenance = !maintenance; manager.scheduleMaintenance(maintenance) }
                Spacer(Modifier.height(6.dp))
                CompactAction("CHECK FOR UPDATES", Modifier.fillMaxWidth()) { message = "CHECKING FOR UPDATE…"; GitHubReleaseUpdater.start(manager.appContext()) { status -> message = status.uppercase(Locale.getDefault()) } }
                Spacer(Modifier.height(5.dp))
                Text(message, color = if (message.contains("UP TO DATE") || message.contains("INSTALLED")) UGREEN else UCYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            }
        }
        Spacer(Modifier.height(10.dp))
        val detailScroll = remember(landscape) { ScrollState(0) }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(detailScroll)) {
        UltimatePanel("OPTIMIZER DIAGNOSTICS", "Latest verified Smart Optimize results.") {
            if (diagnostics.isEmpty()) Text("Run Smart Optimize once to generate diagnostics.", color = UMUTED, fontSize = 9.sp)
            diagnostics.takeLast(7).forEach { line ->
                val raw = line.substringAfter(" | ")
                val display = when {
                    raw.startsWith("CANDIDATES ") -> "CANDIDATES • " + raw.removePrefix("CANDIDATES ").split(",").filter { it.isNotBlank() }.size + " apps"
                    raw.startsWith("PROTECTED ") -> "PROTECTED • " + raw.removePrefix("PROTECTED ").split(",").filter { it.isNotBlank() }.size + " critical apps"
                    raw.startsWith("SOURCES ") -> "PROCESS SOURCES • active"
                    else -> raw
                }
                Text(display, color = UWHITE, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(10.dp))
        UltimatePanel("MAINTENANCE HISTORY", message) {
            val history = manager.history()
            if (history.isEmpty()) Text("No maintenance history yet.", color = UMUTED, fontSize = 10.sp)
            history.take(10).forEach { Text(it, color = UWHITE, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(10.dp))
        UltimatePanel("ADVANCED APP CONTROL", "Rooted devices unlock deeper controls. Standard devices keep Android-safe actions.") {
            Text("Unsupported actions are never reported as completed.", color = UMUTED, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MetricGrid(items: List<Triple<String, String, Color>>, landscape: Boolean) {
    if (landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (a,b,c) -> MetricCard(a,b,c,Modifier.weight(1f)) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.take(2).forEach { (a,b,c) -> MetricCard(a,b,c,Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.drop(2).forEach { (a,b,c) -> MetricCard(a,b,c,Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.shadow(3.dp, RoundedCornerShape(10.dp), ambientColor = UCYAN.copy(alpha = .14f), spotColor = UCYAN.copy(alpha = .14f))
            .background(Brush.verticalGradient(listOf(Color(0xFF17435D), UMETAL_MID, Color(0xFF051723))), RoundedCornerShape(10.dp)).padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = UMUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UltimatePanel(title: String, subtitle: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = UCYAN.copy(alpha = .28f), spotColor = UCYAN.copy(alpha = .28f))
            .background(Brush.verticalGradient(listOf(UMETAL_TOP, UMETAL_MID, UMETAL_BOTTOM)), RoundedCornerShape(14.dp)).padding(15.dp)
    ) {
        Text(title, color = UWHITE, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = UMUTED, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        content()
    }
}

@Composable
private fun UltimateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focused) 1.055f else 1f, label = "ultimateButtonFocus")
    Box(
        Modifier.height(38.dp).scale(zoom)
            .shadow(if (focused) 16.dp else 3.dp, RoundedCornerShape(8.dp), ambientColor = if (focused) UWHITE.copy(alpha = .62f) else UCYAN.copy(alpha = .16f), spotColor = if (focused) UWHITE.copy(alpha = .62f) else UCYAN.copy(alpha = .16f))
            .background(Brush.horizontalGradient(listOf(UMETAL_TOP, UMETAL_MID, UMETAL_BOTTOM)), RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }.focusable(enabled).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (!enabled) UMUTED else UWHITE, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}

@Composable
private fun CompactAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focused) 1.012f else 1f, label = "compactFocus")
    Box(
        modifier.height(38.dp).scale(zoom)
            .shadow(if (focused) 12.dp else 2.dp, RoundedCornerShape(6.dp), ambientColor = if (focused) UWHITE.copy(alpha = .62f) else Color.Black, spotColor = if (focused) UWHITE.copy(alpha = .62f) else Color.Black)
            .background(if (focused) Color(0xFF183548) else Color(0xFF071722), RoundedCornerShape(7.dp))
            .onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = UWHITE, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val zoom by animateFloatAsState(if (focused) 1.045f else 1f, label = "tabFocus")
    Box(
        modifier.height(42.dp).scale(zoom)
            .shadow(if (focused) 14.dp else 2.dp, RoundedCornerShape(8.dp), ambientColor = if (focused) UWHITE.copy(alpha = .58f) else UCYAN.copy(alpha = .14f), spotColor = if (focused) UWHITE.copy(alpha = .58f) else UCYAN.copy(alpha = .14f))
            .background(if (focused) Color(0xFF1B4055) else if (selected) Color(0xFF0B2B3E) else Color(0xFF06141E), RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (focused) UWHITE else if (selected) UCYAN else UMUTED, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}

private fun formatUiBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
}
