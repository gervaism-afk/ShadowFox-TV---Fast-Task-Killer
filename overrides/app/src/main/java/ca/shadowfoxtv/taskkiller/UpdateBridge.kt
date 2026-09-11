package ca.shadowfoxtv.taskkiller

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/** Keeps the Compose entry point while continuously checking the GitHub release updater. */
@Composable
fun ShadowFoxUpdateGate(
    context: Context,
    content: @Composable () -> Unit
) {
    LaunchedEffect(Unit) {
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
    }

    content()
}
