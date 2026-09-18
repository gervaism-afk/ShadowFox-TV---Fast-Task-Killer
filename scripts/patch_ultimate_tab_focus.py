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
    val shape = RoundedCornerShape(4.dp)
    val bg = if (enabled) Brush.verticalGradient(listOf(Color(0xFF2A3042), Color(0xFF1E2235), Color(0xFF141824))) else Brush.verticalGradient(listOf(Color(0xFF1E2235), Color(0xFF1E2235)))
    Box(
        Modifier.height(38.dp)
            .scale(if (focused && enabled) 1.04f else 1f)
            .shadow(if (focused && enabled) 10.dp else 0.dp, shape)
            .background(bg, shape)
            .border(if (focused && enabled) 3.dp else 1.dp, if (focused && enabled) Color(0xFF8EBBFF) else Color(0xFF4D648D), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = UWHITE, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp))
    }
}'''

compact_action = '''@Composable
private fun CompactAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier.height(38.dp)
            .scale(if (focused) 1.04f else 1f)
            .shadow(if (focused) 10.dp else 0.dp, shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A3042), Color(0xFF1E2235), Color(0xFF141824))), shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF8EBBFF) else Color(0xFF4D648D), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = UWHITE, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
    }
}'''

tab_button = '''@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    val bg = if (selected) Brush.verticalGradient(listOf(Color(0xFF2A3042), Color(0xFF1E2235), Color(0xFF141824))) else Brush.verticalGradient(listOf(Color(0xFF1E2235), Color(0xFF1E2235)))
    val fg = UWHITE
    Box(
        modifier.height(42.dp)
            .scale(if (focused) 1.04f else 1f)
            .shadow(if (focused) 10.dp else 0.dp, shape)
            .background(bg, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color(0xFF8EBBFF) else Color(0xFF4D648D), shape)
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

# Guard only the three control implementations we replaced. Other Material Buttons elsewhere
# in Ultimate Center are intentionally left alone until their behavior is audited separately.
if 'RoundedCornerShape(50)' in s:
    raise SystemExit('Legacy pill CompactAction remains')
for marker in ['private fun UltimateButton', 'private fun CompactAction', 'private fun UltimateTabButton']:
    if marker not in s:
        raise SystemExit(f'Missing unified control: {marker}')

p.write_text(s)
print('Applied v6.1.27 metallic 4dp TV D-pad control system')
