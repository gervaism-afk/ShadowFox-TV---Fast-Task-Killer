package ca.shadowfoxtv.taskkiller

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import java.util.Locale

private val UBG = Color(0xFF03111D)
private val UPANEL = Color(0xEA092337)
private val UCYAN = Color(0xFF00E5FF)
private val UORANGE = Color(0xFFFF7A00)
private val UWHITE = Color(0xFFF7FBFF)
private val UMUTED = Color(0xFF9AABB8)
private val UGREEN = Color(0xFF77C943)

class UltimateCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideBars()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = UBG, surface = UPANEL)) {
                UltimateCenter(UltimateManager(applicationContext), onClose = { finish() })
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

private enum class UltimateTab { OPTIMIZE, APPS, NETWORK, SYSTEM }

@Composable
private fun UltimateCenter(manager: UltimateManager, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(UltimateTab.OPTIMIZE) }
    var snapshot by remember { mutableStateOf<UltimateSnapshot?>(null) }
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    fun refresh() { scope.launch { snapshot = manager.snapshot() } }
    LaunchedEffect(Unit) { snapshot = manager.snapshot() }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF03111D), Color(0xFF061B29))))
    ) {
        val side = if (landscape) 18.dp else 20.dp
        val top = if (landscape) 8.dp else 12.dp
        val contentMax = if (landscape) 1180.dp else 620.dp
        val contentWidth = minOf(maxWidth - (side * 2), contentMax)

        Column(
            Modifier.width(contentWidth).fillMaxSize().align(Alignment.TopCenter).padding(top = top, bottom = 10.dp)
        ) {
            if (landscape) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BrandHeader(snapshot, Modifier.weight(1f), compact = true)
                    UltimateButton("REFRESH") { refresh() }
                    Spacer(Modifier.width(8.dp))
                    UltimateButton("BACK") { onClose() }
                }
            } else {
                BrandHeader(snapshot, Modifier.fillMaxWidth(), compact = false)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    UltimateButton("REFRESH") { refresh() }
                    Spacer(Modifier.width(8.dp))
                    UltimateButton("BACK") { onClose() }
                }
            }

            Spacer(Modifier.height(if (landscape) 8.dp else 10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UltimateTab.entries.forEach { item ->
                    UltimateTabButton(item.name, item == tab, Modifier.weight(1f)) { tab = item }
                }
            }
            Spacer(Modifier.height(if (landscape) 8.dp else 10.dp))

            when (tab) {
                UltimateTab.OPTIMIZE -> OptimizeScreen(manager, snapshot, ::refresh, landscape)
                UltimateTab.APPS -> AppsScreen(manager, landscape)
                UltimateTab.NETWORK -> NetworkScreen(manager, landscape)
                UltimateTab.SYSTEM -> SystemScreen(manager, snapshot, ::refresh, landscape)
            }
        }
    }
}

@Composable
private fun BrandHeader(snapshot: UltimateSnapshot?, modifier: Modifier, compact: Boolean) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("ShadowFox", color = UWHITE, fontSize = if (compact) 22.sp else 26.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Spacer(Modifier.width(5.dp))
            Text("ULTIMATE", color = UORANGE, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text("v${BuildConfig.VERSION_NAME}", color = UCYAN, fontSize = if (compact) 9.sp else 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(snapshot?.mode ?: "DETECTING DEVICE...", color = if (snapshot?.root == true) UGREEN else UCYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun OptimizeScreen(manager: UltimateManager, snapshot: UltimateSnapshot?, refresh: () -> Unit, landscape: Boolean) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("READY") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MetricGrid(
            listOf(
                Triple("HEALTH", "${snapshot?.health ?: 0}/100", if ((snapshot?.health ?: 0) >= 75) UGREEN else UORANGE),
                Triple("RAM USED", "${snapshot?.ramUsedPercent ?: 0}%", UCYAN),
                Triple("RUNNING", "${snapshot?.runningApps ?: 0} apps", UCYAN),
                Triple("NETWORK", snapshot?.network ?: "...", UCYAN)
            ), landscape
        )
        Spacer(Modifier.height(10.dp))
        if (landscape) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    SmartOptimizePanel(manager, status, busy, onBusy = { busy = it }, onStatus = { status = it }, refresh = refresh)
                    Spacer(Modifier.height(10.dp))
                    ThermalPanel(snapshot)
                }
                Column(Modifier.weight(1f)) {
                    StreamingPanel()
                }
            }
        } else {
            SmartOptimizePanel(manager, status, busy, onBusy = { busy = it }, onStatus = { status = it }, refresh = refresh)
            Spacer(Modifier.height(10.dp))
            StreamingPanel()
            Spacer(Modifier.height(10.dp))
            ThermalPanel(snapshot)
        }
    }
}

