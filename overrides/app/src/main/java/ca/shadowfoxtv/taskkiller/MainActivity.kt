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
                    ReferenceDashboard(applicationContext)
                }
            }
        }
    }
}

private val Bg = Color(0xFF06101C)
private val Card = Color(0xFF0A2235)
private val Card2 = Color(0xFF0D2C43)
private val Cyan = Color(0xFF19E8F8)
private val CyanSoft = Color(0xFF42D6EB)
private val CyanLine = Color(0xFF2A7790)
private val Orange = Color(0xFFFF8B25)
private val White = Color(0xFFF5FBFF)
private val Muted = Color(0xFFA7B8C5)
private val Green = Color(0xFF85C742)

@Composable
private fun ShadowFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            secondary = Orange,
            background = Bg,
            surface = Card,
            onBackground = White,
            onSurface = White
        ),
        content = content
    )
}

@Composable
private fun ReferenceDashboard(context: Context) {
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
                while (graph.size > 16) graph.removeAt(0)
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
            .background(Bg)
    ) {
        val sx = maxWidth / 960.dp
        val sy = maxHeight / 540.dp
        val scale = minOf(sx, sy)
        val canvasW = 960.dp * scale
        val canvasH = 540.dp * scale

        Box(
            modifier = Modifier
                .size(canvasW, canvasH)
                .align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(960.dp, 540.dp)
                    .scale(scale)
                    .align(Alignment.Center)
            ) {
                CircuitBackground()

                // Top-left title, exactly as the reference composition.
                Column(
                    modifier = Modifier.offset(54.dp, 42.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "ShadowFox",
                            color = White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "TV",
                            color = Orange,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    Text(
                        "www.shadowfoxtv.ca",
                        color = Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Image(
                    painter = painterResource(R.drawable.shadowfox_logo),
                    contentDescription = "ShadowFox TV",
                    modifier = Modifier
                        .offset(785.dp, 25.dp)
                        .size(105.dp)
                )

                // LEFT: narrow System Scan card.
                RefCard(
                    modifier = Modifier
                        .offset(50.dp, 145.dp)
                        .size(205.dp, 295.dp),
                    borderColor = CyanLine
                ) {
                    Box(Modifier.fillMaxSize()) {
                        MagnifierIcon(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = 20.dp)
                                .size(112.dp)
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 25.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("System Scan", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (working) "Scanning system..." else "Scan loaded system scan.",
                                color = Muted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(13.dp))
                            ReferenceButton("Learn More", enabled = !working, onClick = { optimize() })
                        }
                    }
                }

                // CENTER: larger RAM Booster card.
                RefCard(
                    modifier = Modifier
                        .offset(275.dp, 122.dp)
                        .size(300.dp, 335.dp),
                    borderColor = Cyan,
                    strong = true
                ) {
                    Box(Modifier.fillMaxSize()) {
                        RamDial(
                            ramPercent,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = 24.dp)
                                .size(210.dp)
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("RAM Booster", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(13.dp))
                            ReferenceButton(
                                if (working) "Boosting..." else "Boost",
                                width = 155.dp,
                                enabled = !working,
                                onClick = { optimize() }
                            )
                        }
                    }
                }

                // RIGHT TOP: Cache Cleaner.
                RefCard(
                    modifier = Modifier
                        .offset(600.dp, 142.dp)
                        .size(310.dp, 143.dp),
                    borderColor = CyanLine
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 23.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BroomIcon(Modifier.size(73.dp))
                        Spacer(Modifier.width(17.dp))
                        Column {
                            Text("Cache Cleaner", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Remove cache machines\nand cache.",
                                color = Muted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                            Spacer(Modifier.height(11.dp))
                            ReferenceButton("Learn More", width = 125.dp, enabled = !working, onClick = { optimize() })
                        }
                    }
                }

                // RIGHT BOTTOM: Network Monitor.
                RefCard(
                    modifier = Modifier
                        .offset(600.dp, 300.dp)
                        .size(310.dp, 140.dp),
                    borderColor = CyanLine
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 23.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkIcon(Modifier.size(69.dp))
                        Spacer(Modifier.width(17.dp))
                        Column {
                            Text("Network Monitor", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Connect intent monitor", color = Muted, fontSize = 10.sp)
                            Spacer(Modifier.height(9.dp))
                            ReferenceBars(
                                samples = graph,
                                modifier = Modifier.size(185.dp, 48.dp)
                            )
                        }
                    }
                }

                DecorativeAppTiles(
                    modifier = Modifier.offset(51.dp, 467.dp)
                )

                LightningBolt(
                    modifier = Modifier
                        .offset(435.dp, 460.dp)
                        .size(58.dp)
                )

                AndroidTvBadge(
                    modifier = Modifier
                        .offset(760.dp, 465.dp)
                        .size(150.dp, 48.dp)
                )
            }
        }
    }
}

