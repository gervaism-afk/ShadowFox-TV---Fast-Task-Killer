package ca.shadowfoxtv.taskkiller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = UBG, surface = UPANEL)) {
                UltimateCenter(UltimateManager(applicationContext), onClose = { finish() })
            }
        }
    }
}

private enum class UltimateTab { OPTIMIZE, APPS, NETWORK, SYSTEM }

@Composable
private fun UltimateCenter(manager: UltimateManager, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(UltimateTab.OPTIMIZE) }
    var snapshot by remember { mutableStateOf<UltimateSnapshot?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() { scope.launch { snapshot = manager.snapshot() } }
    LaunchedEffect(Unit) { snapshot = manager.snapshot() }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF03111D), Color(0xFF061B29))))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("ShadowFox", color = UWHITE, fontSize = 27.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("ULTIMATE 6.0", color = UORANGE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                    Text(snapshot?.mode ?: "DETECTING DEVICE...", color = if (snapshot?.root == true) UGREEN else UCYAN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                UltimateButton("REFRESH") { refresh() }
                Spacer(Modifier.width(8.dp))
                UltimateButton("BACK") { onClose() }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UltimateTab.entries.forEach { item ->
                    UltimateTabButton(item.name, selected = item == tab, Modifier.weight(1f)) { tab = item }
                }
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                UltimateTab.OPTIMIZE -> OptimizeScreen(manager, snapshot, ::refresh)
                UltimateTab.APPS -> AppsScreen(manager)
                UltimateTab.NETWORK -> NetworkScreen(manager)
                UltimateTab.SYSTEM -> SystemScreen(manager, snapshot, ::refresh)
            }
        }
    }
}

