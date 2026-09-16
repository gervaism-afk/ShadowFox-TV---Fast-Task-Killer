from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

# H96 Android 14 records a 5-second FocusEvent ANR. RootShell can legitimately
# take several seconds on legacy su while probing compatible invocation styles.
# Never run root, diagnostics, socket latency, or optimizer shell work on Compose's main thread.
s = s.replace(
    '        rootAvailable = optimizer.rootAvailable()\n        engineStatus = optimizer.diagnosticsSummary()',
    '        val startup = withContext(Dispatchers.IO) { optimizer.rootAvailable() to optimizer.diagnosticsSummary() }\n        rootAvailable = startup.first\n        engineStatus = startup.second'
)
s = s.replace(
    '        rootAvailable = optimizer.rootAvailable(); engineStatus = optimizer.diagnosticsSummary()',
    '        val startup = withContext(Dispatchers.IO) { optimizer.rootAvailable() to optimizer.diagnosticsSummary() }; rootAvailable = startup.first; engineStatus = startup.second'
)
s = s.replace('            ping = measureLatencyMs()', '            ping = withContext(Dispatchers.IO) { measureLatencyMs() }')
s = s.replace('            busy = true\n            val result = optimizer.optimize()', '            busy = true\n            val result = withContext(Dispatchers.IO) { optimizer.optimize() }')
s = s.replace('            busy = true; val result = optimizer.optimize(); delay(300)', '            busy = true; val result = withContext(Dispatchers.IO) { optimizer.optimize() }; delay(300)')

# Guard against a future patch silently restoring the known ANR pattern.
if 'rootAvailable = optimizer.rootAvailable()' in s:
    raise SystemExit('Unsafe main-thread rootAvailable call remains')
if 'ping = measureLatencyMs()' in s:
    raise SystemExit('Unsafe main-thread latency call remains')
if 'val result = optimizer.optimize()' in s:
    raise SystemExit('Unsafe main-thread optimize call remains')

p.write_text(s)
print('Applied H96 ANR fix: blocking startup/root/network/optimizer work moved to Dispatchers.IO')
