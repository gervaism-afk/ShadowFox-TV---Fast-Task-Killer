package ca.shadowfoxtv.taskkiller

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UserToolsDialog(manager: UltimateManager, onDismiss: () -> Unit) {
    val context = manager.appContext()
    val tools = remember { UserToolsManager(context) }
    var settings by remember { mutableStateOf(tools.healthSettings()) }
    var report by rememberSaveable { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var pendingExport by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var health by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val television = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val dialogScroll = rememberScrollState()
    LaunchedEffect(report != null) { dialogScroll.scrollTo(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "Notifications enabled." else "Notification permission was not granted. You can still check device status here."
        HealthAlerts.settingsChanged(context)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val contents = pendingExport
            scope.launch {
                val saved = withContext(Dispatchers.IO) { runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(contents) }
                        ?: error("No output stream")
                }.isSuccess }
                message = if (saved) "Support report saved." else "Could not save the report. Try Copy or Share."
            }
        } else message = "Save cancelled. You can still Copy or Share the report."
    }
    fun change(next: HealthSettings) {
        settings = next
        tools.saveHealthSettings(next)
        if ((next.lowStorage || next.heat) && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val free = withContext(Dispatchers.IO) { StatFs(Environment.getDataDirectory().absolutePath).availableBytes / 1048576 }
            val low = settings.lowStorage && free < settings.thresholdMb
            health = "${if (low) "LOW STORAGE • " else ""}$free MB free • Thermal: ${HealthAlerts.thermalLabel(context)}"
            delay(5000)
        }
    }
    val contentHeight = (LocalConfiguration.current.screenHeightDp * .53f).dp
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(.92f),
        containerColor = Color(0xFF0A2030), titleContentColor = Color.White, textContentColor = Color.White,
        title = { Text(if (report == null) "HEALTH ALERTS & SUPPORT" else "REVIEW SUPPORT REPORT", fontSize = 18.sp) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = contentHeight).verticalScroll(dialogScroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (report == null) {
                    Text(health, color = Color(0xFF00E5FF), fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolAction("LOW-STORAGE ALERTS: ${if (settings.lowStorage) "ON" else "OFF"}", Modifier.weight(1f)) { change(settings.copy(lowStorage = !settings.lowStorage)) }
                        ToolAction("WARN BELOW: ${settings.thresholdMb} MB", Modifier.weight(1f)) {
                            val values = listOf(512, 1024, 2048, 4096)
                            change(settings.copy(thresholdMb = values[(values.indexOf(settings.thresholdMb) + 1) % values.size]))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolAction(if (Build.VERSION.SDK_INT >= 29) "HEAT WARNINGS: ${if (settings.heat) "ON" else "OFF"}" else "HEAT WARNINGS: UNAVAILABLE", Modifier.weight(1f), enabled = Build.VERSION.SDK_INT >= 29) {
                            change(settings.copy(heat = !settings.heat))
                        }
                        ToolAction("STORAGE SETTINGS", Modifier.weight(1f)) {
                            if (!tools.openStorageSettings()) message = "Storage settings are unavailable on this device."
                        }
                    }
                    ToolAction(if (generating) "PREPARING REPORT…" else "PREVIEW SUPPORT REPORT", enabled = !generating) {
                        scope.launch {
                            generating = true
                            try { report = tools.supportReport(manager); message = "" }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { message = "Could not prepare the support report. Try again." }
                            finally { generating = false }
                        }
                    }
                    Text("Storage is checked periodically and when ShadowFox opens. Heat warnings use Android's thermal status while ShadowFox is open. Settings save automatically.", fontSize = 12.sp)
                    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) Text("System notifications are disabled for ShadowFox.", color = Color(0xFFFF7A00), fontSize = 12.sp)
                } else {
                    Text("Review the report, then choose how to export it. Diagnostic entries may name apps. Nothing is uploaded automatically.", fontSize = 12.sp)
                    if (television && Build.VERSION.SDK_INT >= 29) Text("Save stores a text file in Downloads/ShadowFox on this TV.", fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolAction("COPY", Modifier.weight(1f)) {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("ShadowFox support report", report.orEmpty()))
                            message = "Report copied."
                        }
                        ToolAction("SAVE", Modifier.weight(1f)) {
                            pendingExport = report.orEmpty()
                            if (television && Build.VERSION.SDK_INT >= 29) {
                                val contents = pendingExport
                                scope.launch {
                                    message = "Saving report…"
                                    val saved = tools.saveReportToDownloads(contents)
                                    message = if (saved) "Report saved in Downloads/ShadowFox." else "Could not save the report. Use Copy or Share."
                                }
                            } else {
                                runCatching { save.launch("ShadowFox-support-v${BuildConfig.VERSION_NAME}.txt") }
                                    .onFailure { message = "No file picker available. Use Copy or Share." }
                            }
                        }
                        ToolAction("SHARE", Modifier.weight(1f)) {
                            runCatching {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "ShadowFox support report")
                                    .putExtra(Intent.EXTRA_TEXT, report)
                                context.startActivity(Intent.createChooser(send, "Share support report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.onFailure { message = "No sharing app available. Use Copy or Save." }
                        }
                    }
                    if (message.isNotBlank()) Text(message, color = Color(0xFF00E5FF), fontSize = 12.sp)
                    SelectionContainer { Text(report.orEmpty(), fontSize = 12.sp) }
                }
                if (report == null && message.isNotBlank()) Text(message, color = Color(0xFF00E5FF), fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = Color(0xFF00E5FF)) } },
        dismissButton = if (report != null) ({ TextButton(onClick = { report = null; message = "" }) { Text("BACK", color = Color(0xFF00E5FF)) } }) else null
    )
}

@Composable
private fun ToolAction(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334A), contentColor = Color.White)) {
        Text(label, fontSize = 11.sp)
    }
}
