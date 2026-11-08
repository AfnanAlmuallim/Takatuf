package com.z.takatuf.ui.records

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.z.takatuf.ui.common.ActionSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.domain.Validators
import com.z.takatuf.ui.common.AutosaveDraft
import com.z.takatuf.ui.common.DraftRestoredNote
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.rememberDrafts

/**
 * Recording a contribution: what was done, optional detail, and up to three links to the
 * work. Links turn a claim into evidence; a lead recording one confirms it (US17).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionSheet(
    title: String,
    subtitle: String,
    draftKey: String,
    initial: Contribution?,
    saving: Boolean,
    note: String,
    onSave: (title: String, detail: String, links: List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val drafts = rememberDrafts()
    val restored = remember { if (initial == null) drafts.load(draftKey) else null }
    var what by rememberSaveable { mutableStateOf(initial?.title ?: restored?.get("title").orEmpty()) }
    var detail by rememberSaveable { mutableStateOf(initial?.detail ?: restored?.get("detail").orEmpty()) }
    var links by rememberSaveable { mutableStateOf(initial?.links?.joinToString("\n") ?: restored?.get("links").orEmpty()) }
    var showRestored by rememberSaveable { mutableStateOf(restored != null) }
    if (initial == null) AutosaveDraft(draftKey, mapOf("title" to what, "detail" to detail, "links" to links))

    val linkList = links.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val normalised = linkList.map { Validators.webLink(it) }
    val linksValid = normalised.none { it == null } && linkList.size <= 3

    ActionSheet(onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showRestored) {
                Spacer(Modifier.height(12.dp))
                DraftRestoredNote(onDiscard = { what = ""; detail = ""; links = ""; drafts.clear(draftKey); showRestored = false })
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = what,
                onValueChange = { if (it.length <= 80) what = it },
                label = { Text(stringResource(R.string.contribution_field_title)) },
                placeholder = { Text(stringResource(R.string.contribution_field_title_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = detail,
                onValueChange = { if (it.length <= 500) detail = it },
                label = { Text(stringResource(R.string.contribution_field_detail)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = links,
                onValueChange = { links = it },
                label = { Text(stringResource(R.string.contribution_field_links)) },
                placeholder = { Text(stringResource(R.string.contribution_field_links_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                isError = !linksValid,
                supportingText = {
                    Text(stringResource(if (linksValid) R.string.contribution_links_hint else R.string.contribution_links_invalid))
                },
                minLines = 2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            if (note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.action_save_contribution),
                onClick = { onSave(what.trim(), detail.trim(), normalised.filterNotNull()) },
                enabled = what.trim().length >= 3 && linksValid,
                loading = saving,
            )
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_delete_contribution), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The lead completes the project: a short summary and an optional link to the result (US17). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProjectSheet(saving: Boolean, onComplete: (summary: String, link: String) -> Unit, onDismiss: () -> Unit) {
    var summary by rememberSaveable { mutableStateOf("") }
    var link by rememberSaveable { mutableStateOf("") }
    val normalised = Validators.webLink(link)
    val linkValid = link.isBlank() || normalised != null
    ActionSheet(onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.complete_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.complete_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = summary,
                onValueChange = { if (it.length <= 500) summary = it },
                label = { Text(stringResource(R.string.complete_summary)) },
                placeholder = { Text(stringResource(R.string.complete_summary_placeholder)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = link,
                onValueChange = { if (it.length <= 300) link = it.trim() },
                label = { Text(stringResource(R.string.complete_link)) },
                placeholder = { Text(stringResource(R.string.link_placeholder)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                isError = !linkValid,
                supportingText = if (!linkValid) ({ Text(stringResource(R.string.link_invalid)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.action_complete_project),
                onClick = { onComplete(summary.trim(), normalised.orEmpty()) },
                enabled = summary.trim().length >= 10 && linkValid,
                loading = saving,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Reviewing one teammate: contribution, leadership when they led, and a comment (US18). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    name: String,
    project: String,
    askLeadership: Boolean,
    draftKey: String,
    initialContribution: Int?,
    initialLeadership: Int?,
    initialComment: String?,
    saving: Boolean,
    onSave: (contribution: Int, leadership: Int?, comment: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val drafts = rememberDrafts()
    val restored = remember { if (initialComment == null) drafts.load(draftKey) else null }
    var contribution by rememberSaveable { mutableStateOf(initialContribution ?: restored?.get("contribution")?.toIntOrNull()) }
    var leadership by rememberSaveable { mutableStateOf(initialLeadership ?: restored?.get("leadership")?.toIntOrNull()) }
    var comment by rememberSaveable { mutableStateOf(initialComment ?: restored?.get("comment").orEmpty()) }
    var showRestored by rememberSaveable { mutableStateOf(restored != null) }
    if (initialComment == null) {
        AutosaveDraft(draftKey, mapOf("contribution" to (contribution?.toString() ?: ""), "leadership" to (leadership?.toString() ?: ""), "comment" to comment))
    }
    ActionSheet(onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(stringResource(R.string.review_title, name), style = MaterialTheme.typography.headlineSmall)
            Text(project, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showRestored) {
                Spacer(Modifier.height(12.dp))
                DraftRestoredNote(onDiscard = { contribution = null; leadership = null; comment = ""; drafts.clear(draftKey); showRestored = false })
            }
            Spacer(Modifier.height(16.dp))
            RatingPicker(stringResource(R.string.review_contribution_question, name), contribution, onChange = { contribution = it })
            if (askLeadership) {
                Spacer(Modifier.height(16.dp))
                RatingPicker(stringResource(R.string.review_leadership_question, name), leadership, onChange = { leadership = it })
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= 500) comment = it },
                label = { Text(stringResource(R.string.review_comment)) },
                placeholder = { Text(stringResource(R.string.review_comment_placeholder)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.review_visibility_note, name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.action_save_review),
                onClick = { contribution?.let { onSave(it, if (askLeadership) leadership else null, comment.trim()) } },
                enabled = contribution != null && (!askLeadership || leadership != null),
                loading = saving,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Disputing a review: it stays on record, marked disputed, and moderators are told (US18). */
@Composable
fun DisputeDialog(reviewer: String, onDispute: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dispute_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dispute_body, reviewer), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    label = { Text(stringResource(R.string.dispute_reason)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDispute(reason.trim()) }, enabled = reason.trim().length >= 3) {
                Text(stringResource(R.string.action_dispute_feedback))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
