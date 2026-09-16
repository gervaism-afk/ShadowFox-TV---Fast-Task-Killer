from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/UltimateCenterActivity.kt')
s = p.read_text()

if 'import androidx.compose.foundation.border\n' not in s:
    s = s.replace('import androidx.compose.foundation.background\n', 'import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\n')
if 'import androidx.compose.ui.draw.scale\n' not in s:
    s = s.replace('import androidx.compose.ui.draw.shadow\n', 'import androidx.compose.ui.draw.shadow\nimport androidx.compose.ui.draw.scale\n')
if 'import androidx.compose.ui.focus.onFocusChanged\n' not in s:
    s = s.replace('import androidx.compose.ui.graphics.Brush\n', 'import androidx.compose.ui.focus.onFocusChanged\nimport androidx.compose.ui.graphics.Brush\n')

old = '''@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(42.dp).background(if (selected) UCYAN else Color(0xFF0A2637), RoundedCornerShape(12.dp)).clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) UBG else UWHITE, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}'''
new = '''@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val background = when { focused -> Color(0xFF123F55); selected -> UCYAN; else -> Color(0xFF0A2637) }
    val textColor = when { focused -> UWHITE; selected -> UBG; else -> UWHITE }
    Box(
        modifier.height(42.dp)
            .scale(if (focused) 1.06f else 1f)
            .shadow(if (focused) 14.dp else 0.dp, shape)
            .background(background, shape)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else if (selected) UCYAN else Color(0xFF17465B), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = textColor, fontSize = if (focused) 12.sp else 11.sp, fontWeight = FontWeight.Black)
    }
}'''
if old not in s:
    raise SystemExit('UltimateTabButton baseline did not match; refusing unsafe patch')
p.write_text(s.replace(old, new))
print('Applied Ultimate Center D-pad focus glow/scale patch')
