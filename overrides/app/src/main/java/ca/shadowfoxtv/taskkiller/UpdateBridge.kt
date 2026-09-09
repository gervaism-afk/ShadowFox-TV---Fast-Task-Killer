package ca.shadowfoxtv.taskkiller

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Startup-safe update gate.
 *
 * The updater is intentionally not started from the foreground Activity. This keeps
 * network, DownloadManager, alarm scheduling, root detection and package-install work
 * completely out of the app launch path while we isolate TV firmware startup crashes.
 */
@Composable
fun ShadowFoxUpdateGate(
    context: Context,
    content: @Composable () -> Unit
) {
    content()
}
