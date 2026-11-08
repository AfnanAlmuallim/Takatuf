package com.z.takatuf.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.RoleSuggestion
import com.z.takatuf.ui.common.SkillChips
import com.z.takatuf.ui.common.Tag

/**
 * Optional help with roles: the AI reads what the owner wrote and proposes roles with skills.
 * Each suggestion is added only when the owner taps Add, so they stay in charge of what the
 * listing asks for (charter, Technical Environment).
 */
@Composable
internal fun RoleSuggestions(
    state: EditorUiState,
    onSuggest: () -> Unit,
    onAccept: (RoleSuggestion) -> Unit,
    onAcceptAll: () -> Unit,
    onDismiss: (RoleSuggestion) -> Unit,
    onDismissAll: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.ai_roles_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(if (state.canSuggest) R.string.ai_roles_body else R.string.ai_roles_need_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                state.suggesting -> Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.ai_roles_reading), style = MaterialTheme.typography.bodyMedium)
                }
                state.suggestions.isNotEmpty() -> Suggestions(state.suggestions, onAccept, onAcceptAll, onDismiss, onDismissAll)
                else -> {
                    state.suggestError?.let { error ->
                        Text(
                            stringResource(error.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (state.suggestedNothing) {
                        Text(
                            stringResource(R.string.ai_roles_nothing_new),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(onClick = onSuggest, enabled = state.canSuggest, shape = CircleShape) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                when {
                                    state.suggestError != null -> R.string.action_try_again
                                    state.roles.isEmpty() -> R.string.action_suggest_roles
                                    else -> R.string.action_suggest_more_roles
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Suggestions(
    suggestions: List<RoleSuggestion>,
    onAccept: (RoleSuggestion) -> Unit,
    onAcceptAll: () -> Unit,
    onDismiss: (RoleSuggestion) -> Unit,
    onDismissAll: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        suggestions.forEach { suggestion ->
            SuggestionCard(suggestion, onAdd = { onAccept(suggestion) }, onDismiss = { onDismiss(suggestion) })
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.ai_roles_disclaimer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismissAll) { Text(stringResource(R.string.action_dismiss)) }
            if (suggestions.size > 1) {
                Button(onClick = onAcceptAll, shape = CircleShape) {
                    Text(pluralStringResource(R.plurals.action_add_all_roles, suggestions.size, suggestions.size))
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: RoleSuggestion, onAdd: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(suggestion.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Tag(pluralStringResource(R.plurals.places_count, suggestion.openings, suggestion.openings))
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_dismiss_role, suggestion.title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (suggestion.description.isNotBlank()) {
                Text(
                    suggestion.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
            if (suggestion.skills.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SkillChips(suggestion.skills, emptyList(), Modifier.padding(end = 10.dp))
            }
            TextButton(onClick = onAdd, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_add_role))
            }
        }
    }
}
