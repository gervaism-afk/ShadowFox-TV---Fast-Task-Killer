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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
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
            MaterialTheme(
                colorScheme = darkColorScheme(background = BG, surface = PANEL),
            ) {
                ShadowFoxUpdateGate(applicationContext) {
                    MasterDashboard(applicationContext)
                }
            }
        }
    }
}

private val BG = Color(0xFF03111D)
private val PANEL = Color(0xE60A2030)
private val CYAN = Color(0xFF00E5FF)
private val BLUE = Color(0xFF08AEEA)
private val ORANGE = Color(0xFFFF7A00)
private val WHITE = Color(0xFFF7FBFF)
private val MUTED = Color(0xFF9AABB8)
private val GREEN = Color(0xFF77C943)

@Composable
private fun MasterDashboard(context: Context) {
    var ram by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var apps by remember { mutableIntStateOf(runningProcessCount(context)) }
    var mbps by remember { mutableFloatStateOf(0f) }
    var ping by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val graph = remember { mutableStateListOf<Int>() }
    val optimizer = remember { AppOptimizer(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            val a = totalTrafficBytes()
            delay(1000)
            val b = totalTrafficBytes()
            if (a >= 0 && b >= a) mbps = (b - a) * 8f / 1_000_000f
            ping = measureLatencyMs()
            if (ping > 0) {
                graph.add(ping)
                while (graph.size > 18) graph.removeAt(0)
            }
            ram = memoryUsedPercent(context)
            apps = runningProcessCount(context)
        }
    }

    fun clean() {
        if (busy) return
        scope.launch {
            busy = true
            optimizer.optimize()
            delay(400)
            ram = memoryUsedPercent(context)
            apps = runningProcessCount(context)
            busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val scale = minOf(maxWidth / 960.dp, maxHeight / 540.dp)
        Box(
            Modifier
                .size(960.dp * scale, 540.dp * scale)
                .align(Alignment.Center)
        ) {
            Box(
                Modifier
                    .size(960.dp, 540.dp)
                    .scale(scale)
                    .align(Alignment.Center)
                    .background(BG)
            ) {
                MasterBackdrop()

                Column(Modifier.offset(44.dp, 42.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("ShadowFox", color = WHITE, fontSize = 29.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Spacer(Modifier.width(4.dp))
                        Text("TV", color = ORANGE, fontSize = 29.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    }
                    Text("www.shadowfoxtv.ca", color = MUTED, fontSize = 9.sp)
                }

                Image(
                    painter = painterResource(R.drawable.shadowfox_logo),
                    contentDescription = "ShadowFox TV",
                    modifier = Modifier.offset(804.dp, 26.dp).size(100.dp)
                )

                GlowCard(Modifier.offset(45.dp, 145.dp).size(205.dp, 265.dp), onClick = { clean() }) {
                    Box(Modifier.fillMaxSize()) {
                        ScanDial(Modifier.align(Alignment.TopCenter).padding(top = 26.dp).size(135.dp))
                        Column(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SYSTEM SCAN", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text("Scan loaded system apps.", color = MUTED, fontSize = 8.sp)
                            Spacer(Modifier.height(10.dp))
                            MasterButton(if (busy) "SCANNING..." else "SCAN NOW", 105.dp, !busy) { clean() }
                        }
                    }
                }

                GlowCard(
                    modifier = Modifier.offset(268.dp, 116.dp).size(300.dp, 323.dp),
                    onClick = { clean() },
                    hero = true
                ) {
                    Box(Modifier.fillMaxSize()) {
                        RamGauge(
                            ram,
                            Modifier.align(Alignment.TopCenter).padding(top = 22.dp).size(240.dp)
                        )
                        Column(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("RAM BOOSTER", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(10.dp))
                            MasterButton(if (busy) "BOOSTING..." else "BOOST", 150.dp, !busy) { clean() }
                        }
                    }
                }

                GlowCard(Modifier.offset(592.dp, 133.dp).size(320.dp, 133.dp), onClick = { clean() }) {
                    Row(Modifier.fillMaxSize().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Broom(Modifier.size(64.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("CACHE CLEANER", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("Remove cache machines\nand cache.", color = MUTED, fontSize = 8.sp)
                            Spacer(Modifier.height(9.dp))
                            MasterButton(if (busy) "CLEANING..." else "CLEAN NOW", 105.dp, !busy) { clean() }
                        }
                    }
                }

                GlowCard(Modifier.offset(592.dp, 282.dp).size(320.dp, 128.dp), onClick = {}) {
                    Row(Modifier.fillMaxSize().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetworkIcon(Modifier.size(62.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("NETWORK MONITOR", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text(
                                if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "Connect internet monitor",
                                color = MUTED,
                                fontSize = 8.sp
                            )
                            Spacer(Modifier.height(7.dp))
                            Bars(graph, Modifier.size(175.dp, 43.dp))
                        }
                    }
                }

                AppTiles(Modifier.offset(47.dp, 448.dp).size(205.dp, 42.dp))
                Bolt(Modifier.offset(438.dp, 452.dp).size(47.dp))
                AndroidBadge(Modifier.offset(780.dp, 449.dp).size(132.dp, 39.dp))
            }
        }
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
                ambientColor = CYAN,
                spotColor = CYAN
            )
            .background(Brush.verticalGradient(listOf(Color(0xEA092337), Color(0xED04131F))), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = CYAN.copy(alpha = if (focused) .72f else .28f),
                style = Stroke(if (focused) 2.5.dp.toPx() else 1.2.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
            )
        }
        content()
    }
}

@Composable
private fun MasterButton(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(width)
            .height(32.dp)
            .scale(if (focused) 1.08f else 1f)
            .shadow(
                elevation = if (focused) 20.dp else 9.dp,
                shape = shape,
                clip = false,
                ambientColor = CYAN,
                spotColor = CYAN
            )
            .background(Brush.horizontalGradient(listOf(Color(0xFF16E7F4), Color(0xFF08A9D4))), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF05202A), fontSize = 10.sp, fontWeight = FontWeight.Black)
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
        Column(
            Modifier.align(Alignment.Center).offset(y = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
private fun AndroidBadge(modifier: Modifier) {
    Row(
        modifier.background(Color(0xDD14212A), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("●", color = GREEN, fontSize = 17.sp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text("For", color = MUTED, fontSize = 7.sp)
            Text("ANDROID TV", color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AppTiles(modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val labels = listOf("NETFLIX", "YouTube", "Prime", "tv", "Disney+", "TV", "hulu", "⚙")
        labels.forEachIndexed { i, label ->
            Box(
                Modifier
                    .padding(end = 3.dp)
                    .size(if (i < 4) 23.dp else 22.dp, 18.dp)
                    .background(if (i == 7) Color(0xFF26333C) else Color(0xFFE9EEF2), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (i == 7) WHITE else Color(0xFF182028), fontSize = if (label.length > 5) 4.sp else 6.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MasterBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(BG)
        drawCircle(CYAN.copy(.025f), size.width * .38f, Offset(size.width * .48f, size.height * .45f))
        val p = Path().apply {
            moveTo(size.width * .43f, 0f)
            lineTo(size.width * .39f, size.height * .18f)
            lineTo(size.width * .46f, size.height * .18f)
            lineTo(size.width * .41f, size.height * .38f)
        }
        drawPath(p, CYAN.copy(.055f), style = Stroke(4.dp.toPx()))
    }
}

private fun memoryUsedPercent(context: Context): Float {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    return if (info.totalMem <= 0) 0f else ((info.totalMem - info.availMem).toDouble() / info.totalMem * 100).toFloat().coerceIn(0f, 100f)
}

private fun runningProcessCount(context: Context): Int {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.runningAppProcesses.orEmpty().size
}

private class AppOptimizer(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    suspend fun optimize() = withContext(Dispatchers.IO) {
        val protected = protectedPackages()
        activityManager.runningAppProcesses.orEmpty()
            .flatMap { it.pkgList?.toList().orEmpty() }
            .distinct()
            .filter { it != context.packageName }
            .filterNot { it in protected }
            .filterNot(::isSystemPackage)
            .forEach { runCatching { activityManager.killBackgroundProcesses(it) } }
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
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        ).mapNotNullTo(protected) { it.activityInfo?.packageName }
        packageManager.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), PackageManager.MATCH_ALL)
            .mapNotNullTo(protected) { it.serviceInfo?.packageName }
        return protected
    }
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
