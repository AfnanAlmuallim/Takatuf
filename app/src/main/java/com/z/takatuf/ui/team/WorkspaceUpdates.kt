package com.z.takatuf.ui.team

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.Deliverable
import com.z.takatuf.data.model.TeamUpdate
import com.z.takatuf.ui.common.Monogram
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.relativeTime

private const val UpdateMax = 1000
private const val LinkMax = 300
private const val LabelMax = 60

/** Links to the team's work at the top, then the running log of updates (US13). */
@Composable
internal fun UpdatesTab(
    state: WorkspaceUiState,
    onDeleteUpdate: (TeamUpdate) -> Unit,
    onAddLink: (label: String, url: String) -> Unit,
    onDeleteLink: (Deliverable) -> Unit,
    onCompose: () -> Unit,
) {
    var addingLink by rememberSaveable { mutableStateOf(false) }
    val openUrl = rememberOpenUrl()
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)) {
        item(key = "links") {
            SectionTitle(stringResource(R.string.workspace_links), Modifier.padding(horizontal = 20.dp)) {
                TextButton(onClick = { addingLink = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_add_link))
                }
            }
            if (state.deliverables.isEmpty()) {
                Hint(stringResource(R.string.workspace_links_empty))
            }
        }
        items(state.deliverables, key = { "link-${it.id}" }) { link ->
            LinkRow(
                link = link,
                canDelete = state.isLead || link.addedBy == state.myUid,
                onOpen = { openUrl(link.url) },
                onDelete = { onDeleteLink(link) },
            )
        }
        item(key = "updates") {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.workspace_updates), Modifier.padding(horizontal = 20.dp))
            if (state.updates.isEmpty()) {
                Hint(stringResource(R.string.workspace_updates_empty))
                TextButton(onClick = onCompose, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.action_post_update))
                }
            }
        }
        items(state.updates, key = { "update-${it.id}" }) { update ->
            UpdateRow(
                update = update,
                mine = update.authorId == state.myUid,
                canDelete = state.isLead || update.authorId == state.myUid,
                onOpenLink = { openUrl(update.link) },
                onDelete = { onDeleteUpdate(update) },
            )
        }
    }
    if (addingLink) {
        LinkDialog(
            saving = state.busy,
            onAdd = { label, url -> onAddLink(label, url); addingLink = false },
            onDismiss = { addingLink = false },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun LinkRow(link: Deliverable, canDelete: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(link.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.link_meta, hostOf(link.url), link.addedByName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove_link, link.label), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UpdateRow(update: TeamUpdate, mine: Boolean, canDelete: Boolean, onOpenLink: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
        Monogram(update.authorName, update.authorId, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mine) stringResource(R.string.you) else update.authorName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(relativeTime(update.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(update.text, style = MaterialTheme.typography.bodyMedium)
            if (update.link.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable(onClick = onOpenLink),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(hostOf(update.link), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }
            }
        }
        if (canDelete) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete_update)) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Posting progress, a blocker or a decision, with an optional link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateSheet(posting: Boolean, onPost: (text: String, link: String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text(stringResource(R.string.update_sheet_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= UpdateMax) text = it },
                label = { Text(stringResource(R.string.update_field_text)) },
                placeholder = { Text(stringResource(R.string.update_field_text_placeholder)) },
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { if (it.length <= LinkMax) link = it.trim() },
                label = { Text(stringResource(R.string.update_field_link)) },
                placeholder = { Text(stringResource(R.string.link_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.action_post_update),
                onClick = { onPost(text.trim(), link) },
                enabled = text.isNotBlank() && (link.isEmpty() || looksLikeLink(link)),
                loading = posting,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LinkDialog(saving: Boolean, onAdd: (label: String, url: String) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= LabelMax) label = it },
                    label = { Text(stringResource(R.string.link_field_label)) },
                    placeholder = { Text(stringResource(R.string.link_field_label_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { if (it.length <= LinkMax) url = it.trim() },
                    label = { Text(stringResource(R.string.link_field_url)) },
                    placeholder = { Text(stringResource(R.string.link_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(label.trim(), url) }, enabled = !saving && label.isNotBlank() && looksLikeLink(url)) {
                Text(stringResource(R.string.action_add_link))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Something with a dot and no spaces: enough to be worth opening. */
private fun looksLikeLink(text: String): Boolean = text.length >= 4 && '.' in text && text.none { it.isWhitespace() }

internal fun hostOf(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/').ifBlank { url }

@Composable
internal fun rememberOpenUrl(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return remember(uriHandler) {
        { url ->
            val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            try {
                uriHandler.openUri(target)
            } catch (_: ActivityNotFoundException) {
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
