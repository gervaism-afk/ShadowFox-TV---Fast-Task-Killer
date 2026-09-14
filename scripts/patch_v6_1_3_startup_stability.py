from pathlib import Path

base = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller')

# 1) Main dashboard: network socket work must never run on the Compose main thread.
p = base / 'MainActivity.kt'
s = p.read_text()
old = '            ping = measureLatencyMs()'
new = '            ping = withContext(Dispatchers.IO) { runCatching { measureLatencyMs() }.getOrDefault(0) }'
if old in s:
    s = s.replace(old, new, 1)
elif new not in s:
    raise SystemExit('MainActivity latency call not found')
p.write_text(s)

# 2) UpdateBridge is now hardened directly in overrides. Verify the crash-safe
# delayed updater loop survived extraction/overlay; do not rewrite it again.
p = base / 'UpdateBridge.kt'
s = p.read_text()
required = [
    'delay(15_000)',
    'runCatching { UpdateScheduler.schedule(appContext) }',
    'runCatching { GitHubReleaseUpdater.start(appContext) }',
    'runCatching { GitHubReleaseUpdater.resumePendingInstall(appContext) }'
]
missing = [x for x in required if x not in s]
if missing:
    raise SystemExit('UpdateBridge hardening missing: ' + ', '.join(missing))

print('Applied startup stability: off-main network probe and delayed crash-contained updater loop')
