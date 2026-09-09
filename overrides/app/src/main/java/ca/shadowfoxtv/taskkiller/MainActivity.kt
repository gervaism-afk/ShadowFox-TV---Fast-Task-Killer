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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShadowFoxTheme {
                ShadowFoxUpdateGate(applicationContext) {
                    PremiumDashboard(applicationContext)
                }
            }
        }
    }
}

private val ScreenBlack = Color(0xFF121212)
private val CardBlack = Color(0xFF121212)
private val NeonBlue = Color(0xFF00E5FF)
private val NeonBlueSoft = Color(0x3300E5FF)
private val Orange = Color(0xFFFF6D00)
private val White = Color(0xFFF7FBFF)
private val Muted = Color(0xFF9AA7B1)
private val Green = Color(0xFF7EC845)

@Composable
private fun ShadowFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonBlue,
            secondary = Orange,
            background = ScreenBlack,
            surface = CardBlack,
            onBackground = White,
            onSurface = White
        ),
        content = content
    )
}

@Composable
private fun PremiumDashboard(context: Context) {
    var ramPercent by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var runningApps by remember { mutableIntStateOf(runningProcessCount(context)) }
    var networkMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    val graph = remember { mutableStateListOf<Int>() }
    val optimizer = remember(context) { AppOptimizer(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            val before = totalTrafficBytes()
            delay(1000)
            val after = totalTrafficBytes()
            if (before >= 0L && after >= before) {
                networkMbps = ((after - before) * 8f / 1_000_000f).coerceAtLeast(0f)
            }
            pingMs = measureLatencyMs()
            if (pingMs > 0) {
                graph.add(pingMs)
                while (graph.size > 18) graph.removeAt(0)
            }
            ramPercent = memoryUsedPercent(context)
            runningApps = runningProcessCount(context)
        }
    }

    fun optimize() {
        if (working) return
        scope.launch {
            working = true
            optimizer.optimize()
            delay(450)
            ramPercent = memoryUsedPercent(context)
            runningApps = runningProcessCount(context)
            working = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBlack)
    ) {
        val sx = maxWidth / 960.dp
        val sy = maxHeight / 540.dp
        val scale = minOf(sx, sy)

        Box(
            modifier = Modifier
                .size(960.dp * scale, 540.dp * scale)
                .align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(960.dp, 540.dp)
                    .scale(scale)
                    .align(Alignment.Center)
                    .background(ScreenBlack)
            ) {
                CinematicBackdrop()

                Column(Modifier.offset(48.dp, 32.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("ShadowFox", color = White, fontSize = 32.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Spacer(Modifier.width(6.dp))
                        Text("TV", color = Orange, fontSize = 32.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    }
                    Text("www.shadowfoxtv.ca", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }

                Image(
                    painter = painterResource(R.drawable.shadowfox_logo),
                    contentDescription = "ShadowFox TV",
                    modifier = Modifier.offset(808.dp, 23.dp).size(92.dp)
                )

                PremiumCard(
                    modifier = Modifier.offset(48.dp, 142.dp).size(205.dp, 292.dp),
                    clickable = true,
                    onClick = { optimize() }
                ) {
                    Box(Modifier.fillMaxSize()) {
                        MagnifierIcon(Modifier.align(Alignment.TopCenter).offset(y = 21.dp).size(104.dp))
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 23.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SYSTEM SCAN", color = White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(7.dp))
                            Text("$runningApps ACTIVE PROCESSES", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            PremiumButton(if (working) "SCANNING..." else "SCAN NOW", 112.dp, !working) { optimize() }
                        }
                    }
                }

                PremiumCard(
                    modifier = Modifier.offset(274.dp, 116.dp).size(305.dp, 344.dp),
                    clickable = true,
                    onClick = { optimize() },
                    emphasized = true
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 17.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("RAM BOOSTER", color = White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                            Text("REAL-TIME MEMORY OPTIMIZATION", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier.align(Alignment.Center).offset(y = (-9).dp).size(245.dp, 205.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            SpeedometerGauge(ramPercent, Modifier.fillMaxSize())
                            Column(
                                modifier = Modifier.offset(y = 31.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("${ramPercent.toInt()}%", color = White, fontSize = 41.sp, fontWeight = FontWeight.Black)
                                Text("RAM IN USE", color = White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.1.sp)
                            }
                        }

                        PremiumButton(
                            text = if (working) "BOOSTING..." else "BOOST",
                            width = 160.dp,
                            enabled = !working,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                        ) { optimize() }
                    }
                }

                PremiumCard(
                    modifier = Modifier.offset(603.dp, 142.dp).size(309.dp, 143.dp),
                    clickable = true,
                    onClick = { optimize() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 21.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BroomIcon(Modifier.size(67.dp))
                        Spacer(Modifier.width(15.dp))
                        Column {
                            Text("CACHE CLEANER", color = White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Text("Remove background apps\nand reclaim memory.", color = Muted, fontSize = 9.sp, lineHeight = 13.sp)
                            Spacer(Modifier.height(10.dp))
                            PremiumButton(if (working) "CLEANING..." else "CLEAN NOW", 125.dp, !working) { optimize() }
                        }
                    }
                }

                PremiumCard(
                    modifier = Modifier.offset(603.dp, 300.dp).size(309.dp, 140.dp),
                    clickable = false,
                    onClick = {}
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 21.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkIcon(Modifier.size(65.dp))
                        Spacer(Modifier.width(15.dp))
                        Column {
                            Text("NETWORK MONITOR", color = White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            Text(
                                if (pingMs > 0) "${String.format("%.1f", networkMbps)} Mbps  •  ${pingMs} ms" else "LIVE CONNECTION MONITOR",
                                color = Muted,
                                fontSize = 9.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            NetworkBars(graph, Modifier.size(185.dp, 48.dp))
                        }
                    }
                }

                LightningBolt(Modifier.offset(438.dp, 465.dp).size(52.dp))
                AndroidTvBadge(Modifier.offset(768.dp, 468.dp).size(144.dp, 45.dp))
            }
        }
    }
}

@Composable
private fun PremiumCard(
    modifier: Modifier,
    clickable: Boolean,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val border = if (focused) BorderStroke(2.dp, NeonBlue) else BorderStroke(1.dp, NeonBlueSoft)
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "cardFocus")

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (focused) 20.dp else if (emphasized) 7.dp else 2.dp,
                shape = shape,
                ambientColor = if (focused) NeonBlue else Color.Black,
                spotColor = if (focused) NeonBlue else Color.Black
            )
            .background(CardBlack, shape)
            .border(border, shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (clickable) Modifier.focusable().clickable(onClick = onClick) else Modifier)
    ) {
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .border(BorderStroke(1.dp, NeonBlue.copy(alpha = 0.45f)), RoundedCornerShape(9.dp))
            )
        }
        content()
    }
}

@Composable
private fun PremiumButton(
    text: String,
    width: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "buttonFocus")

    Box(
        modifier = modifier
            .width(width)
            .height(36.dp)
            .scale(scale)
            .shadow(
                elevation = if (focused) 18.dp else 3.dp,
                shape = shape,
                ambientColor = if (focused) NeonBlue else Color.Black,
                spotColor = if (focused) NeonBlue else Color.Black
            )
            .background(if (focused) NeonBlue else Color(0xFF07333A), shape)
            .border(BorderStroke(if (focused) 2.dp else 1.dp, if (focused) NeonBlue else NeonBlue.copy(alpha = 0.75f)), shape)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color.Black else White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SpeedometerGauge(percent: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(percent.coerceIn(0f, 100f), label = "ramGauge")

    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height * 0.79f)
        val radius = size.width * 0.39f
        val start = 180f
        val totalSweep = 180f
        val bounds = Size(radius * 2f, radius * 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)

        drawArc(
            color = Color(0xFF252525),
            startAngle = start,
            sweepAngle = totalSweep,
            useCenter = false,
            topLeft = topLeft,
            size = bounds,
            style = Stroke(18.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = Orange.copy(alpha = 0.22f),
            startAngle = start,
            sweepAngle = totalSweep * animated / 100f,
            useCenter = false,
            topLeft = topLeft,
            size = bounds,
            style = Stroke(25.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = Orange,
            startAngle = start,
            sweepAngle = totalSweep * animated / 100f,
            useCenter = false,
            topLeft = topLeft,
            size = bounds,
            style = Stroke(12.dp.toPx(), cap = StrokeCap.Round)
        )

        for (i in 0..10) {
            val angle = Math.toRadians((start + totalSweep * i / 10f).toDouble())
            val p1 = Offset(
                center.x + cos(angle).toFloat() * radius * 0.72f,
                center.y + sin(angle).toFloat() * radius * 0.72f
            )
            val p2 = Offset(
                center.x + cos(angle).toFloat() * radius * 0.9f,
                center.y + sin(angle).toFloat() * radius * 0.9f
            )
            drawLine(White.copy(alpha = 0.65f), p1, p2, if (i % 5 == 0) 3.dp.toPx() else 1.5.dp.toPx(), StrokeCap.Round)
        }

        val needleAngle = Math.toRadians((start + totalSweep * animated / 100f).toDouble())
        val needleEnd = Offset(
            center.x + cos(needleAngle).toFloat() * radius * 0.74f,
            center.y + sin(needleAngle).toFloat() * radius * 0.74f
        )
        drawLine(NeonBlue.copy(alpha = 0.22f), center, needleEnd, 15.dp.toPx(), StrokeCap.Round)
        drawLine(NeonBlue, center, needleEnd, 5.dp.toPx(), StrokeCap.Round)
        drawCircle(NeonBlue.copy(alpha = 0.30f), 16.dp.toPx(), center)
        drawCircle(NeonBlue, 10.dp.toPx(), center)
        drawCircle(Color(0xFF051014), 4.dp.toPx(), center)
    }
}

@Composable
private fun MagnifierIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * 0.43f, size.height * 0.43f)
        val r = size.minDimension * 0.28f
        drawCircle(NeonBlue.copy(alpha = 0.08f), r * 1.48f, c)
        drawCircle(NeonBlue.copy(alpha = 0.22f), r * 1.25f, c, style = Stroke(9.dp.toPx()))
        drawCircle(NeonBlue, r, c, style = Stroke(6.dp.toPx()))
        val a = Math.toRadians(44.0)
        val start = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
        drawLine(NeonBlue, start, Offset(size.width * 0.84f, size.height * 0.86f), 11.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun BroomIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawLine(NeonBlue, Offset(size.width * .62f, size.height * .08f), Offset(size.width * .54f, size.height * .55f), 7.dp.toPx(), StrokeCap.Round)
        val head = Path().apply {
            moveTo(size.width * .23f, size.height * .53f)
            lineTo(size.width * .72f, size.height * .53f)
            lineTo(size.width * .82f, size.height * .94f)
            lineTo(size.width * .10f, size.height * .94f)
            close()
        }
        drawPath(head, NeonBlue)
        for (i in 1..4) {
            val x = size.width * (.18f + i * .13f)
            drawLine(CardBlack, Offset(x, size.height * .59f), Offset(x - size.width * .05f, size.height * .9f), 2.dp.toPx())
        }
    }
}

