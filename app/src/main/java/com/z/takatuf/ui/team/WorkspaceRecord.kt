package com.z.takatuf.ui.team

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.DraftStore
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.domain.Dates
import com.z.takatuf.ui.common.Monogram
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SecondaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.formatDay
import com.z.takatuf.ui.records.CompleteProjectSheet
import com.z.takatuf.ui.records.ContributionRow
import com.z.takatuf.ui.records.ContributionSheet

/**
 * What the team did, person by person, and the project's close (US17, US18). Members record
 * their own contributions; the lead confirms them, records them for others, and completes the
 * project, which opens feedback.
 */
@Composable
internal fun RecordTab(
    state: WorkspaceUiState,
    onAdd: (TeamPerson, title: String, detail: String, links: List<String>) -> Unit,
    onEdit: (Contribution, title: String, detail: String, links: List<String>) -> Unit,
    onDelete: (Contribution) -> Unit,
    onConfirm: (Contribution, Boolean) -> Unit,
    onComplete: (summary: String, link: String) -> Unit,
    onReview: () -> Unit,
) {
    val listing = state.listing ?: return
    var adding by remember { mutableStateOf<TeamPerson?>(null) }
    var editing by remember { mutableStateOf<Contribution?>(null) }
    var completing by rememberSaveable { mutableStateOf(false) }
    val opened = remember { state.done }
    LaunchedEffect(state.done) {
        if (state.done != opened) {
            adding = null
            editing = null
            completing = false
        }
    }
    val byPerson = state.contributions.groupBy { it.ownerUid }

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
        item(key = "close") {
            ProjectClose(state, onComplete = { completing = true }, onReview = onReview)
        }
        val formerIds = state.formerPeople.map { it.uid }.toSet()
        (state.people + state.formerPeople).forEach { person ->
            val mine = person.uid == state.myUid
            val theirs = byPerson[person.uid].orEmpty()
            item(key = "person-${person.uid}") {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Monogram(person.name, person.uid, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (mine) stringResource(R.string.person_you, person.name) else person.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (person.uid in formerIds) Tag(stringResource(R.string.participation_left), tone = TagTone.Quiet)
                    // Members record their own; the lead can record for anyone.
                    if (mine || state.isLead) {
                        TextButton(onClick = { adding = person }, enabled = !state.busy) {
                            Text(stringResource(R.string.action_add_contribution))
                        }
                    }
                }
                if (theirs.isEmpty()) {
                    Text(
                        stringResource(R.string.record_no_contributions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 62.dp, end = 20.dp),
                    )
                }
            }
            items(theirs, key = { "c-${it.ownerUid}-${it.id}" }) { c ->
                Column(Modifier.padding(start = 62.dp, end = 8.dp)) {
                    ContributionRow(contribution = c, onEdit = if (mine && c.confirmedBy == null) ({ editing = c }) else null)
                    // The lead vouches for members' work; the action sits under it so the text keeps its width.
                    if (state.isLead && !mine) {
                        TextButton(
                            onClick = { onConfirm(c, c.confirmedBy == null) },
                            enabled = !state.busy && !c.pending,
                            contentPadding = PaddingValues(horizontal = 0.dp),
                            modifier = Modifier.offset(y = (-6).dp),
                        ) {
                            Text(stringResource(if (c.confirmedBy == null) R.string.action_confirm else R.string.action_unconfirm))
                        }
                    }
                }
            }
        }
    }

    adding?.let { person ->
        val mine = person.uid == state.myUid
        ContributionSheet(
            title = if (mine) stringResource(R.string.contribution_add_title) else stringResource(R.string.contribution_add_for, person.name),
            subtitle = listing.title,
            draftKey = DraftStore.contribution(listing.id, person.uid),
            initial = null,
            saving = state.busy,
            note = stringResource(if (mine) R.string.contribution_note_self else R.string.contribution_note_lead),
            onSave = { title, detail, links -> onAdd(person, title, detail, links) },
            onDelete = null,
            onDismiss = { adding = null },
        )
    }
    editing?.let { c ->
        ContributionSheet(
            title = stringResource(R.string.contribution_edit_title),
            subtitle = listing.title,
            draftKey = DraftStore.contribution(listing.id, c.ownerUid),
            initial = c,
            saving = state.busy,
            note = stringResource(R.string.contribution_note_edit),
            onSave = { title, detail, links -> onEdit(c, title, detail, links) },
            onDelete = { onDelete(c) },
            onDismiss = { editing = null },
        )
    }
    if (completing) {
        CompleteProjectSheet(saving = state.busy, onComplete = onComplete, onDismiss = { completing = false })
    }
}

/** Either the finished project and its result, or (for the lead) the way to finish it. */
@Composable
private fun ProjectClose(state: WorkspaceUiState, onComplete: () -> Unit, onReview: () -> Unit) {
    val listing = state.listing ?: return
    val completedAt = listing.completedAt
    Column(Modifier.padding(horizontal = 20.dp)) {
        if (completedAt != null) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.complete_done_on, formatDay(Dates.startDate(completedAt))),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    listing.completion?.summary?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    listing.completion?.link?.takeIf { it.isNotBlank() }?.let { link ->
                        val openUrl = rememberOpenUrl()
                        TextButton(onClick = { openUrl(link) }, contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text(stringResource(R.string.complete_open_result))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = stringResource(R.string.action_review_teammates), onClick = onReview)
        } else {
            SectionTitle(stringResource(R.string.record_tab_title))
            Text(
                stringResource(if (state.isLead) R.string.record_tab_body_lead else R.string.record_tab_body_member),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.isLead) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = stringResource(R.string.action_complete_project),
                    onClick = onComplete,
                    icon = Icons.Rounded.TaskAlt,
                    enabled = !state.busy,
                )
            }
            // Feedback on someone who left opens with their departure, before the project ends (US18).
            if (state.formerPeople.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = stringResource(R.string.action_review_teammates), onClick = onReview)
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
