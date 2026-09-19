from pathlib import Path

# Add Advanced Tools launcher to the existing Ultimate Center without redesigning it.
p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/UltimateCenterActivity.kt')
s = p.read_text()

if 'import android.content.Intent\n' not in s:
    s = s.replace('import android.content.res.Configuration\n', 'import android.content.Intent\nimport android.content.res.Configuration\n', 1)
if 'import androidx.compose.ui.platform.LocalContext\n' not in s:
    s = s.replace('import androidx.compose.ui.platform.LocalConfiguration\n', 'import androidx.compose.ui.platform.LocalConfiguration\nimport androidx.compose.ui.platform.LocalContext\n', 1)

old = '''        UltimatePanel("ADVANCED APP CONTROL", "Rooted devices unlock deeper controls. Standard devices keep Android-safe actions.") {
            Text("Unsupported actions are never reported as completed.", color = UMUTED, fontSize = 10.sp)
        }'''
new = '''        UltimatePanel("ADVANCED APP CONTROL", "Running Apps • Startup • System Apps • Gaming • Safety • Update Center") {
            val context = LocalContext.current
            Text("Rooted devices unlock deeper controls. Standard devices keep Android-safe actions. Unsupported actions are never reported as completed.", color = UMUTED, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            UltimateButton("OPEN ADVANCED TOOLS") {
                context.startActivity(Intent(context, AdvancedToolsActivity::class.java))
            }
        }'''
if old not in s:
    raise SystemExit('Expected Advanced App Control panel not found')
s = s.replace(old, new, 1)
p.write_text(s)

# Make scheduled self-heal health-aware while preserving ordinary scheduled maintenance behavior.
u = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/UltimateCore.kt')
us = u.read_text()
old_receiver = '''                val manager = UltimateManager(context)
                if (manager.maintenanceEnabled()) kotlinx.coroutines.runBlocking { manager.smartOptimize() }'''
new_receiver = '''                val manager = UltimateManager(context)
                if (manager.maintenanceEnabled()) {
                    val advanced = context.getSharedPreferences("shadowfox_advanced", Context.MODE_PRIVATE)
                    val selfHeal = advanced.getBoolean("self_heal", false)
                    kotlinx.coroutines.runBlocking {
                        if (selfHeal) {
                            val snap = manager.snapshot()
                            if (snap.health < 85 || snap.ramUsedPercent > 75) manager.smartOptimize()
                        } else {
                            manager.smartOptimize()
                        }
                    }
                }'''
if old_receiver not in us:
    raise SystemExit('Expected MaintenanceReceiver block not found')
us = us.replace(old_receiver, new_receiver, 1)
u.write_text(us)

print('Integrated ShadowFox v6.1 Advanced Tools and health-aware self-heal')
