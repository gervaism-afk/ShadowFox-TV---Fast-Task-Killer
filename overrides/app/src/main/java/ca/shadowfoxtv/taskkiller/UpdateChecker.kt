package ca.shadowfoxtv.taskkiller

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val UPDATE_METADATA_URL = "https://shadowfoxtv.ca/update.json"
private val UpdateCharcoal = Color(0xFF121212)
private val UpdateNeonBlue = Color(0xFF18E9FF)
private val UpdateOrange = Color(0xFFFF8A24)
private val UpdateMuted = Color(0xFFB7C3CC)

data class UpdateMetadata(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

sealed interface UpdateState {
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val metadata: UpdateMetadata) : UpdateState
    data class Error(val message: String) : UpdateState
}

object UpdateChecker {
    suspend fun checkForUpdate(context: Context): UpdateState = withContext(Dispatchers.IO) {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val localVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val connection = (URL(UPDATE_METADATA_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "ShadowFoxTV-Updater/${packageInfo.versionName ?: "unknown"}")
            }

            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    error("Update server returned HTTP $responseCode")
                }

                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonText)
                val remote = UpdateMetadata(
                    versionCode = json.getLong("versionCode"),
                    versionName = json.getString("versionName").trim(),
                    apkUrl = json.getString("apkUrl").trim(),
                    releaseNotes = json.optString("releaseNotes", "A new ShadowFox TV update is available.").trim()
                )

                require(remote.versionName.isNotBlank()) { "Missing versionName" }
                require(remote.apkUrl.startsWith("https://")) { "apkUrl must use HTTPS" }

                if (remote.versionCode > localVersionCode) {
                    UpdateState.Available(remote)
                } else {
                    UpdateState.UpToDate
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { UpdateState.Error(it.message ?: "Unable to check for updates") }
    }

    fun downloadAndInstall(
        context: Context,
        metadata: UpdateMetadata,
        onDownloadStarted: (Long) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val request = DownloadManager.Request(Uri.parse(metadata.apkUrl)).apply {
            setTitle("ShadowFox TV ${metadata.versionName}")
            setDescription("Downloading system update…")
            setMimeType(APK_MIME_TYPE)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                "ShadowFox-TV-${metadata.versionName}.apk"
            )
        }

        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (t: Throwable) {
            onError(t.message ?: "Unable to start update download")
            return
        }

        onDownloadStarted(downloadId)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != downloadId) return

                runCatching {
                    appContext.unregisterReceiver(this)
                }

                val query = DownloadManager.Query().setFilterById(downloadId)
                downloadManager.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        onError("Downloaded update could not be located")
                        return
                    }

                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIndex >= 0) cursor.getInt(statusIndex) else DownloadManager.STATUS_FAILED
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        onError("Update download failed")
                        return
                    }
                }

                val downloadedApkUri = downloadManager.getUriForDownloadedFile(downloadId)
                if (downloadedApkUri == null) {
                    onError("Downloaded APK URI is unavailable")
                    return
                }

                launchPackageInstaller(appContext, downloadedApkUri)
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun launchPackageInstaller(context: Context, downloadedApkUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(settingsIntent) }
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(downloadedApkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        runCatching { context.startActivity(installIntent) }
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}

@Composable
fun ShadowFoxUpdateGate(
    context: Context,
    content: @Composable () -> Unit
) {
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Checking) }
    var dismissedVersion by remember { mutableStateOf<Long?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        updateState = UpdateChecker.checkForUpdate(context)
    }

    content()

    val available = (updateState as? UpdateState.Available)?.metadata
    if (available != null && dismissedVersion != available.versionCode) {
        ShadowFoxUpdateDialog(
            metadata = available,
            downloading = downloading,
            errorMessage = downloadError,
            onUpdateNow = {
                if (!downloading) {
                    downloadError = null
                    UpdateChecker.downloadAndInstall(
                        context = context,
                        metadata = available,
                        onDownloadStarted = { downloading = true },
                        onError = {
                            downloading = false
                            downloadError = it
                        }
                    )
                }
            },
            onLater = {
                dismissedVersion = available.versionCode
                downloading = false
                downloadError = null
            }
        )
    }
}

@Composable
private fun ShadowFoxUpdateDialog(
    metadata: UpdateMetadata,
    downloading: Boolean,
    errorMessage: String?,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    val updateFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        updateFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onLater() },
        containerColor = UpdateCharcoal,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(2.dp, UpdateNeonBlue.copy(alpha = 0.85f), RoundedCornerShape(20.dp)),
        title = {
            Text(
                text = "🚀 Critical System Update Available (v${metadata.versionName})",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = metadata.releaseNotes,
                    color = UpdateMuted,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                if (downloading) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "DOWNLOADING UPDATE…",
                        color = UpdateNeonBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = errorMessage,
                        color = UpdateOrange,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            UpdateDialogButton(
                text = if (downloading) "DOWNLOADING…" else "UPDATE NOW",
                accent = UpdateNeonBlue,
                enabled = !downloading,
                onClick = onUpdateNow,
                modifier = Modifier.focusRequester(updateFocusRequester)
            )
        },
        dismissButton = {
            UpdateDialogButton(
                text = "LATER",
                accent = UpdateOrange,
                enabled = !downloading,
                onClick = onLater
            )
        }
    )
}

@Composable
private fun UpdateDialogButton(
    text: String,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .scale(if (focused) 1.08f else 1f)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else accent.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) accent else accent.copy(alpha = 0.18f),
            contentColor = if (focused) Color.Black else Color.White,
            disabledContainerColor = Color(0xFF242424),
            disabledContentColor = UpdateMuted
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp
        )
    }
}
