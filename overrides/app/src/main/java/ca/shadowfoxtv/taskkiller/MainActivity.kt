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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

        // True edge-to-edge TV canvas. No app/status/navigation header strip.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

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
private val PanelTop = Color(0xE61E1E1E)
private val PanelBottom = Color(0xE60D0D0D)
private val DeepBlack = Color(0xFF07090B)
private val NeonBlue = Color(0xFF00E5FF)
private val ElectricBlue = Color(0xFF00AEEF)
private val ArcOrange = Color(0xFFFF7A00)
private val ArcRed = Color(0xFFFF3D1F)
private val White = Color(0xFFF6FAFF)
private val Muted = Color(0xFF98A5B0)
private val Green = Color(0xFF78D34B)

@Composable
private fun ShadowFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonBlue,
            secondary = ArcOrange,
            background = ScreenBlack,
            surface = PanelBottom,
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
        val uiScale = minOf(sx, sy)

        Box(
            modifier = Modifier
                .size(960.dp * uiScale, 540.dp * uiScale)
                .align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(960.dp, 540.dp)
                    .scale(uiScale)
                    .align(Alignment.Center)
                    .background(ScreenBlack)
            ) {
                CircuitDepthBackdrop()

                // Brand header floats directly on the root canvas — no structural bar.
                Column(Modifier.offset(44.dp, 26.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "ShadowFox",
                            color = White,
                            fontSize = 31.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "TV",
                            color = ArcOrange,
                            fontSize = 31.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    Text(
                        "www.shadowfoxtv.ca",
                        color = Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Image(
                    painter = painterResource(R.drawable.shadowfox_logo),
                    contentDescription = "ShadowFox TV",
                    modifier = Modifier.offset(810.dp, 18.dp).size(88.dp)
                )

                // LEFT — SYSTEM SCAN
                NeonGlassCard(
                    modifier = Modifier.offset(42.dp, 132.dp).size(215.dp, 324.dp),
                    onClick = { optimize() }
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SYSTEM SCAN", color = White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(3.dp))
                            Text("LIVE PROCESS ANALYSIS", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        MagnifierIcon(
                            Modifier
                                .align(Alignment.Center)
                                .offset(y = (-10).dp)
                                .size(122.dp)
                        )

                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "$runningApps ACTIVE PROCESSES",
                                color = Muted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(12.dp))
                            NeonPillButton(
                                text = if (working) "SCANNING..." else "SCAN NOW",
                                width = 130.dp,
                                enabled = !working
                            ) { optimize() }
                        }
                    }
                }

                // CENTER — RAM BOOSTER hero card
                NeonGlassCard(
                    modifier = Modifier.offset(278.dp, 108.dp).size(322.dp, 372.dp),
                    onClick = { optimize() },
                    hero = true
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("RAM BOOSTER", color = White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(2.dp))
                            Text("REAL-TIME MEMORY OPTIMIZATION", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        GaugeContainer(
                            percent = ramPercent,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = (-10).dp)
                                .size(270.dp, 238.dp)
                        )

                        NeonPillButton(
                            text = if (working) "BOOSTING..." else "BOOST",
                            width = 190.dp,
                            enabled = !working,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 22.dp)
                        ) { optimize() }
                    }
                }

                // RIGHT TOP — CACHE CLEANER
                NeonGlassCard(
                    modifier = Modifier.offset(622.dp, 132.dp).size(296.dp, 150.dp),
                    onClick = { optimize() }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BroomIcon(Modifier.size(70.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("CACHE CLEANER", color = White, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            Text(
                                "Remove background apps\nand reclaim memory.",
                                color = Muted,
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            NeonPillButton(
                                text = if (working) "CLEANING..." else "CLEAN NOW",
                                width = 128.dp,
                                enabled = !working
                            ) { optimize() }
                        }
                    }
                }

                // RIGHT BOTTOM — NETWORK MONITOR
                NeonGlassCard(
                    modifier = Modifier.offset(622.dp, 300.dp).size(296.dp, 156.dp),
                    onClick = {}
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkIcon(Modifier.size(67.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("NETWORK MONITOR", color = White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                            Text(
                                if (pingMs > 0) "${String.format("%.1f", networkMbps)} Mbps  •  ${pingMs} ms" else "LIVE CONNECTION MONITOR",
                                color = Muted,
                                fontSize = 9.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            NetworkBars(graph, Modifier.size(178.dp, 49.dp))
                        }
                    }
                }

                LightningBolt(Modifier.offset(440.dp, 486.dp).size(50.dp))
                AndroidTvBadge(Modifier.offset(774.dp, 480.dp).size(144.dp, 44.dp))
            }
        }
    }
}

