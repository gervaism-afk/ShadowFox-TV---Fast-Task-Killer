from pathlib import Path

base = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller')
p = base / 'MainActivity.kt'
s = p.read_text()

# H96/Rockchip safe runtime: remove all continuous network/socket work from the
# dashboard. Network monitoring is non-essential and some legacy TV firmware
# terminates the process during these probes. Keep the UI and focus behavior.
s = s.replace('''    LaunchedEffect(Unit) {
        while (true) {
            val before = totalTrafficBytes()
            delay(1000)
            val after = totalTrafficBytes()
            if (before >= 0 && after >= before) mbps = (after - before) * 8f / 1_000_000f
            ping = withContext(Dispatchers.IO) { runCatching { measureLatencyMs() }.getOrDefault(0) }
            if (ping > 0) {
                graph.add(ping)
                while (graph.size > 18) graph.removeAt(0)
            }
            ram = memoryUsedPercent(context)
            apps = runningProcessCount(context)
        }
    }''', '''    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            ram = runCatching { memoryUsedPercent(context) }.getOrDefault(ram)
            apps = runCatching { runningProcessCount(context) }.getOrDefault(apps)
        }
    }''')

# Also neutralize any mobile layouts injected by later build patches. This is
# intentionally broad so no orientation can retain the old socket probe loop.
s = s.replace('''            val before = totalTrafficBytes(); delay(1000); val after = totalTrafficBytes()
            if (before >= 0 && after >= before) mbps = (after - before) * 8f / 1_000_000f
            ping = withContext(Dispatchers.IO) { runCatching { measureLatencyMs() }.getOrDefault(0) }
            if (ping > 0) { graph.add(ping); while (graph.size > 18) graph.removeAt(0) }
            ram = memoryUsedPercent(context); apps = runningProcessCount(context)''', '''            delay(5_000)
            ram = runCatching { memoryUsedPercent(context) }.getOrDefault(ram)
            apps = runCatching { runningProcessCount(context) }.getOrDefault(apps)''')

p.write_text(s)

# Disable automatic updater execution from the foreground activity entirely.
# Update Center remains available manually. This removes root/download/installer
# activity as a possible delayed foreground crash source.
p = base / 'UpdateBridge.kt'
p.write_text('''package ca.shadowfoxtv.taskkiller

import android.content.Context
import androidx.compose.runtime.Composable

/** Foreground-safe gate. Updates are checked explicitly from Update Center. */
@Composable
fun ShadowFoxUpdateGate(
    context: Context,
    content: @Composable () -> Unit
) {
    content()
}
''')

print('Applied v6.1.5 H96 safe runtime: no foreground updater and no continuous socket probes')
