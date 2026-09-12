from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

launch = 'context.startActivity(Intent(context, UltimateCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))'

# Make the existing Network cards the obvious, selectable entry point to ShadowFox Ultimate Center.
repls = {
    'GlowCard(Modifier.offset(592.dp, 282.dp).size(320.dp, 128.dp), onClick = {})':
        f'GlowCard(Modifier.offset(592.dp, 282.dp).size(320.dp, 128.dp), onClick = {{ {launch} }})',
    'GlowCard(Modifier.fillMaxWidth().height(85.dp), onClick = {})':
        f'GlowCard(Modifier.fillMaxWidth().height(85.dp), onClick = {{ {launch} }})',
}
for old, new in repls.items():
    if old not in s:
        raise SystemExit(f'Expected dashboard entry point not found: {old[:80]}')
    s = s.replace(old, new, 1)

# TV: make Ultimate Center the primary title and keep live network status visible.
old_tv = '''Text("NETWORK MONITOR", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text(
                                if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE CONNECTION MONITOR",
                                color = MUTED,
                                fontSize = 8.sp
                            )'''
new_tv = '''Text("ULTIMATE CENTER", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("PRESS TO OPEN • OPTIMIZE • APPS • NETWORK • SYSTEM", color = CYAN, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE CONNECTION MONITOR",
                                color = MUTED,
                                fontSize = 8.sp
                            )'''
if old_tv not in s:
    raise SystemExit('TV network title block not found')
s = s.replace(old_tv, new_tv, 1)

# Mobile: replace the ambiguous Network title with a clear center launcher.
old_mobile = '''Text("NETWORK", color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                Text(if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE MONITOR", color = MUTED, fontSize = 7.sp)'''
new_mobile = '''Text("ULTIMATE CENTER", color = WHITE, fontSize = 12.sp, fontWeight = FontWeight.Black)
                                Text("TAP TO OPEN", color = CYAN, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                Text(if (ping > 0) "${String.format("%.1f", mbps)} Mbps • ${ping} ms" else "LIVE MONITOR", color = MUTED, fontSize = 7.sp)'''
if old_mobile not in s:
    raise SystemExit('Mobile network title block not found')
s = s.replace(old_mobile, new_mobile, 1)

# Never show contradictory ROOT READY when root is not actually granted/detected.
s = s.replace('StatTile("ROOT", if (root) "ACTIVE" else "READY"', 'StatTile("ROOT", if (root) "ACTIVE" else "STANDARD"')
s = s.replace('MobileStatTile("ROOT", if (rootAvailable) "ACTIVE" else "READY"', 'MobileStatTile("ROOT", if (rootAvailable) "ACTIVE" else "STANDARD"')

p.write_text(s)

# v6.0.2 responsive Ultimate Center compile fix.
# Pass the local load function as a callback instead of invoking/coercing it incorrectly.
u = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/UltimateCenterActivity.kt')
us = u.read_text()
old_callback = 'AppActions(manager, item, load, { message = it }, scope)'
new_callback = 'AppActions(manager, item, { load() }, { message = it }, scope)'
if old_callback not in us:
    raise SystemExit('Expected Ultimate Center Apps callback not found')
us = us.replace(old_callback, new_callback, 1)
u.write_text(us)

print('ShadowFox 6.0.2 Ultimate Center entry, root labels, and mobile callback fixed')
