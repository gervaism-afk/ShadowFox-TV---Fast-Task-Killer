package ca.shadowfoxtv.taskkiller

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

/** Keeps the Compose entry point while checking updates without risking app startup. */
@Composable
fun ShadowFoxUpdateGate(
    context: Context,
    content: @Composable () -> Unit
) {
    LaunchedEffect(Unit) {
        val appContext = context.applicationContext

        // Let older/pre-rooted TV firmware finish launching the UI before any
        // updater, root or installer work begins. None of this may crash startup.
        delay(15_000)
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
    }

    content()
}
