package com.z.takatuf.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonRemove
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.ui.common.AnswerCard
import com.z.takatuf.ui.common.ChoiceChip
import com.z.takatuf.ui.common.Fact
import com.z.takatuf.ui.common.Monogram
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SecondaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.Stepper
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.relativeTime

private const val ResponsibilityMax = 300

/** Who's on the team, what each person owns, and the membership decisions (US13, US15, US16). */
@Composable
internal fun PeopleTab(
    state: WorkspaceUiState,
    onOpenPerson: (String) -> Unit,
    onReviewApplicants: () -> Unit,
    onSetResponsibility: (TeamPerson, String) -> Unit,
    onReopen: (roleId: String, places: Int, reconfirm: Collection<String>) -> Unit,
    onNominate: (TeamPerson) -> Unit,
    onCancelNomination: () -> Unit,
    onAcceptLead: () -> Unit,
    onDeclineLead: () -> Unit,
    onRemove: (TeamPerson) -> Unit,
    onLeave: () -> Unit,
) {
    val listing = state.listing ?: return
    var editingResponsibility by remember { mutableStateOf<TeamPerson?>(null) }
    var confirmNominate by remember { mutableStateOf<TeamPerson?>(null) }
    var confirmRemove by remember { mutableStateOf<TeamPerson?>(null) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var reopening by rememberSaveable { mutableStateOf(false) }
    val pending = listing.pendingLeader

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
        if (pending != null) {
            item(key = "handover") {
                when {
                    pending.uid == state.myUid -> AnswerCard(
                        prompt = stringResource(R.string.lead_request_here, listing.ownerName),
                        busy = state.busy,
                        primary = stringResource(R.string.action_become_lead),
                        onPrimary = onAcceptLead,
                        secondary = stringResource(R.string.action_decline),
                        onSecondary = onDeclineLead,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    state.isLead -> Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.lead_handover_waiting, pending.name),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onCancelNomination, enabled = !state.busy) {
                                Text(stringResource(R.string.action_take_back))
                            }
                        }
                    }
                }
            }
        }
        item(key = "team-title") {
            SectionTitle(
                pluralStringResource(R.plurals.team_people, listing.headcount, listing.headcount),
                Modifier.padding(horizontal = 20.dp),
            )
            OffAppNote(listing.headcount - state.people.size)
        }
        items(state.people, key = { it.uid }) { person ->
            PersonRow(
                person = person,
                me = person.uid == state.myUid,
                responsibility = state.responsibilities[person.uid].orEmpty(),
                canEditResponsibility = state.isLead || person.uid == state.myUid,
                canManage = state.isLead && !person.isLead,
                nominated = pending?.uid == person.uid,
                onOpen = { onOpenPerson(person.uid) },
                onEditResponsibility = { editingResponsibility = person },
                onNominate = { confirmNominate = person },
                onRemove = { confirmRemove = person },
            )
        }
        item(key = "recruiting") {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Recruiting(listing, state, onReviewApplicants, onReopen = { reopening = true })
        }
        item(key = "leave") {
            Spacer(Modifier.height(16.dp))
            when {
                !state.isLead -> TextButton(
                    onClick = { confirmLeave = true },
                    enabled = !state.busy,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_leave_team), color = MaterialTheme.colorScheme.error)
                }
                listing.members.isNotEmpty() -> Text(
                    stringResource(R.string.lead_leave_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }

    editingResponsibility?.let { person ->
        ResponsibilityDialog(
            person = person,
            me = person.uid == state.myUid,
            initial = state.responsibilities[person.uid].orEmpty(),
            saving = state.busy,
            onSave = { onSetResponsibility(person, it); editingResponsibility = null },
            onDismiss = { editingResponsibility = null },
        )
    }
    confirmNominate?.let { person ->
        AlertDialog(
            onDismissRequest = { confirmNominate = null },
            title = { Text(stringResource(R.string.nominate_title, person.name)) },
            text = { Text(stringResource(R.string.nominate_body, person.name)) },
            confirmButton = {
                TextButton(onClick = { confirmNominate = null; onNominate(person) }) { Text(stringResource(R.string.action_ask_to_lead)) }
            },
            dismissButton = { TextButton(onClick = { confirmNominate = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    confirmRemove?.let { person ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.remove_member_title, person.name)) },
            text = { Text(stringResource(R.string.remove_member_body, person.name)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = null; onRemove(person) }) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.leave_team_title, listing.title)) },
            text = { Text(stringResource(R.string.leave_team_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onLeave() }) {
                    Text(stringResource(R.string.action_leave_team), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_stay)) } },
        )
    }
    if (reopening) {
        ReopenSheet(
            listing = listing,
            pastApplicants = state.pastApplicants,
            saving = state.busy,
            onReopen = { roleId, places, reconfirm -> onReopen(roleId, places, reconfirm); reopening = false },
            onDismiss = { reopening = false },
        )
    }
}

