package ca.shadowfoxtv.taskkiller

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * ShadowFox TV self updater.
 *
 * Primary path on rooted devices:
 * 1) Check the latest GitHub release.
 * 2) Download the newer signed APK into app cache.
 * 3) Verify package name and signing certificate against the installed app.
 * 4) Install silently with root using `pm install -r`.
 *
 * Non-root fallback keeps the previous DownloadManager + Android package installer flow.
 */
object GitHubReleaseUpdater {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/gervaism-afk/ShadowFox-TV---Fast-Task-Killer/releases/latest"
    private const val PREFS = "shadowfox_update"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_DOWNLOAD_VERSION = "download_version"
    private const val KEY_PENDING_INSTALL_URI = "pending_install_uri"
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var checkStarted = false
    @Volatile private var monitoredDownloadId = -1L

    fun start(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pendingId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val pendingVersion = prefs.getString(KEY_DOWNLOAD_VERSION, null)

        if (pendingId > 0L && !pendingVersion.isNullOrBlank()) {
            monitorDownload(appContext, pendingId, pendingVersion)
            return
        }

        if (checkStarted) return
        checkStarted = true
        scope.launch {
            try {
                checkLatestRelease(appContext)
            } finally {
                checkStarted = false
            }
        }
    }

    fun resumePendingInstall(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pendingUri = prefs.getString(KEY_PENDING_INSTALL_URI, null) ?: return
        if (!canInstallPackages(context)) return
        prefs.edit().remove(KEY_PENDING_INSTALL_URI).apply()
        launchPackageInstaller(context, Uri.parse(pendingUri))
    }

    private suspend fun checkLatestRelease(context: Context) = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ShadowFox-TV-Task-Killer/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(payload)
            val latestVersion = release.optString("tag_name", "").replaceFirst(Regex("^[vV]"), "")
            if (latestVersion.isBlank() || compareVersions(latestVersion, BuildConfig.VERSION_NAME) <= 0) return@withContext

            var apkUrl: String? = null
            val assets = release.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name", "")
                    if (name.lowercase(Locale.US).endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", null)
                        if (!apkUrl.isNullOrBlank()) break
                    }
                }
            }
            if (apkUrl.isNullOrBlank()) return@withContext

            if (hasRoot()) {
                val installed = downloadAndInstallRooted(context, apkUrl!!, latestVersion)
                if (installed) return@withContext
            }

            withContext(Dispatchers.Main) {
                startUpdateDownload(context, apkUrl!!, latestVersion)
            }
        } catch (_: Exception) {
            // Never block app startup because of update failures.
        } finally {
            connection?.disconnect()
        }
    }

    private fun hasRoot(): Boolean = try {
        val process = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0 && output.contains("uid=0")
    } catch (_: Exception) {
        false
    }

    private fun downloadAndInstallRooted(context: Context, apkUrl: String, version: String): Boolean {
        val target = File(context.cacheDir, "shadowfox-update-$version.apk")
        var connection: HttpURLConnection? = null
        return try {
            if (target.exists()) target.delete()
            connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "ShadowFox-TV-Task-Killer/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode !in 200..299) return false
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (!verifyDownloadedApk(context, target)) {
                target.delete()
                return false
            }

            // Root can read app-private cache, but 0644 avoids ROM-specific permission quirks.
            target.setReadable(true, false)
            val command = "pm install -r --user 0 '${target.absolutePath.replace("'", "'\\''")}'"
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            val success = exit == 0 && output.contains("Success", ignoreCase = true)
            if (success) target.delete()
            success
        } catch (_: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun verifyDownloadedApk(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        return try {
            val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)
            } ?: return false

            if (archive.packageName != context.packageName) return false
            installedSignerDigests(context) == archiveSignerDigests(archive)
        } catch (_: Exception) {
            false
        }
    }

    private fun installedSignerDigests(context: Context): Set<String> {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        return packageSignerDigests(info)
    }

    private fun archiveSignerDigests(info: android.content.pm.PackageInfo): Set<String> = packageSignerDigests(info)

    private fun packageSignerDigests(info: android.content.pm.PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun startUpdateDownload(context: Context, apkUrl: String, version: String) {
        try {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existingId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
            val existingVersion = prefs.getString(KEY_DOWNLOAD_VERSION, null)
            if (existingId > 0L && version == existingVersion) {
                monitorDownload(context, existingId, version)
                return
            }

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("ShadowFox TV - Task Killer v$version")
                setDescription("Downloading update")
                setMimeType(APK_MIME)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "ShadowFox-TV-Task-Killer-v$version.apk")
            }
            val id = manager.enqueue(request)
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_DOWNLOAD_VERSION, version).apply()
            monitorDownload(context, id, version)
        } catch (_: Exception) {
        }
    }

    private fun monitorDownload(context: Context, downloadId: Long, version: String) {
        if (monitoredDownloadId == downloadId) return
        monitoredDownloadId = downloadId
        scope.launch(Dispatchers.IO) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (manager == null) {
                monitoredDownloadId = -1L
                return@launch
            }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            while (true) {
                var finished = false
                try {
                    manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val index = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val status = if (index >= 0) cursor.getInt(index) else DownloadManager.STATUS_FAILED
                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    val uri = manager.getUriForDownloadedFile(downloadId)
                                    prefs.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_DOWNLOAD_VERSION).apply()
                                    if (uri != null) withContext(Dispatchers.Main) { requestInstall(context, uri) }
                                    finished = true
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    prefs.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_DOWNLOAD_VERSION).apply()
                                    finished = true
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    finished = true
                }
                if (finished) break
                delay(1_000)
            }
            monitoredDownloadId = -1L
        }
    }

    private fun requestInstall(context: Context, apkUri: Uri) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PENDING_INSTALL_URI, apkUri.toString()).apply()
        if (!canInstallPackages(context)) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            return
        }
        prefs.edit().remove(KEY_PENDING_INSTALL_URI).apply()
        launchPackageInstaller(context, apkUri)
    }

    private fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    private fun launchPackageInstaller(context: Context, apkUri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (_: Exception) {
            Toast.makeText(context, "Update downloaded. Open Downloads to install it.", Toast.LENGTH_LONG).show()
        }
    }

    private fun compareVersions(a: String, b: String): Int {
        val aa = a.split('.')
        val bb = b.split('.')
        val count = maxOf(aa.size, bb.size)
        for (i in 0 until count) {
            val av = aa.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val bv = bb.getOrNull(i)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}