@Composable
private fun SmartOptimizePanel(manager: UltimateManager, status: String, busy: Boolean, onBusy: (Boolean) -> Unit, onStatus: (String) -> Unit, refresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    UltimatePanel("SMART OPTIMIZE", "Automatically chooses the safest cleanup supported by this device.") {
        Text(status, color = if (status.contains("ROOT") || status.contains("stopped")) UGREEN else UMUTED, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        UltimateButton(if (busy) "OPTIMIZING..." else "ONE-TAP SMART OPTIMIZE", enabled = !busy) {
            scope.launch {
                onBusy(true)
                onStatus("SCANNING RAM • APPS • CACHE...")
                val r = manager.smartOptimize()
                onStatus(r.summary)
                onBusy(false)
                refresh()
            }
        }
    }
}

@Composable
private fun StreamingPanel() {
    UltimatePanel("STREAMING MODE", "Stops safe background apps, protects the player you choose, checks memory, then launches it.") {
        Text("Open APPS and press STREAM beside your IPTV, VLC, Kodi or movie player.", color = UMUTED, fontSize = 10.sp)
    }
}

@Composable
private fun ThermalPanel(snapshot: UltimateSnapshot?) {
    UltimatePanel("THERMAL + PERFORMANCE", "Live device condition based on actual Android telemetry.") {
        Text("Thermal: ${snapshot?.temperatureStatus ?: "..."}  •  Free RAM: ${formatUiBytes(snapshot?.freeRam ?: 0)}  •  Free Storage: ${formatUiBytes(snapshot?.freeStorage ?: 0)}", color = UWHITE, fontSize = 10.sp)
    }
}

@Composable
private fun AppsScreen(manager: UltimateManager, landscape: Boolean) {
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<ManagedApp>>(emptyList()) }
    var message by remember { mutableStateOf("Loading apps...") }
    fun load() { scope.launch { apps = manager.apps(); message = "${apps.size} launchable apps" } }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        Text(message, color = UMUTED, fontSize = 10.sp)
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            apps.forEach { item ->
                UltimatePanel(item.label, "${item.packageName} • ${if (item.running) "RUNNING" else "IDLE"}${if (item.system) " • SYSTEM" else ""}") {
                    if (landscape) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppActions(manager, item, load, { message = it }, scope)
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MetricGrid(
            listOf(
                Triple("STATUS", report?.verdict ?: "TESTING", if (report?.connected == true) UGREEN else UORANGE),
                Triple("TYPE", report?.transport ?: "...", UCYAN),
                Triple("PING", if ((report?.pingMs ?: -1) >= 0) "${report?.pingMs} ms" else "--", UCYAN),
                Triple("DNS", if ((report?.dnsMs ?: -1) >= 0) "${report?.dnsMs} ms" else "--", UCYAN)
            ), landscape
        )
        Spacer(Modifier.height(10.dp))
        UltimatePanel("CONNECTION DIAGNOSTICS", "Direct socket checks for streaming reliability.") {
            Text(when (report?.verdict) {
                "EXCELLENT" -> "Connection looks excellent for streaming."
                "GOOD" -> "Connection looks healthy."
                "FAIR" -> "Streaming should work, but latency is elevated."
                "HIGH LATENCY" -> "High latency detected. Check Wi-Fi signal, router load or ISP."
                "NO INTERNET" -> "No usable internet connection detected."
                else -> "Run the test to diagnose the connection."
            }, color = UWHITE, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            UltimateButton(if (testing) "TESTING..." else "RUN NETWORK TEST", enabled = !testing) { test() }
        }
    }
}

@Composable
private fun SystemScreen(manager: UltimateManager, snapshot: UltimateSnapshot?, refresh: () -> Unit, landscape: Boolean) {
    val device = remember { manager.deviceReport() }
    var storage by remember { mutableStateOf(manager.storageReport()) }
    var maintenance by remember { mutableStateOf(manager.maintenanceEnabled()) }
    var message by remember { mutableStateOf("SYSTEM READY") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
            Spacer(Modifier.height(8.dp))
            if (landscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UltimateButton("CLEAR SHADOWFOX CACHE") { val freed = manager.clearOwnCache(); storage = manager.storageReport(); message = "${formatUiBytes(freed)} cleared" }
                    UltimateButton(if (maintenance) "AUTO MAINTENANCE: ON" else "AUTO MAINTENANCE: OFF") { maintenance = !maintenance; manager.scheduleMaintenance(maintenance) }
                    UltimateButton("CHECK UPDATE") { message = "Update check started"; refresh() }
                }
            } else {
                CompactAction("CLEAR SHADOWFOX CACHE", Modifier.fillMaxWidth()) { val freed = manager.clearOwnCache(); storage = manager.storageReport(); message = "${formatUiBytes(freed)} cleared" }
                Spacer(Modifier.height(6.dp))
                CompactAction(if (maintenance) "AUTO MAINTENANCE: ON" else "AUTO MAINTENANCE: OFF", Modifier.fillMaxWidth()) { maintenance = !maintenance; manager.scheduleMaintenance(maintenance) }
                Spacer(Modifier.height(6.dp))
                CompactAction("REFRESH SYSTEM", Modifier.fillMaxWidth()) { refresh(); message = "System refreshed" }
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
    }
}

@Composable
private fun MetricGrid(items: List<Triple<String, String, Color>>, landscape: Boolean) {
    if (landscape) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        modifier.shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = UCYAN.copy(.3f), spotColor = UCYAN.copy(.3f))
            .background(UPANEL, RoundedCornerShape(12.dp)).padding(vertical = 11.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = UMUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UltimatePanel(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = UCYAN.copy(.2f), spotColor = UCYAN.copy(.2f))
            .background(UPANEL, RoundedCornerShape(12.dp)).padding(13.dp)
    ) {
        Text(title, color = UWHITE, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = UMUTED, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(7.dp))
        content()
    }
}

@Composable
private fun UltimateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = UCYAN, contentColor = Color(0xFF05202A), disabledContainerColor = Color(0xFF31505A)),
        modifier = Modifier.height(36.dp).focusable()
    ) { Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}

@Composable
private fun CompactAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(38.dp).background(UCYAN, RoundedCornerShape(50)).clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color(0xFF05202A), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).background(if (selected) UCYAN else Color(0xFF0A2637), RoundedCornerShape(12.dp)).clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (selected) Color(0xFF05202A) else UWHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}

private fun formatUiBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
}
