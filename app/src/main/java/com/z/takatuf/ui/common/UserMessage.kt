package com.z.takatuf.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.launch

/** A one-off confirmation or error for a snackbar. [id] makes repeats of the same text distinct. */
data class UserMessage(
    @param:StringRes val text: Int,
    val args: List<Any> = emptyList(),
    val id: Long = System.nanoTime(),
)

/**
 * Shows [message] once, then reports it as shown so it isn't repeated after rotation.
 *
 * Reporting it clears the message, which restarts the keyed effect; the snackbar therefore runs
 * in the screen's own scope so that restart doesn't cancel it mid-display.
 */
@Composable
fun MessageEffect(message: UserMessage?, snackbar: SnackbarHostState, onShown: () -> Unit) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(message?.id) {
        if (message != null) {
            val text = resources.getString(message.text, *message.args.toTypedArray())
            onShown()
            scope.launch { snackbar.showSnackbar(text) }
        }
    }
}
