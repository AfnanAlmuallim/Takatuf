package com.z.takatuf.ui.editor

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.Catalog
import com.z.takatuf.data.RoleTemplates
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Role
import com.z.takatuf.domain.Dates
import com.z.takatuf.domain.SkillMatcher
import com.z.takatuf.ui.common.DateTile
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.Stepper
import com.z.takatuf.ui.common.teamSizeText

private const val MaxRoleSkills = 12

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoleSheet(
    initial: Role,
    isNew: Boolean,
    maxOpenings: Int,
    onSave: (Role) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var description by rememberSaveable { mutableStateOf(initial.description) }
    var skills by rememberSaveable { mutableStateOf(initial.skills) }
    var openings by rememberSaveable { mutableIntStateOf(initial.openings.coerceIn(1, maxOpenings.coerceAtLeast(1))) }
    var skillInput by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    fun addSkill(name: String) {
        val clean = name.trim()
        if (clean.isNotEmpty() && skills.size < MaxRoleSkills && skills.none { SkillMatcher.normalize(it) == SkillMatcher.normalize(clean) }) {
            skills = skills + clean
        }
        skillInput = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                stringResource(if (isNew) R.string.role_sheet_add_title else R.string.role_sheet_edit_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (isNew) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.role_templates_label), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    items(RoleTemplates.all, key = { it.title }) { template ->
                        AssistChip(
                            onClick = {
                                title = template.title
                                description = template.description
                                skills = template.skills
                            },
                            label = { Text(template.title) },
                            shape = CircleShape,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 60) title = it },
                label = { Text(stringResource(R.string.role_title_label)) },
                isError = showErrors && title.isBlank(),
                supportingText = if (showErrors && title.isBlank()) ({ Text(stringResource(R.string.role_title_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { if (it.length <= 300) description = it },
                label = { Text(stringResource(R.string.role_description_label)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.role_skills_label), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            if (skills.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    skills.forEach { skill ->
                        InputChip(
                            selected = false,
                            onClick = { skills = skills - skill },
                            label = { Text(skill) },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_remove_named, skill),
                                    modifier = Modifier.size(InputChipDefaults.IconSize),
                                )
                            },
                            shape = CircleShape,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = skillInput,
                onValueChange = { skillInput = it },
                label = { Text(stringResource(R.string.skills_add_label)) },
                singleLine = true,
                trailingIcon = if (skillInput.isNotBlank()) {
                    { IconButton(onClick = { addSkill(skillInput) }) { Icon(Icons.Rounded.Add, stringResource(R.string.skills_add_typed, skillInput.trim())) } }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addSkill(skillInput) }),
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = remember(skillInput, skills) {
                Catalog.skills
                    .filter { s -> skills.none { SkillMatcher.normalize(it) == SkillMatcher.normalize(s) } }
                    .filter { skillInput.isNotBlank() && it.contains(skillInput.trim(), ignoreCase = true) }
                    .take(6)
            }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        SuggestionChip(onClick = { addSkill(suggestion) }, label = { Text(suggestion) }, shape = CircleShape)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Stepper(
                label = stringResource(R.string.role_openings_label),
                value = openings,
                onValueChange = { openings = it },
                range = 1..maxOpenings.coerceAtLeast(1),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(if (isNew) R.string.action_add_role else R.string.action_save_role),
                onClick = {
                    showErrors = true
                    if (title.isNotBlank()) {
                        onSave(initial.copy(title = title.trim(), description = description.trim(), skills = skills, openings = openings))
                    }
                },
            )
            if (onRemove != null) {
                TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_remove_role), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPickerSheet(
    events: List<Listing>,
    onPick: (Listing) -> Unit,
    onShareNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query, events) {
        val q = query.trim()
        events.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.event?.organizer?.contains(q, ignoreCase = true) == true }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.imePadding()) {
            Text(
                stringResource(R.string.event_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.event_picker_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(matches, key = { it.id }) { event ->
                    val details = event.event
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(event) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (details != null) {
                            val start = Dates.startDate(details.startDate)
                            DateTile(start.monthValue, start.dayOfMonth)
                            Spacer(Modifier.width(14.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (details != null) {
                                Text(
                                    "${details.organizer}\n${teamSizeText(details.teamSizeMin, details.teamSizeMax)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 86.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Text(
                            text = stringResource(if (matches.isEmpty()) R.string.event_picker_none else R.string.event_picker_missing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onShareNew, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.post_event_title))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
