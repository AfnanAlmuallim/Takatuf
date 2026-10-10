package com.z.takatuf.ui.discover

import com.z.takatuf.ui.common.ChoiceChip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.kindColor
import androidx.compose.foundation.selection.toggleable

private val hourLimits = listOf(4, 8, 12)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    state: DiscoverUiState,
    onFilters: (DiscoverFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    val filters = state.filters
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.discover_filters),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (filters.activeCount > 0) {
                    TextButton(onClick = { onFilters(DiscoverFilters()) }) { Text(stringResource(R.string.action_clear)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.filter_skills_title))
            Text(
                stringResource(R.string.filter_skills_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            val skillOptions = (filters.skills.toList() + state.wantedSkills).distinctBy { it.lowercase() }
            if (skillOptions.isEmpty()) {
                Text(
                    stringResource(R.string.filter_skills_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                skillOptions.forEach { skill ->
                    val on = filters.skills.any { it.equals(skill, ignoreCase = true) }
                    ChoiceChip(
                        selected = on,
                        onClick = {
                            onFilters(
                                filters.copy(
                                    skills = if (on) filters.skills.filterNot { it.equals(skill, ignoreCase = true) }.toSet()
                                    else filters.skills + skill,
                                ),
                            )
                        },
                        label = { Text(skill) },
                        shape = CircleShape,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionTitle(stringResource(R.string.filter_time_title))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    selected = filters.maxHours == null,
                    onClick = { onFilters(filters.copy(maxHours = null)) },
                    label = { Text(stringResource(R.string.filter_time_any)) },
                    shape = CircleShape,
                )
                hourLimits.forEach { limit ->
                    ChoiceChip(
                        selected = filters.maxHours == limit,
                        onClick = { onFilters(filters.copy(maxHours = limit)) },
                        label = { Text(stringResource(R.string.filter_time_up_to, limit)) },
                        shape = CircleShape,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = filters.includeClosed,
                        onValueChange = { onFilters(filters.copy(includeClosed = it)) },
                        role = Role.Switch,
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.filter_include_closed), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.filter_include_closed_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = filters.includeClosed, onCheckedChange = null)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = pluralStringResource(R.plurals.show_results, state.items.size, state.items.size),
                onClick = onDismiss,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostSheet(onPick: (ListingKind) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.post_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            PostOption(
                icon = Icons.Rounded.Lightbulb,
                kind = ListingKind.Project,
                title = stringResource(R.string.post_project_title),
                body = stringResource(R.string.post_project_body),
                onClick = { onPick(ListingKind.Project) },
            )
            PostOption(
                icon = Icons.Rounded.Groups,
                kind = ListingKind.Team,
                title = stringResource(R.string.post_team_title),
                body = stringResource(R.string.post_team_body),
                onClick = { onPick(ListingKind.Team) },
            )
            PostOption(
                icon = Icons.Rounded.EmojiEvents,
                kind = ListingKind.Event,
                title = stringResource(R.string.post_event_title),
                body = stringResource(R.string.post_event_body),
                onClick = { onPick(ListingKind.Event) },
            )
        }
    }
}

@Composable
private fun PostOption(icon: ImageVector, kind: ListingKind, title: String, body: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = kindColor(kind))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