/** Teammates counted in the team size who don't use Takatuf, so the numbers add up. */
@Composable
internal fun OffAppNote(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Text(
        pluralStringResource(R.plurals.people_off_app, count, count),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun PersonRow(
    person: TeamPerson,
    me: Boolean,
    responsibility: String,
    canEditResponsibility: Boolean,
    canManage: Boolean,
    nominated: Boolean,
    onOpen: () -> Unit,
    onEditResponsibility: () -> Unit,
    onNominate: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Monogram(person.name, person.uid, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (me) stringResource(R.string.person_you, person.name) else person.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (person.isLead) {
                    Spacer(Modifier.width(8.dp))
                    Tag(stringResource(R.string.team_lead), icon = Icons.Rounded.MilitaryTech, tone = TagTone.Accent)
                } else if (nominated) {
                    Spacer(Modifier.width(8.dp))
                    Tag(stringResource(R.string.lead_nominated), tone = TagTone.Quiet)
                }
            }
            Text(
                when {
                    person.isLead -> person.major
                    person.roleTitle.isNotBlank() -> stringResource(R.string.person_role_joined, person.roleTitle, relativeTime(person.joinedAt))
                    else -> stringResource(R.string.person_former_lead)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            // What this person owns; the lead sets anyone's, members set their own.
            Row(
                Modifier.clickable(enabled = canEditResponsibility, onClick = onEditResponsibility),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    responsibility.ifBlank {
                        stringResource(if (canEditResponsibility) R.string.responsibility_add else R.string.responsibility_none)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        responsibility.isNotBlank() -> MaterialTheme.colorScheme.onSurface
                        canEditResponsibility -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (canEditResponsibility && responsibility.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.action_edit_responsibility, person.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (canManage) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_manage_member, person.name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!nominated) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_make_lead)) },
                            leadingIcon = { Icon(Icons.Rounded.MilitaryTech, contentDescription = null) },
                            onClick = { menu = false; onNominate() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove_from_team)) },
                        leadingIcon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }
    }
}

/** Open places and, for the lead, the two ways to fill them: applicants or a reopened place. */
@Composable
private fun Recruiting(listing: Listing, state: WorkspaceUiState, onReviewApplicants: () -> Unit, onReopen: () -> Unit) {
    val open = listing.recruitment?.openPlaces ?: 0
    if (!state.isLead && open == 0) return
    Column(Modifier.padding(horizontal = 20.dp)) {
        SectionTitle(stringResource(R.string.workspace_recruiting))
        Fact(
            Icons.Rounded.Groups,
            if (open > 0) pluralStringResource(R.plurals.open_places_count, open, open) else stringResource(R.string.workspace_team_full),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.isLead) {
            Spacer(Modifier.height(12.dp))
            if (open > 0 && state.activeApplicants > 0) {
                PrimaryButton(
                    text = pluralStringResource(R.plurals.review_applicants_count, state.activeApplicants, state.activeApplicants),
                    onClick = onReviewApplicants,
                )
                Spacer(Modifier.height(8.dp))
            }
            SecondaryButton(
                text = stringResource(R.string.action_reopen_place),
                onClick = onReopen,
                icon = Icons.Rounded.LockOpen,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.reopen_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResponsibilityDialog(
    person: TeamPerson,
    me: Boolean,
    initial: String,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (me) stringResource(R.string.responsibility_title_me) else stringResource(R.string.responsibility_title, person.name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= ResponsibilityMax) text = it },
                placeholder = { Text(stringResource(R.string.responsibility_placeholder)) },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = !saving && text.trim() != initial) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Reopening places in the same project, so the workspace carries on. Earlier applicants for the
 * role are asked to confirm they're still interested before they're considered again (US15).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReopenSheet(
    listing: Listing,
    pastApplicants: List<Application>,
    saving: Boolean,
    onReopen: (roleId: String, places: Int, reconfirm: Collection<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val roles = listing.recruitment?.roles.orEmpty()
    var roleId by rememberSaveable { mutableStateOf(roles.firstOrNull()?.id.orEmpty()) }
    var places by rememberSaveable { mutableIntStateOf(1) }
    val role = roles.firstOrNull { it.id == roleId }
    // Applicants are "earlier" when they applied for a place that has since filled or closed.
    val wasShut = listing.status != ListingStatus.Open || (role?.openings ?: 0) == 0
    val earlier = if (wasShut) pastApplicants.filter { it.roleId == roleId } else emptyList()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(stringResource(R.string.reopen_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.reopen_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.reopen_role), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                roles.forEach { r ->
                    ChoiceChip(selected = r.id == roleId, onClick = { roleId = r.id }, label = { Text(r.title) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Stepper(
                label = stringResource(R.string.reopen_places),
                value = places,
                onValueChange = { places = it },
                range = 1..4,
                supporting = role?.let { pluralStringResource(R.plurals.reopen_places_now, it.openings, it.openings) },
            )
            Spacer(Modifier.height(16.dp))
            if (earlier.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            pluralStringResource(R.plurals.reopen_ask_earlier, earlier.size, earlier.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.reopen_ask_earlier_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = pluralStringResource(R.plurals.action_reopen_places, places, places),
                onClick = { onReopen(roleId, places, earlier.map { it.applicantId }) },
                enabled = role != null,
                loading = saving,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
