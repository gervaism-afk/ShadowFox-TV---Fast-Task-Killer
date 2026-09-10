package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MobileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = M_BG, surface = M_PANEL)) {
                ShadowFoxUpdateGate(applicationContext) {
                    MobileHome(applicationContext)
                }
            }
        }
    }
}

private val M_BG = Color(0xFF03111D)
private val M_PANEL = Color(0xFF082033)
private val M_CYAN = Color(0xFF00E5FF)
private val M_ORANGE = Color(0xFFFF7A00)
private val M_WHITE = Color(0xFFF7FBFF)
private val M_MUTED = Color(0xFF9AABB8)
private val M_GREEN = Color(0xFF77C943)

@Composable
private fun MobileHome(context: Context) {
    var ram by remember { mutableIntStateOf(mobileRamPercent(context)) }
    var apps by remember { mutableIntStateOf(mobileProcessCount(context)) }
    var busy by remember { mutableStateOf(false) }
    var rootUsed by remember { mutableStateOf(false) }
    var ramFreedMb by remember { mutableIntStateOf(0) }
    var closedApps by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun optimize() {
        if (busy) return
        scope.launch {
            busy = true
            val before = mobileAvailableRam(context)
            val result = withContext(Dispatchers.IO) { mobileOptimize(context) }
            delay(300)
            val after = mobileAvailableRam(context)
            ram = mobileRamPercent(context)
            apps = mobileProcessCount(context)
            ramFreedMb = (((after - before).coerceAtLeast(0L)) / (1024L * 1024L)).toInt()
            closedApps = result.first
            rootUsed = result.second
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(M_BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("ShadowFox", color = M_WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    Text(" TV", color = M_ORANGE, fontSize = 28.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                }
                Text("www.shadowfoxtv.ca", color = M_MUTED, fontSize = 10.sp)
            }
            Image(
                painter = painterResource(R.drawable.shadowfox_logo),
                contentDescription = "ShadowFox TV",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(82.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        MobileCard(title = "RAM BOOSTER", subtitle = "$ram% RAM IN USE", button = if (busy) "BOOSTING..." else "BOOST", enabled = !busy, onClick = { optimize() })
        Spacer(Modifier.height(14.dp))
        MobileCard(title = "SYSTEM SCAN", subtitle = "$apps ACTIVE PROCESSES", button = if (busy) "SCANNING..." else "SCAN NOW", enabled = !busy, onClick = { optimize() })
        Spacer(Modifier.height(14.dp))
        MobileCard(title = "CACHE CLEANER", subtitle = "ROOT-SAFE SYSTEM CLEANUP", button = if (busy) "CLEANING..." else "CLEAN NOW", enabled = !busy, onClick = { optimize() })
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MobileStat("RAM FREED", "$ramFreedMb MB", Modifier.weight(1f))
            MobileStat("APPS CLOSED", closedApps.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MobileStat("ROOT", if (rootUsed) "ACTIVE" else "READY", Modifier.weight(1f), if (rootUsed) M_GREEN else M_WHITE)
            MobileStat("ANDROID", Build.VERSION.RELEASE ?: "Unknown", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        MobileStat("DEVICE", "${Build.MANUFACTURER} ${Build.MODEL}".trim(), Modifier.fillMaxWidth())
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MobileCard(title: String, subtitle: String, button: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B2B42), Color(0xFF071B2A))), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(20.dp)
    ) {
        Text(title, color = M_WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = M_MUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(M_CYAN, RoundedCornerShape(50))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(button, color = Color(0xFF03202A), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MobileStat(label: String, value: String, modifier: Modifier, valueColor: Color = M_WHITE) {
    Column(
        modifier
            .background(M_PANEL, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(label, color = M_CYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun mobileRamPercent(context: Context): Int {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    if (info.totalMem <= 0L) return 0
    return (((info.totalMem - info.availMem).toDouble() / info.totalMem) * 100.0).toInt().coerceIn(0, 100)
}

private fun mobileAvailableRam(context: Context): Long {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return info.availMem
}

private fun mobileProcessCount(context: Context): Int {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return am.runningAppProcesses.orEmpty().size
}

private fun mobileOptimize(context: Context): Pair<Int, Boolean> {
    val root = runCatching {
        val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor() == 0 && out.contains("uid=0")
    }.getOrDefault(false)

    if (root) {
        val packages = runCatching {
            val p = ProcessBuilder("su", "-c", "pm list packages -3").redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor()
            text.lineSequence().map { it.trim().removePrefix("package:") }
                .filter { it.isNotBlank() && it != context.packageName }
                .toList()
        }.getOrDefault(emptyList())
        var closed = 0
        packages.forEach { pkg ->
            val ok = runCatching {
                val p = ProcessBuilder("su", "-c", "am force-stop '$pkg'").redirectErrorStream(true).start()
                p.inputStream.close()
                p.waitFor() == 0
            }.getOrDefault(false)
            if (ok) closed++
        }
        runCatching { ProcessBuilder("su", "-c", "pm trim-caches 999999999999").start().waitFor() }
        return closed to true
    }

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    var closed = 0
    am.runningAppProcesses.orEmpty().flatMap { it.pkgList?.toList().orEmpty() }.distinct().forEach { pkg ->
        if (pkg != context.packageName) {
            runCatching { am.killBackgroundProcesses(pkg); closed++ }
        }
    }
    return closed to false
}
