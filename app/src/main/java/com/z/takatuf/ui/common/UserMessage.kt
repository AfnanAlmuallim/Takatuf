package com.z.takatuf.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import com.z.takatuf.R
import kotlinx.coroutines.launch

/**
 * A one-off confirmation or error for a snackbar. [id] makes repeats of the same text distinct.
 * A failed action carries [retry], which repeats exactly the same action: the same document ids
 * and the same notifications, so retrying never creates a duplicate (US21).
 */
data class UserMessage(
    @param:StringRes val text: Int,
    val args: List<Any> = emptyList(),
    val id: Long = System.nanoTime(),
    val retry: (() -> Unit)? = null,
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
            val retry = message.retry
            onShown()
            if (retry != null) SheetFailure.current = SheetFailure.Failure(text, retry)
            scope.launch {
                if (retry == null) {
                    snackbar.showSnackbar(text)
                } else {
                    val result = snackbar.showSnackbar(
                        message = text,
                        actionLabel = resources.getString(R.string.action_retry),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) retry()
                }
            }
        }
    }
}
