package com.z.takatuf.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.R
import com.z.takatuf.data.firebase.SyncTracker
import kotlinx.coroutines.delay

/**
 * A small pill at the top of the app that keeps pending and finished actions apart (US21):
 * "2 changes waiting to send" while offline, "All changes sent" once they go through, and a
 * clear notice when the server refused one.
 */
@Composable
fun SyncBanner(modifier: Modifier = Modifier) {
    val pending by SyncTracker.pending.collectAsStateWithLifecycle()
    var outcome by remember { mutableStateOf<SyncTracker.Outcome?>(null) }
    LaunchedEffect(Unit) {
        SyncTracker.outcomes.collect {
            outcome = it
            delay(if (it == SyncTracker.Outcome.Refused) 6_000 else 3_000)
            outcome = null
        }
    }
    val state = when {
        outcome == SyncTracker.Outcome.Refused -> Triple(Icons.Rounded.ErrorOutline, stringResource(R.string.sync_refused), true)
        pending > 0 -> Triple(Icons.Rounded.CloudOff, pluralStringResource(R.plurals.sync_pending, pending, pending), false)
        outcome == SyncTracker.Outcome.AllSent -> Triple(Icons.Rounded.CloudDone, stringResource(R.string.sync_all_sent), false)
        else -> null
    }
    AnimatedVisibility(visible = state != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier.statusBarsPadding()) {
        val (icon, text, error) = state ?: return@AnimatedVisibility
        Surface(
            shape = CircleShape,
            color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 3.dp,
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