@Composable
private fun NeonGlassCard(
    modifier: Modifier,
    onClick: () -> Unit,
    hero: Boolean = false,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (hero) 20.dp else 16.dp)
    val focusScale by animateFloatAsState(if (focused) 1.055f else 1f, label = "cardFocus")
    val glow = if (focused) NeonBlue else NeonBlue.copy(alpha = if (hero) 0.40f else 0.24f)

    Box(
        modifier = modifier
            .scale(focusScale)
            .shadow(
                elevation = if (focused) 28.dp else if (hero) 15.dp else 10.dp,
                shape = shape,
                clip = false,
                ambientColor = glow,
                spotColor = glow
            )
            .background(
                brush = Brush.verticalGradient(listOf(PanelTop, PanelBottom)),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        // Soft internal bloom gives the card depth without a flat outline.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = NeonBlue.copy(alpha = if (focused) 0.055f else 0.024f),
                radius = size.minDimension * 0.78f,
                center = Offset(size.width * 0.52f, size.height * 0.38f)
            )
        }
        content()
    }
}

@Composable
private fun GaugeContainer(percent: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = shape,
                clip = false,
                ambientColor = NeonBlue.copy(alpha = 0.45f),
                spotColor = NeonBlue.copy(alpha = 0.45f)
            )
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xF20B1014), Color(0xF2050709))
                ),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        AutomotiveGauge(percent, Modifier.fillMaxSize().padding(10.dp))
    }
}

@Composable
private fun AutomotiveGauge(percent: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(percent.coerceIn(0f, 100f), label = "automotiveGauge")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.55f)
            val radius = size.minDimension * 0.41f
            val startAngle = 145f
            val totalSweep = 250f
            val arcSize = Size(radius * 2f, radius * 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)

            // Recessed mechanical ring.
            drawCircle(
                color = Color.Black.copy(alpha = 0.50f),
                radius = radius * 1.16f,
                center = center
            )
            drawCircle(
                color = NeonBlue.copy(alpha = 0.09f),
                radius = radius * 1.10f,
                center = center,
                style = Stroke(10.dp.toPx())
            )

            // Thick inactive speedometer track.
            drawArc(
                color = Color(0xFF242A2F),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(24.dp.toPx(), cap = StrokeCap.Round)
            )

            // Neon blue -> orange/red active gradient built from small arc segments.
            val activeSweep = totalSweep * animated / 100f
            val segments = 64
            for (i in 0 until segments) {
                val segmentStartFraction = i / segments.toFloat()
                val segmentEndFraction = (i + 1) / segments.toFloat()
                val segmentStartSweep = totalSweep * segmentStartFraction
                if (segmentStartSweep >= activeSweep) break
                val segmentEndSweep = minOf(totalSweep * segmentEndFraction, activeSweep)
                val t = segmentStartFraction
                val color = if (t < 0.62f) {
                    lerp(NeonBlue, ArcOrange, t / 0.62f)
                } else {
                    lerp(ArcOrange, ArcRed, (t - 0.62f) / 0.38f)
                }
                drawArc(
                    color = color.copy(alpha = 0.18f),
                    startAngle = startAngle + segmentStartSweep,
                    sweepAngle = (segmentEndSweep - segmentStartSweep) + 1.1f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(34.dp.toPx(), cap = StrokeCap.Butt)
                )
                drawArc(
                    color = color,
                    startAngle = startAngle + segmentStartSweep,
                    sweepAngle = (segmentEndSweep - segmentStartSweep) + 1.1f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(17.dp.toPx(), cap = StrokeCap.Butt)
                )
            }

            // Automotive-style inner tick marks.
            for (i in 0..20) {
                val angleDegrees = startAngle + totalSweep * i / 20f
                val angle = Math.toRadians(angleDegrees.toDouble())
                val major = i % 5 == 0
                val inner = if (major) 0.68f else 0.74f
                val outer = 0.87f
                val p1 = Offset(
                    center.x + cos(angle).toFloat() * radius * inner,
                    center.y + sin(angle).toFloat() * radius * inner
                )
                val p2 = Offset(
                    center.x + cos(angle).toFloat() * radius * outer,
                    center.y + sin(angle).toFloat() * radius * outer
                )
                drawLine(
                    color = White.copy(alpha = if (major) 0.82f else 0.40f),
                    start = p1,
                    end = p2,
                    strokeWidth = if (major) 3.dp.toPx() else 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Sharp needle with strong cyan glow.
            val needleAngle = Math.toRadians((startAngle + activeSweep).toDouble())
            val needleEnd = Offset(
                center.x + cos(needleAngle).toFloat() * radius * 0.68f,
                center.y + sin(needleAngle).toFloat() * radius * 0.68f
            )
            val backAngle = Math.toRadians((startAngle + activeSweep + 180f).toDouble())
            val needleTail = Offset(
                center.x + cos(backAngle).toFloat() * radius * 0.12f,
                center.y + sin(backAngle).toFloat() * radius * 0.12f
            )

            drawLine(NeonBlue.copy(alpha = 0.20f), needleTail, needleEnd, 16.dp.toPx(), StrokeCap.Round)
            drawLine(NeonBlue, needleTail, needleEnd, 4.dp.toPx(), StrokeCap.Round)
            drawCircle(NeonBlue.copy(alpha = 0.24f), 17.dp.toPx(), center)
            drawCircle(NeonBlue, 10.dp.toPx(), center)
            drawCircle(DeepBlack, 4.dp.toPx(), center)
        }

        // High-contrast digital display beneath the needle hub.
        Box(
            modifier = Modifier
                .offset(y = 61.dp)
                .size(122.dp, 54.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(10.dp),
                    ambientColor = NeonBlue.copy(alpha = 0.35f),
                    spotColor = NeonBlue.copy(alpha = 0.35f)
                )
                .background(Color(0xF2070A0C), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${animated.toInt()}%",
                    color = White,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 31.sp
                )
                Text(
                    "RAM IN USE",
                    color = NeonBlue,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.0.sp
                )
            }
        }
    }
}