@Composable
private fun RefCard(
    modifier: Modifier,
    borderColor: Color,
    strong: Boolean = false,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(if (strong) 14.dp else 12.dp)
    Box(
        modifier = modifier
            .background(if (strong) Card2 else Card, shape)
            .border(if (strong) 3.dp else 1.5.dp, borderColor.copy(alpha = if (strong) 0.95f else 0.65f), shape)
    ) {
        if (strong) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(11.dp))
            )
        }
        content()
    }
}

@Composable
private fun ReferenceButton(
    text: String,
    width: androidx.compose.ui.unit.Dp = 105.dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(width)
            .height(33.dp)
            .scale(if (focused) 1.06f else 1f)
            .background(if (focused) White else Cyan, RoundedCornerShape(18.dp))
            .border(1.dp, if (focused) Cyan else CyanSoft, RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .then(
                if (enabled) Modifier.referenceClickable(onClick) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color(0xFF083044),
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}

private fun Modifier.referenceClickable(onClick: () -> Unit): Modifier =
    androidx.compose.foundation.clickable(onClick = onClick)

@Composable
private fun MagnifierIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * 0.43f, size.height * 0.43f)
        val r = size.minDimension * 0.28f
        drawCircle(Color(0xFF113549), r * 1.45f, c)
        drawCircle(Cyan.copy(alpha = 0.35f), r * 1.26f, c, style = Stroke(8.dp.toPx()))
        drawCircle(Cyan, r, c, style = Stroke(7.dp.toPx()))
        drawCircle(Cyan.copy(alpha = 0.13f), r * 0.72f, c)
        drawArc(CyanSoft, 20f, 120f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        val a = Math.toRadians(44.0)
        val start = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
        val end = Offset(size.width * 0.84f, size.height * 0.86f)
        drawLine(Cyan, start, end, 13.dp.toPx(), StrokeCap.Round)
        drawLine(CyanSoft, start, end, 5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun RamDial(percent: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(percent.coerceIn(0f, 100f), label = "ram")
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height * 0.58f)
        val radius = size.minDimension * 0.37f
        val startAngle = 155f
        val sweepTotal = 230f
        drawArc(
            color = Color(0xFF173C50),
            startAngle = startAngle,
            sweepAngle = sweepTotal,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(18.dp.toPx(), cap = StrokeCap.Butt)
        )
        drawArc(
            color = Cyan,
            startAngle = startAngle,
            sweepAngle = sweepTotal * 0.68f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(14.dp.toPx(), cap = StrokeCap.Butt)
        )
        drawArc(
            color = Orange,
            startAngle = startAngle + sweepTotal * 0.68f,
            sweepAngle = sweepTotal * 0.32f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(14.dp.toPx(), cap = StrokeCap.Butt)
        )

        for (i in 0..10) {
            val angle = Math.toRadians((startAngle + sweepTotal * i / 10f).toDouble())
            val p1 = Offset(
                center.x + cos(angle).toFloat() * radius * 0.73f,
                center.y + sin(angle).toFloat() * radius * 0.73f
            )
            val p2 = Offset(
                center.x + cos(angle).toFloat() * radius * 0.9f,
                center.y + sin(angle).toFloat() * radius * 0.9f
            )
            drawLine(White.copy(alpha = 0.7f), p1, p2, 1.5.dp.toPx())
        }

        val needleAngle = Math.toRadians((startAngle + sweepTotal * animated / 100f).toDouble())
        val needleEnd = Offset(
            center.x + cos(needleAngle).toFloat() * radius * 0.72f,
            center.y + sin(needleAngle).toFloat() * radius * 0.72f
        )
        drawLine(CyanSoft, center, needleEnd, 4.dp.toPx(), StrokeCap.Round)
        drawCircle(Cyan, 10.dp.toPx(), center)
        drawCircle(Color(0xFF0D344C), 5.dp.toPx(), center)
    }
    Box(modifier = modifier) {
        Text(
            "DIGITAL",
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-25).dp),
            color = Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BroomIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stem = Cyan
        drawLine(stem, Offset(size.width * .62f, size.height * .08f), Offset(size.width * .54f, size.height * .55f), 7.dp.toPx(), StrokeCap.Round)
        val head = Path().apply {
            moveTo(size.width * .23f, size.height * .53f)
            lineTo(size.width * .72f, size.height * .53f)
            lineTo(size.width * .82f, size.height * .94f)
            lineTo(size.width * .10f, size.height * .94f)
            close()
        }
        drawPath(head, Cyan)
        for (i in 1..4) {
            val x = size.width * (.18f + i * .13f)
            drawLine(Card2, Offset(x, size.height * .58f), Offset(x - size.width * .05f, size.height * .9f), 2.dp.toPx())
        }
    }
}

