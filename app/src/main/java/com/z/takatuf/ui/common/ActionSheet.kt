package com.z.takatuf.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.z.takatuf.R

/** The latest failed action, for a sheet that covers the screen's snackbar (US21). */
object SheetFailure {
    var current by mutableStateOf<Failure?>(null)

    data class Failure(val text: String, val retry: () -> Unit)
}

/**
 * A bottom sheet for a form that saves. It draws above the screen and would cover the screen's
 * snackbar, so a failed save shows here too, with its Retry (US21).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    DisposableEffect(Unit) {
        SheetFailure.current = null
        onDispose { SheetFailure.current = null }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SheetFailure.current?.let { failure ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(failure.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            SheetFailure.current = null
                            failure.retry()
                        },
                    ) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
        content()
    }
}