@Composable
private fun NeonPillButton(
    text: String,
    width: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val focusScale by animateFloatAsState(if (focused) 1.09f else 1f, label = "buttonFocus")
    val glow = if (focused) NeonBlue else NeonBlue.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .width(width)
            .height(38.dp)
            .scale(focusScale)
            .shadow(
                elevation = if (focused) 24.dp else 12.dp,
                shape = shape,
                clip = false,
                ambientColor = glow,
                spotColor = glow
            )
            .background(
                if (focused) {
                    Brush.horizontalGradient(listOf(Color(0xFF22F0FF), Color(0xFF00B7E5)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xE6124853), Color(0xE607242C)))
                },
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color(0xFF001013) else White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun MagnifierIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * 0.43f, size.height * 0.43f)
        val r = size.minDimension * 0.28f
        drawCircle(NeonBlue.copy(alpha = 0.08f), r * 1.58f, c)
        drawCircle(NeonBlue.copy(alpha = 0.17f), r * 1.30f, c, style = Stroke(12.dp.toPx()))
        drawCircle(NeonBlue, r, c, style = Stroke(6.dp.toPx()))
        val a = Math.toRadians(44.0)
        val start = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
        drawLine(NeonBlue.copy(alpha = 0.18f), start, Offset(size.width * 0.84f, size.height * 0.86f), 19.dp.toPx(), StrokeCap.Round)
        drawLine(NeonBlue, start, Offset(size.width * 0.84f, size.height * 0.86f), 9.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun BroomIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawLine(
            NeonBlue.copy(alpha = 0.18f),
            Offset(size.width * .62f, size.height * .08f),
            Offset(size.width * .54f, size.height * .55f),
            15.dp.toPx(),
            StrokeCap.Round
        )
        drawLine(
            NeonBlue,
            Offset(size.width * .62f, size.height * .08f),
            Offset(size.width * .54f, size.height * .55f),
            6.dp.toPx(),
            StrokeCap.Round
        )
        val head = Path().apply {
            moveTo(size.width * .23f, size.height * .53f)
            lineTo(size.width * .72f, size.height * .53f)
            lineTo(size.width * .82f, size.height * .94f)
            lineTo(size.width * .10f, size.height * .94f)
            close()
        }
        drawPath(head, NeonBlue.copy(alpha = 0.20f), style = Stroke(11.dp.toPx()))
        drawPath(head, NeonBlue)
        for (i in 1..4) {
            val x = size.width * (.18f + i * .13f)
            drawLine(DeepBlack, Offset(x, size.height * .59f), Offset(x - size.width * .05f, size.height * .9f), 2.dp.toPx())
        }
    }
}

