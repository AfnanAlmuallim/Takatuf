package com.z.takatuf.ui.applicants

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.z.takatuf.R
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.model.Profile
import com.z.takatuf.domain.SkillMatcher
import com.z.takatuf.ui.activity.ApplicationStatusTag
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.BottomActionBar
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.Fact
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SecondaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.SkillChips
import com.z.takatuf.ui.common.formatDays
import com.z.takatuf.ui.common.relativeTime
import com.z.takatuf.ui.navigation.ApplicantRoute
import com.z.takatuf.ui.passport.HiddenPassport
import com.z.takatuf.ui.passport.IdentityHeader
import com.z.takatuf.ui.passport.PassportSections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicantDetailScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        val route = handle.toRoute<ApplicantRoute>()
        ApplicantDetailViewModel(route.listingId, route.applicantId, c.listings, c.applications, c.profiles)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    val application = state.application

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.applicant_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (application != null) {
                BottomActionBar {
                    when (application.status) {
                        ApplicationStatus.Withdrawn -> Text(
                            stringResource(R.string.applicant_withdrew, relativeTime(application.updatedAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ApplicationStatus.Shortlisted -> {
                            SecondaryButton(
                                text = stringResource(R.string.action_remove_from_shortlist),
                                onClick = { viewModel.setShortlisted(false) },
                                enabled = !state.busy,
                                icon = Icons.Rounded.Star,
                            )
                            Text(
                                stringResource(R.string.shortlist_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ApplicationStatus.Submitted -> {
                            PrimaryButton(
                                text = stringResource(R.string.action_shortlist),
                                onClick = { viewModel.setShortlisted(true) },
                                loading = state.busy,
                                icon = Icons.Rounded.StarOutline,
                            )
                            Text(
                                stringResource(R.string.shortlist_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.error != null || application == null -> Column(Modifier.padding(padding)) {
                ErrorState(state.error ?: com.z.takatuf.data.AppError.NotFound, onRetry = onBack)
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                val profile = state.profile ?: Profile(
                    uid = application.applicantId,
                    displayName = application.applicantName,
                    major = application.applicantMajor,
                )
                Spacer(Modifier.height(8.dp))
                IdentityHeader(profile)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ApplicationStatusTag(application.status)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.applied_when, relativeTime(application.createdAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val role = state.listing?.recruitment?.roles?.firstOrNull { it.id == application.roleId }
                val passport = (state.passport as? PassportAccess.Visible)?.passport
                val applicantSkills = passport?.skills.orEmpty().map { it.name }
                Spacer(Modifier.height(24.dp))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.applicant_applied_for), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(application.roleTitle, style = MaterialTheme.typography.titleMedium)
                        if (role != null && role.skills.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            SkillChips(role.skills, applicantSkills)
                            if (passport != null) {
                                val covered = SkillMatcher.covered(applicantSkills, role.skills).size
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        pluralStringResource(R.plurals.applicant_covers, role.skills.size, covered, role.skills.size),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.applicant_pitch))
                Text(application.contribution, style = MaterialTheme.typography.bodyLarge)

                Spacer(Modifier.height(20.dp))
                SectionTitle(stringResource(R.string.passport_availability))
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                    Fact(Icons.Rounded.Schedule, pluralStringResource(R.plurals.hours_per_week_long, application.hoursPerWeek, application.hoursPerWeek), color = MaterialTheme.colorScheme.onSurface)
                    passport?.availability?.days?.takeIf { it.isNotEmpty() }?.let {
                        Fact(Icons.Rounded.CalendarMonth, formatDays(it), color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (application.availabilityNote.isNotBlank()) {
                        Fact(Icons.AutoMirrored.Rounded.Notes, application.availabilityNote, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.passport_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                when (val access = state.passport) {
                    is PassportAccess.Visible -> PassportSections(
                        access.passport,
                        highlight = role?.skills.orEmpty(),
                        hide = setOf(com.z.takatuf.ui.passport.PassportSection.Availability),
                    )
                    PassportAccess.Hidden -> HiddenPassport(profile.displayName, state.profile?.visibility)
                    PassportAccess.Missing -> Text(
                        stringResource(R.string.passport_missing, profile.displayName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    null -> Unit
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
