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
