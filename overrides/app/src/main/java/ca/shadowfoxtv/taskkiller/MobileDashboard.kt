package ca.shadowfoxtv.taskkiller

import android.app.ActivityManager
import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

private val MBG = Color(0xFF03111D)
private val MPANEL = Color(0xFF082033)
private val MCYAN = Color(0xFF00E5FF)
private val MORANGE = Color(0xFFFF7A00)
private val MWHITE = Color(0xFFF7FBFF)
private val MMUTED = Color(0xFF9AABB8)
private val MGREEN = Color(0xFF77C943)

@Composable
fun MobileDashboard(context: Context) {
    var ram by remember { mutableIntStateOf(mobileRamPercent(context)) }
    var apps by remember { mutableIntStateOf(mobileProcessCount(context)) }
    var busy by remember { mutableStateOf(false) }
    var root by remember { mutableStateOf(false) }
    var freed by remember { mutableIntStateOf(0) }
    var closed by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            ram = mobileRamPercent(context)
            apps = mobileProcessCount(context)
            delay(1500)
        }
    }

    fun clean() {
        if (busy) return
        scope.launch {
            busy = true
            val before = mobileAvailRam(context)
            val result = withContext(Dispatchers.IO) { mobileOptimize(context) }
            delay(350)
            val after = mobileAvailRam(context)
            ram = mobileRamPercent(context)
            apps = mobileProcessCount(context)
            freed = (((after - before).coerceAtLeast(0L)) / (1024L * 1024L)).toInt()
            closed = result.first
            root = result.second
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(MBG), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("ShadowFox", color = MWHITE, fontSize = 27.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Text(" TV", color = MORANGE, fontSize = 27.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    }
                    Text("www.shadowfoxtv.ca", color = MMUTED, fontSize = 10.sp)
                }
                Image(
                    painter = painterResource(R.drawable.shadowfox_logo),
                    contentDescription = "ShadowFox TV",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            MobileHero("RAM BOOSTER", "$ram% RAM IN USE", if (busy) "BOOSTING..." else "BOOST", !busy) { clean() }
            Spacer(Modifier.height(14.dp))
            MobileAction("SYSTEM SCAN", "$apps ACTIVE PROCESSES", if (busy) "SCANNING..." else "SCAN NOW", !busy) { clean() }
            Spacer(Modifier.height(14.dp))
            MobileAction("CACHE CLEANER", "ROOT-SAFE CLEANUP WITHOUT DELETING APP DATA", if (busy) "CLEANING..." else "CLEAN NOW", !busy) { clean() }
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MobileStat("RAM FREED", "$freed MB", Modifier.weight(1f))
                MobileStat("APPS CLOSED", closed.toString(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MobileStat("ROOT", if (root) "ACTIVE" else "READY", Modifier.weight(1f), if (root) MGREEN else MWHITE)
                MobileStat("ANDROID", Build.VERSION.RELEASE ?: "Unknown", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            MobileStat("DEVICE", "${Build.MANUFACTURER} ${Build.MODEL}".trim(), Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MobileHero(title: String, subtitle: String, button: String, enabled: Boolean, onClick: () -> Unit) {
    MobileCard(title, subtitle, button, enabled, onClick, 190.dp)
}

@Composable
private fun MobileAction(title: String, subtitle: String, button: String, enabled: Boolean, onClick: () -> Unit) {
    MobileCard(title, subtitle, button, enabled, onClick, 132.dp)
}

@Composable
private fun MobileCard(title: String, subtitle: String, button: String, enabled: Boolean, onClick: () -> Unit, minHeight: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .height(minHeight)
            .background(Brush.verticalGradient(listOf(Color(0xFF0B2B42), Color(0xFF071A29))), shape)
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = MWHITE, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = MMUTED, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(MCYAN, RoundedCornerShape(50))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(button, color = Color(0xFF03202A), fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MobileStat(label: String, value: String, modifier: Modifier, valueColor: Color = MWHITE) {
    Column(modifier.background(MPANEL, RoundedCornerShape(13.dp)).padding(14.dp)) {
        Text(label, color = MCYAN, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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

private fun mobileAvailRam(context: Context): Long {
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
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor() == 0 && text.contains("uid=0")
    }.getOrDefault(false)

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    if (!root) {
        var count = 0
        am.runningAppProcesses.orEmpty()
            .flatMap { it.pkgList?.toList().orEmpty() }
            .distinct()
            .filter { it != context.packageName }
            .forEach { pkg -> runCatching { am.killBackgroundProcesses(pkg); count++ } }
        return count to false
    }

    val packages = runCatching {
        val p = ProcessBuilder("su", "-c", "pm list packages -3").redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        text.lineSequence().map { it.trim().removePrefix("package:") }
            .filter { it.isNotBlank() && it != context.packageName }
            .toList()
    }.getOrDefault(emptyList())

    var count = 0
    packages.forEach { pkg ->
        val ok = runCatching {
            val p = ProcessBuilder("su", "-c", "am force-stop '$pkg'").redirectErrorStream(true).start()
            p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor() == 0
        }.getOrDefault(false)
        if (ok) count++
    }
    runCatching { ProcessBuilder("su", "-c", "pm trim-caches 999999999999").start().waitFor() }
    return count to true
}
