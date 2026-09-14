from pathlib import Path

base = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller')

# 1) Main dashboard: network socket work must never run on the Compose main thread.
p = base / 'MainActivity.kt'
s = p.read_text()
old = '            ping = measureLatencyMs()'
new = '            ping = withContext(Dispatchers.IO) { runCatching { measureLatencyMs() }.getOrDefault(0) }'
if old not in s:
    raise SystemExit('MainActivity latency call not found')
s = s.replace(old, new, 1)
p.write_text(s)

# 2) Updater bridge: startup/update checks must never be able to terminate the UI.
p = base / 'UpdateBridge.kt'
s = p.read_text()
old = '''    LaunchedEffect(Unit) {
        val appContext = context.applicationContext
        UpdateScheduler.schedule(appContext)
        GitHubReleaseUpdater.start(appContext)

        var seconds = 0
        while (true) {
            GitHubReleaseUpdater.resumePendingInstall(appContext)
            if (seconds >= 60) {
                GitHubReleaseUpdater.start(appContext)
                seconds = 0
            }
            delay(1_000)
            seconds++
        }
    }'''
new = '''    LaunchedEffect(Unit) {
        val appContext = context.applicationContext
        runCatching { UpdateScheduler.schedule(appContext) }
        runCatching { GitHubReleaseUpdater.start(appContext) }

        var seconds = 0
        while (true) {
            runCatching { GitHubReleaseUpdater.resumePendingInstall(appContext) }
            if (seconds >= 60) {
                runCatching { GitHubReleaseUpdater.start(appContext) }
                seconds = 0
            }
            delay(1_000)
            seconds++
        }
    }'''
if old not in s:
    raise SystemExit('UpdateBridge startup block not found')
s = s.replace(old, new, 1)
p.write_text(s)

print('Applied v6.1.3 startup stability: off-main network probe and crash-contained updater loop')
