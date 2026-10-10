package com.z.takatuf.ui.editor

import com.z.takatuf.ui.common.ChoiceChip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.z.takatuf.R
import com.z.takatuf.data.Catalog
import com.z.takatuf.data.RoleTemplates
import com.z.takatuf.data.model.EventFormat
import com.z.takatuf.data.model.EventType
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.Role
import com.z.takatuf.domain.Dates
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.BottomActionBar
import com.z.takatuf.ui.common.ChoiceRow
import com.z.takatuf.ui.common.DateField
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SeatRing
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.Stepper
import com.z.takatuf.ui.common.eventTypeLabel
import com.z.takatuf.ui.common.formatEventRange
import com.z.takatuf.ui.common.seatsDescription
import com.z.takatuf.ui.common.teamSizeText
import com.z.takatuf.ui.navigation.EditorRoute

private const val MaxTeamSize = 20

/** Role templates shown before "More roles"; the rest stay one tap away. */
private const val QuickTemplates = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onClose: () -> Unit,
    onSaved: (listingId: String, wasEditing: Boolean) -> Unit,
    onShareEventInstead: () -> Unit,
) {
    val resources = LocalResources.current
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        val route = handle.toRoute<EditorRoute>()
        EditorViewModel(
            kind = route.listingKind,
            listingId = route.listingId,
            eventId = route.eventId,
            session = c.session,
            listings = c.listings,
            teamTitleFor = { title -> resources.getString(R.string.team_default_title, title) },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val dirty = state.title.isNotBlank() || state.outcome.isNotBlank() || state.roles.isNotEmpty() || state.description.isNotBlank()
    val requestClose = { if (dirty && !state.isEditing) confirmDiscard = true else onClose() }
    BackHandler(onBack = requestClose)

    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it, state.isEditing) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(editorTitle(state)) },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
        bottomBar = {
            BottomActionBar(Modifier.imePadding()) {
                when {
                    state.error != null -> Text(stringResource(state.error!!.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    state.errors.isNotEmpty() -> Text(stringResource(R.string.editor_fix_fields), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                PrimaryButton(
                    text = stringResource(if (state.isEditing) R.string.action_save_changes else R.string.action_publish),
                    onClick = viewModel::publish,
                    loading = state.saving,
                    enabled = !state.loading,
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            when (state.kind) {
                ListingKind.Event -> EventForm(state, viewModel)
                else -> RecruitmentForm(state, viewModel, onShareEventInstead)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.action_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_keep_editing)) } },
        )
    }
}

@Composable
private fun editorTitle(state: EditorUiState): String = stringResource(
    when {
        state.isEditing && state.kind == ListingKind.Event -> R.string.action_edit_event
        state.isEditing -> R.string.action_edit_listing
        state.kind == ListingKind.Project -> R.string.post_project_title
        state.kind == ListingKind.Team -> R.string.post_team_title
        else -> R.string.post_event_title
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecruitmentForm(state: EditorUiState, vm: EditorViewModel, onShareEventInstead: () -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var editingRole by rememberSaveable { mutableStateOf<String?>(null) }
    var newRole by rememberSaveable { mutableStateOf(false) }
    val isTeam = state.kind == ListingKind.Team
    val eventMax = state.event?.event?.teamSizeMax

    Spacer(Modifier.height(8.dp))
    if (isTeam) {
        SectionTitle(stringResource(R.string.editor_event_section))
        EventChooser(state, onChoose = { showPicker = true })
        Spacer(Modifier.height(20.dp))
    }
    OutlinedTextField(
        value = state.title,
        onValueChange = { v -> if (v.length <= 90) vm.update { it.copy(title = v, errors = it.errors - Field.Title) } },
        label = { Text(stringResource(if (isTeam) R.string.editor_team_name else R.string.editor_project_name)) },
        isError = Field.Title in state.errors,
        supportingText = if (Field.Title in state.errors) ({ Text(stringResource(R.string.editor_title_error)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.outcome,
        onValueChange = { v -> if (v.length <= 400) vm.update { it.copy(outcome = v, errors = it.errors - Field.Outcome) } },
        label = { Text(stringResource(if (isTeam) R.string.editor_team_goal else R.string.editor_outcome)) },
        placeholder = { Text(stringResource(if (isTeam) R.string.editor_team_goal_placeholder else R.string.editor_outcome_placeholder)) },
        isError = Field.Outcome in state.errors,
        supportingText = { Text(stringResource(if (Field.Outcome in state.errors) R.string.editor_outcome_error else R.string.editor_outcome_hint)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.editor_roles_section))
    Text(
        stringResource(R.string.editor_roles_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = if (Field.Roles in state.errors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.roles.forEach { role -> RoleSummary(role, onClick = { editingRole = role.id }) }
    }
    if (state.roles.isNotEmpty()) Spacer(Modifier.height(12.dp))
    val used = state.roles.map { it.title.lowercase() }.toSet()
    var showAllTemplates by rememberSaveable { mutableStateOf(false) }
    val available = RoleTemplates.all.filter { it.title.lowercase() !in used }
    val shown = if (showAllTemplates) available else available.take(QuickTemplates)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { template ->
            AssistChip(
                onClick = { vm.addRoleFromTemplate(template) },
                label = { Text(template.title) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                shape = CircleShape,
            )
        }
        if (available.size > QuickTemplates) {
            AssistChip(
                onClick = { showAllTemplates = !showAllTemplates },
                label = {
                    Text(
                        if (showAllTemplates) stringResource(R.string.editor_fewer_roles)
                        else pluralStringResource(R.plurals.editor_more_roles, available.size - QuickTemplates, available.size - QuickTemplates),
                    )
                },
                trailingIcon = {
                    Icon(
                        if (showAllTemplates) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                shape = CircleShape,
            )
        }
        AssistChip(
            onClick = { newRole = true },
            label = { Text(stringResource(R.string.editor_custom_role)) },
            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            shape = CircleShape,
        )
    }

    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.editor_team_section))
    val teamMax = ((eventMax ?: MaxTeamSize) - state.openPlaces).coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        SeatRing(
            taken = state.teamSize,
            open = state.openPlaces,
            description = seatsDescription(state.teamSize, state.openPlaces),
            size = 56.dp,
        )
        Spacer(Modifier.width(16.dp))
        Stepper(
            label = stringResource(R.string.editor_team_size),
            value = state.teamSize,
            onValueChange = { v -> vm.update { it.copy(teamSize = v, errors = it.errors - Field.TeamSize) } },
            range = 1..teamMax.coerceAtLeast(state.teamSize),
            supporting = when {
                Field.TeamSize in state.errors && eventMax != null ->
                    pluralStringResource(R.plurals.editor_team_size_over, eventMax, eventMax, state.teamSize + state.openPlaces)
                else -> pluralStringResource(R.plurals.editor_team_summary, state.openPlaces, state.teamSize + state.openPlaces, state.openPlaces)
            },
            isError = Field.TeamSize in state.errors,
        )
    }

    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.editor_commitment_section))
    Text(stringResource(R.string.field_hours_per_week), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Catalog.hoursPerWeek.forEach { hours ->
            ChoiceChip(
                selected = state.hoursPerWeek == hours,
                onClick = { vm.update { it.copy(hoursPerWeek = hours) } },
                label = { Text(stringResource(R.string.hours_value, hours)) },
                shape = CircleShape,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.editor_duration), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (if (isTeam) Catalog.teamDurations else Catalog.projectDurations).forEach { duration ->
            ChoiceChip(
                selected = state.duration == duration,
                onClick = { vm.update { it.copy(duration = if (it.duration == duration) "" else duration) } },
                label = { Text(duration) },
                shape = CircleShape,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    DateField(
        label = stringResource(R.string.editor_deadline),
        date = state.deadline,
        onDate = { d -> vm.update { it.copy(deadline = d, errors = it.errors - Field.Deadline) } },
        placeholder = stringResource(R.string.editor_deadline_none),
        supporting = stringResource(if (Field.Deadline in state.errors) R.string.editor_deadline_error else R.string.editor_deadline_hint),
        isError = Field.Deadline in state.errors,
        clearable = true,
        earliest = Dates.today(),
    )

    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = state.description,
        onValueChange = { v -> if (v.length <= 2000) vm.update { it.copy(description = v) } },
        label = { Text(stringResource(R.string.editor_about)) },
        placeholder = { Text(stringResource(R.string.editor_about_placeholder)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showPicker) {
        EventPickerSheet(
            events = state.events,
            onPick = { event -> vm.chooseEvent(event); showPicker = false },
            onShareNew = { showPicker = false; onShareEventInstead() },
            onDismiss = { showPicker = false },
        )
    }
    val roleToEdit = state.roles.firstOrNull { it.id == editingRole }
    if (roleToEdit != null) {
        RoleSheet(
            initial = roleToEdit,
            isNew = false,
            maxOpenings = 10,
            onSave = { vm.saveRole(it); editingRole = null },
            onRemove = { vm.removeRole(roleToEdit.id); editingRole = null },
            onDismiss = { editingRole = null },
        )
    }
    if (newRole) {
        RoleSheet(
            initial = Role(id = vm.newRoleId(), title = ""),
            isNew = true,
            maxOpenings = 10,
            onSave = { vm.saveRole(it); newRole = false },
            onRemove = null,
            onDismiss = { newRole = false },
        )
    }
}

@Composable
private fun EventChooser(state: EditorUiState, onChoose: () -> Unit) {
    val event = state.event
    val details = event?.event
    val context = LocalContext.current
    val isError = Field.Event in state.errors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (event != null) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isError) 2.dp else 1.dp, if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChoose),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (event == null || details == null) {
                    Text(stringResource(R.string.editor_choose_event), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(if (isError) R.string.editor_event_error else R.string.editor_choose_event_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(event.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${formatEventRange(context, details.startDate, details.endDate)}\n${teamSizeText(details.teamSizeMin, details.teamSizeMax)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (event != null) {
                Text(stringResource(R.string.action_change), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun RoleSummary(role: Role, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(role.title, style = MaterialTheme.typography.titleSmall)
                if (role.skills.isNotEmpty()) {
                    Text(
                        role.skills.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Text(
                pluralStringResource(R.plurals.places_count, role.openings, role.openings),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventForm(state: EditorUiState, vm: EditorViewModel) {
    Spacer(Modifier.height(8.dp))
    ChoiceRow(
        options = EventType.entries,
        selected = state.eventType,
        onSelect = { t -> vm.update { it.copy(eventType = t) } },
        label = { eventTypeLabel(it) },
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.title,
        onValueChange = { v -> if (v.length <= 90) vm.update { it.copy(title = v, errors = it.errors - Field.Title) } },
        label = { Text(stringResource(R.string.editor_event_name)) },
        isError = Field.Title in state.errors,
        supportingText = if (Field.Title in state.errors) ({ Text(stringResource(R.string.editor_title_error)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.organizer,
        onValueChange = { v -> if (v.length <= 80) vm.update { it.copy(organizer = v, errors = it.errors - Field.Organizer) } },
        label = { Text(stringResource(R.string.editor_organizer)) },
        isError = Field.Organizer in state.errors,
        supportingText = { Text(stringResource(if (Field.Organizer in state.errors) R.string.editor_organizer_error else R.string.editor_organizer_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.sourceUrl,
        onValueChange = { v -> if (v.length <= 300) vm.update { it.copy(sourceUrl = v, errors = it.errors - Field.SourceUrl) } },
        label = { Text(stringResource(R.string.editor_source)) },
        placeholder = { Text("https://") },
        isError = Field.SourceUrl in state.errors,
        supportingText = { Text(stringResource(if (Field.SourceUrl in state.errors) R.string.editor_source_error else R.string.editor_source_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(20.dp))
    SectionTitle(stringResource(R.string.editor_dates_section))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateField(
            label = stringResource(R.string.editor_starts),
            date = state.startDate,
            onDate = { d -> vm.update { it.copy(startDate = d, errors = it.errors - Field.StartDate) } },
            isError = Field.StartDate in state.errors,
            modifier = Modifier.weight(1f),
        )
        DateField(
            label = stringResource(R.string.editor_ends),
            date = state.endDate,
            onDate = { d -> vm.update { it.copy(endDate = d, errors = it.errors - Field.EndDate) } },
            isError = Field.EndDate in state.errors,
            earliest = state.startDate,
            modifier = Modifier.weight(1f),
        )
    }
    if (Field.StartDate in state.errors || Field.EndDate in state.errors) {
        Text(
            stringResource(R.string.editor_dates_error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp),
        )
    }
    Spacer(Modifier.height(12.dp))
    DateField(
        label = stringResource(R.string.editor_registration_deadline),
        date = state.registrationDeadline,
        onDate = { d -> vm.update { it.copy(registrationDeadline = d, errors = it.errors - Field.RegistrationDeadline) } },
        placeholder = stringResource(R.string.editor_deadline_none),
        supporting = if (Field.RegistrationDeadline in state.errors) stringResource(R.string.editor_registration_error) else null,
        isError = Field.RegistrationDeadline in state.errors,
        clearable = true,
    )

    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.editor_rules_section))
    Stepper(
        label = stringResource(R.string.editor_team_min),
        value = state.teamSizeMin,
        onValueChange = { v -> vm.update { it.copy(teamSizeMin = v, teamSizeMax = maxOf(it.teamSizeMax, v), errors = it.errors - Field.TeamSizeRange) } },
        range = 1..MaxTeamSize,
    )
    Spacer(Modifier.height(8.dp))
    Stepper(
        label = stringResource(R.string.editor_team_max),
        value = state.teamSizeMax,
        onValueChange = { v -> vm.update { it.copy(teamSizeMax = v, teamSizeMin = minOf(it.teamSizeMin, v), errors = it.errors - Field.TeamSizeRange) } },
        range = 1..MaxTeamSize,
        supporting = teamSizeText(state.teamSizeMin, state.teamSizeMax),
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.eligibility,
        onValueChange = { v -> if (v.length <= 500) vm.update { it.copy(eligibility = v, errors = it.errors - Field.Eligibility) } },
        label = { Text(stringResource(R.string.section_eligibility)) },
        placeholder = { Text(stringResource(R.string.editor_eligibility_placeholder)) },
        isError = Field.Eligibility in state.errors,
        supportingText = if (Field.Eligibility in state.errors) ({ Text(stringResource(R.string.editor_eligibility_error)) }) else null,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(20.dp))
    SectionTitle(stringResource(R.string.editor_where_section))
    ChoiceRow(
        options = EventFormat.entries,
        selected = state.format,
        onSelect = { f -> vm.update { it.copy(format = f) } },
        label = {
            stringResource(
                when (it) {
                    EventFormat.InPerson -> R.string.format_in_person
                    EventFormat.Online -> R.string.format_online
                    EventFormat.Hybrid -> R.string.format_hybrid
                },
            )
        },
    )
    if (state.format != EventFormat.Online) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.location,
            onValueChange = { v -> if (v.length <= 80) vm.update { it.copy(location = v) } },
            label = { Text(stringResource(R.string.editor_location)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = state.description,
        onValueChange = { v -> if (v.length <= 2000) vm.update { it.copy(description = v) } },
        label = { Text(stringResource(R.string.editor_about)) },
        placeholder = { Text(stringResource(R.string.editor_event_about_placeholder)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}
