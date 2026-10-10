package com.z.takatuf.ui.listing

import com.z.takatuf.ui.common.ChoiceChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.Catalog
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Visibility
import com.z.takatuf.domain.SkillMatcher
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.passport.visibilityIcon

const val ContributionMin = 20
const val ContributionMax = 1000

/**
 * Applying takes one screen: the role is preselected when the student tapped "Apply for this
 * role", and hours come from their passport, so usually only the pitch needs writing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ApplySheet(
    listing: Listing,
    initialRoleId: String?,
    myHours: Int,
    mySkills: List<String>,
    visibility: Visibility,
    sending: Boolean,
    error: AppError?,
    onSend: (roleId: String, contribution: String, hours: Int, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val recruitment = listing.recruitment ?: return
    val openRoles = recruitment.roles.filter { it.openings > 0 }.ifEmpty { recruitment.roles }
    var roleId by rememberSaveable { mutableStateOf(initialRoleId?.takeIf { id -> openRoles.any { it.id == id } } ?: openRoles.first().id) }
    var contribution by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableIntStateOf(if (myHours > 0) myHours else recruitment.hoursPerWeek) }
    var note by rememberSaveable { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    val contributionTooShort = contribution.trim().length < ContributionMin
    val role = openRoles.first { it.id == roleId }

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
                text = stringResource(R.string.apply_title, listing.title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.apply_role), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                openRoles.forEach { option ->
                    ChoiceChip(
                        selected = option.id == roleId,
                        onClick = { roleId = option.id },
                        label = { Text(option.title) },
                        shape = CircleShape,
                    )
                }
            }
            val covered = SkillMatcher.covered(mySkills, role.skills)
            if (role.skills.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (covered.isNotEmpty()) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = if (covered.isNotEmpty()) pluralStringResource(R.plurals.role_you_cover, role.skills.size, covered.size, role.skills.size)
                        else stringResource(R.string.apply_role_asks_for, role.skills.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (covered.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = contribution,
                onValueChange = { if (it.length <= ContributionMax) contribution = it },
                label = { Text(stringResource(R.string.apply_contribution_label)) },
                placeholder = { Text(stringResource(R.string.apply_contribution_placeholder)) },
                isError = showErrors && contributionTooShort,
                supportingText = {
                    Row {
                        Text(
                            text = if (showErrors && contributionTooShort) pluralStringResource(R.plurals.apply_contribution_error, ContributionMin, ContributionMin)
                            else stringResource(R.string.apply_contribution_hint),
                            modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(R.string.char_count, contribution.length, ContributionMax))
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.apply_hours), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (Catalog.hoursPerWeek + hours).distinct().sorted().forEach { value ->
                    ChoiceChip(
                        selected = hours == value,
                        onClick = { hours = value },
                        label = { Text(stringResource(R.string.hours_value, value)) },
                        shape = CircleShape,
                    )
                }
            }
            if (recruitment.hoursPerWeek > 0 && hours < recruitment.hoursPerWeek) {
                Text(
                    text = stringResource(R.string.apply_hours_below, recruitment.hoursPerWeek),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                label = { Text(stringResource(R.string.apply_note_label)) },
                placeholder = { Text(stringResource(R.string.field_availability_note_placeholder)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(visibilityIcon(visibility), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(
                            when (visibility) {
                                Visibility.Everyone -> R.string.apply_privacy_everyone
                                Visibility.Applied -> R.string.apply_privacy_applied
                                Visibility.Private -> R.string.apply_privacy_private
                            },
                            listing.ownerName,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(error.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.action_send_application),
                loading = sending,
                onClick = {
                    showErrors = true
                    if (!contributionTooShort) onSend(roleId, contribution, hours, note)
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = pluralStringResource(R.plurals.apply_places_left, role.openings, role.openings, role.title),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
