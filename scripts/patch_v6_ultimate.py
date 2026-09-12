from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

# Make the existing Network Monitor cards the entry point to the new Ultimate Center.
repls = {
    'GlowCard(Modifier.offset(592.dp, 282.dp).size(320.dp, 128.dp), onClick = {})':
        'GlowCard(Modifier.offset(592.dp, 282.dp).size(320.dp, 128.dp), onClick = { context.startActivity(Intent(context, UltimateCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })',
    'GlowCard(Modifier.fillMaxWidth().height(85.dp), onClick = {})':
        'GlowCard(Modifier.fillMaxWidth().height(85.dp), onClick = { context.startActivity(Intent(context, UltimateCenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })',
}
for old, new in repls.items():
    if old not in s:
        raise SystemExit(f'Expected dashboard entry point not found: {old[:80]}')
    s = s.replace(old, new, 1)

s = s.replace(
    'Text("NETWORK MONITOR", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)',
    'Text("NETWORK MONITOR", color = WHITE, fontSize = 17.sp, fontWeight = FontWeight.Black)\n                            Text("PRESS FOR ULTIMATE CENTER", color = CYAN, fontSize = 7.sp, fontWeight = FontWeight.Bold)',
    1
)
s = s.replace(
    'Text("NETWORK", color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Black)',
    'Text("NETWORK", color = WHITE, fontSize = 13.sp, fontWeight = FontWeight.Black)\n                                Text("ULTIMATE CENTER", color = CYAN, fontSize = 6.sp, fontWeight = FontWeight.Bold)',
    1
)

p.write_text(s)
print('ShadowFox 6 Ultimate Center wired into TV and mobile dashboards')
