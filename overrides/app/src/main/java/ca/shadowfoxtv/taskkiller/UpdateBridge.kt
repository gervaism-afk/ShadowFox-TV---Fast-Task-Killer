package ca.shadowfoxtv.taskkiller

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ShadowFoxUpdateGate(context: Context, content: @Composable () -> Unit) {
    LaunchedEffect(Unit) {
        GitHubReleaseUpdater.resumePendingInstall(context)
        GitHubReleaseUpdater.start(context)
    }
    val report = StartupDiagnostics.report(context)
    if (report.isNotBlank()) {
        StartupDiagnostics.stage(context, "Diagnostic report displayed")
        Box(Modifier.fillMaxSize().background(Color(0xFF03111D)).padding(38.dp), contentAlignment = Alignment.Center) {
            Column {
                Text("SHADOWFOX H96 DIAGNOSTIC REPORT", color = Color(0xFF00E5FF), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Photograph this screen and send it to ChatGPT", color = Color(0xFFFF7A00), fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
                Text(report, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    } else {
        StartupDiagnostics.stage(context, "Main dashboard composing")
        content()
    }
}
