package com.z.takatuf.ui.passport

import com.z.takatuf.ui.common.ChoiceChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.Catalog
import com.z.takatuf.data.model.Availability
import com.z.takatuf.data.model.Skill
import com.z.takatuf.data.model.SkillLevel
import com.z.takatuf.data.model.Visibility
import com.z.takatuf.data.model.Weekday
import com.z.takatuf.domain.SkillMatcher
import com.z.takatuf.ui.common.skillLevelLabel
import com.z.takatuf.ui.common.weekdayShort

const val MaxSkills = 30
const val MaxInterests = 20
const val HeadlineMax = 140

/** Year 1–5, or 6 for graduate students. */
@Composable
fun yearLabel(year: Int): String =
    if (year >= 6) stringResource(R.string.year_graduate) else stringResource(R.string.year_n, year)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BasicsEditor(
    name: String,
    onName: (String) -> Unit,
    major: String,
    onMajor: (String) -> Unit,
    year: Int?,
    onYear: (Int?) -> Unit,
    headline: String,
    onHeadline: (String) -> Unit,
    modifier: Modifier = Modifier,
    nameError: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text(stringResource(R.string.field_full_name)) },
            isError = nameError,
            supportingText = if (nameError) ({ Text(stringResource(R.string.auth_error_name)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        var expanded by rememberSaveable { mutableStateOf(false) }
        val suggestions = Catalog.majors.filter { it.contains(major.trim(), ignoreCase = true) && !it.equals(major.trim(), ignoreCase = true) }
        ExposedDropdownMenuBox(expanded = expanded && suggestions.isNotEmpty(), onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = major,
                onValueChange = { onMajor(it); expanded = true },
                label = { Text(stringResource(R.string.field_major)) },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            )
            ExposedDropdownMenu(expanded = expanded && suggestions.isNotEmpty(), onDismissRequest = { expanded = false }) {
                suggestions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onMajor(option); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.field_year), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..6).forEach { y ->
                    ChoiceChip(
                        selected = year == y,
                        onClick = { onYear(if (year == y) null else y) },
                        label = { Text(yearLabel(y)) },
                        shape = CircleShape,
                    )
                }
            }
        }
        OutlinedTextField(
            value = headline,
            onValueChange = { if (it.length <= HeadlineMax) onHeadline(it) },
            label = { Text(stringResource(R.string.field_headline)) },
            placeholder = { Text(stringResource(R.string.field_headline_placeholder)) },
            supportingText = { Text(stringResource(R.string.char_count, headline.length, HeadlineMax)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Add skills by typing or tapping a suggestion, then set how strong each one is. Suggestions
 * narrow as you type, and anything not in the list can still be added as typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillsEditor(
    skills: List<Skill>,
    onChange: (List<Skill>) -> Unit,
    modifier: Modifier = Modifier,
    major: String = "",
) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    fun add(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || skills.size >= MaxSkills) return
        if (skills.none { SkillMatcher.normalize(it.name) == SkillMatcher.normalize(clean) }) {
            onChange(skills + Skill(clean))
        }
        query = ""
    }
    val suggestions = Catalog.skillsFor(major)
        .filter { candidate -> skills.none { SkillMatcher.normalize(it.name) == SkillMatcher.normalize(candidate) } }
        .filter { trimmed.isEmpty() || it.contains(trimmed, ignoreCase = true) }
        .take(if (trimmed.isEmpty()) 12 else 8)
    val canAddTyped = trimmed.isNotEmpty() &&
        Catalog.skills.none { it.equals(trimmed, ignoreCase = true) } &&
        skills.none { it.name.equals(trimmed, ignoreCase = true) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.skills_add_label)) },
            placeholder = { Text(stringResource(R.string.skills_add_placeholder)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (trimmed.isNotEmpty()) {
                { IconButton(onClick = { add(trimmed) }) { Icon(Icons.Rounded.Add, stringResource(R.string.skills_add_typed, trimmed)) } }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(trimmed) }),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (canAddTyped) {
                SuggestionChip(
                    onClick = { add(trimmed) },
                    label = { Text(stringResource(R.string.skills_add_typed, trimmed)) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(SuggestionChipDefaults.IconSize)) },
                    shape = CircleShape,
                )
            }
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { add(suggestion) },
                    label = { Text(suggestion) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(SuggestionChipDefaults.IconSize)) },
                    shape = CircleShape,
                )
            }
        }
        if (skills.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    skills.forEachIndexed { index, skill ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        SkillLevelRow(
                            skill = skill,
                            onLevel = { level -> onChange(skills.map { if (it == skill) it.copy(level = level) else it }) },
                            onRemove = { onChange(skills - skill) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillLevelRow(skill: Skill, onLevel: (SkillLevel) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(skill.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove_named, skill.name))
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(end = 12.dp)) {
            SkillLevel.entries.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = skill.level == level,
                    onClick = { onLevel(level) },
                    shape = SegmentedButtonDefaults.itemShape(index, SkillLevel.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    icon = {},
                    label = { Text(skillLevelLabel(level), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestsEditor(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var custom by rememberSaveable { mutableStateOf("") }
    val options = (Catalog.interests + selected).distinctBy { it.lowercase() }
    fun addCustom() {
        val clean = custom.trim()
        if (clean.isNotEmpty() && selected.size < MaxInterests && selected.none { it.equals(clean, ignoreCase = true) }) {
            onChange(selected + clean)
        }
        custom = ""
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { interest ->
                val isOn = selected.any { it.equals(interest, ignoreCase = true) }
                ChoiceChip(
                    selected = isOn,
                    onClick = {
                        onChange(
                            if (isOn) selected.filterNot { it.equals(interest, ignoreCase = true) }
                            else if (selected.size < MaxInterests) selected + interest else selected,
                        )
                    },
                    label = { Text(interest) },
                    shape = CircleShape,
                )
            }
        }
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text(stringResource(R.string.interests_add_label)) },
            singleLine = true,
            trailingIcon = if (custom.isNotBlank()) {
                { IconButton(onClick = ::addCustom) { Icon(Icons.Rounded.Add, stringResource(R.string.skills_add_typed, custom.trim())) } }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addCustom() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvailabilityEditor(
    availability: Availability,
    onChange: (Availability) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.field_hours_per_week), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Catalog.hoursPerWeek.forEach { hours ->
                    ChoiceChip(
                        selected = availability.hoursPerWeek == hours,
                        onClick = { onChange(availability.copy(hoursPerWeek = if (availability.hoursPerWeek == hours) 0 else hours)) },
                        label = { Text(stringResource(R.string.hours_value, hours)) },
                        shape = CircleShape,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.field_days), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Weekday.entries.forEach { day ->
                    val on = day in availability.days
                    ChoiceChip(
                        selected = on,
                        onClick = { onChange(availability.copy(days = if (on) availability.days - day else availability.days + day)) },
                        label = { Text(weekdayShort(day)) },
                        shape = CircleShape,
                    )
                }
            }
        }
        OutlinedTextField(
            value = availability.note,
            onValueChange = { if (it.length <= 200) onChange(availability.copy(note = it)) },
            label = { Text(stringResource(R.string.field_availability_note)) },
            placeholder = { Text(stringResource(R.string.field_availability_note_placeholder)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun VisibilityPicker(
    selected: Visibility,
    onSelect: (Visibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Visibility.entries.forEach { option ->
            val isSelected = option == selected
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = isSelected, onClick = { onSelect(option) }, role = Role.RadioButton),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(visibilityIcon(option), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(visibilityTitle(option), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(visibilityBody(option), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = isSelected, onClick = null)
                }
            }
        }
    }
}

fun visibilityIcon(visibility: Visibility) = when (visibility) {
    Visibility.Everyone -> Icons.Rounded.Public
    Visibility.Applied -> Icons.Rounded.MarkEmailRead
    Visibility.Private -> Icons.Rounded.Lock
}

@Composable
fun visibilityTitle(visibility: Visibility) = stringResource(
    when (visibility) {
        Visibility.Everyone -> R.string.visibility_everyone
        Visibility.Applied -> R.string.visibility_applied
        Visibility.Private -> R.string.visibility_private
    },
)

@Composable
fun visibilityBody(visibility: Visibility) = stringResource(
    when (visibility) {
        Visibility.Everyone -> R.string.visibility_everyone_body
        Visibility.Applied -> R.string.visibility_applied_body
        Visibility.Private -> R.string.visibility_private_body
    },
)
