package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.foundation.border
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
private val METAL_TOP = Color(0xFF1A2732)
private val METAL_MID = Color(0xFF07111A)
private val METAL_BOTTOM = Color(0xFF020508)
private val METAL_EDGE = Color(0xFF27495E)
private val CYAN = Color(0xFF00E5FF)
private val BLUE = Color(0xFF08AEEA)
private val ORANGE = Color(0xFFFF7A00)
private val WHITE = Color(0xFFF7FBFF)
private val MUTED = Color(0xFFB7C2CA)
private val GREEN = Color(0xFF77C943)

@Composable
private fun MasterDashboard(context: Context) {
    val optimizePrefs = remember { context.getSharedPreferences("shadowfox_optimizer", Context.MODE_PRIVATE) }
    var ram by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var apps by remember { mutableIntStateOf(runningProcessCount(context)) }
    var mbps by remember { mutableFloatStateOf(0f) }
    var ping by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var optimizationRootUsed by remember { mutableStateOf(optimizePrefs.getBoolean("root_used", false)) }
    var ramFreed by remember { mutableStateOf(0L) }
    var storageFreed by remember { mutableStateOf(0L) }
    var closedApps by remember { mutableIntStateOf(0) }
    var optimizeHasRun by remember { mutableStateOf(optimizePrefs.getBoolean("has_run", false)) }
    var cacheBusy by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(0L) }
    var clock by remember { mutableStateOf(Date()) }
    LaunchedEffect(optimizeHasRun) {
        if (optimizeHasRun) {
            closedApps = optimizePrefs.getInt("closed_apps", closedApps)
            ramFreed = optimizePrefs.getLong("ram_freed", ramFreed)
            storageFreed = optimizePrefs.getLong("storage_freed", storageFreed)
            optimizationRootUsed = optimizePrefs.getBoolean("root_used", optimizationRootUsed)
        }
    }
    val proEngine = remember { ShadowFoxProEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rootAvailable = withContext(Dispatchers.IO) { rootShellAvailable() }
        var processRefreshTick = 0
        while (true) {
            val before = totalTrafficBytes()
            delay(1000)
            val after = totalTrafficBytes()
            if (before >= 0 && after >= before) mbps = (after - before) * 8f / 1_000_000f
            ping = measureLatencyMs()
            ram = memoryUsedPercent(context)
            if (processRefreshTick % 5 == 0) {
                apps = withContext(Dispatchers.IO) {
                    proEngine.runningThirdPartyCount()
                }
            }
            processRefreshTick++
            clock = Date()
        }
    }

    fun cleanCache() {
        if (cacheBusy) return
        cacheBusy = true
        scope.launch {
            try {
                cacheCleared = UltimateManager(context).clearCache()
            } finally {
                cacheBusy = false
            }
        }
    }

    fun optimize() {
        if (busy) return
        busy = true
        optimizeHasRun = false
        scope.launch {
            try {
                val result = ShadowFoxProEngine(context.applicationContext).optimize()
                closedApps = result.closedApps
                ramFreed = result.ramFreedBytes
                storageFreed = result.storageFreedBytes
                rootAvailable = result.rootUsed
                optimizationRootUsed = result.rootUsed
                optimizeHasRun = true
                optimizePrefs.edit()
                    .putBoolean("has_run", true)
                    .putInt("closed_apps", result.closedApps)
                    .putLong("ram_freed", result.ramFreedBytes)
                    .putLong("storage_freed", result.storageFreedBytes)
                    .putBoolean("root_used", result.rootUsed)
                    .putString("summary", result.summary)
                    .commit()
                ram = memoryUsedPercent(context)
                apps = withContext(Dispatchers.IO) {
                    if (rootAvailable) proEngine.runningThirdPartyCount() else runningProcessCount(context)
                }
            } finally {
                busy = false
            }
        }
    }

    val configuration = LocalConfiguration.current
    val hasTelevisionUi = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val isPhone = !hasTelevisionUi && configuration.smallestScreenWidthDp < 600
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

                // Reference-spec metallic header
                DisplayCard(Modifier.offset(12.dp, 8.dp).size(936.dp, 74.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.shadowfox_logo), "ShadowFox TV", contentScale = ContentScale.Fit, modifier = Modifier.size(190.dp, 62.dp))
                        Column(Modifier.width(250.dp)) {
                            Text("OPTIMIZE • CLEAN • PERFORM", color = MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text((if (rootAvailable) "ROOTED PRO MODE" else "STANDARD MODE") + "  •  v" + BuildConfig.VERSION_NAME, color = if (rootAvailable) CYAN else MUTED, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BUILT FOR ANDROID TV", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("FASTER • SMOOTHER • BETTER", color = MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.width(120.dp), horizontalAlignment = Alignment.End) {
                            Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(clock), color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(clock), color = MUTED, fontSize = 9.sp)
                        }
                    }
                }

                // Reference left navigation rail
                DisplayCard(Modifier.offset(12.dp, 92.dp).size(142.dp, 404.dp)) {
                    Column(Modifier.fillMaxSize().padding(8.dp)) {
                        NavEntry("⌂", "OPTIMIZE", false, Modifier.height(64.dp).fillMaxWidth()) { optimize() }
                        NavEntry("▦", "APPS", false, Modifier.height(64.dp).fillMaxWidth()) { openUltimate(context, "APPS") }
                        NavEntry("⌁", "NETWORK", false, Modifier.height(64.dp).fillMaxWidth()) { openUltimate(context, "NETWORK") }
                        NavEntry("⚙", "SYSTEM", false, Modifier.height(64.dp).fillMaxWidth()) { openUltimate(context, "SYSTEM") }
                        Spacer(Modifier.weight(1f))
                        Image(painterResource(R.drawable.shadowfox_logo), null, contentScale = ContentScale.Fit, modifier = Modifier.height(105.dp).fillMaxWidth())
                        Text("SHADOWFOX TV", color = MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text("PERFORMANCE WITHOUT LIMITS", color = MUTED, fontSize = 6.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }

                MetricCard("SHADOWFOX SCORE", scoreFor(ram, ping).toString() + "/100", "OPTIMIZED", Modifier.offset(166.dp, 92.dp).size(190.dp, 78.dp))
                MetricCard("RAM USED", ram.toInt().toString() + "%", formatBytes(availableMemoryBytes(context)) + " FREE", Modifier.offset(364.dp, 92.dp).size(190.dp, 78.dp))
                MetricCard("RUNNING APPS", apps.toString() + " apps", "LIVE", Modifier.offset(562.dp, 92.dp).size(190.dp, 78.dp))
                MetricCard("NETWORK", if (ping in 1..90) "EXCELLENT" else String.format("%.1f Mbps", mbps), if (ping > 0) "$ping ms PING" else "CHECKING", Modifier.offset(760.dp, 92.dp).size(188.dp, 78.dp))

                // Large reference Smart Optimize module
                DisplayCard(Modifier.offset(166.dp, 182.dp).size(338.dp, 250.dp)) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        RamGauge(ram, Modifier.size(if (optimizeHasRun) 78.dp else 108.dp))
                        Text("SMART OPTIMIZE", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        if (!optimizeHasRun) {
                            Text("Automatically chooses the safest", color = MUTED, fontSize = 9.sp)
                            Text("cleanup supported by this device.", color = MUTED, fontSize = 9.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                        MasterButton(
                            text = when {
                                busy -> "WORKING…"
                                optimizeHasRun -> "⚡  RUN SMART OPTIMIZE AGAIN"
                                else -> "⚡  ONE-TAP SMART OPTIMIZE"
                            },
                            width = 245.dp,
                            enabled = true,
                            onClick = { if (!busy) optimize() }
                        )
                        Spacer(Modifier.height(5.dp))
                        if (optimizeHasRun) {
                            Column(
                                Modifier.width(275.dp).height(57.dp)
                                    .border(1.dp, CYAN.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("✓  OPTIMIZATION COMPLETE", color = GREEN, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    Text("$closedApps APPS", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(formatBytes(ramFreed) + " RAM", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(formatBytes(storageFreed) + " CACHE", color = WHITE, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(if (optimizationRootUsed) "ROOT ✓ VERIFIED" else "STANDARD MODE", color = if (optimizationRootUsed) CYAN else MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (!busy) {
                            Text("✓  READY  •  Last run: Never", color = GREEN, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("●  OPTIMIZING…", color = CYAN, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Reference Cache Cleaner module
                DisplayCard(Modifier.offset(516.dp, 182.dp).size(432.dp, 118.dp)) {
                    Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Broom(Modifier.size(54.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("CACHE CLEANER", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("Remove temporary and cached files", color = MUTED, fontSize = 9.sp)
                            Spacer(Modifier.height(8.dp))
                            MasterButton(if (cacheBusy) "CLEANING…" else "CLEAN CACHE", 150.dp, true) { if (!cacheBusy) cleanCache() }
                        }
                        Column(Modifier.width(105.dp)) {
                            Text("CACHE CLEARED", color = MUTED, fontSize = 8.sp)
                            Text(formatBytes(cacheCleared), color = CYAN, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Reference Ultimate Center module
                DisplayCard(Modifier.offset(516.dp, 312.dp).size(432.dp, 120.dp)) {
                    Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Bolt(Modifier.size(48.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("ULTIMATE CENTER", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("Access all optimization tools:", color = MUTED, fontSize = 9.sp)
                            Text("Apps • Network • System", color = MUTED, fontSize = 9.sp)
                            Spacer(Modifier.height(8.dp))
                            MasterButton("OPEN ULTIMATE CENTER", 190.dp, true) { openUltimate(context, "OPTIMIZE") }
                        }
                        Column(Modifier.width(135.dp)) {
                            Text("✓  LIVE CONNECTION", color = CYAN, fontSize = 8.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("✓  REAL-TIME PERFORMANCE", color = CYAN, fontSize = 8.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("✓  SYSTEM OPTIMIZATION", color = CYAN, fontSize = 8.sp)
                        }
                    }
                }

                // Full-width Thermal + Performance strip
                DisplayCard(Modifier.offset(166.dp, 444.dp).size(782.dp, 52.dp)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("THERMAL", color = CYAN, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.width(245.dp)) {
                            Text("THERMAL + PERFORMANCE", color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Black)
                            Text("Live device condition based on actual Android telemetry.", color = MUTED, fontSize = 8.sp)
                        }
                        Text("Thermal: NORMAL", color = CYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(20.dp))
                        Text("Free RAM: " + formatBytes(availableMemoryBytes(context)), color = MUTED, fontSize = 9.sp)
                        Spacer(Modifier.width(20.dp))
                        Text("Free Storage: " + formatBytes(freeStorageBytes()), color = MUTED, fontSize = 9.sp)
                    }
                }

                Text("SHADOWFOX TV   |   OPTIMIZED FOR PERFORMANCE", color = MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
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
            Brush.verticalGradient(listOf(Color(0xFF050607), Color(0xFF1A1E21), Color(0xFF080A0C), Color(0xFF020303)))
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
            .background(Brush.verticalGradient(listOf(Color(0xFF050607), Color(0xFF1A1E21), Color(0xFF080A0C), Color(0xFF020303))))
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
            .background(if (focused) Color(0xFF0C1013) else Color.Transparent, shape)
            .shadow(if (focused) 2.dp else 0.dp, shape, false, WHITE.copy(alpha = .16f), WHITE.copy(alpha = .16f))
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
            .background(Brush.verticalGradient(listOf(METAL_TOP, METAL_MID, METAL_BOTTOM)), shape)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = METAL_EDGE,
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
    val focusScale = if (focused) 1.012f else 1f
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .scale(focusScale)
            .shadow(
                elevation = if (focused) 5.dp else if (hero) 8.dp else 3.dp,
                shape = shape,
                clip = false,
                ambientColor = if (focused) CYAN.copy(alpha = .22f) else Color.Black,
                spotColor = if (focused) CYAN.copy(alpha = .22f) else Color.Black
            )
            .background(Brush.verticalGradient(listOf(Color(0xFC171A1D), Color(0xFC030405), Color(0xFC0E1113), Color(0xFC010203))), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = if (focused) CYAN.copy(alpha = .82f) else METAL_EDGE,
                style = Stroke(if (focused) 1.35.dp.toPx() else 1.dp.toPx()),
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
            .scale(if (focused) 1.015f else 1f)
            .shadow(if (focused) 3.dp else 1.dp, shape, false, if (focused) WHITE.copy(alpha = .18f) else Color.Black, if (focused) WHITE.copy(alpha = .18f) else Color.Black)
            .background(Brush.horizontalGradient(listOf(METAL_TOP, METAL_MID, METAL_BOTTOM)), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) { drawRoundRect(color = if (focused) CYAN.copy(alpha = .82f) else METAL_EDGE, style = Stroke(if (focused) 1.25.dp.toPx() else 1.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx())) }
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
        drawRect(Brush.verticalGradient(listOf(Color(0xFF07131D), Color(0xFF02070B), BG)))
        drawCircle(BLUE.copy(.035f), size.width * .42f, Offset(size.width * .48f, size.height * .40f))
        val p = Path().apply {
            moveTo(size.width * .43f, 0f)
            lineTo(size.width * .39f, size.height * .18f)
            lineTo(size.width * .46f, size.height * .18f)
            lineTo(size.width * .41f, size.height * .38f)
        }
        drawPath(p, CYAN.copy(.025f), style = Stroke(3.dp.toPx()))
    }
}

private data class RootResult(val success: Boolean, val output: String)

private fun rootShellAvailable(): Boolean {
    return RootShell.isRootAvailable()
}

private fun runRoot(command: String): RootResult {
    val result = RootShell.exec(command)
    return RootResult(result.success, result.output)
}

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
    if (bytes < 1024L * 1024L) return "${(bytes / 1024L).coerceAtLeast(1L)} KB"
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
