from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

# Route phones to dedicated layouts. TV/Fire TV keep the approved MasterDashboard unchanged.
s = s.replace('landscape -> MasterDashboard(applicationContext)', 'landscape -> FinalMobileLandscapeDashboard(applicationContext)', 1)
s = s.replace('else -> MobileDashboard(applicationContext)', 'else -> FinalMobilePortraitDashboard(applicationContext)', 1)

marker = '\n@Composable\nprivate fun MobileDashboard(context: Context) {'
if marker not in s:
    raise SystemExit('MobileDashboard insertion point not found')

final_layouts = r'''

@Composable
private fun FinalMobilePortraitDashboard(context: Context) {
    var ram by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var apps by remember { mutableIntStateOf(runningProcessCount(context)) }
    var mbps by remember { mutableFloatStateOf(0f) }
    var ping by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var ramFreed by remember { mutableStateOf(0L) }
    var storageFreed by remember { mutableStateOf(0L) }
    var closedApps by remember { mutableIntStateOf(0) }
    var engineStatus by remember { mutableStateOf("ENGINE READY") }
    val graph = remember { mutableStateListOf<Int>() }
    val optimizer = remember { ShadowFoxProEngine(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rootAvailable = optimizer.rootAvailable()
        engineStatus = optimizer.diagnosticsSummary()
        while (true) {
            val before = totalTrafficBytes(); delay(1000); val after = totalTrafficBytes()
            if (before >= 0 && after >= before) mbps = (after - before) * 8f / 1_000_000f
            ping = measureLatencyMs()
            if (ping > 0) { graph.add(ping); while (graph.size > 18) graph.removeAt(0) }
            ram = memoryUsedPercent(context); apps = runningProcessCount(context)
        }
    }

    fun clean() {
        if (busy) return
        scope.launch {
            busy = true
            val result = optimizer.optimize()
            delay(350)
            ram = memoryUsedPercent(context); apps = runningProcessCount(context)
            rootAvailable = result.rootUsed; ramFreed = result.ramFreedBytes
            storageFreed = result.storageFreedBytes; closedApps = result.closedApps
            engineStatus = result.summary; busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        MasterBackdrop()
        val contentWidth = minOf(maxWidth - 32.dp, 560.dp)
        Column(
            Modifier.width(contentWidth).align(Alignment.TopCenter).verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("ShadowFox", color = WHITE, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Spacer(Modifier.width(4.dp)); Text("TV", color = ORANGE, fontSize = 24.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    }
                    Text("www.shadowfoxtv.ca", color = MUTED, fontSize = 8.sp)
                    Text("PRO ENGINE • v${BuildConfig.VERSION_NAME}", color = CYAN, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                Image(painter = painterResource(R.drawable.shadowfox_logo), contentDescription = "ShadowFox TV", contentScale = ContentScale.Fit, modifier = Modifier.size(62.dp))
            }

            Spacer(Modifier.height(8.dp))
            GlowCard(Modifier.fillMaxWidth().height(220.dp), onClick = { clean() }, hero = true) {
                Column(Modifier.fillMaxSize().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    RamGauge(ram, Modifier.size(142.dp))
                    Spacer(Modifier.height(1.dp))
                    Text("RAM BOOSTER", color = WHITE, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text(engineStatus, color = if (rootAvailable) GREEN else MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    MasterButton(if (busy) "DEEP CLEAN..." else "DEEP CLEAN", 142.dp, !busy) { clean() }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlowCard(Modifier.weight(1f).height(174.dp), onClick = { clean() }) {
                    Column(Modifier.fillMaxSize().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        ScanDial(Modifier.size(78.dp))
                        Spacer(Modifier.height(3.dp))
                        Text("SYSTEM SCAN", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text("$apps ACTIVE PROCESSES", color = MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        MasterButton(if (busy) "SCANNING..." else "SCAN + CLEAN", 104.dp, !busy) { clean() }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlowCard(Modifier.fillMaxWidth().height(83.dp), onClick = { clean() }) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Broom(Modifier.size(32.dp)); Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text("CACHE CLEANER", color = WHITE, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                Text("Trim app cache", color = MUTED, fontSize = 6.sp)
                                Spacer(Modifier.height(3.dp))
                                MasterButton(if (busy) "CLEANING..." else "CLEAR CACHE", 82.dp, !busy) { clean() }
                            }
                        }
                    }
                    GlowCard(Modifier.fillMaxWidth().height(83.dp), onClick = { context.startActivity(Intent(context, UltimateCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            NetworkIcon(Modifier.size(30.dp)); Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text("ULTIMATE CENTER", color = WHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
                                Text("TAP TO OPEN", color = CYAN, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                                Text(if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE MONITOR", color = MUTED, fontSize = 6.sp, maxLines = 1)
                                Spacer(Modifier.height(2.dp)); Bars(graph, Modifier.fillMaxWidth().height(13.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileStatTile("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f).height(66.dp))
                MobileStatTile("CACHE CLEARED", formatBytes(storageFreed), Modifier.weight(1f).height(66.dp))
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileStatTile("APPS CLOSED", closedApps.toString(), Modifier.weight(1f).height(66.dp))
                MobileStatTile("ROOT", if (rootAvailable) "ACTIVE" else "STANDARD", Modifier.weight(1f).height(66.dp), if (rootAvailable) GREEN else WHITE)
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileStatTile("DEVICE", deviceLabel(), Modifier.weight(1.45f).height(66.dp), compactValue = true)
                MobileStatTile("ANDROID", Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }, Modifier.weight(.75f).height(66.dp))
            }
            Spacer(Modifier.height(12.dp)); Bolt(Modifier.size(34.dp)); Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FinalMobileLandscapeDashboard(context: Context) {
    var ram by remember { mutableFloatStateOf(memoryUsedPercent(context)) }
    var apps by remember { mutableIntStateOf(runningProcessCount(context)) }
    var mbps by remember { mutableFloatStateOf(0f) }
    var ping by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var rootAvailable by remember { mutableStateOf(false) }
    var ramFreed by remember { mutableStateOf(0L) }
    var storageFreed by remember { mutableStateOf(0L) }
    var closedApps by remember { mutableIntStateOf(0) }
    var engineStatus by remember { mutableStateOf("ENGINE READY") }
    val graph = remember { mutableStateListOf<Int>() }
    val optimizer = remember { ShadowFoxProEngine(context) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rootAvailable = optimizer.rootAvailable(); engineStatus = optimizer.diagnosticsSummary()
        while (true) {
            val before = totalTrafficBytes(); delay(1000); val after = totalTrafficBytes()
            if (before >= 0 && after >= before) mbps = (after - before) * 8f / 1_000_000f
            ping = measureLatencyMs()
            if (ping > 0) { graph.add(ping); while (graph.size > 18) graph.removeAt(0) }
            ram = memoryUsedPercent(context); apps = runningProcessCount(context)
        }
    }

    fun clean() {
        if (busy) return
        scope.launch {
            busy = true; val result = optimizer.optimize(); delay(300)
            ram = memoryUsedPercent(context); apps = runningProcessCount(context)
            rootAvailable = result.rootUsed; ramFreed = result.ramFreedBytes; storageFreed = result.storageFreedBytes
            closedApps = result.closedApps; engineStatus = result.summary; busy = false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        MasterBackdrop()
        val side = 14.dp
        val mainHeight = (maxHeight - 118.dp).coerceAtLeast(190.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = side, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("ShadowFox", color = WHITE, fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                        Spacer(Modifier.width(4.dp)); Text("TV", color = ORANGE, fontSize = 20.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    }
                    Row { Text("www.shadowfoxtv.ca", color = MUTED, fontSize = 7.sp); Spacer(Modifier.width(10.dp)); Text("PRO ENGINE • v${BuildConfig.VERSION_NAME}", color = CYAN, fontSize = 6.sp, fontWeight = FontWeight.Bold) }
                }
                Image(painter = painterResource(R.drawable.shadowfox_logo), contentDescription = "ShadowFox TV", contentScale = ContentScale.Fit, modifier = Modifier.size(48.dp))
            }

            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth().height(mainHeight), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                GlowCard(Modifier.weight(.82f).fillMaxSize(), onClick = { clean() }) {
                    Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        ScanDial(Modifier.size(82.dp))
                        Text("SYSTEM SCAN", color = WHITE, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        Text("$apps ACTIVE PROCESSES", color = MUTED, fontSize = 6.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp)); MasterButton(if (busy) "SCANNING..." else "SCAN + CLEAN", 102.dp, !busy) { clean() }
                    }
                }
                GlowCard(Modifier.weight(1.08f).fillMaxSize(), onClick = { clean() }, hero = true) {
                    Column(Modifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        RamGauge(ram, Modifier.size(132.dp))
                        Text("RAM BOOSTER", color = WHITE, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text(engineStatus, color = if (rootAvailable) GREEN else MUTED, fontSize = 6.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp)); MasterButton(if (busy) "DEEP CLEAN..." else "DEEP CLEAN", 136.dp, !busy) { clean() }
                    }
                }
                Column(Modifier.weight(1.12f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    GlowCard(Modifier.fillMaxWidth().weight(1f), onClick = { clean() }) {
                        Row(Modifier.fillMaxSize().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Broom(Modifier.size(42.dp)); Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text("CACHE CLEANER", color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                Text("Trim app cache without deleting data.", color = MUTED, fontSize = 6.sp, maxLines = 1)
                                Spacer(Modifier.height(5.dp)); MasterButton(if (busy) "CLEANING..." else "CLEAR CACHE", 92.dp, !busy) { clean() }
                            }
                        }
                    }
                    GlowCard(Modifier.fillMaxWidth().weight(1f), onClick = { context.startActivity(Intent(context, UltimateCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) {
                        Row(Modifier.fillMaxSize().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            NetworkIcon(Modifier.size(42.dp)); Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text("ULTIMATE CENTER", color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                Text("PRESS TO OPEN • OPTIMIZE • APPS • NETWORK • SYSTEM", color = CYAN, fontSize = 5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE MONITOR", color = MUTED, fontSize = 6.sp)
                                Spacer(Modifier.height(3.dp)); Bars(graph, Modifier.fillMaxWidth().height(20.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MobileStatTile("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f).fillMaxSize())
                MobileStatTile("CACHE CLEARED", formatBytes(storageFreed), Modifier.weight(1f).fillMaxSize())
                MobileStatTile("APPS CLOSED", closedApps.toString(), Modifier.weight(1f).fillMaxSize())
                MobileStatTile("ROOT", if (rootAvailable) "ACTIVE" else "STANDARD", Modifier.weight(1f).fillMaxSize(), if (rootAvailable) GREEN else WHITE)
                MobileStatTile("DEVICE", deviceLabel(), Modifier.weight(1.35f).fillMaxSize(), compactValue = true)
                MobileStatTile("ANDROID", Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }, Modifier.weight(.8f).fillMaxSize())
            }
        }
    }
}
'''

s = s.replace(marker, final_layouts + marker, 1)
p.write_text(s)
print('Applied locked v6.0.4 portrait and landscape main dashboard layouts; TV/Fire TV master preserved')
