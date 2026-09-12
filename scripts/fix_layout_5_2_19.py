from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

# Keep the 960x540 master canvas as the dedicated Android TV / Fire TV layout.
# Scale it exactly once so every edge, including the bottom stats strip, remains visible.
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
if old_scale in s:
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
if old_close in s:
    s = s.replace(old_close, new_close, 1)

# Phones in landscape get a protected viewport around the TV-style dashboard.
# This prevents tall/wide Samsung screens and gesture/navigation insets from clipping the bottom row.
old_landscape = '''                        landscape -> Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                            MasterDashboard(applicationContext)
                        }'''
new_landscape = '''                        landscape -> Box(
                            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp)
                        ) {
                            MasterDashboard(applicationContext)
                        }'''
if old_landscape in s:
    s = s.replace(old_landscape, new_landscape, 1)
else:
    old_direct = '''                        landscape -> MasterDashboard(applicationContext)'''
    if old_direct not in s:
        raise SystemExit('Expected phone landscape route not found')
    s = s.replace(old_direct, new_landscape, 1)

# Portrait is a separate phone composition: narrower, shorter cards and more breathing room.
s = s.replace('val contentWidth = minOf(maxWidth - 24.dp, 620.dp)', 'val contentWidth = minOf(maxWidth - 48.dp, 520.dp)', 1)
s = s.replace('val contentWidth = minOf(maxWidth - 40.dp, 560.dp)', 'val contentWidth = minOf(maxWidth - 48.dp, 520.dp)', 1)
s = s.replace('GlowCard(Modifier.fillMaxWidth().height(245.dp)', 'GlowCard(Modifier.fillMaxWidth().height(214.dp)', 1)
s = s.replace('.padding(top = 2.dp).size(178.dp)', '.padding(top = 0.dp).size(154.dp)', 1)
s = s.replace('GlowCard(Modifier.weight(1f).height(178.dp)', 'GlowCard(Modifier.weight(1f).height(160.dp)', 1)
s = s.replace('.padding(top = 8.dp).size(92.dp)', '.padding(top = 6.dp).size(78.dp)', 1)
s = s.replace('GlowCard(Modifier.fillMaxWidth().height(85.dp)', 'GlowCard(Modifier.fillMaxWidth().height(76.dp)', 2)
s = s.replace('MobileStatTile("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f).height(58.dp))', 'MobileStatTile("RAM FREED", formatBytes(ramFreed), Modifier.weight(1f).height(54.dp))', 1)
s = s.replace('MobileStatTile("CACHE CLEARED", formatBytes(storageFreed), Modifier.weight(1f).height(58.dp))', 'MobileStatTile("CACHE CLEARED", formatBytes(storageFreed), Modifier.weight(1f).height(54.dp))', 1)
s = s.replace('MobileStatTile("APPS CLOSED", closedApps.toString(), Modifier.weight(1f).height(58.dp))', 'MobileStatTile("APPS CLOSED", closedApps.toString(), Modifier.weight(1f).height(54.dp))', 1)
s = s.replace('MobileStatTile("ROOT", if (rootAvailable) "ACTIVE" else "READY", Modifier.weight(1f).height(58.dp)', 'MobileStatTile("ROOT", if (rootAvailable) "ACTIVE" else "READY", Modifier.weight(1f).height(54.dp)', 1)
s = s.replace('MobileStatTile("DEVICE", deviceLabel(), Modifier.weight(1.45f).height(58.dp)', 'MobileStatTile("DEVICE", deviceLabel(), Modifier.weight(1.45f).height(54.dp)', 1)
s = s.replace('MobileStatTile("ANDROID", Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }, Modifier.weight(.75f).height(58.dp))', 'MobileStatTile("ANDROID", Build.VERSION.RELEASE.orEmpty().ifBlank { "Unknown" }, Modifier.weight(.75f).height(54.dp))', 1)

p.write_text(s)
print('Applied final independent portrait, phone-landscape, and TV/Fire TV viewport layout')