@Composable
private fun NetworkIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * .5f, size.height * .73f)
        val r1 = size.minDimension * .16f
        drawArc(Cyan, 205f, 130f, false, topLeft = Offset(c.x - r1, c.y - r1), size = Size(r1*2,r1*2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        val r2 = size.minDimension * .29f
        drawArc(Cyan, 205f, 130f, false, topLeft = Offset(c.x - r2, c.y - r2), size = Size(r2*2,r2*2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        val r3 = size.minDimension * .42f
        drawArc(Cyan, 205f, 130f, false, topLeft = Offset(c.x - r3, c.y - r3), size = Size(r3*2,r3*2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        val tower = Path().apply {
            moveTo(c.x, c.y - size.height*.10f)
            lineTo(c.x - size.width*.12f, size.height*.95f)
            lineTo(c.x + size.width*.12f, size.height*.95f)
            close()
        }
        drawPath(tower, Cyan)
        drawCircle(Card, 4.dp.toPx(), c)
    }
}

@Composable
private fun ReferenceBars(samples: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bars = 17
        val spacing = size.width / bars
        val sampleMax = max(1, samples.maxOrNull() ?: 1)
        for (i in 0 until bars) {
            val v = if (samples.isNotEmpty()) samples[i % samples.size].toFloat() / sampleMax else ((i * 37) % 100) / 100f
            val h = size.height * (0.2f + v * 0.72f)
            val color = if (i == bars - 3) Orange else CyanSoft
            drawLine(color, Offset(spacing*i + spacing*.5f, size.height), Offset(spacing*i + spacing*.5f, size.height-h), 4.dp.toPx(), StrokeCap.Butt)
        }
    }
}

@Composable
private fun DecorativeAppTiles(modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TinyTile("NETFLIX", Color.White, Color(0xFFE21D2E), 39.dp)
            TinyTile("▶", Color.White, Color(0xFFE52D27), 39.dp)
            TinyTile("TV", Color.White, Color(0xFFE23B2D), 39.dp)
            TinyTile("tv", Color.White, Color(0xFF2B313B), 39.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TinyTile("Disney+", Color(0xFF182F70), White, 39.dp)
            TinyTile("▣", Color(0xFF79A7FF), White, 39.dp)
            TinyTile("hulu", Color(0xFF70D43A), Color(0xFF122014), 39.dp)
            TinyTile("⚙", Color(0xFF364657), White, 39.dp)
        }
    }
}

@Composable
private fun TinyTile(text: String, bg: Color, fg: Color, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(width, 22.dp)
            .background(bg, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = if (text.length > 4) 6.sp else 9.sp, fontWeight = FontWeight.Bold)
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
        drawPath(p, Cyan)
        drawPath(p, Cyan.copy(alpha=.35f), style=Stroke(5.dp.toPx()))
    }
}

@Composable
private fun AndroidTvBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xFF253743), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF53707C), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("♟", color = Green, fontSize = 25.sp)
        Spacer(Modifier.width(7.dp))
        Column {
            Text("For", color = White, fontSize = 8.sp)
            Text("Android TV", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CircuitBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Bg)
        val glow = Color(0xFF0D5E77).copy(alpha = .18f)
        drawRect(glow, topLeft = Offset(size.width*.23f, 0f), size = Size(size.width*.48f, size.height))
        val line = Color(0xFF1B4C61).copy(alpha=.28f)
        val points = listOf(
            listOf(Offset(size.width*.36f,0f), Offset(size.width*.43f,size.height*.25f), Offset(size.width*.40f,size.height*.48f)),
            listOf(Offset(size.width*.50f,0f), Offset(size.width*.47f,size.height*.30f), Offset(size.width*.54f,size.height*.52f)),
            listOf(Offset(size.width*.62f,0f), Offset(size.width*.58f,size.height*.21f), Offset(size.width*.65f,size.height*.43f))
        )
        points.forEach { p -> for (i in 0 until p.lastIndex) drawLine(line,p[i],p[i+1],2.dp.toPx()) }
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