@Composable
private fun NetworkIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width * .5f, size.height * .73f)
        listOf(.16f, .29f, .42f).forEach { factor ->
            val r = size.minDimension * factor
            drawArc(
                NeonBlue.copy(alpha = 0.18f),
                205f,
                130f,
                false,
                Offset(c.x - r, c.y - r),
                Size(r * 2, r * 2),
                style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                NeonBlue,
                205f,
                130f,
                false,
                Offset(c.x - r, c.y - r),
                Size(r * 2, r * 2),
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        val tower = Path().apply {
            moveTo(c.x, c.y - size.height * .10f)
            lineTo(c.x - size.width * .12f, size.height * .95f)
            lineTo(c.x + size.width * .12f, size.height * .95f)
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
            val value = if (samples.isNotEmpty()) {
                samples[i % samples.size].toFloat() / sampleMax
            } else {
                ((i * 37) % 100) / 100f
            }
            val h = size.height * (0.18f + value * 0.74f)
            val color = if (i == bars - 3) ArcOrange else NeonBlue
            val x = spacing * i + spacing * .5f
            drawLine(color.copy(alpha = 0.14f), Offset(x, size.height), Offset(x, size.height - h), 10.dp.toPx())
            drawLine(color, Offset(x, size.height), Offset(x, size.height - h), 4.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun LightningBolt(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width * .57f, 0f)
            lineTo(size.width * .20f, size.height * .54f)
            lineTo(size.width * .48f, size.height * .54f)
            lineTo(size.width * .34f, size.height)
            lineTo(size.width * .82f, size.height * .39f)
            lineTo(size.width * .54f, size.height * .39f)
            close()
        }
        drawPath(p, NeonBlue.copy(alpha = .15f), style = Stroke(15.dp.toPx()))
        drawPath(p, NeonBlue.copy(alpha = .34f), style = Stroke(8.dp.toPx()))
        drawPath(p, NeonBlue)
    }
}

@Composable
private fun AndroidTvBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .shadow(
                elevation = 9.dp,
                shape = shape,
                ambientColor = NeonBlue.copy(alpha = 0.26f),
                spotColor = NeonBlue.copy(alpha = 0.26f)
            )
            .background(Brush.verticalGradient(listOf(PanelTop, PanelBottom)), shape)
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
private fun CircuitDepthBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(ScreenBlack)

        // Soft center and corner blooms for premium depth.
        drawCircle(
            color = NeonBlue.copy(alpha = 0.035f),
            radius = size.width * 0.32f,
            center = Offset(size.width * 0.47f, size.height * 0.48f)
        )
        drawCircle(
            color = ArcOrange.copy(alpha = 0.018f),
            radius = size.width * 0.22f,
            center = Offset(size.width * 0.80f, size.height * 0.18f)
        )

        // Lightweight circuit geometry: static Canvas strokes, no bitmap decode or animation.
        val circuit = NeonBlue.copy(alpha = 0.055f)
        val node = NeonBlue.copy(alpha = 0.08f)
        val horizontalRows = listOf(96f, 186f, 275f, 365f, 452f)
        horizontalRows.forEachIndexed { index, y ->
            val inset = if (index % 2 == 0) 12f else 52f
            val yPx = y.dp.toPx()
            val startX = inset.dp.toPx()
            val endX = size.width - (inset + 18f).dp.toPx()
            drawLine(circuit, Offset(startX, yPx), Offset(endX, yPx), 1.dp.toPx())
            val nodeX = if (index % 2 == 0) size.width * .22f else size.width * .77f
            drawCircle(node, 3.dp.toPx(), Offset(nodeX, yPx))
        }

        val verticals = listOf(.12f, .31f, .53f, .72f, .90f)
        verticals.forEachIndexed { index, ratio ->
            val x = size.width * ratio
            val y1 = if (index % 2 == 0) size.height * .08f else size.height * .18f
            val y2 = if (index % 2 == 0) size.height * .88f else size.height * .96f
            drawLine(circuit, Offset(x, y1), Offset(x, y2), 1.dp.toPx())
        }
    }
}

private fun memoryUsedPercent(context: Context): Float {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    manager.getMemoryInfo(info)
    if (info.totalMem <= 0L) return 0f
    return ((info.totalMem - info.availMem).toDouble() / info.totalMem.toDouble() * 100.0)
        .toFloat()
        .coerceIn(0f, 100f)
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
        Socket().use { socket ->
            socket.connect(InetSocketAddress("1.1.1.1", 443), 1200)
        }
        ((System.nanoTime() - started) / 1_000_000L).toInt().coerceAtLeast(1)
    }.getOrDefault(0)
}
