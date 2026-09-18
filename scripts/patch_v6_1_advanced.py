from pathlib import Path

# Preserve health-aware scheduled maintenance behavior. Production v6.1.29.

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

print('Preserved health-aware self-heal; Advanced Tools UI removed')
