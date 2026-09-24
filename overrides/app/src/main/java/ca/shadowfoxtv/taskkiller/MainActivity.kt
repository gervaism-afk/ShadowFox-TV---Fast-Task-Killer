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
import androidx.activity.OnBackPressedCallback
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import org.json.JSONObject
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
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // TV remotes should leave immediately. Do not wait for dashboard
                // polling/root/network coroutines to finish.
                isEnabled = false
                finishAndRemoveTask()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        })
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = PANEL)) {
                ShadowFoxUpdateGate(applicationContext) {
                    AdaptiveDashboard(applicationContext)
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
private fun AdaptiveDashboard(context: Context) {
    val configuration = LocalConfiguration.current
    val isTelevision = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    if (isTelevision) MasterDashboard(context) else MobileDashboard(context)
}

@Composable
private fun MobileDashboard(context: Context) {
    var ram by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var apps by remember { mutableIntStateOf(runningProcessCount(context)) }
    var busy by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var ramFreed by remember { mutableStateOf(0L) }
    var storageFreed by remember { mutableStateOf(0L) }
    var closedApps by remember { mutableIntStateOf(0) }
    val optimizer = remember { ShadowFoxProEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()
    fun optimize() {
        if (busy) return
        scope.launch {
            busy = true
            val result = withTimeoutOrNull(50_000L) { optimizer.optimize() }
            if (result == null) { busy = false; return@launch }
            delay(350)
            ram = memoryUsedPercent(context)
            apps = withContext(Dispatchers.IO) { optimizer.runningThirdPartyCount() }
            rootAvailable = result.rootUsed
            ramFreed = result.ramFreedBytes
            storageFreed = result.storageFreedBytes
            closedApps = result.closedApps
            busy = false
        }
    }
    LaunchedEffect(Unit) { while (true) { ram = memoryUsedPercent(context); delay(2000) } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A2B42), BG, Color(0xFF01070C))))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, top = 42.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.shadowfox_logo), "ShadowFox TV", Modifier.size(76.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ShadowFox TV", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("CACHE CLEANER • v${BuildConfig.VERSION_NAME}", color = CYAN, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("MOBILE PERFORMANCE CENTER", color = MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
            GlowCard(Modifier.fillMaxWidth().height(265.dp), onClick = { optimize() }, hero = true) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RamGauge(ram, Modifier.size(184.dp))
                    Text("RAM BOOSTER", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text(if (busy) "OPTIMIZING DEVICE..." else "Tap to free memory and process safe background apps", color = MUTED, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MobileActionCard("SYSTEM SCAN", "$apps processes", Modifier.weight(1f), busy) { optimize() }
                MobileActionCard("CACHE CLEANER", if (storageFreed > 0) formatBytes(storageFreed) else "Ready", Modifier.weight(1f), busy) { optimize() }
            }
            Spacer(Modifier.height(12.dp))
            GlowCard(Modifier.fillMaxWidth().height(108.dp), onClick = { context.startActivity(Intent(context, UltimateCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    NetworkIcon(Modifier.size(52.dp)); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text("ULTIMATE CENTER", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        Spacer(Modifier.height(3.dp))
                        Text("Apps • Network • System • Advanced controls", color = MUTED, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text("TAP TO OPEN", color = CYAN, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileStat("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f)); MobileStat("CACHE", formatBytes(storageFreed), Modifier.weight(1f)); MobileStat("APPS", closedApps.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileStat("ROOT", if (rootAvailable) "ACTIVE" else "READY", Modifier.weight(1f)); MobileStat("ANDROID", Build.VERSION.RELEASE, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp)); Text("www.shadowfoxtv.ca", color = MUTED, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MobileActionCard(title: String, subtitle: String, modifier: Modifier, busy: Boolean, action: () -> Unit) {
    GlowCard(modifier.height(112.dp), onClick = action) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(5.dp))
            Text(if (busy) "WORKING..." else subtitle, color = if (busy) ORANGE else CYAN, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(8.dp))
            Text("TAP TO RUN", color = MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MobileStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(Color(0xD90A1C29), RoundedCornerShape(10.dp)).padding(10.dp)) {
        Text(label, color = MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

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
    var weather by remember {
        val prefs = context.getSharedPreferences("shadowfox_weather", Context.MODE_PRIVATE)
        mutableStateOf(
            if (prefs.contains("temp")) WeatherSnapshot(
                prefs.getString("city", "LOCAL") ?: "LOCAL",
                prefs.getInt("temp", 0),
                prefs.getInt("code", 0),
                prefs.getBoolean("is_day", true)
            ) else null
        )
    }
    val graph = remember { mutableStateListOf<Int>() }
    val optimizer = remember { ShadowFoxProEngine(context.applicationContext) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Weather refreshes once per app launch. Avoid background polling on TV sticks.
        launch(Dispatchers.IO) {
            weather = fetchIpWeather(context) ?: weather
        }
        while (true) {
            val before = totalTrafficBytes()
            delay(1000)
            val after = totalTrafficBytes()
            if (before >= 0 && after >= before) mbps = (after - before) * 8f / 1_000_000f
            ping = measureLatencyMs()
            if (ping > 0) {
                graph.add(ping)
                while (graph.size > 18) graph.removeAt(0)
            }
            ram = memoryUsedPercent(context)
            // Do not launch root shells every second for a decorative app counter.
            // Running-app discovery is performed only during an explicit optimization.
        }
    }

    fun clean() {
        if (busy) return
        scope.launch {
            busy = true
            val result = withTimeoutOrNull(50_000L) { optimizer.optimize() }
            if (result == null) { busy = false; return@launch }
            delay(450)
            ram = memoryUsedPercent(context)
            apps = withContext(Dispatchers.IO) { optimizer.runningThirdPartyCount() }
            rootAvailable = result.rootUsed
            ramFreed = result.ramFreedBytes
            storageFreed = result.storageFreedBytes
            closedApps = result.closedApps
            busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val scale = minOf(maxWidth / 960.dp, maxHeight / 540.dp)
        Box(Modifier.size(960.dp * scale, 540.dp * scale).align(Alignment.Center)) {
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
                        Text(
                            "ShadowFox",
                            color = Color(0xFFBFF7FF),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 0.7.sp,
                            style = TextStyle(shadow = Shadow(color = CYAN.copy(alpha = .90f), offset = Offset(0f, 2f), blurRadius = 12f))
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "TV",
                            color = Color(0xFFFFC247),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 0.7.sp,
                            style = TextStyle(shadow = Shadow(color = Color(0xFFFF7A00).copy(alpha = .90f), offset = Offset(0f, 2f), blurRadius = 11f))
                        )
                    }
                    Text("www.shadowfoxtv.ca", color = Color(0xFFC6DCE8), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }

                Image(
                    painter = painterResource(R.drawable.shadowfox_logo),
                    contentDescription = "ShadowFox TV",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.offset(790.dp, 18.dp).size(122.dp, 98.dp)
                )

                WeatherBadge(weather, Modifier.offset(458.dp, 38.dp))

                GlowCard(Modifier.offset(45.dp, 145.dp).size(205.dp, 265.dp), onClick = { clean() }) {
                    Box(Modifier.fillMaxSize()) {
                        ScanDial(Modifier.align(Alignment.TopCenter).padding(top = 26.dp).size(135.dp))
                        Column(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("SYSTEM SCAN", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text("$apps ACTIVE PROCESSES", color = MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
                        RamGauge(ram, Modifier.align(Alignment.TopCenter).padding(top = 22.dp).size(240.dp))
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
                            Text("Trim app cache without deleting data.", color = MUTED, fontSize = 8.sp)
                            Spacer(Modifier.height(9.dp))
                            MasterButton(if (busy) "CLEANING..." else "CLEAN NOW", 105.dp, !busy) { clean() }
                        }
                    }
                }

                GlowCard(Modifier.offset(592.dp, 282.dp).size(320.dp, 128.dp), onClick = { context.startActivity(Intent(context, UltimateCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                    Row(Modifier.fillMaxSize().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetworkIcon(Modifier.size(62.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("ULTIMATE CENTER", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text(
                                if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE CONNECTION MONITOR",
                                color = MUTED,
                                fontSize = 8.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("PRESS TO OPEN • APPS • NETWORK • SYSTEM", color = CYAN, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Bars(graph, Modifier.size(175.dp, 28.dp))
                        }
                    }
                }

                BottomSystemStrip(
                    root = rootAvailable,
                    ramFreed = ramFreed,
                    storageFreed = storageFreed,
                    closedApps = closedApps,
                    modifier = Modifier.offset(45.dp, 454.dp).size(867.dp, 58.dp)
                )
                Bolt(Modifier.offset(456.dp, 451.dp).size(38.dp, 70.dp))
            }
        }
    }
}

private data class WeatherSnapshot(val city: String, val tempC: Int, val code: Int, val isDay: Boolean = true)

private suspend fun fetchIpWeather(context: Context): WeatherSnapshot? = withContext(Dispatchers.IO) {
    runCatching {
        val prefs = context.getSharedPreferences("shadowfox_weather", Context.MODE_PRIVATE)
        fun readUrl(url: String): String {
            val conn = URL(url).openConnection()
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            return conn.getInputStream().bufferedReader().use { it.readText() }
        }
        // Cross-check two independent IP geolocation providers. Some Canadian ISPs
        // terminate traffic in Toronto even when the device is hundreds of kilometres away.
        // Prefer the more specific result when the providers disagree.
        data class IpFix(val lat: Double, val lon: Double, val city: String, val region: String)
        val fixes = mutableListOf<IpFix>()
        runCatching {
            val j = JSONObject(readUrl("https://ipwho.is/"))
            if (j.optBoolean("success", true)) fixes += IpFix(
                j.getDouble("latitude"), j.getDouble("longitude"),
                j.optString("city"), j.optString("region")
            )
        }
        runCatching {
            val j = JSONObject(readUrl("https://ipapi.co/json/"))
            fixes += IpFix(
                j.getDouble("latitude"), j.getDouble("longitude"),
                j.optString("city"), j.optString("region")
            )
        }
        if (fixes.isEmpty()) error("IP location unavailable")
        val loc = fixes.firstOrNull { it.city.isNotBlank() && !it.city.equals("Toronto", true) }
            ?: fixes.first()
        val lat = loc.lat
        val lon = loc.lon
        val city = loc.city.ifBlank { loc.region.ifBlank { "LOCAL" } }
        val current = JSONObject(readUrl("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&temperature_unit=celsius")).getJSONObject("current")
        WeatherSnapshot(city, kotlin.math.round(current.getDouble("temperature_2m")).toInt(), current.getInt("weather_code"), current.optInt("is_day", 1) == 1).also { result ->
            prefs.edit().putString("city", result.city).putInt("temp", result.tempC).putInt("code", result.code).putBoolean("is_day", result.isDay).apply()
        }
    }.getOrElse {
        val prefs = context.getSharedPreferences("shadowfox_weather", Context.MODE_PRIVATE)
        if (prefs.contains("temp")) WeatherSnapshot(prefs.getString("city", "LOCAL") ?: "LOCAL", prefs.getInt("temp", 0), prefs.getInt("code", 0), prefs.getBoolean("is_day", true)) else null
    }
}

private fun weatherSymbol(code: Int, isDay: Boolean = true): String = when (code) {
    0 -> if (isDay) "☀" else "☾"
    1, 2 -> if (isDay) "⛅" else "☾"
    3 -> "☁"
    45, 48 -> "≋"
    in 51..67, in 80..82 -> "☂"
    in 71..77, in 85..86 -> "❄"
    in 95..99 -> "ϟ"
    else -> "•"
}

@Composable
private fun WeatherBadge(weather: WeatherSnapshot?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Row(modifier.shadow(5.dp, shape, false, CYAN.copy(alpha = .22f), CYAN.copy(alpha = .22f)).background(Color(0xD90A2030), shape).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (weather == null) "•" else weatherSymbol(weather.code, weather.isDay), color = if (weather?.code in 95..99) ORANGE else CYAN, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(weather?.let { result -> "${result.tempC}°C" } ?: "--°C", color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Text(weather?.city ?: "WEATHER", color = MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
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
        StatTile("RAM FREED", formatBytes(ramFreed), Modifier.size(126.dp, 54.dp))
        Spacer(Modifier.width(9.dp))
        StatTile("CACHE CLEARED", formatBytes(storageFreed), Modifier.size(126.dp, 54.dp))
        Spacer(Modifier.width(9.dp))
        StatTile("APPS CLOSED", closedApps.toString(), Modifier.size(112.dp, 54.dp))
        Spacer(Modifier.width(78.dp))
        StatTile("ROOT", if (root) "ACTIVE" else "READY", Modifier.size(112.dp, 54.dp), if (root) GREEN else WHITE)
        Spacer(Modifier.width(9.dp))
        StatTile("DEVICE", deviceLabel(), Modifier.size(175.dp, 54.dp))
        Spacer(Modifier.width(9.dp))
        StatTile("ANDROID", Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }, Modifier.size(100.dp, 54.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color = WHITE) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .shadow(6.dp, shape, false, CYAN.copy(alpha = .25f), CYAN.copy(alpha = .25f))
            .background(Color(0xD90A1C29), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color(0xFFC9D7E0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
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
private fun MasterButton(text: String, width: androidx.compose.ui.unit.Dp, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .width(width)
            .height(32.dp)
            .scale(if (focused) 1.08f else 1f)
            .shadow(if (focused) 20.dp else 9.dp, shape, false, CYAN, CYAN)
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
        val c = Offset(size.width * .50f, size.height * .50f)
        val r = size.minDimension * .34f
        // Multi-layer machined bezel and illuminated face.
        drawCircle(CYAN.copy(.07f), r * 1.42f, c)
        drawCircle(Color(0xFF020A10), r * 1.26f, c)
        drawCircle(Color(0xFF173A4E), r * 1.20f, c, style = Stroke(5.dp.toPx()))
        drawCircle(CYAN.copy(.48f), r * 1.13f, c, style = Stroke(2.dp.toPx()))
        drawCircle(Color(0xFF071722), r * 1.06f, c)
        drawCircle(BLUE.copy(.50f), r * .72f, c, style = Stroke(2.dp.toPx()))
        drawCircle(CYAN.copy(.20f), r * .52f, c, style = Stroke(1.dp.toPx()))
        // Precision 60-division scale with cardinal marker blocks.
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90.0)
            val major = i % 5 == 0
            val mid = i % 5 == 0 || i % 5 == 2
            val inner = if (major) .72f else if (mid) .79f else .84f
            val col = when { i >= 48 -> ORANGE; major -> WHITE; else -> CYAN }
            drawLine(col.copy(if (major) .95f else .55f),
                Offset(c.x+cos(a).toFloat()*r*inner,c.y+sin(a).toFloat()*r*inner),
                Offset(c.x+cos(a).toFloat()*r*1.01f,c.y+sin(a).toFloat()*r*1.01f),
                (if (major) 2.4f else 1f).dp.toPx(), StrokeCap.Round)
        }
        // Four small technical quadrant screws.
        listOf(-45.0,45.0,135.0,225.0).forEach { deg ->
            val a=Math.toRadians(deg); val p=Offset(c.x+cos(a).toFloat()*r*.55f,c.y+sin(a).toFloat()*r*.55f)
            drawCircle(Color(0xFF78909C),2.6.dp.toPx(),p); drawCircle(Color(0xFF10242F),1.dp.toPx(),p)
        }
        val a = Math.toRadians(-28.0)
        val p = Offset(c.x+cos(a).toFloat()*r*.68f,c.y+sin(a).toFloat()*r*.68f)
        drawLine(CYAN.copy(.18f),c,p,13.dp.toPx(),StrokeCap.Round)
        drawLine(WHITE,c,p,2.7.dp.toPx(),StrokeCap.Round)
        drawCircle(Color(0xFF08131A),9.dp.toPx(),c)
        drawCircle(ORANGE,6.dp.toPx(),c)
        drawCircle(WHITE,2.dp.toPx(),c)
    }
}

@Composable
private fun RamGauge(value: Float, modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val c=Offset(size.width/2,size.height*.49f)
            val r=size.minDimension*.39f
            drawCircle(CYAN.copy(.06f),r*1.28f,c)
            drawCircle(Color(0xFF020A10),r*1.17f,c)
            drawCircle(Color(0xFF1B4358),r*1.12f,c,style=Stroke(6.dp.toPx()))
            drawCircle(CYAN.copy(.48f),r*1.04f,c,style=Stroke(2.dp.toPx()))
            drawCircle(Color(0xFF071722),r*.96f,c)
            val start=135f; val sweep=270f
            drawArc(Color(0xFF102C3B),start,sweep,false,Offset(c.x-r*.86f,c.y-r*.86f),Size(r*1.72f,r*1.72f),style=Stroke(16.dp.toPx(),cap=StrokeCap.Round))
            // 100-step illuminated status track.
            for(i in 0..99){
                val t=i/99f
                val col=when { t<.60f -> lerp(CYAN,Color(0xFF4DFFDF),t/.60f); t<.82f -> lerp(Color(0xFF4DFFDF),ORANGE,(t-.60f)/.22f); else -> lerp(ORANGE,Color(0xFFFF3B30),(t-.82f)/.18f) }
                drawArc(col.copy(if(t<=value.coerceIn(0f,100f)/100f) .95f else .20f),start+sweep*t,1.8f,false,Offset(c.x-r*.86f,c.y-r*.86f),Size(r*1.72f,r*1.72f),style=Stroke(8.dp.toPx()))
            }
            // Fine calibration scale.
            for(i in 0..50){
                val a=Math.toRadians((start+sweep*i/50f).toDouble()); val major=i%5==0
                val inner=if(major).58f else .65f
                drawLine(if(i>=40) ORANGE else if(major) WHITE else CYAN.copy(.55f),
                    Offset(c.x+cos(a).toFloat()*r*inner,c.y+sin(a).toFloat()*r*inner),
                    Offset(c.x+cos(a).toFloat()*r*.78f,c.y+sin(a).toFloat()*r*.78f),
                    (if(major)2.2f else .8f).dp.toPx(),StrokeCap.Round)
            }
            drawCircle(CYAN.copy(.18f),r*.48f,c,style=Stroke(1.dp.toPx()))
            val a=Math.toRadians((start+sweep*value.coerceIn(0f,100f)/100f).toDouble())
            val end=Offset(c.x+cos(a).toFloat()*r*.58f,c.y+sin(a).toFloat()*r*.58f)
            drawLine(CYAN.copy(.20f),c,end,13.dp.toPx(),StrokeCap.Round)
            drawLine(WHITE,c,end,2.8.dp.toPx(),StrokeCap.Round)
            drawCircle(Color(0xFF08131A),11.dp.toPx(),c)
            drawCircle(ORANGE,7.dp.toPx(),c)
            drawCircle(WHITE,2.5.dp.toPx(),c)
        }
        Column(Modifier.align(Alignment.Center).offset(y=69.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("${value.toInt()}%",color=WHITE,fontSize=20.sp,fontWeight=FontWeight.Black)
            Text("RAM LOAD",color=CYAN,fontSize=7.sp,fontWeight=FontWeight.Bold)
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
        val c=Offset(size.width*.5f,size.height*.55f)
        val r=size.minDimension*.40f
        drawCircle(CYAN.copy(.07f),r*1.22f,c)
        drawCircle(CYAN.copy(.25f),r,c,style=Stroke(3.dp.toPx()))
        for(i in 0..23){
            val a=Math.toRadians((i*15.0-90.0))
            drawLine(if(i>17) ORANGE else CYAN.copy(.75f),
                Offset(c.x+cos(a).toFloat()*r*.72f,c.y+sin(a).toFloat()*r*.72f),
                Offset(c.x+cos(a).toFloat()*r*.94f,c.y+sin(a).toFloat()*r*.94f),
                (if(i%3==0)2f else 1f).dp.toPx())
        }
        val a=Math.toRadians(-32.0)
        val end=Offset(c.x+cos(a).toFloat()*r*.68f,c.y+sin(a).toFloat()*r*.68f)
        drawLine(CYAN.copy(.22f),c,end,9.dp.toPx(),StrokeCap.Round)
        drawLine(WHITE,c,end,2.dp.toPx(),StrokeCap.Round)
        drawCircle(ORANGE,5.dp.toPx(),c)
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
