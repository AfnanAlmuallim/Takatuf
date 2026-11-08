package com.z.takatuf.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.TakatufApplication
import com.z.takatuf.data.DraftStore
import kotlinx.coroutines.delay

@Composable
fun rememberDrafts(): DraftStore {
    val context = LocalContext.current
    return remember { (context.applicationContext as TakatufApplication).container.drafts }
}

/** Keeps [values] on the device under [key] a moment after the student stops typing (US21). */
@Composable
fun AutosaveDraft(key: String, values: Map<String, String>) {
    val drafts = rememberDrafts()
    LaunchedEffect(key, values) {
        delay(400)
        drafts.save(key, values)
    }
}

/** Says a form was filled from a saved draft, with a way to start over. */
@Composable
fun DraftRestoredNote(onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.draft_restored),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard_draft)) }
        }
    }
}
