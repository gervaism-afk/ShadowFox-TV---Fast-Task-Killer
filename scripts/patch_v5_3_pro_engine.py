from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

# Use the new root-first engine everywhere without disturbing the approved layouts.
s = s.replace('val optimizer = remember { AppOptimizer(context) }', 'val optimizer = remember { ShadowFoxProEngine(context) }')

# Add compact engine status state to both TV and mobile dashboards.
needle = '    var closedApps by remember { mutableIntStateOf(0) }\n'
replacement = needle + '    var engineStatus by remember { mutableStateOf("ENGINE READY") }\n'
count = s.count(needle)
if count < 2:
    raise SystemExit(f'Expected two closedApps state blocks, found {count}')
s = s.replace(needle, replacement)

# Detect and display root immediately on screen load, not only after first cleanup.
needle = '    LaunchedEffect(Unit) {\n        while (true) {'
replacement = '    LaunchedEffect(Unit) {\n        rootAvailable = optimizer.rootAvailable()\n        engineStatus = optimizer.diagnosticsSummary()\n        while (true) {'
count = s.count(needle)
if count < 2:
    raise SystemExit(f'Expected two LaunchedEffect blocks, found {count}')
s = s.replace(needle, replacement)

# Feed verified, measured cleanup summary back to the UI.
needle = '''            closedApps = result.closedApps
            busy = false'''
replacement = '''            closedApps = result.closedApps
            engineStatus = result.summary
            busy = false'''
count = s.count(needle)
if count < 2:
    raise SystemExit(f'Expected two cleanup result blocks, found {count}')
s = s.replace(needle, replacement)

# TV hero: add a tiny real-results status line under RAM BOOSTER while preserving spacing.
old_tv = '''                            Text("RAM BOOSTER", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.height(10.dp))
                            MasterButton(if (busy) "BOOSTING..." else "BOOST", 150.dp, !busy) { clean() }'''
new_tv = '''                            Text("RAM BOOSTER", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            Text(engineStatus, color = if (rootAvailable) GREEN else MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(7.dp))
                            MasterButton(if (busy) "DEEP CLEAN..." else "DEEP CLEAN", 150.dp, !busy) { clean() }'''
if old_tv not in s:
    raise SystemExit('TV RAM booster block not found')
s = s.replace(old_tv, new_tv, 1)

# Mobile hero mirrors the same verified status and deep-clean action.
old_mobile = '''                        Text("RAM BOOSTER", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(6.dp))
                        MasterButton(if (busy) "BOOSTING..." else "BOOST", 150.dp, !busy) { clean() }'''
new_mobile = '''                        Text("RAM BOOSTER", color = WHITE, fontSize = 19.sp, fontWeight = FontWeight.Black)
                        Text(engineStatus, color = if (rootAvailable) GREEN else MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        MasterButton(if (busy) "DEEP CLEAN..." else "DEEP CLEAN", 150.dp, !busy) { clean() }'''
if old_mobile not in s:
    raise SystemExit('Mobile RAM booster block not found')
s = s.replace(old_mobile, new_mobile, 1)

# Scan/cache buttons keep the same visual design but now run the stronger verified engine.
# Their labels make it clear they are real cleanup actions rather than decorative meters.
s = s.replace('MasterButton(if (busy) "SCANNING..." else "SCAN NOW", 105.dp, !busy) { clean() }',
              'MasterButton(if (busy) "SCANNING..." else "SCAN + CLEAN", 105.dp, !busy) { clean() }')
s = s.replace('MasterButton(if (busy) "CLEANING..." else "CLEAN NOW", 105.dp, !busy) { clean() }',
              'MasterButton(if (busy) "CLEANING..." else "CLEAR CACHE", 105.dp, !busy) { clean() }')
s = s.replace('MasterButton(if (busy) "CLEANING..." else "CLEAN NOW", 88.dp, !busy) { clean() }',
              'MasterButton(if (busy) "CLEANING..." else "CLEAR CACHE", 88.dp, !busy) { clean() }')

p.write_text(s)
print('Applied ShadowFox v5.3 Pro Engine UI integration')