@Composable
private fun NetworkIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * .5f, size.height * .73f)
        listOf(.16f, .29f, .42f).forEach { factor ->
            val r = size.minDimension * factor
            drawArc(NeonBlue, 205f, 130f, false, Offset(c.x-r,c.y-r), Size(r*2,r*2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        val tower = Path().apply {
            moveTo(c.x, c.y - size.height*.10f)
            lineTo(c.x - size.width*.12f, size.height*.95f)
            lineTo(c.x + size.width*.12f, size.height*.95f)
            close()
        }
        drawPath(tower, NeonBlue)
    }
}

@Composable
private fun NetworkBars(samples: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bars = 17
        val spacing = size.width / bars
        val sampleMax = max(1, samples.maxOrNull() ?: 1)
        for (i in 0 until bars) {
            val value = if (samples.isNotEmpty()) samples[i % samples.size].toFloat() / sampleMax else ((i * 37) % 100) / 100f
            val h = size.height * (0.18f + value * 0.74f)
            drawLine(if (i == bars - 3) Orange else NeonBlue, Offset(spacing*i + spacing*.5f, size.height), Offset(spacing*i + spacing*.5f, size.height-h), 4.dp.toPx())
        }
    }
}

@Composable
private fun LightningBolt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width*.57f, 0f)
            lineTo(size.width*.20f, size.height*.54f)
            lineTo(size.width*.48f, size.height*.54f)
            lineTo(size.width*.34f, size.height)
            lineTo(size.width*.82f, size.height*.39f)
            lineTo(size.width*.54f, size.height*.39f)
            close()
        }
        drawPath(p, NeonBlue)
        drawPath(p, NeonBlue.copy(alpha=.22f), style=Stroke(7.dp.toPx()))
    }
}

@Composable
private fun AndroidTvBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(CardBlack, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, NeonBlueSoft), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("●", color = Green, fontSize = 20.sp)
        Spacer(Modifier.width(7.dp))
        Column {
            Text("FOR", color = Muted, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text("ANDROID TV", color = White, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CinematicBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(ScreenBlack)
        val cyan = NeonBlue.copy(alpha = 0.035f)
        val orange = Orange.copy(alpha = 0.025f)
        drawCircle(cyan, size.width * .31f, Offset(size.width*.48f, size.height*.45f))
        drawCircle(orange, size.width * .20f, Offset(size.width*.78f, size.height*.22f))
        val line = NeonBlue.copy(alpha = .06f)
        val step = size.width / 10f
        for (i in -1..10) {
            drawLine(line, Offset(i*step, 0f), Offset((i+2)*step, size.height), 1.dp.toPx())
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
        val packages = activityManager.runningAppProcesses.orEmpty()
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

        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        ).mapNotNullTo(protected) { it.activityInfo?.packageName }

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
