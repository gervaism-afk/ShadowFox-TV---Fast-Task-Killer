from pathlib import Path

p = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller/MainActivity.kt')
s = p.read_text()

# 1) Fix the TV canvas so it is scaled once and truly centered.
old_scale = '''    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val scale = minOf(maxWidth / 960.dp, maxHeight / 540.dp)
        Box(Modifier.size(960.dp * scale, 540.dp * scale).align(Alignment.Center)) {
            Box(
                Modifier
                    .size(960.dp, 540.dp)
                    .scale(scale)
                    .align(Alignment.Center)
                    .background(BG)
            ) {'''
new_scale = '''    BoxWithConstraints(Modifier.fillMaxSize().background(BG)) {
        val scale = minOf(maxWidth / 960.dp, maxHeight / 540.dp)
        Box(
            Modifier
                .size(960.dp, 540.dp)
                .scale(scale)
                .align(Alignment.Center)
                .background(BG)
        ) {'''
if old_scale in s:
    s = s.replace(old_scale, new_scale, 1)

old_close = '''                Bolt(Modifier.offset(456.dp, 457.dp).size(38.dp))
            }
        }
    }
}'''
new_close = '''                Bolt(Modifier.offset(456.dp, 448.dp).size(38.dp))
        }
    }
}'''
if old_close in s:
    s = s.replace(old_close, new_close, 1)

# 2) Pull the TV stats row up and make its typography fit reliably.
s = s.replace(
    'modifier = Modifier.offset(45.dp, 454.dp).size(867.dp, 58.dp)',
    'modifier = Modifier.offset(45.dp, 442.dp).size(867.dp, 64.dp)',
    1
)
s = s.replace('Modifier.size(126.dp, 48.dp)', 'Modifier.size(126.dp, 54.dp)', 2)
s = s.replace('Modifier.size(112.dp, 48.dp)', 'Modifier.size(112.dp, 54.dp)', 2)
s = s.replace('Modifier.size(175.dp, 48.dp)', 'Modifier.size(175.dp, 54.dp)', 1)
s = s.replace('Modifier.size(100.dp, 48.dp)', 'Modifier.size(100.dp, 54.dp)', 1)

old_stat = '''private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color = WHITE) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .shadow(6.dp, shape, false, CYAN.copy(alpha = .25f), CYAN.copy(alpha = .25f))
            .background(Color(0xD90A1C29), shape)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, color = MUTED, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}'''
new_stat = '''private fun StatTile(label: String, value: String, modifier: Modifier, valueColor: Color = WHITE) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .shadow(6.dp, shape, false, CYAN.copy(alpha = .25f), CYAN.copy(alpha = .25f))
            .background(Color(0xD90A1C29), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, color = CYAN, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, color = valueColor, fontSize = 10.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}'''
if old_stat in s:
    s = s.replace(old_stat, new_stat, 1)

# 3) Replace the optimizer with a root-first implementation that verifies each force-stop.
start = s.find('private class AppOptimizer(private val context: Context) {')
end = s.find('\nprivate data class RootResult', start)
if start == -1 or end == -1:
    raise SystemExit('AppOptimizer block not found')

optimizer = r'''private class AppOptimizer(private val context: Context) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    suspend fun optimize(): CleanupResult = withContext(Dispatchers.IO) {
        val beforeRam = availableMemoryBytes(context)
        val beforeStorage = freeStorageBytes()
        val protected = protectedPackages()
        val root = rootShellAvailable()

        val candidates = if (root) {
            runRoot("pm list packages -3").output
                .lineSequence()
                .map { it.trim().removePrefix("package:") }
                .filter { it.isNotBlank() && it != context.packageName && it !in protected }
                .toList()
        } else {
            activityManager.runningAppProcesses.orEmpty()
                .flatMap { it.pkgList?.toList().orEmpty() }
                .distinct()
                .filter { it != context.packageName && it !in protected }
                .filterNot(::isSystemPackage)
        }

        var closed = 0
        if (root) {
            for (pkg in candidates) {
                if (runRoot("am force-stop '${pkg.replace("'", "'\\''")}'").success) closed++
            }
            runRoot("pm trim-caches 999999999999")
            runRoot("sync")
        } else {
            for (pkg in candidates) {
                runCatching {
                    activityManager.killBackgroundProcesses(pkg)
                    closed++
                }
            }
        }

        Thread.sleep(500)
        val afterRam = availableMemoryBytes(context)
        val afterStorage = freeStorageBytes()

        CleanupResult(
            closedApps = closed,
            ramFreedBytes = (afterRam - beforeRam).coerceAtLeast(0L),
            storageFreedBytes = (afterStorage - beforeStorage).coerceAtLeast(0L),
            rootUsed = root
        )
    }

    private fun protectedPackages(): Set<String> {
        val protected = mutableSetOf(
            context.packageName,
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.android.vending",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.amazon.tv.launcher",
            "com.amazon.firehomestarter",
            "com.amazon.device.software.ota"
        )

        runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNullTo(protected) { it.activityInfo?.packageName }
        }

        runCatching {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { app ->
                    val intent = packageManager.getLaunchIntentForPackage(app.packageName)
                    intent != null && app.packageName.contains("vpn", ignoreCase = true)
                }
                .mapTo(protected) { it.packageName }
        }

        return protected
    }

    private fun isSystemPackage(packageName: String): Boolean = runCatching {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)
}
'''

s = s[:start] + optimizer + s[end:]
p.write_text(s)
print('Applied TV bottom-row fix and verified root-first cleanup engine')
