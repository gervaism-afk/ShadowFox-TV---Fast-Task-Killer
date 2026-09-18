from pathlib import Path

files = [
 Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt'),
 Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/AdvancedToolsActivity.kt'),
 Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/UltimateCenterActivity.kt'),
]
for p in files:
    s=p.read_text()
    if 'import androidx.compose.foundation.border' not in s:
        s=s.replace('import androidx.compose.foundation.background\n','import androidx.compose.foundation.background\nimport androidx.compose.foundation.border\n',1)
    if 'import androidx.compose.ui.graphics.Brush' not in s:
        # Some generated screens import Color but not Brush; metallic gradients require both.
        if 'import androidx.compose.ui.graphics.Color\n' in s:
            s=s.replace('import androidx.compose.ui.graphics.Color\n','import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Brush\n',1)
        else:
            raise SystemExit(f'Missing Color import anchor in {p}')
    # Core ShadowFox metallic palette: dark slate, steel-blue surfaces, silver-blue edges.
    reps={
      'Color(0xFF0F111A)':'Color(0xFF0A0F15)',
      'Color(0xFF1E2235)':'Color(0xFF111C27)',
      'Color(0xFF1E2235)':'Color(0xFF152536)',
      'Color(0xED04131F)':'Color(0xFF0C151F)',
      'Color(0xFF061B29)':'Color(0xFF111820)',
      'Color(0xFF1E2235)':'Color(0xFF152536)',
      'Color(0xFF00E5FF)':'Color(0xFF5AA9E6)',
      'Color(0xFF08AEEA)':'Color(0xFF3D7FB3)',
      'Color(0xD90A1C29)':'Color(0xFF111C27)',
      'Color(0xFF16E7F4)':'Color(0xFF3E78A6)',
      'Color(0xFF08A9D4)':'Color(0xFF1D4262)',
      'Color(0xFF31505A)':'Color(0xFF344554)',
      'Color(0xFF17465B)':'Color(0xFF4D648D)',
    }
    for a,b in reps.items(): s=s.replace(a,b)
    # Crisp industrial geometry everywhere.
    s=s.replace('RoundedCornerShape(50)', 'RoundedCornerShape(4.dp)')
    s=s.replace('RoundedCornerShape(20.dp)', 'RoundedCornerShape(4.dp)')
    s=s.replace('RoundedCornerShape(12.dp)', 'RoundedCornerShape(4.dp)')
    s=s.replace('RoundedCornerShape(10.dp)', 'RoundedCornerShape(4.dp)')
    # Main dashboard metallic card and button fills.
    s=s.replace('Brush.verticalGradient(listOf(Color(0xFF152536), Color(0xFF0C151F)))',
                'Brush.verticalGradient(listOf(Color(0xFF1E2235), Color(0xFF1E2235), Color(0xFF1E2235)))')
    s=s.replace('Brush.horizontalGradient(listOf(Color(0xFF3E78A6), Color(0xFF1D4262)))',
                'Brush.verticalGradient(listOf(Color(0xFF477FA9), Color(0xFF183650), Color(0xFF315F82)))')
    # Remove bright cyan page glow; use dark metallic slate.
    s=s.replace('Brush.verticalGradient(listOf(Color(0xFF0A0F15), Color(0xFF111820)))',
                'Brush.verticalGradient(listOf(Color(0xFF0F111A), Color(0xFF0F111A)))')
    p.write_text(s)

# Add sharp silver-blue outlines to the Advanced core controls.
p=files[1]; s=p.read_text()
s=s.replace('.background(APANEL, RoundedCornerShape(4.dp)).padding(12.dp)',
            '.background(Brush.verticalGradient(listOf(Color(0xFF20384F), Color(0xFF101A24))), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF6889A5), RoundedCornerShape(4.dp)).padding(12.dp)')
s=s.replace('.background(ACYAN, RoundedCornerShape(4.dp)).clickable',
            '.background(Brush.verticalGradient(listOf(Color(0xFF477FA9), Color(0xFF183650))), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF4D648D), RoundedCornerShape(4.dp)).clickable')
p.write_text(s)

# Add metallic surfaces + outlines to Ultimate cards/panels after focus patch has run.
p=files[2]; s=p.read_text()
s=s.replace('.background(UPANEL, RoundedCornerShape(4.dp)).padding',
            '.background(Brush.verticalGradient(listOf(Color(0xFF20384F), Color(0xFF101A24))), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF6889A5), RoundedCornerShape(4.dp)).padding')
p.write_text(s)

print('Applied ShadowFox dark blue metallic visual system to all three app surfaces')

