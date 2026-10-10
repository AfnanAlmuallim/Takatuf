package com.z.takatuf.ui.applicants

import com.z.takatuf.ui.common.ChoiceChip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.ui.activity.ApplicationStatusTag
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.LoadingRows
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.Monogram
import com.z.takatuf.ui.common.NewDot
import com.z.takatuf.ui.common.SkillChips
import com.z.takatuf.ui.common.relativeTime
import com.z.takatuf.ui.navigation.ApplicantsRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicantsScreen(
    onBack: () -> Unit,
    onOpenApplicant: (listingId: String, applicantId: String) -> Unit,
    onOpenListing: (String) -> Unit,
) {
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        ApplicantsViewModel(handle.toRoute<ApplicantsRoute>().listingId, c.listings, c.applications, c.profiles)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    val listing = state.listing

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.applicants_title), style = MaterialTheme.typography.titleLarge)
                        if (listing != null) {
                            Text(
                                listing.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onOpenListing(listing.id) },
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> LoadingRows()
                state.error == AppError.PermissionDenied -> EmptyState(
                    title = stringResource(R.string.applicants_not_owner_title),
                    body = stringResource(R.string.applicants_not_owner_body),
                    actionLabel = stringResource(R.string.action_go_back),
                    onAction = onBack,
                    openSeats = 0,
                )
                state.error != null -> ErrorState(state.error!!, viewModel::retry)
                else -> {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ApplicantFilter.entries, key = { it.name }) { filter ->
                            ChoiceChip(
                                selected = state.filter == filter,
                                onClick = { viewModel.onFilter(filter) },
                                label = { Text(stringResource(filterLabel(filter), state.count(filter))) },
                                shape = CircleShape,
                            )
                        }
                    }
                    val shown = state.shown
                    if (shown.isEmpty()) {
                        EmptyState(
                            title = stringResource(
                                when {
                                    state.applicants.isEmpty() -> R.string.applicants_none_title
                                    else -> R.string.applicants_filter_empty_title
                                },
                            ),
                            body = stringResource(
                                when {
                                    state.applicants.isEmpty() -> R.string.applicants_none_body
                                    else -> R.string.applicants_filter_empty_body
                                },
                            ),
                        )
                    } else {
                        val roleSkills = listing?.recruitment?.roles.orEmpty().associate { it.id to it.skills }
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(shown, key = { _, it -> it.applicantId }) { index, application ->
                                ApplicantRow(
                                    application = application,
                                    passport = state.passports[application.applicantId],
                                    roleSkills = roleSkills[application.roleId].orEmpty(),
                                    onClick = { onOpenApplicant(application.listingId, application.applicantId) },
                                    onShortlist = { viewModel.setShortlisted(application.applicantId, it) },
                                    modifier = Modifier.animateItem(),
                                )
                                if (index < shown.lastIndex) {
                                    HorizontalDivider(Modifier.padding(start = 76.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun filterLabel(filter: ApplicantFilter) = when (filter) {
    ApplicantFilter.All -> R.string.applicants_filter_all
    ApplicantFilter.New -> R.string.applicants_filter_new
    ApplicantFilter.Shortlisted -> R.string.applicants_filter_shortlisted
    ApplicantFilter.Withdrawn -> R.string.applicants_filter_withdrawn
}

/**
 * Everything needed to triage one applicant without opening them: who, which role, their
 * pitch, and which of the role's skills they bring. Shortlisting is one tap.
 */
@Composable
private fun ApplicantRow(
    application: Application,
    passport: PassportAccess?,
    roleSkills: List<String>,
    onClick: () -> Unit,
    onShortlist: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Monogram(application.applicantName, application.applicantId, size = 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    application.applicantName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (application.isNew) {
                    Spacer(Modifier.width(8.dp))
                    NewDot()
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.applicants_new), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                stringResource(R.string.applicant_role_hours, application.roleTitle, application.hoursPerWeek),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                application.contribution,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            when (passport) {
                is PassportAccess.Visible -> {
                    val skills = passport.passport.skills.map { it.name }
                    val shown = (roleSkills.filter { r -> skills.any { com.z.takatuf.domain.SkillMatcher.normalize(it) == com.z.takatuf.domain.SkillMatcher.normalize(r) } } + skills)
                        .distinctBy { com.z.takatuf.domain.SkillMatcher.normalize(it) }
                    if (shown.isNotEmpty()) SkillChips(skills = shown, have = roleSkills, max = 4)
                }
                PassportAccess.Hidden -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp).width(14.dp))
                    Text(stringResource(R.string.applicants_passport_hidden), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Unit
            }
            if (!application.status.isActive || application.status == ApplicationStatus.Shortlisted) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ApplicationStatusTag(application.status)
                    Spacer(Modifier.width(8.dp))
                    Text(relativeTime(application.updatedAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (application.status.isActive) {
            val shortlisted = application.status == ApplicationStatus.Shortlisted
            IconToggleButton(
                checked = shortlisted,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onShortlist(it)
                },
            ) {
                Icon(
                    imageVector = if (shortlisted) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = stringResource(
                        if (shortlisted) R.string.action_remove_from_shortlist_named else R.string.action_shortlist_named,
                        application.applicantName,
                    ),
                    tint = if (shortlisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
