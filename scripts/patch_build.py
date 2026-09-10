from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

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
        val portrait = maxHeight >= maxWidth
        val contentWidth = minOf(maxWidth - 24.dp, if (portrait) 620.dp else 940.dp)
        Column(
            modifier = Modifier
                .width(contentWidth)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(top = 10.dp, bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("ShadowFox", color = WHITE, fontSize = if (portrait) 26.sp else 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Spacer(Modifier.width(4.dp))
                        Text("TV", color = ORANGE, fontSize = if (portrait) 26.sp else 22.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    }
                    Text("www.shadowfoxtv.ca", color = MUTED, fontSize = 9.sp)
                }
                Image(
                    painter = painterResource(R.drawable.shadowfox_logo),
                    contentDescription = "ShadowFox TV",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(if (portrait) 68.dp else 58.dp)
                )
            }

            Spacer(Modifier.height(if (portrait) 10.dp else 6.dp))

            if (portrait) {
                GlowCard(Modifier.fillMaxWidth().height(255.dp), onClick = { clean() }, hero = true) {
                    Box(Modifier.fillMaxSize()) {
                        RamGauge(ram, Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(185.dp))
                        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RAM BOOSTER", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(7.dp))
                            MasterButton(if (busy) "BOOSTING..." else "BOOST", 160.dp, !busy) { clean() }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                GlowCard(Modifier.fillMaxWidth().height(160.dp), onClick = { clean() }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        ScanDial(Modifier.size(105.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SYSTEM SCAN", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("$apps ACTIVE PROCESSES", color = MUTED, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(9.dp))
                            MasterButton(if (busy) "SCANNING..." else "SCAN NOW", 118.dp, !busy) { clean() }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                GlowCard(Modifier.fillMaxWidth().height(125.dp), onClick = { clean() }) {
                    Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Broom(Modifier.size(58.dp))
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("CACHE CLEANER", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("Trim app cache without deleting data.", color = MUTED, fontSize = 8.sp)
                            Spacer(Modifier.height(8.dp))
                            MasterButton(if (busy) "CLEANING..." else "CLEAN NOW", 118.dp, !busy) { clean() }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                GlowCard(Modifier.fillMaxWidth().height(125.dp), onClick = {}) {
                    Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        NetworkIcon(Modifier.size(58.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("NETWORK MONITOR", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text(if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE CONNECTION MONITOR", color = MUTED, fontSize = 8.sp)
                            Spacer(Modifier.height(6.dp))
                            Bars(graph, Modifier.fillMaxWidth().height(38.dp))
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowCard(Modifier.fillMaxWidth().height(210.dp), onClick = { clean() }, hero = true) {
                            Box(Modifier.fillMaxSize()) {
                                RamGauge(ram, Modifier.align(Alignment.TopCenter).padding(top = 2.dp).size(155.dp))
                                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("RAM BOOSTER", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                                    Spacer(Modifier.height(5.dp))
                                    MasterButton(if (busy) "BOOSTING..." else "BOOST", 140.dp, !busy) { clean() }
                                }
                            }
                        }
                        GlowCard(Modifier.fillMaxWidth().height(118.dp), onClick = { clean() }) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ScanDial(Modifier.size(78.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SYSTEM SCAN", color = WHITE, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                    Text("$apps ACTIVE PROCESSES", color = MUTED, fontSize = 7.sp)
                                    Spacer(Modifier.height(6.dp))
                                    MasterButton(if (busy) "SCANNING..." else "SCAN NOW", 105.dp, !busy) { clean() }
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlowCard(Modifier.fillMaxWidth().height(159.dp), onClick = { clean() }) {
                            Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Broom(Modifier.size(56.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("CACHE CLEANER", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    Text("Trim app cache without deleting data.", color = MUTED, fontSize = 8.sp)
                                    Spacer(Modifier.height(8.dp))
                                    MasterButton(if (busy) "CLEANING..." else "CLEAN NOW", 110.dp, !busy) { clean() }
                                }
                            }
                        }
                        GlowCard(Modifier.fillMaxWidth().height(159.dp), onClick = {}) {
                            Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                NetworkIcon(Modifier.size(56.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("NETWORK MONITOR", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
                                    Text(if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE CONNECTION MONITOR", color = MUTED, fontSize = 8.sp)
                                    Spacer(Modifier.height(6.dp))
                                    Bars(graph, Modifier.fillMaxWidth().height(42.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f).height(52.dp))
                StatTile("CACHE CLEARED", formatBytes(storageFreed), Modifier.weight(1f).height(52.dp))
                if (!portrait) StatTile("APPS CLOSED", closedApps.toString(), Modifier.weight(1f).height(52.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (portrait) StatTile("APPS CLOSED", closedApps.toString(), Modifier.weight(1f).height(52.dp))
                StatTile("ROOT", if (rootAvailable) "ACTIVE" else "READY", Modifier.weight(1f).height(52.dp), if (rootAvailable) GREEN else MUTED)
                StatTile("ANDROID", Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }, Modifier.weight(1f).height(52.dp))
            }
            Spacer(Modifier.height(8.dp))
            StatTile("DEVICE", deviceLabel(), Modifier.fillMaxWidth().height(52.dp))
            Spacer(Modifier.height(10.dp))
            Bolt(Modifier.size(34.dp))
        }
    }
}
'''

marker = '\n@Composable\nprivate fun BottomSystemStrip('
if marker not in s:
    raise SystemExit('BottomSystemStrip insertion point not found')
s = s.replace(marker, mobile + marker, 1)

p.write_text(s)
print('Applied compact grouped ShadowFox mobile portrait and landscape layouts')
