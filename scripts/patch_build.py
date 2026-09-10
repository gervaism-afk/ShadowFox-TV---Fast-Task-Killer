from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

# Add only the layout imports needed for the phone/tablet dashboard.
s = s.replace('import androidx.compose.foundation.layout.BoxWithConstraints\n', 'import androidx.compose.foundation.layout.BoxWithConstraints\nimport androidx.compose.foundation.layout.Arrangement\n')
s = s.replace('import androidx.compose.foundation.layout.fillMaxSize\n', 'import androidx.compose.foundation.layout.fillMaxSize\nimport androidx.compose.foundation.layout.fillMaxWidth\n')
s = s.replace('import androidx.compose.foundation.layout.width\n', 'import androidx.compose.foundation.layout.width\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll\n')

old_startup = '''class MainActivity : ComponentActivity() {
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
}'''

new_startup = '''class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTvDevice = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)

        if (isTvDevice) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = PANEL)) {
                ShadowFoxUpdateGate(applicationContext) {
                    if (isTvDevice) MasterDashboard(applicationContext)
                    else MobileDashboard(applicationContext)
                }
            }
        }
    }
}'''

if old_startup not in s:
    raise SystemExit('Expected stable MainActivity startup block not found')
s = s.replace(old_startup, new_startup, 1)

# Keep the approved TV composition unchanged, but make the full 960x540 canvas fit the available TV viewport.
old_scale = '''    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val scale = minOf(maxWidth / 960.dp, maxHeight / 540.dp)
        Box(Modifier.size(960.dp * scale, 540.dp * scale).align(Alignment.Center)) {
            Box(
                Modifier
                    .size(960.dp, 540.dp)
                    .scale(scale)
                    .align(Alignment.Center)
                    .background(BG)
            ) {'''

new_scale = '''    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val scale = minOf(maxWidth / 960.dp, maxHeight / 540.dp)
        Box(
            Modifier
                .size(960.dp, 540.dp)
                .scale(scale)
                .align(Alignment.Center)
                .background(BG)
        ) {'''

if old_scale not in s:
    raise SystemExit('Expected stable dashboard scaling block not found')
s = s.replace(old_scale, new_scale, 1)

old_close = '''                Bolt(Modifier.offset(456.dp, 457.dp).size(38.dp))
            }
        }
    }
}'''
new_close = '''                Bolt(Modifier.offset(456.dp, 457.dp).size(38.dp))
        }
    }
}'''
if old_close not in s:
    raise SystemExit('Expected stable dashboard closing block not found')
s = s.replace(old_close, new_close, 1)

mobile = r'''

@Composable
private fun MobileDashboard(context: Context) {
    var ram by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var apps by remember { mutableIntStateOf(runningProcessCount(context)) }
    var mbps by remember { mutableFloatStateOf(0f) }
    var ping by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var ramFreed by remember { mutableStateOf(0L) }
    var storageFreed by remember { mutableStateOf(0L) }
    var closedApps by remember { mutableIntStateOf(0) }
    val graph = remember { mutableStateListOf<Int>() }
    val optimizer = remember { AppOptimizer(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
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
            apps = runningProcessCount(context)
        }
    }

    fun clean() {
        if (busy) return
        scope.launch {
            busy = true
            val result = optimizer.optimize()
            delay(450)
            ram = memoryUsedPercent(context)
            apps = runningProcessCount(context)
            rootAvailable = result.rootUsed
            ramFreed = result.ramFreedBytes
            storageFreed = result.storageFreedBytes
            closedApps = result.closedApps
            busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val contentWidth = minOf(maxWidth - 28.dp, 620.dp)
        Column(
            modifier = Modifier
                .width(contentWidth)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(top = 18.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("ShadowFox", color = WHITE, fontSize = 28.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Spacer(Modifier.width(4.dp))
                        Text("TV", color = ORANGE, fontSize = 28.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    }
                    Text("www.shadowfoxtv.ca", color = MUTED, fontSize = 9.sp)
                }
                Image(
                    painter = painterResource(R.drawable.shadowfox_logo),
                    contentDescription = "ShadowFox TV",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(88.dp, 70.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            GlowCard(Modifier.fillMaxWidth().height(330.dp), onClick = { clean() }, hero = true) {
                Box(Modifier.fillMaxSize()) {
                    RamGauge(ram, Modifier.align(Alignment.TopCenter).padding(top = 16.dp).size(235.dp))
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("RAM BOOSTER", color = WHITE, fontSize = 21.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(10.dp))
                        MasterButton(if (busy) "BOOSTING..." else "BOOST", 170.dp, !busy) { clean() }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            GlowCard(Modifier.fillMaxWidth().height(220.dp), onClick = { clean() }) {
                Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    ScanDial(Modifier.size(135.dp))
                    Spacer(Modifier.width(22.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("SYSTEM SCAN", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text("$apps ACTIVE PROCESSES", color = MUTED, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        MasterButton(if (busy) "SCANNING..." else "SCAN NOW", 120.dp, !busy) { clean() }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            GlowCard(Modifier.fillMaxWidth().height(155.dp), onClick = { clean() }) {
                Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Broom(Modifier.size(72.dp))
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text("CACHE CLEANER", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("Trim app cache without deleting data.", color = MUTED, fontSize = 9.sp)
                        Spacer(Modifier.height(12.dp))
                        MasterButton(if (busy) "CLEANING..." else "CLEAN NOW", 122.dp, !busy) { clean() }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            GlowCard(Modifier.fillMaxWidth().height(160.dp), onClick = {}) {
                Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    NetworkIcon(Modifier.size(72.dp))
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text("NETWORK MONITOR", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text(
                            if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE CONNECTION MONITOR",
                            color = MUTED,
                            fontSize = 9.sp
                        )
                        Spacer(Modifier.height(9.dp))
                        Bars(graph, Modifier.fillMaxWidth().height(48.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f).height(58.dp))
                StatTile("CACHE CLEARED", formatBytes(storageFreed), Modifier.weight(1f).height(58.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("APPS CLOSED", closedApps.toString(), Modifier.weight(1f).height(58.dp))
                StatTile("ROOT", if (rootAvailable) "ACTIVE" else "READY", Modifier.weight(1f).height(58.dp), if (rootAvailable) GREEN else MUTED)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("DEVICE", deviceLabel(), Modifier.weight(1.45f).height(58.dp))
                StatTile("ANDROID", Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }, Modifier.weight(.75f).height(58.dp))
            }

            Spacer(Modifier.height(18.dp))
            Bolt(Modifier.size(42.dp))
        }
    }
}
'''

marker = '\n@Composable\nprivate fun BottomSystemStrip('
if marker not in s:
    raise SystemExit('BottomSystemStrip insertion point not found')
s = s.replace(marker, mobile + marker, 1)

p.write_text(s)
print('Applied approved ShadowFox visual system to responsive phone/tablet layout')
