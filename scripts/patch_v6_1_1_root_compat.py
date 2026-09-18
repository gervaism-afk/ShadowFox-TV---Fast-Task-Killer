from pathlib import Path

base = Path('extracted/app/src/main/java/ca/shadowfoxtv/taskkiller')

# Pro Engine: use the shared root compatibility layer for all privileged commands.
p = base / 'ShadowFoxProEngine.kt'
s = p.read_text()
start = s.find('    private fun runRoot(command: String): RootExec {')
end = s.find('\n    private fun formatBytes', start)
if start == -1 or end == -1:
    raise SystemExit('ShadowFoxProEngine runRoot block not found')
replacement = '''    private fun runRoot(command: String): RootExec {
        val r = RootShell.exec(command)
        return RootExec(r.success, r.output)
    }
'''
s = s[:start] + replacement + s[end:]
p.write_text(s)

# Ultimate core: route every root command through the same compatibility layer.
p = base / 'UltimateCore.kt'
s = p.read_text()
start = s.find('    private fun runRoot(command: String): Pair<Boolean, String> = runCatching {')
end = s.find('\n    private fun shellQuote', start)
if start == -1 or end == -1:
    raise SystemExit('UltimateCore runRoot block not found')
replacement = '''    private fun runRoot(command: String): Pair<Boolean, String> {
        val r = RootShell.exec(command)
        return r.success to r.output
    }
'''
s = s[:start] + replacement + s[end:]
p.write_text(s)

# Advanced Tools UI removed in v6.1.29; no AdvancedToolsActivity patching remains.

# Updater: detect root through the same layer and install with the compatible shell.
p = base / 'GitHubReleaseUpdater.kt'
s = p.read_text()
old = '''    private fun hasRoot(): Boolean = try {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0 && output.contains("uid=0")
    } catch (_: Exception) {
        false
    }'''
new = '''    private fun hasRoot(): Boolean = RootShell.isRootAvailable()'''
if old not in s:
    raise SystemExit('Updater hasRoot block not found')
s = s.replace(old, new, 1)
old_install = '''            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            val success = exit == 0 && output.contains("Success", ignoreCase = true)'''
new_install = '''            val rootResult = RootShell.exec(command, 45)
            val output = rootResult.output
            val success = rootResult.success && output.contains("Success", ignoreCase = true)'''
if old_install not in s:
    raise SystemExit('Updater root install block not found')
s = s.replace(old_install, new_install, 1)
p.write_text(s)

print('Applied v6.1.1 H96/legacy-root compatibility to detector, optimizer, advanced tools and updater')
