package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = PANEL)) {
                ShadowFoxUpdateGate(applicationContext) {
                    MasterDashboard(applicationContext)
                }
            }
        }
    }
}

private val BG = Color(0xFF010305)
private val PANEL = Color(0xF20A0E12)
private val CYAN = Color(0xFF00E5FF)
private val BLUE = Color(0xFF08AEEA)
private val ORANGE = Color(0xFFFF7A00)
private val WHITE = Color(0xFFF7FBFF)
private val MUTED = Color(0xFFB7C2CA)
private val GREEN = Color(0xFF77C943)

@Composable
private fun MasterDashboard(context: Context) {
    var ram by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var apps by remember { mutableIntStateOf(runningProcessCount(context)) }
    var mbps by remember { mutableFloatStateOf(0f) }
    var ping by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var ramFreed by remember { mutableStateOf(0L) }
    var storageFreed by remember { mutableStateOf(0L) }
    var closedApps by remember { mutableIntStateOf(0) }
    var clock by remember { mutableStateOf(Date()) }
    val optimizer = remember { AppOptimizer(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rootAvailable = withContext(Dispatchers.IO) { rootShellAvailable() }
        while (true) {
            val before = totalTrafficBytes()
            delay(1000)
            val after = totalTrafficBytes()
            if (before >= 0 && after >= before) mbps = (after - before) * 8f / 1_000_000f
            ping = measureLatencyMs()
            ram = memoryUsedPercent(context)
            apps = runningProcessCount(context)
            clock = Date()
        }
    }

    fun optimize() {
        if (busy) return
        scope.launch {
            busy = true
            val result = optimizer.optimize()
            ram = memoryUsedPercent(context)
            apps = runningProcessCount(context)
            rootAvailable = result.rootUsed
            ramFreed = result.ramFreedBytes
            storageFreed = result.storageFreedBytes
            closedApps = result.closedApps
            busy = false
        }
    }

    val configuration = LocalConfiguration.current
    val isPhone = configuration.smallestScreenWidthDp < 600
    val mobilePortrait = isPhone && configuration.screenHeightDp > configuration.screenWidthDp
    if (isPhone) {
        if (mobilePortrait) {
            MobileDashboard(
                context = context, ram = ram, apps = apps, mbps = mbps, ping = ping, busy = busy,
                rootAvailable = rootAvailable, ramFreed = ramFreed, storageFreed = storageFreed,
                closedApps = closedApps, clock = clock, onOptimize = { optimize() }
            )
        } else {
            MobileLandscapeDashboard(
                context = context, ram = ram, apps = apps, mbps = mbps, ping = ping, busy = busy,
                rootAvailable = rootAvailable, ramFreed = ramFreed, storageFreed = storageFreed,
                closedApps = closedApps, clock = clock, onOptimize = { optimize() }
            )
        }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val scale = minOf(maxWidth / 960.dp, maxHeight / 540.dp)
        Box(Modifier.size(960.dp * scale, 540.dp * scale).align(Alignment.Center)) {
            Box(Modifier.size(960.dp, 540.dp).scale(scale).align(Alignment.Center).background(BG)) {
                MasterBackdrop()

                // Header
                Row(
                    Modifier.offset(18.dp, 12.dp).size(924.dp, 64.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.shadowfox_logo),
                        contentDescription = "ShadowFox TV",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(154.dp, 56.dp)
                    )
                    Column(Modifier.width(190.dp)) {
                        Text("OPTIMIZE • CLEAN • PERFORM", color = MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text((if (rootAvailable) "ROOTED PRO MODE" else "STANDARD MODE") + "  •  v" + BuildConfig.VERSION_NAME, color = if (rootAvailable) CYAN else MUTED, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("BUILT FOR ANDROID TV", color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("FASTER • SMOOTHER • BETTER", color = CYAN, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.width(150.dp), horizontalAlignment = Alignment.End) {
                        Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(clock).uppercase(Locale.getDefault()), color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(clock).uppercase(Locale.getDefault()), color = CYAN, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        Text(deviceLabel() + "  •  ANDROID " + Build.VERSION.RELEASE.orEmpty(), color = MUTED, fontSize = 6.sp, maxLines = 1)
                    }
                }

                // Left navigation rail
                Box(Modifier.offset(18.dp, 88.dp).size(142.dp, 406.dp).background(Brush.verticalGradient(listOf(Color(0xFF080D12), Color(0xFF020406))), RoundedCornerShape(6.dp))) {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        NavEntry("⌂", "OPTIMIZE", false, Modifier.fillMaxSize().weight(1f)) { optimize() }
                        NavEntry("▦", "APPS", false, Modifier.fillMaxSize().weight(1f)) { openUltimate(context, "APPS") }
                        NavEntry("⌁", "NETWORK", false, Modifier.fillMaxSize().weight(1f)) { openUltimate(context, "NETWORK") }
                        NavEntry("⚙", "SYSTEM", false, Modifier.fillMaxSize().weight(1f)) { openUltimate(context, "SYSTEM") }
                        Image(
                            painter = painterResource(R.drawable.shadowfox_logo),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(88.dp).width(118.dp)
                        )
                    }
                }

                // Four live metric cards
                MetricCard("SHADOWFOX SCORE", scoreFor(ram, ping).toString(), "SYSTEM HEALTH", Modifier.offset(174.dp, 88.dp).size(182.dp, 86.dp))
                MetricCard("RAM USED", ram.toInt().toString() + "%", formatBytes(availableMemoryBytes(context)) + " FREE", Modifier.offset(366.dp, 88.dp).size(182.dp, 86.dp))
                MetricCard("RUNNING APPS", apps.toString(), if (apps == 1) "1 APP" else "$apps APPS", Modifier.offset(558.dp, 88.dp).size(182.dp, 86.dp))
                MetricCard("NETWORK", String.format("%.1f Mbps", mbps), if (ping > 0) "$ping ms PING" else "CHECKING", Modifier.offset(750.dp, 88.dp).size(192.dp, 86.dp))

                // Smart Optimize hero
                DisplayCard(Modifier.offset(174.dp, 188.dp).size(480.dp, 220.dp), hero = true) {
                    Column(Modifier.fillMaxSize().padding(22.dp)) {
                        Text("SMART OPTIMIZE", color = WHITE, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("One-touch performance optimization", color = MUTED, fontSize = 9.sp)
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RamGauge(ram, Modifier.size(148.dp))
                            Spacer(Modifier.width(20.dp))
                            Column {
                                Text(if (busy) "OPTIMIZING…" else "READY TO OPTIMIZE", color = CYAN, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                Text("Closes background apps and trims cache", color = MUTED, fontSize = 8.sp)
                                Spacer(Modifier.height(13.dp))
                                MasterButton(if (busy) "WORKING…" else "OPTIMIZE NOW", 150.dp, !busy) { optimize() }
                            }
                        }
                    }
                }

                GlowCard(Modifier.offset(668.dp, 188.dp).size(274.dp, 103.dp), onClick = { openUltimate(context, "SYSTEM") }) {
                    Column(Modifier.fillMaxSize().padding(15.dp)) {
                        Text("CACHE CLEANER", color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("Manage ShadowFox cache safely", color = MUTED, fontSize = 8.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(if (storageFreed > 0) formatBytes(storageFreed) + " CLEARED" else "READY", color = CYAN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                GlowCard(Modifier.offset(668.dp, 305.dp).size(274.dp, 103.dp), onClick = {
                    openUltimate(context, "OPTIMIZE")
                }) {
                    Column(Modifier.fillMaxSize().padding(15.dp)) {
                        Text("ULTIMATE CENTER", color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text("Advanced ShadowFox controls", color = MUTED, fontSize = 8.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(if (rootAvailable) "ROOT ACCESS ACTIVE" else "SYSTEM TOOLS", color = if (rootAvailable) GREEN else CYAN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                DisplayCard(Modifier.offset(174.dp, 422.dp).size(768.dp, 72.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(235.dp)) {
                            Text("THERMAL + PERFORMANCE", color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            Text("Live system performance status", color = MUTED, fontSize = 8.sp)
                        }
                        StatTile("RAM FREED", formatBytes(ramFreed), Modifier.size(120.dp, 46.dp))
                        Spacer(Modifier.width(8.dp))
                        StatTile("APPS CLOSED", closedApps.toString(), Modifier.size(112.dp, 46.dp))
                        Spacer(Modifier.width(8.dp))
                        StatTile("ROOT", if (rootAvailable) "ACTIVE" else "READY", Modifier.size(105.dp, 46.dp), if (rootAvailable) GREEN else MUTED)
                    }
                }

                Text(
                    "SHADOWFOX TV  |  OPTIMIZED FOR PERFORMANCE",
                    color = MUTED,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun MobileDashboard(
    context: Context,
    ram: Float,
    apps: Int,
    mbps: Float,
    ping: Int,
    busy: Boolean,
    rootAvailable: Boolean,
    ramFreed: Long,
    storageFreed: Long,
    closedApps: Int,
    clock: Date,
    onOptimize: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF010305), Color(0xFF070B0F), Color(0xFF010305)))
        ).padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.shadowfox_logo), "ShadowFox TV", contentScale = ContentScale.Fit, modifier = Modifier.size(150.dp, 54.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(clock), color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(if (rootAvailable) "ROOTED PRO • v" + BuildConfig.VERSION_NAME else "MOBILE • v" + BuildConfig.VERSION_NAME, color = if (rootAvailable) CYAN else MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("OPTIMIZE • CLEAN • PERFORM", color = MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MobileMetric("SCORE", scoreFor(ram, ping).toString(), Modifier.weight(1f))
            MobileMetric("RAM", ram.toInt().toString() + "%", Modifier.weight(1f))
            MobileMetric("APPS", apps.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        DisplayCard(Modifier.fillMaxWidth().height(238.dp), hero = true) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Text("SMART OPTIMIZE", color = WHITE, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text("One-touch performance optimization", color = MUTED, fontSize = 10.sp)
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    RamGauge(ram, Modifier.size(142.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (busy) "OPTIMIZING…" else "READY", color = CYAN, fontSize = 15.sp, fontWeight = FontWeight.Black)
                        Text(if (rootAvailable) "Root access active" else "Android safe mode", color = MUTED, fontSize = 9.sp)
                        Spacer(Modifier.height(12.dp))
                        MasterButton(if (busy) "WORKING…" else "OPTIMIZE NOW", 150.dp, !busy, onOptimize)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowCard(Modifier.weight(1f).height(92.dp), onClick = { openUltimate(context, "SYSTEM") }) {
                Column(Modifier.padding(13.dp)) {
                    Text("CACHE CLEANER", color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text(if (storageFreed > 0) formatBytes(storageFreed) + " CLEARED" else "READY", color = CYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            GlowCard(Modifier.weight(1f).height(92.dp), onClick = { openUltimate(context, "OPTIMIZE") }) {
                Column(Modifier.padding(13.dp)) {
                    Text("ULTIMATE CENTER", color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Text("ADVANCED TOOLS", color = CYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        DisplayCard(Modifier.fillMaxWidth().height(86.dp)) {
            Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                MobileMetric("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f))
                MobileMetric("CLOSED", closedApps.toString(), Modifier.weight(1f))
                MobileMetric("NETWORK", String.format("%.1fM", mbps), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MobileNav("OPTIMIZE") { onOptimize() }
            MobileNav("APPS") { openUltimate(context, "APPS") }
            MobileNav("NETWORK") { openUltimate(context, "NETWORK") }
            MobileNav("SYSTEM") { openUltimate(context, "SYSTEM") }
        }
        Spacer(Modifier.height(8.dp))
        Text("SHADOWFOX TV  |  OPTIMIZED FOR PERFORMANCE", color = MUTED, fontSize = 7.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun MobileLandscapeDashboard(
    context: Context, ram: Float, apps: Int, mbps: Float, ping: Int, busy: Boolean,
    rootAvailable: Boolean, ramFreed: Long, storageFreed: Long, closedApps: Int,
    clock: Date, onOptimize: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF010305), Color(0xFF070B0F), Color(0xFF010305))))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.shadowfox_logo), "ShadowFox TV", contentScale = ContentScale.Fit, modifier = Modifier.size(104.dp, 38.dp))
            Text("OPTIMIZE • CLEAN • PERFORM", color = MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(if (rootAvailable) "ROOTED PRO" else "MOBILE", color = if (rootAvailable) CYAN else MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text("  •  v" + BuildConfig.VERSION_NAME, color = MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(clock), color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.55f)) {
                DisplayCard(Modifier.fillMaxWidth().weight(1f), hero = true) {
                    Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SMART OPTIMIZE", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("One-touch performance optimization", color = MUTED, fontSize = 8.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(if (busy) "OPTIMIZING…" else "READY TO OPTIMIZE", color = CYAN, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Text(if (rootAvailable) "Root access active" else "Android safe mode", color = MUTED, fontSize = 8.sp)
                            Spacer(Modifier.height(10.dp))
                            MasterButton(if (busy) "WORKING…" else "OPTIMIZE NOW", 142.dp, !busy, onOptimize)
                        }
                        RamGauge(ram, Modifier.size(128.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MobileMetric("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f))
                    MobileMetric("CLOSED", closedApps.toString(), Modifier.weight(1f))
                    MobileMetric("NETWORK", String.format("%.1fM", mbps), Modifier.weight(1f))
                }
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MobileMetric("SCORE", scoreFor(ram, ping).toString(), Modifier.weight(1f))
                    MobileMetric("RAM", ram.toInt().toString() + "%", Modifier.weight(1f))
                    MobileMetric("APPS", apps.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                GlowCard(Modifier.fillMaxWidth().height(70.dp), onClick = { openUltimate(context, "SYSTEM") }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("CACHE CLEANER", color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text(if (storageFreed > 0) formatBytes(storageFreed) + " CLEARED" else "READY", color = CYAN, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                GlowCard(Modifier.fillMaxWidth().height(70.dp), onClick = { openUltimate(context, "OPTIMIZE") }) {
                    Column(Modifier.padding(12.dp)) {
                        Text("ULTIMATE CENTER", color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        Text("ADVANCED TOOLS", color = CYAN, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MobileNav("OPTIMIZE", onOptimize)
            MobileNav("APPS") { openUltimate(context, "APPS") }
            MobileNav("NETWORK") { openUltimate(context, "NETWORK") }
            MobileNav("SYSTEM") { openUltimate(context, "SYSTEM") }
        }
    }
}

@Composable
private fun MobileMetric(label: String, value: String, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    Column(modifier.background(Color(0xFF090E12), shape).padding(horizontal = 9.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(value, color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun MobileNav(label: String, onClick: () -> Unit) {
    Box(Modifier.height(42.dp).width(82.dp).background(Color(0xFF080D12), RoundedCornerShape(6.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = WHITE, fontSize = 8.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun NavEntry(icon: String, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .background(if (focused) Color(0xFF0B1821) else Color.Transparent, shape)
            .shadow(if (focused) 12.dp else 0.dp, shape, false, CYAN.copy(alpha = .75f), CYAN.copy(alpha = .75f))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = if (focused) CYAN else MUTED, fontSize = 16.sp)
        Spacer(Modifier.width(9.dp))
        Text(label, color = if (focused) WHITE else MUTED, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

private fun openUltimate(context: Context, tab: String) {
    context.startActivity(
        Intent(context, UltimateCenterActivity::class.java)
            .putExtra("shadowfox_tab", tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun MetricCard(title: String, value: String, detail: String, modifier: Modifier) {
    DisplayCard(modifier) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Text(title, color = MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text(value, color = WHITE, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text(detail, color = CYAN, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun scoreFor(ram: Float, ping: Int): Int {
    val ramScore = (100f - ram).coerceIn(0f, 100f)
    val networkScore = if (ping <= 0) 80f else (100f - ping.coerceAtMost(100)).coerceAtLeast(0f)
    return (ramScore * .7f + networkScore * .3f).toInt().coerceIn(0, 100)
}

@Composable
private fun BottomSystemStrip(
    root: Boolean,
    ramFreed: Long,
    storageFreed: Long,
    closedApps: Int,
    modifier: Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StatTile("RAM FREED", formatBytes(ramFreed), Modifier.size(126.dp, 48.dp))
        Spacer(Modifier.width(9.dp))
        StatTile("CACHE CLEARED", formatBytes(storageFreed), Modifier.size(126.dp, 48.dp))
        Spacer(Modifier.width(9.dp))
        StatTile("APPS CLOSED", closedApps.toString(), Modifier.size(112.dp, 48.dp))
        Spacer(Modifier.width(78.dp))
        StatTile("ROOT", if (root) "ACTIVE" else "READY", Modifier.size(112.dp, 48.dp), if (root) GREEN else MUTED)
        Spacer(Modifier.width(9.dp))
        StatTile("DEVICE", deviceLabel(), Modifier.size(175.dp, 48.dp))
        Spacer(Modifier.width(9.dp))
        StatTile("ANDROID", Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }, Modifier.size(100.dp, 48.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color = WHITE) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .shadow(6.dp, shape, false, CYAN.copy(alpha = .25f), CYAN.copy(alpha = .25f))
            .background(Color(0xD90A1C29), shape)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, color = MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun DisplayCard(
    modifier: Modifier,
    hero: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .shadow(
                elevation = if (hero) 15.dp else 9.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .background(Brush.verticalGradient(listOf(Color(0xF20D141A), Color(0xF205090D))), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFF35505E),
                style = Stroke(1.2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )
        }
        content()
    }
}

@Composable
private fun GlowCard(
    modifier: Modifier,
    onClick: () -> Unit,
    hero: Boolean = false,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(if (focused) 1.045f else 1f, label = "focus")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .scale(focusScale)
            .shadow(
                elevation = if (focused) 25.dp else if (hero) 15.dp else 9.dp,
                shape = shape,
                clip = false,
                ambientColor = if (focused) CYAN else Color.Black,
                spotColor = if (focused) CYAN else Color.Black
            )
            .background(Brush.verticalGradient(listOf(Color(0xF20D141A), Color(0xF205090D))), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = if (focused) CYAN.copy(alpha = .90f) else Color(0xFF35505E),
                style = Stroke(if (focused) 2.5.dp.toPx() else 1.2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())
            )
        }
        content()
    }
}

@Composable
private fun MasterButton(text: String, width: androidx.compose.ui.unit.Dp, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .width(width)
            .height(32.dp)
            .scale(if (focused) 1.08f else 1f)
            .shadow(if (focused) 16.dp else 2.dp, shape, false, if (focused) CYAN else Color.Black, if (focused) CYAN else Color.Black)
            .background(Brush.horizontalGradient(listOf(Color(0xFF101820), Color(0xFF071015))), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (focused) CYAN else WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ScanDial(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * .48f, size.height * .47f)
        val r = size.minDimension * .29f
        drawCircle(CYAN.copy(.10f), r * 1.45f, c)
        drawCircle(CYAN.copy(.35f), r * 1.18f, c, style = Stroke(9.dp.toPx()))
        drawCircle(CYAN, r, c, style = Stroke(5.dp.toPx()))
        drawCircle(BLUE.copy(.5f), r * .68f, c, style = Stroke(5.dp.toPx()))
        val p = Offset(c.x + r * .72f, c.y + r * .72f)
        drawLine(CYAN, c, p, 5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun RamGauge(value: Float, modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * .52f)
            val r = size.minDimension * .37f
            drawCircle(Color(0xFF07141D), r * 1.15f, c)
            drawCircle(CYAN.copy(.25f), r * 1.08f, c, style = Stroke(9.dp.toPx()))
            val start = 140f
            val sweep = 260f
            drawArc(Color(0xFF153443), start, sweep, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
            for (i in 0..50) {
                val t = i / 50f
                val color = if (t < .65f) lerp(CYAN, ORANGE, t / .65f) else lerp(ORANGE, Color.Red, (t - .65f) / .35f)
                drawArc(color, start + sweep * t, sweep / 50 + 1f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(11.dp.toPx()))
            }
            for (i in 0..10) {
                val a = Math.toRadians((start + sweep * i / 10).toDouble())
                drawLine(
                    WHITE.copy(.7f),
                    Offset(c.x + cos(a).toFloat() * r * .66f, c.y + sin(a).toFloat() * r * .66f),
                    Offset(c.x + cos(a).toFloat() * r * .82f, c.y + sin(a).toFloat() * r * .82f),
                    2.dp.toPx()
                )
            }
            val a = Math.toRadians((start + sweep * value.coerceIn(0f, 100f) / 100f).toDouble())
            val end = Offset(c.x + cos(a).toFloat() * r * .62f, c.y + sin(a).toFloat() * r * .62f)
            drawLine(CYAN.copy(.25f), c, end, 13.dp.toPx(), StrokeCap.Round)
            drawLine(CYAN, c, end, 3.dp.toPx(), StrokeCap.Round)
            drawCircle(CYAN, 8.dp.toPx(), c)
        }
        Column(Modifier.align(Alignment.Center).offset(y = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${value.toInt()}%", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("RAM", color = MUTED, fontSize = 7.sp)
        }
    }
}

@Composable
private fun Broom(modifier: Modifier) {
    Canvas(modifier) {
        drawLine(CYAN, Offset(size.width * .58f, size.height * .08f), Offset(size.width * .50f, size.height * .54f), 6.dp.toPx(), StrokeCap.Round)
        val p = Path().apply {
            moveTo(size.width * .20f, size.height * .52f)
            lineTo(size.width * .70f, size.height * .52f)
            lineTo(size.width * .80f, size.height * .92f)
            lineTo(size.width * .10f, size.height * .92f)
            close()
        }
        drawPath(p, CYAN)
    }
}

@Composable
private fun NetworkIcon(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * .5f, size.height * .72f)
        listOf(.18f, .30f, .42f).forEach { r0 ->
            val r = size.minDimension * r0
            drawArc(CYAN, 205f, 130f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(CYAN, 5.dp.toPx(), c)
    }
}

@Composable
private fun Bars(samples: List<Int>, modifier: Modifier) {
    Canvas(modifier) {
        val count = 16
        val maxSample = max(1, samples.maxOrNull() ?: 1)
        for (i in 0 until count) {
            val q = if (samples.isEmpty()) ((i * 37) % 100) / 100f else samples[i % samples.size].toFloat() / maxSample
            val h = size.height * (.18f + .72f * q)
            val x = size.width / count * (i + .5f)
            drawLine(if (i == 12) ORANGE else CYAN, Offset(x, size.height), Offset(x, size.height - h), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun Bolt(modifier: Modifier) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width * .57f, 0f)
            lineTo(size.width * .18f, size.height * .55f)
            lineTo(size.width * .47f, size.height * .55f)
            lineTo(size.width * .34f, size.height)
            lineTo(size.width * .83f, size.height * .38f)
            lineTo(size.width * .55f, size.height * .38f)
            close()
        }
        drawPath(p, CYAN.copy(.2f), style = Stroke(13.dp.toPx()))
        drawPath(p, CYAN)
    }
}

@Composable
private fun MasterBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(BG)
        drawCircle(CYAN.copy(.008f), size.width * .38f, Offset(size.width * .48f, size.height * .45f))
        val p = Path().apply {
            moveTo(size.width * .43f, 0f)
            lineTo(size.width * .39f, size.height * .18f)
            lineTo(size.width * .46f, size.height * .18f)
            lineTo(size.width * .41f, size.height * .38f)
        }
        drawPath(p, CYAN.copy(.025f), style = Stroke(3.dp.toPx()))
    }
}

private data class CleanupResult(
    val closedApps: Int,
    val ramFreedBytes: Long,
    val storageFreedBytes: Long,
    val rootUsed: Boolean
)

private class AppOptimizer(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    suspend fun optimize(): CleanupResult = withContext(Dispatchers.IO) {
        val beforeRam = availableMemoryBytes(context)
        val beforeStorage = freeStorageBytes()
        val protected = protectedPackages()
        val root = rootShellAvailable()

        val packages = if (root) {
            runRoot("pm list packages -3").output
                .lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:") }
                .filter { it.isNotBlank() && it != context.packageName && it !in protected }
                .toList()
        } else {
            activityManager.runningAppProcesses.orEmpty()
                .flatMap { it.pkgList?.toList().orEmpty() }
                .distinct()
                .filter { it != context.packageName && it !in protected }
                .filterNot(::isSystemPackage)
        }

        var closed = 0
        if (root && packages.isNotEmpty()) {
            val command = packages.joinToString(" ; ") { pkg -> "am force-stop '$pkg'" }
            if (runRoot(command).success) closed = packages.size
            runRoot("pm trim-caches 999999999999")
            runRoot("sync")
        } else {
            packages.forEach { packageName ->
                runCatching {
                    activityManager.killBackgroundProcesses(packageName)
                    closed++
                }
            }
        }

        delay(300)
        val afterRam = availableMemoryBytes(context)
        val afterStorage = freeStorageBytes()
        CleanupResult(
            closedApps = closed,
            ramFreedBytes = (afterRam - beforeRam).coerceAtLeast(0L),
            storageFreedBytes = (afterStorage - beforeStorage).coerceAtLeast(0L),
            rootUsed = root
        )
    }

    private fun isSystemPackage(packageName: String): Boolean {
        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return true
        val flags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return info.flags and flags != 0
    }

    private fun protectedPackages(): Set<String> {
        val protected = mutableSetOf(
            context.packageName,
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.amazon.device.software.ota"
        )
        packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNullTo(protected) { it.activityInfo?.packageName }
        packageManager.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), PackageManager.MATCH_ALL)
            .mapNotNullTo(protected) { it.serviceInfo?.packageName }
        return protected
    }
}

private data class RootResult(val success: Boolean, val output: String)

private fun rootShellAvailable(): Boolean = runRoot("id").let { it.success && it.output.contains("uid=0") }

private fun runRoot(command: String): RootResult = runCatching {
    val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val code = process.waitFor()
    RootResult(code == 0, output)
}.getOrElse { RootResult(false, "") }

private fun memoryUsedPercent(context: Context): Float {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return if (info.totalMem <= 0) 0f else ((info.totalMem - info.availMem).toDouble() / info.totalMem * 100).toFloat().coerceIn(0f, 100f)
}

private fun availableMemoryBytes(context: Context): Long {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return info.availMem
}

private fun runningProcessCount(context: Context): Int {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.runningAppProcesses.orEmpty().size
}

private fun freeStorageBytes(): Long = runCatching {
    StatFs(Environment.getDataDirectory().absolutePath).availableBytes
}.getOrDefault(0L)

private fun deviceLabel(): String {
    val maker = Build.MANUFACTURER.orEmpty().trim()
    val model = Build.MODEL.orEmpty().trim()
    return when {
        maker.isBlank() -> model
        model.startsWith(maker, ignoreCase = true) -> model
        else -> "$maker $model"
    }.take(24)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.0f MB", mb)
}

private fun totalTrafficBytes(): Long {
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return -1
    return rx + tx
}

private suspend fun measureLatencyMs(): Int = withContext(Dispatchers.IO) {
    val started = System.nanoTime()
    runCatching {
        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), 1200) }
        ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrDefault(0)
}
