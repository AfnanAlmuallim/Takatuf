package com.z.takatuf.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.R
import com.z.takatuf.TakatufApplication
import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.LoadingRows
import com.z.takatuf.ui.common.relativeTime
import com.z.takatuf.ui.theme.TakatufTheme

/** Everything that happened to the student, newest first; unread items stand out (US19). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpen: (AppNotification) -> Unit) {
    val viewModel = appViewModel { c, _ -> NotificationsViewModel(c.session, c.notifications) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val res = LocalResources.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // With enlarged text the label would crowd out the title, so the icon stands alone.
                    if (state.unread > 0 && LocalDensity.current.fontScale > 1.15f) {
                        IconButton(onClick = viewModel::markAllRead) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = stringResource(R.string.action_mark_all_read))
                        }
                    } else if (state.unread > 0) {
                        TextButton(onClick = viewModel::markAllRead) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_mark_all_read))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingRows()
                state.error != null -> ErrorState(state.error!!, viewModel::retry)
                state.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.notifications_empty_title),
                    body = stringResource(R.string.notifications_empty_body),
                    openSeats = 3,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                        NotificationRow(
                            item = item,
                            title = item.title(res),
                            onClick = { viewModel.open(item); onOpen(item) },
                            onDelete = { viewModel.delete(item) },
                        )
                        if (index < state.items.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: AppNotification, title: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val unreadLabel = stringResource(R.string.notification_unread)
    val readLabel = stringResource(R.string.notification_read)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (item.read) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .semantics { stateDescription = if (item.read) readLabel else unreadLabel }
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            // Unread is shown by a dot, a tinted row and bold text, never by colour alone.
            if (!item.read) Box(Modifier.size(10.dp).background(TakatufTheme.colors.attention, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
            )
            if (item.note.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(item.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
            }
            Spacer(Modifier.height(4.dp))
            Text(relativeTime(item.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove_notification), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A tab's title with the notification bell beside it. */
@Composable
fun TabTitle(text: String, onInbox: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        InboxButton(onClick = onInbox)
    }
}

/** The bell in each tab's header, with the number of unread notifications. */
@Composable
fun InboxButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as TakatufApplication).container
    val uid = container.session.uid.orEmpty()
    val unreadFlow = remember(uid) { container.notifications.unreadCount(uid) }
    val unread by unreadFlow.collectAsStateWithLifecycle(0)
    val description = if (unread > 0) {
        pluralStringResource(R.plurals.notifications_unread_a11y, unread, unread)
    } else {
        stringResource(R.string.notifications_title)
    }
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
        BadgedBox(
            badge = {
                if (unread > 0) {
                    Badge(containerColor = TakatufTheme.colors.attention, contentColor = TakatufTheme.colors.onAttention) {
                        Text(if (unread > 99) "99+" else unread.toString())
                    }
                }
            },
        ) {
            Icon(if (unread > 0) Icons.Rounded.Notifications else Icons.Outlined.Notifications, contentDescription = null)
        }
    }
}
