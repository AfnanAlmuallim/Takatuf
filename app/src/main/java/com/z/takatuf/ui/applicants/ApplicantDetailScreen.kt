package com.z.takatuf.ui.applicants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.model.Profile
import com.z.takatuf.domain.ApplicantFit
import com.z.takatuf.domain.Eligibility
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.ApplicationStatusTag
import com.z.takatuf.ui.common.BottomActionBar
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.Fact
import com.z.takatuf.ui.common.FitBadge
import com.z.takatuf.ui.common.FitFacts
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SecondaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.SkillChips
import com.z.takatuf.ui.common.formatDay
import com.z.takatuf.ui.common.formatDays
import com.z.takatuf.ui.common.relativeTime
import com.z.takatuf.ui.navigation.ApplicantRoute
import com.z.takatuf.ui.passport.HiddenPassport
import com.z.takatuf.ui.passport.IdentityHeader
import com.z.takatuf.ui.passport.PassportSection
import com.z.takatuf.ui.passport.PassportSections
import com.z.takatuf.ui.team.OfferSheet
import com.z.takatuf.domain.Dates
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicantDetailScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        val route = handle.toRoute<ApplicantRoute>()
        ApplicantDetailViewModel(route.listingId, route.applicantId, c.session, c.listings, c.applications, c.profiles)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    val application = state.application
    var showOffer by rememberSaveable { mutableStateOf(false) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }

    // The sheet closes once the offer is out.
    LaunchedEffect(state.done) { if (state.done != 0L) showOffer = false }

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
                    DecisionBar(
                        application = application,
                        listing = state.listing,
                        busy = state.busy,
                        onShortlist = viewModel::setShortlisted,
                        onOffer = { showOffer = true },
                        onCancelOffer = { confirmCancel = true },
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.error != null || application == null -> Column(Modifier.padding(padding)) {
                ErrorState(state.error ?: AppError.NotFound, onRetry = onBack)
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
                    ApplicationStatusTag(application, forOwner = true)
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
                        }
                        state.fit?.let { fit ->
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(14.dp))
                            FitBreakdown(fit, application.applicantName, state.listing)
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.applicant_pitch))
                Text(application.contribution, style = MaterialTheme.typography.bodyLarge)

                Spacer(Modifier.height(20.dp))
                SectionTitle(stringResource(R.string.passport_availability))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        hide = setOf(PassportSection.Availability),
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

    if (showOffer && application != null) {
        OfferSheet(
            names = listOf(application.applicantName),
            sending = state.busy,
            onSend = viewModel::sendOffer,
            onDismiss = { showOffer = false },
        )
    }
    if (confirmCancel && application != null) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.cancel_offer_title)) },
            text = { Text(stringResource(R.string.cancel_offer_body, application.applicantName)) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; viewModel.cancelOffer() }) {
                    Text(stringResource(R.string.action_cancel_offer))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.action_keep_offer)) }
            },
        )
    }
}

/**
 * Why this applicant is ranked where they are: the fit label, the facts behind it, and what they
 * would add that the team doesn't have yet (US09, NFR10).
 */
@Composable
private fun FitBreakdown(fit: ApplicantFit, name: String, listing: Listing?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FitBadge(fit)
        FitFacts(fit)
        if (fit.bringsToTeam.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.AddCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.fit_brings, fit.bringsToTeam.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (fit.sharedInterests.isNotEmpty()) {
            Fact(Icons.Rounded.Interests, stringResource(R.string.fit_interests, fit.sharedInterests.joinToString(", ")))
        }
        when {
            fit.eligibility == Eligibility.Ineligible -> Text(
                stringResource(R.string.fit_ineligible_body, name, eligibleLabel(listing)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            fit.limitedInfo -> Text(
                stringResource(R.string.fit_limited_body, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun eligibleLabel(listing: Listing?): String = when (listing?.eligibleLevel) {
    com.z.takatuf.data.model.EligibleLevel.Undergraduate -> stringResource(R.string.eligible_undergraduate)
    com.z.takatuf.data.model.EligibleLevel.Graduate -> stringResource(R.string.eligible_graduate)
    else -> stringResource(R.string.eligible_any)
}

/** The one decision the owner can make next, given where the application stands. */
@Composable
private fun ColumnScope.DecisionBar(
    application: Application,
    listing: Listing?,
    busy: Boolean,
    onShortlist: (Boolean) -> Unit,
    onOffer: () -> Unit,
    onCancelOffer: () -> Unit,
) {
    val now = Instant.now()
    val role = listing?.recruitment?.roles?.firstOrNull { it.id == application.roleId }
    val hasPlace = (listing?.recruitment?.openPlaces ?: 0) > 0 && (role?.openings ?: 0) > 0
    when (application.status) {
        ApplicationStatus.Submitted, ApplicationStatus.Shortlisted -> {
            val shortlisted = application.status == ApplicationStatus.Shortlisted
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = stringResource(if (shortlisted) R.string.action_shortlisted else R.string.action_shortlist),
                    onClick = { onShortlist(!shortlisted) },
                    enabled = !busy,
                    icon = if (shortlisted) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = stringResource(R.string.action_send_offer),
                    onClick = onOffer,
                    enabled = hasPlace,
                    loading = busy,
                    icon = Icons.AutoMirrored.Rounded.Send,
                    modifier = Modifier.weight(1f),
                )
            }
            BarNote(
                if (hasPlace) stringResource(R.string.offer_note) else stringResource(R.string.offer_no_place, application.roleTitle),
            )
        }
        ApplicationStatus.Offered -> if (application.offerExpired(now)) {
            PrimaryButton(
                text = stringResource(R.string.action_renew_offer),
                onClick = onOffer,
                enabled = hasPlace,
                loading = busy,
                icon = Icons.AutoMirrored.Rounded.Send,
            )
            BarNote(
                stringResource(
                    R.string.offer_expired_owner,
                    application.applicantName,
                    formatDay(Dates.lastDay(application.offerExpiresAt ?: now)),
                ),
            )
        } else {
            BarNote(
                stringResource(
                    R.string.offer_waiting_owner,
                    application.applicantName,
                    formatDay(Dates.lastDay(application.offerExpiresAt ?: now)),
                ),
            )
            SecondaryButton(
                text = stringResource(R.string.action_cancel_offer),
                onClick = onCancelOffer,
                enabled = !busy,
            )
        }
        ApplicationStatus.Reconfirm -> BarNote(stringResource(R.string.reconfirm_waiting_owner, application.applicantName))
        ApplicationStatus.Accepted -> BarNote(stringResource(R.string.applicant_joined, relativeTime(application.respondedAt ?: application.updatedAt)))
        ApplicationStatus.Declined -> BarNote(stringResource(R.string.applicant_declined, relativeTime(application.respondedAt ?: application.updatedAt)))
        ApplicationStatus.Withdrawn -> BarNote(stringResource(R.string.applicant_withdrew, relativeTime(application.updatedAt)))
        ApplicationStatus.Left -> BarNote(stringResource(R.string.applicant_left, relativeTime(application.updatedAt)))
        ApplicationStatus.Removed -> BarNote(stringResource(R.string.applicant_removed))
    }
}

@Composable
private fun BarNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
