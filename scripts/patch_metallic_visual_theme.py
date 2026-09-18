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

advanced = ROOT/"AdvancedToolsActivity.kt"
a = advanced.read_text()
# Guarantee Advanced Tools root is the same solid charcoal slate.
a = re.sub(r'Modifier\.fillMaxSize\(\)\.background\([^\n]+\)',
           'Modifier.fillMaxSize().background(Color(0xFF0F111A))', a)
advanced.write_text(a)

print("v6.1.22 LAST-PASS: killed global blue wrappers; forced solid #0F111A canvas, #1E2235 panels + #4D648D 1dp edges, 4dp geometry, reflective 3-stop controls")
