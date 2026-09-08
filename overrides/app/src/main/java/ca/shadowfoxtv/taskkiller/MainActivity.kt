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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
                ShadowFoxDashboard(applicationContext)
            }
        }
    }
}

private val NeonBlue = Color(0xFF1AA7FF)
private val Orange = Color(0xFFFF8A00)
private val AppBackground = Color(0xFF121212)
private val Panel = Color(0xFF1A1A1A)
private val PanelBorder = Color(0xFF2A2A2A)
private val Muted = Color(0xFF9AA4AE)

@Composable
private fun ShadowFoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonBlue,
            secondary = Orange,
            background = AppBackground,
            surface = Panel,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
private fun ShadowFoxDashboard(context: Context) {
    var networkMbps by remember { mutableFloatStateOf(0f) }
    var pingMs by remember { mutableIntStateOf(0) }
    val latencyHistory = remember { mutableStateListOf<Int>() }
    var statusText by remember { mutableStateOf("Ready") }
    var isOptimizing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val optimizer = remember(context) { AppOptimizer(context) }

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
                latencyHistory.add(pingMs)
                while (latencyHistory.size > 30) latencyHistory.removeAt(0)
            }
        }
    }

    fun runOptimization() {
        if (isOptimizing) return
        scope.launch {
            isOptimizing = true
            statusText = "Optimizing background apps..."
            val result = optimizer.optimize()
            statusText = if (result.requestedKills == 0) {
                "No eligible background apps found"
            } else {
                "Optimization requested for ${result.requestedKills} apps"
            }
            isOptimizing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 34.dp, vertical = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "SHADOWFOX TV",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Text(
                    text = "SYSTEM & NETWORK OPTIMIZER",
                    color = NeonBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    letterSpacing = 1.8.sp
                )
            }
            Text(
                text = "www.shadowfoxtv.ca",
                color = Muted,
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BufferCard(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                statusText = statusText,
                isOptimizing = isOptimizing,
                onOptimize = ::runOptimization
            )

            SpeedometerCard(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight(),
                networkMbps = networkMbps,
                pingMs = pingMs
            )

            LatencyCard(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                pingMs = pingMs,
                samples = latencyHistory
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Protected: system apps, launcher services and detected VPN services",
                color = Muted,
                fontSize = 11.sp
            )
            TvActionButton(
                text = if (isOptimizing) "OPTIMIZING..." else "RUN FULL OPTIMIZATION",
                accent = Orange,
                enabled = !isOptimizing,
                onClick = ::runOptimization
            )
        }
    }
}

@Composable
private fun BufferCard(
    modifier: Modifier,
    statusText: String,
    isOptimizing: Boolean,
    onOptimize: () -> Unit
) {
    DashboardCard(modifier) {
        Text("BUFFER STATUS", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Text("STREAMING MEMORY", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(5.dp))
        Text("STABLE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = statusText,
            color = if (isOptimizing) Orange else Muted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.weight(1f))
        TvActionButton(
            text = if (isOptimizing) "WORKING..." else "OPTIMIZE BUFFER",
            accent = NeonBlue,
            enabled = !isOptimizing,
            onClick = onOptimize,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SpeedometerCard(modifier: Modifier, networkMbps: Float, pingMs: Int) {
    DashboardCard(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("LIVE NETWORK", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Speedometer(
                speedMbps = networkMbps,
                modifier = Modifier.size(238.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (networkMbps < 10f) String.format("%.1f", networkMbps) else String.format("%.0f", networkMbps),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("Mbps", color = NeonBlue, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(9.dp))
                Text("Ping: ${if (pingMs > 0) "${pingMs}ms" else "--"}", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Speedometer(speedMbps: Float, modifier: Modifier = Modifier) {
    val displaySpeed by animateFloatAsState(targetValue = speedMbps.coerceIn(0f, 300f), label = "speed")
    Canvas(modifier = modifier) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2f
        drawArc(
            color = Color(0xFF272727),
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        val sweep = 270f * (displaySpeed / 300f)
        drawArc(
            color = NeonBlue,
            startAngle = 135f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        val angle = Math.toRadians((135f + sweep).toDouble())
        val radius = size.minDimension * 0.37f
        val center = Offset(size.width / 2f, size.height / 2f)
        val needleEnd = Offset(
            x = center.x + cos(angle).toFloat() * radius,
            y = center.y + sin(angle).toFloat() * radius
        )
        drawLine(Orange, center, needleEnd, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Orange, radius = 7.dp.toPx(), center = center)
    }
}

@Composable
private fun LatencyCard(modifier: Modifier, pingMs: Int, samples: List<Int>) {
    DashboardCard(modifier) {
        Text("LATENCY", color = Orange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (pingMs > 0) "$pingMs ms" else "-- ms",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 32.sp
        )
        Text(
            text = when {
                pingMs <= 0 -> "Measuring connection"
                pingMs < 35 -> "Excellent response"
                pingMs < 70 -> "Good response"
                else -> "High latency"
            },
            color = Muted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(14.dp))
        LatencyGraph(
            samples = samples,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun LatencyGraph(samples: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val maxValue = max(80, samples.maxOrNull() ?: 80).toFloat()
        val minValue = min(samples.minOrNull() ?: 0, 20).toFloat()
        val range = (maxValue - minValue).coerceAtLeast(1f)
        val xStep = size.width / (samples.size - 1)
        val points = samples.mapIndexed { index, sample ->
            val normalized = (sample - minValue) / range
            Offset(index * xStep, size.height - (normalized * size.height * 0.78f) - size.height * 0.1f)
        }
        for (i in 0 until points.lastIndex) {
            drawLine(
                color = Orange,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        points.forEach { drawCircle(Orange.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = it) }
        points.forEach { drawCircle(Color.White, radius = 2.dp.toPx(), center = it) }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable Column.() -> Unit
) {
    Card(
        modifier = modifier.border(1.dp, PanelBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            horizontalAlignment = horizontalAlignment,
            content = content
        )
    }
}

@Composable
private fun TvActionButton(
    text: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.06f else 1f)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else accent.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) accent else accent.copy(alpha = 0.16f),
            contentColor = if (focused) Color.Black else Color.White,
            disabledContainerColor = Color(0xFF222222),
            disabledContentColor = Muted
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
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
        val packages = mutableSetOf(
            context.packageName,
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.systemui",
            "com.android.settings"
        )

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packages += packageManager.queryIntentActivities(homeIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }

        val vpnIntent = Intent(VpnService.SERVICE_INTERFACE)
        packages += packageManager.queryIntentServices(vpnIntent, 0)
            .mapNotNull { it.serviceInfo?.packageName }

        return packages
    }
}

private fun totalTrafficBytes(): Long {
    val rx = TrafficStats.getTotalRxBytes()
    val tx = TrafficStats.getTotalTxBytes()
    if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return -1L
    return rx + tx
}

private suspend fun measureLatencyMs(): Int = withContext(Dispatchers.IO) {
    repeat(2) {
        val socket = Socket()
        try {
            val start = System.nanoTime()
            socket.connect(InetSocketAddress("1.1.1.1", 443), 1500)
            val elapsedMs = ((System.nanoTime() - start) / 1_000_000L).toInt()
            return@withContext elapsedMs.coerceAtLeast(1)
        } catch (_: Exception) {
            // Retry once before reporting unavailable.
        } finally {
            runCatching { socket.close() }
        }
    }
    0
}
