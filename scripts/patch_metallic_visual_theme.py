from pathlib import Path
import re

ROOT=Path("extracted/app/src/main/java/ca/shadowfoxtv/taskkiller")
files=[ROOT/"MainActivity.kt",ROOT/"AdvancedToolsActivity.kt",ROOT/"UltimateCenterActivity.kt"]

for p in files:
    s=p.read_text()
    if "import androidx.compose.foundation.border" not in s:
        s=s.replace("import androidx.compose.foundation.background","import androidx.compose.foundation.background\nimport androidx.compose.foundation.border",1)
    if "import androidx.compose.ui.graphics.Brush" not in s and "import androidx.compose.ui.graphics.Color" in s:
        s=s.replace("import androidx.compose.ui.graphics.Color","import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Brush",1)

    # Absolute root canvas. Remove every legacy blue canvas value.
    for old in ["03111D","0A0F15","111820","080C11"]:
        s=s.replace("0xFF"+old,"0xFF0F111A")

    # All legacy panel/card fills become one anodized gunmetal surface.
    for old in ["0A2030","092337","0A2637","111C27","152536","20384F","101A24","182B3D"]:
        s=re.sub(r"0x(?:FF|EA|E6)"+old, "0xFF1E2235", s)

    # Hardware edge radius is globally capped at 4dp.
    s=re.sub(r"RoundedCornerShape\((?:\d+(?:\.\d+)?)(?:\.dp)?\)", "RoundedCornerShape(4.dp)", s)

    # Metallic outline and reflective active-control palette.
    s=s.replace("0xFF6889A5","0xFF4D648D").replace("0xFF9AB7CE","0xFF4D648D")
    s=s.replace("Color(0xFF477FA9), Color(0xFF183650), Color(0xFF315F82)",
                "Color(0xFF3A7BD5), Color(0xFF2A52BE), Color(0xFF1A365D)")
    s=s.replace("Color(0xFF477FA9), Color(0xFF183650)",
                "Color(0xFF3A7BD5), Color(0xFF2A52BE), Color(0xFF1A365D)")
    s=s.replace("Color(0xFF273552)","Color(0xFF2A52BE)")
    p.write_text(s)

print("FORCED ShadowFox root metallic theme: canvas #0F111A, panels #1E2235, edge #4D648D, active gradient #3A7BD5/#2A52BE/#1A365D, max radius 4dp")


# v6.1.22 authoritative generated-source visual override.
# This runs LAST, after legacy layout patches, and removes the global blue wash itself.
main = ROOT/"MainActivity.kt"
m = main.read_text()
m = re.sub(
    r'@Composable\nprivate fun MasterBackdrop\(\) \{.*?\n\}',
    '''@Composable
private fun MasterBackdrop() {
    Box(Modifier.fillMaxSize().background(Color(0xFF0F111A)))
}''',
    m, count=1, flags=re.S
)
# Main dashboard card surface: flat gunmetal + mandatory titanium 1dp edge.
m = m.replace(
    '.background(Brush.verticalGradient(listOf(Color(0xFF1E2235), Color(0xED04131F))), shape)',
    '.background(Color(0xFF1E2235), shape)\n            .border(1.dp, Color(0xFF4D648D), shape)'
)
# Main dashboard buttons: exact reflective 3-stop vertical metallic gradient + 1dp edge.
m = m.replace(
    '.background(Brush.horizontalGradient(listOf(Color(0xFF16E7F4), Color(0xFF08A9D4))), shape)',
    '.background(Brush.verticalGradient(listOf(Color(0xFF3A7BD5), Color(0xFF2A52BE), Color(0xFF1A365D))), shape)\n            .border(1.dp, Color(0xFF4D648D), shape)'
)
# Canvas-drawn card edge was still rounded independently of Compose shape.
m = m.replace('cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())',
              'cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())')
main.write_text(m)

