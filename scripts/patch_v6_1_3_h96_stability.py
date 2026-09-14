from pathlib import Path

base = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller')

# H96/legacy Android TV stability: keep visible D-pad focus but avoid GPU-heavy
# animated shadow/scale focus effects that can be unreliable on some Rockchip ROMs.
for name in ['UltimateCenterActivity.kt', 'AdvancedToolsActivity.kt']:
    p = base / name
    s = p.read_text()
    s = s.replace('.scale(if (focused) 1.08f else 1f)\n            .shadow(if (focused) 22.dp else 0.dp, shape, clip = false, ambientColor = UWHITE, spotColor = UCYAN)\n            ', '')
    s = s.replace('.scale(if (focused) 1.06f else 1f)\n            .shadow(if (focused) 20.dp else 0.dp, shape, clip = false, ambientColor = UWHITE, spotColor = UCYAN)\n            ', '')
    s = s.replace('.scale(if (focused) 1.05f else 1f)\n            .shadow(if (focused) 20.dp else 0.dp, shape, clip = false, ambientColor = UWHITE, spotColor = UCYAN)\n            ', '')
    s = s.replace('.scale(if (focused) 1.08f else 1f)\n            .shadow(if (focused) 22.dp else 0.dp, shape, clip=false, ambientColor=AWHITE, spotColor=ACYAN)\n            ', '')
    s = s.replace('.scale(if (focused) 1.06f else 1f)\n            .shadow(if (focused) 22.dp else 0.dp, shape, clip=false, ambientColor=AWHITE, spotColor=ACYAN)\n            ', '')
    p.write_text(s)

print('Applied v6.1.3 H96-safe focus rendering and startup stability')
