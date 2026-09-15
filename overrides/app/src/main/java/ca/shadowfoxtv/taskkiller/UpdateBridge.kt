package ca.shadowfoxtv.taskkiller

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Stability-safe update gate.
 *
 * Automatic update polling/install is intentionally disabled here. On rooted Android TV
 * devices a background package replacement can terminate the currently running process,
 * which looks exactly like an app crash. Updates remain disabled until H96 stability is
 * confirmed and can later be reintroduced as an explicit user action.
 */
@Composable
fun ShadowFoxUpdateGate(
    context: Context,
    content: @Composable () -> Unit
) {
    content()
}
