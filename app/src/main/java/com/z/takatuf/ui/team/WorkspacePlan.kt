package com.z.takatuf.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.z.takatuf.ui.common.ActionSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.Milestone
import com.z.takatuf.data.model.MilestoneStatus
import com.z.takatuf.domain.Dates
import com.z.takatuf.ui.common.ChoiceChip
import com.z.takatuf.ui.common.ChoiceRow
import com.z.takatuf.ui.common.DateField
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.Fact
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.formatDay
import java.time.Instant
import java.time.LocalDate

private const val MilestoneTitleMax = 80

/** Milestones, outstanding first, with done ones kept below and clearly set apart (US14). */
@Composable
internal fun PlanTab(
    state: WorkspaceUiState,
    onEdit: (Milestone) -> Unit,
    onSetStatus: (Milestone, MilestoneStatus) -> Unit,
    onAdd: () -> Unit,
) {
    var mineOnly by rememberSaveable { mutableStateOf(false) }
    if (state.milestones.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.plan_empty_title),
            body = stringResource(R.string.plan_empty_body),
            actionLabel = stringResource(R.string.action_add_milestone),
            onAction = onAdd,
        )
        return
    }
    val now = Instant.now()
    val outstanding = state.outstanding.filter { !mineOnly || it.assigneeId == state.myUid }
    val completed = state.completed.filter { !mineOnly || it.assigneeId == state.myUid }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "progress") { Progress(state, now) }
        item(key = "filter") {
            ChoiceRow(
                options = listOf(false, true),
                selected = mineOnly,
                onSelect = { mineOnly = it },
                label = { stringResource(if (it) R.string.plan_filter_mine else R.string.plan_filter_all) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item(key = "outstanding") {
            SectionTitle(
                pluralStringResource(R.plurals.plan_outstanding, outstanding.size, outstanding.size),
                Modifier.padding(horizontal = 20.dp),
            )
        }
        if (outstanding.isEmpty()) {
            item(key = "outstanding-none") {
                Text(
                    stringResource(if (mineOnly) R.string.plan_none_mine else R.string.plan_none_outstanding),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(outstanding, key = { it.id }) { milestone ->
            MilestoneRow(milestone, state, now, onEdit, onSetStatus)
        }
        if (completed.isNotEmpty()) {
            item(key = "completed") {
                Spacer(Modifier.height(12.dp))
                SectionTitle(
                    pluralStringResource(R.plurals.plan_completed, completed.size, completed.size),
                    Modifier.padding(horizontal = 20.dp),
                )
            }
            items(completed, key = { it.id }) { milestone ->
                MilestoneRow(milestone, state, now, onEdit, onSetStatus)
            }
        }
    }
}

@Composable
private fun Progress(state: WorkspaceUiState, now: Instant) {
    val total = state.milestones.size
    val done = state.completed.size
    val overdue = state.outstanding.count { it.isOverdue(now) }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.plan_progress, total, done, total),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (overdue > 0) {
                Tag(pluralStringResource(R.plurals.plan_overdue_count, overdue, overdue), tone = TagTone.Attention)
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilestoneRow(
    milestone: Milestone,
    state: WorkspaceUiState,
    now: Instant,
    onEdit: (Milestone) -> Unit,
    onSetStatus: (Milestone, MilestoneStatus) -> Unit,
) {
    val canEdit = state.canEdit(milestone)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = canEdit, role = Role.Button) { onEdit(milestone) }
            .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // One tap marks it done, or back to to-do.
        IconButton(
            onClick = { onSetStatus(milestone, if (milestone.isDone) MilestoneStatus.Todo else MilestoneStatus.Done) },
            enabled = canEdit && !state.busy,
        ) {
            Icon(
                imageVector = when (milestone.status) {
                    MilestoneStatus.Done -> Icons.Rounded.CheckCircle
                    MilestoneStatus.Doing -> Icons.Rounded.Timelapse
                    MilestoneStatus.Todo -> Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = stringResource(if (milestone.isDone) R.string.action_mark_not_done else R.string.action_mark_done, milestone.title),
                tint = when (milestone.status) {
                    MilestoneStatus.Done, MilestoneStatus.Doing -> MaterialTheme.colorScheme.primary
                    MilestoneStatus.Todo -> MaterialTheme.colorScheme.outline
                },
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(top = 12.dp)
                .alpha(if (milestone.isDone) 0.72f else 1f),
        ) {
            Text(
                milestone.title,
                style = MaterialTheme.typography.titleSmall,
                textDecoration = if (milestone.isDone) TextDecoration.LineThrough else null,
                color = if (milestone.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Fact(
                    Icons.Rounded.Person,
                    if (milestone.assigneeId == state.myUid) stringResource(R.string.you) else milestone.assigneeName,
                    color = if (milestone.assigneeId == state.myUid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val due = milestone.dueDate
                when {
                    milestone.isDone -> milestone.completedAt?.let {
                        Fact(Icons.Rounded.CheckCircle, stringResource(R.string.milestone_done_on, formatDay(Dates.startDate(it))))
                    }
                    due != null && milestone.isOverdue(now) ->
                        Tag(stringResource(R.string.milestone_overdue, formatDay(Dates.lastDay(due))), tone = TagTone.Attention)
                    due != null -> Fact(Icons.Rounded.Event, stringResource(R.string.milestone_due, formatDay(Dates.lastDay(due))))
                    // Saved before target dates were required; editing it asks for one.
                    else -> Tag(stringResource(R.string.milestone_needs_date), tone = TagTone.Attention)
                }
                if (milestone.status == MilestoneStatus.Doing) {
                    Tag(stringResource(R.string.milestone_doing), tone = TagTone.Accent)
                }
                if (milestone.pending) Tag(stringResource(R.string.pending_send), tone = TagTone.Quiet)
            }
        }
    }
}

/** Adding or editing a milestone: what, who, by when, and (when editing) how far along. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MilestoneSheet(
    milestone: Milestone?,
    people: List<TeamPerson>,
    myUid: String,
    canDelete: Boolean,
    saving: Boolean,
    onSave: (title: String, assignee: TeamPerson, due: LocalDate, status: MilestoneStatus) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(milestone?.title.orEmpty()) }
    var assigneeId by rememberSaveable { mutableStateOf(milestone?.assigneeId ?: myUid) }
    var dueEpochDay by rememberSaveable { mutableStateOf(milestone?.dueDate?.let { Dates.lastDay(it).toEpochDay() }) }
    var status by rememberSaveable { mutableStateOf(milestone?.status ?: MilestoneStatus.Todo) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val assignee = people.firstOrNull { it.uid == assigneeId } ?: people.firstOrNull { it.uid == myUid } ?: people.firstOrNull()

    ActionSheet(onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                stringResource(if (milestone == null) R.string.milestone_new_title else R.string.milestone_edit_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= MilestoneTitleMax) title = it },
                label = { Text(stringResource(R.string.milestone_field_title)) },
                placeholder = { Text(stringResource(R.string.milestone_field_title_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.milestone_field_assignee), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                people.forEach { person ->
                    ChoiceChip(
                        selected = person.uid == assignee?.uid,
                        onClick = { assigneeId = person.uid },
                        label = { Text(if (person.uid == myUid) stringResource(R.string.you) else person.name) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // A target date is required (US14); undated milestones from before must get one here.
            DateField(
                label = stringResource(R.string.milestone_field_due),
                date = dueEpochDay?.let(LocalDate::ofEpochDay),
                onDate = { dueEpochDay = it?.toEpochDay() },
                placeholder = stringResource(R.string.milestone_field_due_choose),
                supporting = if (dueEpochDay == null) stringResource(R.string.milestone_field_due_required) else null,
                isError = dueEpochDay == null && milestone != null,
                earliest = if (milestone == null) Dates.today() else null,
            )
            if (milestone != null) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.milestone_field_status), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ChoiceRow(
                    options = MilestoneStatus.entries,
                    selected = status,
                    onSelect = { status = it },
                    label = { stringResource(statusLabel(it)) },
                )
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = stringResource(if (milestone == null) R.string.action_add_milestone else R.string.action_save),
                onClick = {
                    val due = dueEpochDay?.let(LocalDate::ofEpochDay)
                    if (assignee != null && due != null) onSave(title.trim(), assignee, due, status)
                },
                enabled = title.isNotBlank() && assignee != null && dueEpochDay != null,
                loading = saving,
            )
            if (milestone != null && canDelete) {
                Spacer(Modifier.height(4.dp))
                if (confirmDelete) {
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.milestone_delete_confirm),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onDelete, enabled = !saving) {
                                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_delete_milestone), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun statusLabel(status: MilestoneStatus): Int = when (status) {
    MilestoneStatus.Todo -> R.string.milestone_todo
    MilestoneStatus.Doing -> R.string.milestone_doing
    MilestoneStatus.Done -> R.string.milestone_done
}