@Composable
private fun OptimizeScreen(manager: UltimateManager, snapshot: UltimateSnapshot?, refresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("READY") }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("HEALTH", "${snapshot?.health ?: 0}/100", if ((snapshot?.health ?: 0) >= 75) UGREEN else UORANGE, Modifier.weight(1f))
            MetricCard("RAM USED", "${snapshot?.ramUsedPercent ?: 0}%", UCYAN, Modifier.weight(1f))
            MetricCard("RUNNING", "${snapshot?.runningApps ?: 0} apps", UCYAN, Modifier.weight(1f))
            MetricCard("NETWORK", snapshot?.network ?: "...", UCYAN, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        UltimatePanel("SMART OPTIMIZE", "Automatically chooses the safest cleanup supported by this device.") {
            Text(status, color = if (status.contains("ROOT") || status.contains("stopped")) UGREEN else UMUTED, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            UltimateButton(if (busy) "OPTIMIZING..." else "ONE-TAP SMART OPTIMIZE", enabled = !busy) {
                scope.launch {
                    busy = true
                    status = "SCANNING RAM • APPS • CACHE..."
                    val r = manager.smartOptimize()
                    status = r.summary
                    busy = false
                    refresh()
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        UltimatePanel("STREAMING MODE", "Stops safe background apps, protects the player you choose, checks memory, then launches it.") {
            Text("Open APPS and press STREAM beside your IPTV, VLC, Kodi or movie player.", color = UMUTED, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        UltimatePanel("THERMAL + PERFORMANCE", "Live device condition based on actual Android telemetry.") {
            Text("Thermal: ${snapshot?.temperatureStatus ?: "..."}   •   Free RAM: ${formatUiBytes(snapshot?.freeRam ?: 0)}   •   Free Storage: ${formatUiBytes(snapshot?.freeStorage ?: 0)}", color = UWHITE, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AppsScreen(manager: UltimateManager) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UltimateButton("LAUNCH") { manager.launch(item.packageName) }
                        UltimateButton(if (item.protected) "UNPROTECT" else "PROTECT") {
                            manager.setProtected(item.packageName, !item.protected); load()
                        }
                        UltimateButton("STREAM") {
                            scope.launch { message = manager.streamingOptimize(item.packageName).summary; load() }
                        }
                        UltimateButton("SETTINGS") { manager.openSettings(item.packageName) }
                        if (!item.system) UltimateButton("UNINSTALL") { manager.uninstall(item.packageName) }
                    }
                }
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

@Composable
private fun NetworkScreen(manager: UltimateManager) {
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<NetworkReport?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun test() { scope.launch { testing = true; report = manager.networkReport(); testing = false } }
    LaunchedEffect(Unit) { test() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("STATUS", report?.verdict ?: "TESTING", if (report?.connected == true) UGREEN else UORANGE, Modifier.weight(1f))
            MetricCard("TYPE", report?.transport ?: "...", UCYAN, Modifier.weight(1f))
            MetricCard("PING", if ((report?.pingMs ?: -1) >= 0) "${report?.pingMs} ms" else "--", UCYAN, Modifier.weight(1f))
            MetricCard("DNS", if ((report?.dnsMs ?: -1) >= 0) "${report?.dnsMs} ms" else "--", UCYAN, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        UltimatePanel("CONNECTION DIAGNOSTICS", "Direct socket checks for streaming reliability.") {
            Text(when (report?.verdict) {
                "EXCELLENT" -> "Connection looks excellent for streaming."
                "GOOD" -> "Connection looks healthy."
                "FAIR" -> "Streaming should work, but latency is elevated."
                "HIGH LATENCY" -> "High latency detected. Check Wi-Fi signal, router load or ISP."
                "NO INTERNET" -> "No usable internet connection detected."
                else -> "Run the test to diagnose the connection."
            }, color = UWHITE, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            UltimateButton(if (testing) "TESTING..." else "RUN NETWORK TEST", enabled = !testing) { test() }
        }
    }
}

@Composable
private fun SystemScreen(manager: UltimateManager, snapshot: UltimateSnapshot?, refresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val device = remember { manager.deviceReport() }
    var storage by remember { mutableStateOf(manager.storageReport()) }
    var maintenance by remember { mutableStateOf(manager.maintenanceEnabled()) }
    var message by remember { mutableStateOf("SYSTEM READY") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("MODE", device.rootMode, if (snapshot?.root == true) UGREEN else UCYAN, Modifier.weight(1f))
            MetricCard("ANDROID", "${device.android} / SDK ${device.sdk}", UCYAN, Modifier.weight(1f))
            MetricCard("WIDEVINE", device.widevine, UCYAN, Modifier.weight(1f))
            MetricCard("THERMAL", device.thermal, if (device.thermal == "NORMAL") UGREEN else UORANGE, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        UltimatePanel("DEVICE CENTER", "${device.manufacturer} ${device.model} • ${device.abi}") {
            Text("Storage: ${formatUiBytes(storage.usedBytes)} used / ${formatUiBytes(storage.totalBytes)} total • ${formatUiBytes(storage.freeBytes)} free", color = UWHITE, fontSize = 12.sp)
            Text("ShadowFox cache: ${formatUiBytes(storage.appCacheBytes)}", color = UMUTED, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UltimateButton("CLEAR SHADOWFOX CACHE") {
                    val freed = manager.clearOwnCache(); storage = manager.storageReport(); message = "${formatUiBytes(freed)} cleared"
                }
                UltimateButton(if (maintenance) "AUTO MAINTENANCE: ON" else "AUTO MAINTENANCE: OFF") {
                    maintenance = !maintenance; manager.scheduleMaintenance(maintenance)
                }
                UltimateButton("CHECK UPDATE") { GitHubReleaseUpdater.start((manager.javaClass.getDeclaredField("app").apply { isAccessible = true }.get(manager) as android.content.Context)); message = "Update check started" }
            }
        }
        Spacer(Modifier.height(10.dp))
        UltimatePanel("MAINTENANCE HISTORY", message) {
            val history = manager.history()
            if (history.isEmpty()) Text("No maintenance history yet.", color = UMUTED, fontSize = 10.sp)
            history.take(10).forEach { Text(it, color = UWHITE, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(10.dp))
        UltimatePanel("ADVANCED APP CONTROL", "Rooted devices unlock package freeze/disable and deeper cleanup. Standard devices keep the same UI but only expose Android-safe actions.") {
            Text("No feature pretends to have root when it does not. Unsupported controls remain safe and limited by Android.", color = UMUTED, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = UCYAN.copy(.3f), spotColor = UCYAN.copy(.3f))
            .background(UPANEL, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = UMUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UltimatePanel(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp), ambientColor = UCYAN.copy(.2f), spotColor = UCYAN.copy(.2f))
            .background(UPANEL, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(title, color = UWHITE, fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = UMUTED, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun UltimateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = UCYAN, contentColor = Color(0xFF05202A), disabledContainerColor = Color(0xFF31505A)),
        modifier = Modifier.height(36.dp).focusable()
    ) { Text(text, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}

@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(42.dp)
            .background(if (selected) UCYAN else Color(0xFF0A2637), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color(0xFF05202A) else UWHITE, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

private fun formatUiBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(Locale.US, "%.2f GB", mb / 1024.0) else String.format(Locale.US, "%.0f MB", mb)
}
