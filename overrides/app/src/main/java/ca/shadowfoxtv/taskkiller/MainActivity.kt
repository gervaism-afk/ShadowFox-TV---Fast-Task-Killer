package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShadowFoxTheme {
                ShadowFoxUpdateGate(applicationContext) {
                    ShadowFoxDashboard(applicationContext)
                }
            }
        }
    }
}

private val Background = Color(0xFF07101A)
private val SurfaceDeep = Color(0xFF0B1722)
private val SurfaceMid = Color(0xFF102230)
private val Cyan = Color(0xFF18E9FF)
private val CyanDim = Color(0xFF0A7D91)
private val Orange = Color(0xFFFF8A24)
private val White = Color(0xFFF5FBFF)
private val Muted = Color(0xFF9BB0BF)
private val Green = Color(0xFF54E38E)

@Composable
private fun ShadowFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Orange,
            background = Background,
            surface = SurfaceDeep,
            onBackground = White,
            onSurface = White
        ),
        content = content
    )
}

@Composable
private fun ShadowFoxDashboard(context: Context) {
    var networkMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableIntStateOf(0) }
    var ramPercent by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var runningApps by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("SYSTEM READY") }
    var working by remember { mutableStateOf(false) }
    val history = remember { mutableStateListOf<Int>() }
    val optimizer = remember(context) { AppOptimizer(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            val before = totalTrafficBytes()
            delay(1000)
            val after = totalTrafficBytes()
            if (before >= 0 && after >= before) {
                networkMbps = ((after - before) * 8f / 1_000_000f).coerceAtLeast(0f)
            }
            pingMs = measureLatencyMs()
            if (pingMs > 0) {
                history.add(pingMs)
                while (history.size > 28) history.removeAt(0)
            }
            ramPercent = memoryUsedPercent(context)
            runningApps = runningProcessCount(context)
        }
    }

    fun optimize(label: String) {
        if (working) return
        scope.launch {
            working = true
            status = label
            val result = optimizer.optimize()
            delay(500)
            ramPercent = memoryUsedPercent(context)
            runningApps = runningProcessCount(context)
            status = if (result.requestedKills > 0) {
                "${result.requestedKills} APPS OPTIMIZED"
            } else {
                "SYSTEM ALREADY CLEAN"
            }
            working = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        TechBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 24.dp)
        ) {
            Header()
            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SystemScanCard(
                    modifier = Modifier
                        .weight(0.88f)
                        .fillMaxHeight(0.78f),
                    runningApps = runningApps,
                    status = status,
                    working = working,
                    onScan = { optimize("SCANNING SYSTEM...") }
                )

                RamBoosterCard(
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight(),
                    ramPercent = ramPercent,
                    working = working,
                    onBoost = { optimize("BOOSTING MEMORY...") }
                )

                Column(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    CacheCleanerCard(
                        modifier = Modifier.weight(1f),
                        working = working,
                        onClean = { optimize("CLEANING BACKGROUND APPS...") }
                    )
                    NetworkMonitorCard(
                        modifier = Modifier.weight(1f),
                        networkMbps = networkMbps,
                        pingMs = pingMs,
                        history = history
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = status,
                    color = if (working) Orange else Cyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp
                )
                Surface(
                    color = Color(0xFF192A21),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.border(1.dp, Color(0xFF3D6B4B), RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("●", color = Green, fontSize = 12.sp)
                        Spacer(Modifier.width(7.dp))
                        Text("FOR ANDROID TV", color = White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.shadowfox_logo),
                contentDescription = "ShadowFox TV logo",
                modifier = Modifier.size(58.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("ShadowFox", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                    Spacer(Modifier.width(5.dp))
                    Text("TV", color = Orange, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
                }
                Text("www.shadowfoxtv.ca", color = Muted, fontSize = 11.sp)
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Image(
                painter = painterResource(id = R.drawable.shadowfox_logo),
                contentDescription = null,
                modifier = Modifier.size(50.dp)
            )
            Text("SYSTEM OPTIMIZER", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        }
    }
}

@Composable
private fun SystemScanCard(
    modifier: Modifier,
    runningApps: Int,
    status: String,
    working: Boolean,
    onScan: () -> Unit
) {
    NeonCard(modifier = modifier, accent = CyanDim) {
        Text("SYSTEM SCAN", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text("Scan installed and running apps", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(18.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ScanOrb(Modifier.size(118.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(runningApps.toString(), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                Text("ACTIVE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))
        Text(status, color = Muted, fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        GlowButton(
            text = if (working) "WORKING..." else "SCAN NOW",
            accent = Cyan,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
            onClick = onScan
        )
    }
}

@Composable
private fun RamBoosterCard(
    modifier: Modifier,
    ramPercent: Float,
    working: Boolean,
    onBoost: () -> Unit
) {
    NeonCard(modifier = modifier, accent = Cyan, strong = true, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("RAM BOOSTER", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text("Optimize memory for smoother streaming", color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            RamGauge(ramPercent, Modifier.size(238.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${ramPercent.toInt()}%", color = White, fontWeight = FontWeight.Black, fontSize = 38.sp)
                Text("RAM IN USE", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
            }
        }

        GlowButton(
            text = if (working) "BOOSTING..." else "BOOST",
            accent = Cyan,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(0.76f),
            onClick = onBoost
        )
    }
}

@Composable
private fun CacheCleanerCard(modifier: Modifier, working: Boolean, onClean: () -> Unit) {
    NeonCard(modifier = modifier, accent = CyanDim) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✦", color = Cyan, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("CACHE CLEANER", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Remove background apps and reclaim memory", color = Muted, fontSize = 9.sp, lineHeight = 12.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        GlowButton(
            text = if (working) "CLEANING..." else "CLEAN NOW",
            accent = Cyan,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
            onClick = onClean
        )
    }
}

@Composable
private fun NetworkMonitorCard(
    modifier: Modifier,
    networkMbps: Float,
    pingMs: Int,
    history: List<Int>
) {
    NeonCard(modifier = modifier, accent = CyanDim) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◉", color = Cyan, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("NETWORK MONITOR", color = White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("Live connection performance", color = Muted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (networkMbps < 10f) String.format("%.1f", networkMbps) else String.format("%.0f", networkMbps),
                color = White,
                fontWeight = FontWeight.Black,
                fontSize = 23.sp
            )
            Spacer(Modifier.width(5.dp))
            Text("Mbps", color = Cyan, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(if (pingMs > 0) "${pingMs} ms" else "-- ms", color = Orange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        MiniGraph(samples = history, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun NeonCard(
    modifier: Modifier,
    accent: Color,
    strong: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = modifier
            .background(if (strong) SurfaceMid else SurfaceDeep, shape)
            .border(if (strong) 2.dp else 1.dp, accent.copy(alpha = if (strong) 0.95f else 0.65f), shape)
    ) {
        if (strong) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(5.dp)
                    .border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

@Composable
private fun GlowButton(
    text: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) accent else Color(0xFF0E5360),
            contentColor = if (focused) Color.Black else White,
            disabledContainerColor = Color(0xFF23313A),
            disabledContentColor = Muted
        ),
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.07f else 1f)
            .border(if (focused) 2.dp else 1.dp, if (focused) White else accent, RoundedCornerShape(12.dp))
    ) {
        Text(text, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RamGauge(percent: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(percent.coerceIn(0f, 100f), label = "ram")
    Canvas(modifier = modifier) {
        val stroke = 17.dp.toPx()
        val inset = stroke
        drawArc(
            color = Color(0xFF173B47),
            startAngle = 140f,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        val sweep = 260f * (animated / 100f)
        drawArc(
            color = Cyan,
            startAngle = 140f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = Orange,
            startAngle = 140f + 260f * 0.72f,
            sweepAngle = 260f * 0.28f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
        )

        val center = Offset(size.width / 2, size.height / 2)
        val angle = Math.toRadians((140f + sweep).toDouble())
        val radius = size.minDimension * 0.31f
        val end = Offset(
            center.x + cos(angle).toFloat() * radius,
            center.y + sin(angle).toFloat() * radius
        )
        drawLine(Orange, center, end, 4.dp.toPx(), StrokeCap.Round)
        drawCircle(Cyan, 12.dp.toPx(), center)
        drawCircle(White, 5.dp.toPx(), center)
    }
}

@Composable
private fun ScanOrb(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF0D2934), size.minDimension * 0.44f, c)
        drawCircle(Cyan.copy(alpha = 0.35f), size.minDimension * 0.42f, c, style = Stroke(5.dp.toPx()))
        drawCircle(Cyan, size.minDimension * 0.31f, c, style = Stroke(4.dp.toPx()))
        drawCircle(Cyan.copy(alpha = 0.15f), size.minDimension * 0.22f, c)
        drawArc(Cyan, -45f, 220f, false, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun MiniGraph(samples: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val maxValue = max(80, samples.maxOrNull() ?: 80).toFloat()
        val minValue = min(10, samples.minOrNull() ?: 10).toFloat()
        val range = (maxValue - minValue).coerceAtLeast(1f)
        val xStep = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { index, sample ->
            val x = xStep * index
            val y = size.height - ((sample - minValue) / range) * size.height * 0.82f - size.height * 0.08f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Cyan, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        samples.forEachIndexed { index, sample ->
            val x = xStep * index
            val y = size.height - ((sample - minValue) / range) * size.height * 0.82f - size.height * 0.08f
            drawCircle(if (index % 5 == 0) Orange else Cyan.copy(alpha = 0.35f), 3.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
private fun TechBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val line = Color(0xFF133142).copy(alpha = 0.26f)
        val step = 88.dp.toPx()
        var x = -step
        while (x < size.width + step) {
            drawLine(line, Offset(x, 0f), Offset(x + step * 0.65f, size.height), 1.dp.toPx())
            x += step
        }
        var y = 40.dp.toPx()
        while (y < size.height) {
            drawLine(line.copy(alpha = 0.16f), Offset(0f, y), Offset(size.width, y + 28.dp.toPx()), 1.dp.toPx())
            y += 92.dp.toPx()
        }
    }
}

private fun memoryUsedPercent(context: Context): Float {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    if (info.totalMem <= 0L) return 0f
    return ((info.totalMem - info.availMem).toDouble() / info.totalMem.toDouble() * 100.0).toFloat().coerceIn(0f, 100f)
}

private fun runningProcessCount(context: Context): Int {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.runningAppProcesses.orEmpty().size
}

private data class CleanupResult(val requestedKills: Int)

private class AppOptimizer(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    suspend fun optimize(): CleanupResult = withContext(Dispatchers.IO) {
        val protected = protectedPackages()
        val packages = activityManager.runningAppProcesses
            .orEmpty()
            .flatMap { it.pkgList?.toList().orEmpty() }
            .distinct()
            .filter { it != context.packageName }
            .filterNot { it in protected }
            .filterNot(::isSystemPackage)

        var requested = 0
        packages.forEach { packageName ->
            runCatching {
                activityManager.killBackgroundProcesses(packageName)
                requested++
            }
        }
        CleanupResult(requested)
    }

    private fun isSystemPackage(packageName: String): Boolean {
        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return true
        val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        return info.flags and systemFlags != 0
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

        val vpnIntent = Intent(VpnService.SERVICE_INTERFACE)
        packageManager.queryIntentServices(vpnIntent, PackageManager.MATCH_ALL)
            .mapNotNullTo(protected) { it.serviceInfo?.packageName }

        return protected
    }
}

private fun totalTrafficBytes(): Long {
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return -1L
    return rx + tx
}

private suspend fun measureLatencyMs(): Int = withContext(Dispatchers.IO) {
    val started = System.nanoTime()
    runCatching {
        Socket().use { socket -> socket.connect(InetSocketAddress("1.1.1.1", 443), 1200) }
        ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrDefault(0)
}
