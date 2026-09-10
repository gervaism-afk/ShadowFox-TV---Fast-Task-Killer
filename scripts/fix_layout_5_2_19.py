from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

# 1) Fix the fixed 960x540 canvas scaling. The previous nested scaled wrapper
# could visually crop the bottom strip on wide phone landscape screens.
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
    raise SystemExit('Expected MasterDashboard scaling block not found')
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
    raise SystemExit('Expected MasterDashboard closing block not found')
s = s.replace(old_close, new_close, 1)

# 2) In phone landscape use the corrected full dashboard directly. This keeps
# the bottom RAM/cache/apps/root/device/android strip visible and centered.
old_landscape = '''                        landscape -> Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                            MasterDashboard(applicationContext)
                        }'''
new_landscape = '''                        landscape -> MasterDashboard(applicationContext)'''
if old_landscape not in s:
    raise SystemExit('Expected landscape wrapper not found')
s = s.replace(old_landscape, new_landscape, 1)

# 3) Pull the portrait dashboard inward slightly so the entire UI reads as one
# centered composition instead of touching the phone edges.
old_width = '        val contentWidth = minOf(maxWidth - 24.dp, 620.dp)'
new_width = '        val contentWidth = minOf(maxWidth - 40.dp, 560.dp)'
if old_width not in s:
    raise SystemExit('Expected mobile content width not found')
s = s.replace(old_width, new_width, 1)

# Center the header group inside the same portrait content column while keeping
# the same ShadowFox TV left/right visual relationship.
old_header = '''            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {'''
new_header = '''            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {'''
# Formatting-only but verifies we are editing the intended mobile header.
if old_header not in s:
    raise SystemExit('Expected mobile header row not found')
s = s.replace(old_header, new_header, 1)

p.write_text(s)
print('Applied v5.2.19 portrait centering and full landscape bottom-strip fix')