ultimate = ROOT/"UltimateCenterActivity.kt"
u = ultimate.read_text()
# Remove Ultimate Center's separate blue global wrapper gradient.
u = u.replace(
    'Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0F111A), Color(0xFF061B29))))',
    'Modifier.fillMaxSize().background(Color(0xFF0F111A))'
)
# Flat gunmetal panels always get titanium edge.
u = u.replace(
    '.background(UPANEL, RoundedCornerShape(4.dp)).padding(vertical = 11.dp, horizontal = 8.dp)',
    '.background(UPANEL, RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp)).padding(vertical = 11.dp, horizontal = 8.dp)'
)
u = u.replace(
    '.background(UPANEL, RoundedCornerShape(4.dp)).padding(13.dp)',
    '.background(UPANEL, RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp)).padding(13.dp)'
)
# Legacy flat cyan custom controls become exact reflective metal.
u = u.replace(
    '.background(UCYAN, RoundedCornerShape(4.dp)).clickable(onClick = onClick).focusable()',
    '.background(Brush.verticalGradient(listOf(Color(0xFF3A7BD5), Color(0xFF2A52BE), Color(0xFF1A365D))), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp)).clickable(onClick = onClick).focusable()'
)
ultimate.write_text(u)

print("v6.1.22 LAST-PASS: killed global blue wrappers; forced solid #0F111A canvas, #1E2235 panels + #4D648D 1dp edges, 4dp geometry, reflective 3-stop controls")


# v6.1.23 comprehensive sub-screen/detail-view pass.
# Runs after every legacy patch so old generated UI cannot restore flat/cyan controls.
for p in [ROOT/"AdvancedToolsActivity.kt", ROOT/"UltimateCenterActivity.kt"]:
    s=p.read_text()
    # Every nested canvas is the same master charcoal.
    s=s.replace("Modifier.fillMaxSize().background(ABG)", "Modifier.fillMaxSize().background(Color(0xFF0F111A))")
    s=s.replace("Modifier.fillMaxSize().background(UBG)", "Modifier.fillMaxSize().background(Color(0xFF0F111A))")
    # Premium list/detail modules: gunmetal surface, titanium edge, 4dp hardware geometry.
    s=s.replace(".background(APANEL, RoundedCornerShape(4.dp)).padding(",
                ".background(Color(0xFF1E2235), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp)).padding(")
    s=s.replace(".background(UPANEL, RoundedCornerShape(4.dp)).padding(",
                ".background(Color(0xFF1E2235), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp)).padding(")
    # Avoid duplicate borders if a prior pass already supplied one.
    s=s.replace(".border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp))",
                ".border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp))")
    p.write_text(s)

u=(ROOT/"UltimateCenterActivity.kt").read_text()
# Replace Material3 flat cyan button with the same custom reflective hardware used everywhere else.
u=re.sub(r'''@Composable
private fun UltimateButton\(text: String, enabled: Boolean = true, onClick: \(\) -> Unit\) \{.*?
\}''', '''@Composable
private fun UltimateButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(36.dp)
            .background(
                if (enabled) Brush.verticalGradient(listOf(Color(0xFF3A7BD5), Color(0xFF2A52BE), Color(0xFF1A365D)))
                else Brush.verticalGradient(listOf(Color(0xFF1E2235), Color(0xFF1E2235))),
                RoundedCornerShape(4.dp)
            )
            .border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = UWHITE, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}''', u, count=1, flags=re.S)

u=re.sub(r'''@Composable
private fun CompactAction\(text: String, modifier: Modifier, onClick: \(\) -> Unit\) \{.*?
\}''', '''@Composable
private fun CompactAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(38.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFF3A7BD5), Color(0xFF2A52BE), Color(0xFF1A365D))), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = UWHITE, fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}''', u, count=1, flags=re.S)

u=re.sub(r'''@Composable
private fun UltimateTabButton\(text: String, selected: Boolean, modifier: Modifier, onClick: \(\) -> Unit\) \{.*?
\}''', '''@Composable
private fun UltimateTabButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fill = if (selected) Brush.verticalGradient(listOf(Color(0xFF3A7BD5), Color(0xFF2A52BE), Color(0xFF1A365D)))
               else Brush.verticalGradient(listOf(Color(0xFF1E2235), Color(0xFF1E2235)))
    Box(
        modifier.height(42.dp).background(fill, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick).focusable(),
        contentAlignment = Alignment.Center
    ) { Text(text, color = UWHITE, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1) }
}''', u, count=1, flags=re.S)
(ROOT/"UltimateCenterActivity.kt").write_text(u)

print("v6.1.23 SUB-SCREEN PASS: charcoal nested canvases; gunmetal 1dp modules; 4dp geometry; 3-stop metallic controls across Advanced + Ultimate detail views")
