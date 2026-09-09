package ca.shadowfoxtv.taskkiller

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/** Keeps the existing Compose entry point while using the previous GitHub-release updater. */
@Composable
fun ShadowFoxUpdateGate(
    context: Context,
    content: @Composable () -> Unit
) {
    LaunchedEffect(Unit) {
        UpdateScheduler.schedule(context.applicationContext)
        GitHubReleaseUpdater.start(context.applicationContext)

        // The previous updater resumed installation after the user returned from the
        // "Install unknown apps" settings screen. Poll lightly while this activity is alive
        // so the same behavior is preserved without coupling the updater to the dashboard UI.
        while (true) {
            GitHubReleaseUpdater.resumePendingInstall(context.applicationContext)
            delay(1_000)
        }
    }

    content()
}
