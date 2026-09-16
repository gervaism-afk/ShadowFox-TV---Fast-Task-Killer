from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/UltimateCenterActivity.kt')
s = p.read_text()

for anchor, imp in [
    ('import androidx.compose.foundation.background\n', 'import androidx.compose.foundation.border\n'),
    ('import androidx.compose.ui.draw.shadow\n', 'import androidx.compose.ui.draw.scale\n'),
    ('import androidx.compose.ui.graphics.Brush\n', 'import androidx.compose.ui.focus.onFocusChanged\n'),
]:
    if imp not in s:
        if anchor not in s: raise SystemExit(f'Import anchor missing: {anchor.strip()}')
        s = s.replace(anchor, anchor + imp, 1)

def replace_function(name, next_marker, body):
    global s
    start = s.find('@Composable\nprivate fun ' + name + '(')
    end = s.find('\n\n' + next_marker, start)
    if start < 0 or end < 0:
        raise SystemExit(f'{name} function boundary not found')
    s = s[:start] + body + s[end:]

ultimate_button = '''@Composable
private fun UltimateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val bg = if (enabled) UCYAN else Color(0xFF31505A)
    Box(
        Modifier.height(38.dp)
            .scale(if (focused && enabled) 1.04f else 1f)
            .shadow(if (focused && enabled) 10.dp else 0.dp, shape)
            .background(bg, shape)
            .border(if (focused && enabled) 3.dp else 1.dp, if (focused && enabled) Color.White else UCYAN.copy(alpha = if (enabled) .75f else .25f), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF05202A), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp))
    }
}'''

compact_action = '''@Composable
private fun CompactAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(38.dp)
            .scale(if (focused) 1.04f else 1f)
            .shadow(if (focused) 10.dp else 0.dp, shape)
            .background(UCYAN, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else UCYAN.copy(alpha = .75f), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF05202A), fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}'''

tab_button = '''@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val bg = if (selected) UCYAN else Color(0xFF0A2637)
    val fg = if (selected) Color(0xFF05202A) else UWHITE
    Box(
        modifier.height(42.dp)
            .scale(if (focused) 1.04f else 1f)
            .shadow(if (focused) 10.dp else 0.dp, shape)
            .background(bg, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else if (selected) UCYAN else Color(0xFF17465B), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}'''

replace_function('UltimateButton', '@Composable\nprivate fun CompactAction', ultimate_button)
replace_function('CompactAction', '@Composable\nprivate fun UltimateTabButton', compact_action)
replace_function('UltimateTabButton', 'private fun formatUiBytes', tab_button)

# Guards: no legacy Material Button implementation or pill-shaped CompactAction should survive.
if 'Button(' in s:
    raise SystemExit('Legacy Material Button remains in UltimateCenterActivity')
if 'RoundedCornerShape(50)' in s:
    raise SystemExit('Legacy pill CompactAction remains')

p.write_text(s)
print('Applied unified Ultimate Center TV D-pad focus + cyan button system')
