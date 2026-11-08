package com.z.takatuf.ui.team

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute
import com.z.takatuf.R
import com.z.takatuf.data.model.Listing
import com.z.takatuf.domain.ApplicantFit
import com.z.takatuf.domain.KeptReason
import com.z.takatuf.domain.Recommendation
import com.z.takatuf.domain.SkillMatcher
import com.z.takatuf.domain.Strategy
import com.z.takatuf.domain.TeamOption
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.BottomActionBar
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.FitBadge
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.Monogram
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SeatRing
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.SkillChips
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.seatsDescription
import com.z.takatuf.ui.navigation.TeamBuilderRoute
import com.z.takatuf.ui.theme.TakatufTheme
import java.time.Instant

@Composable
fun TeamBuilderScreen(
    onBack: () -> Unit,
    onOpenApplicant: (listingId: String, applicantId: String) -> Unit,
) {
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        TeamBuilderViewModel(handle.toRoute<TeamBuilderRoute>().listingId, c.session, c.listings, c.applications, c.profiles)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var showOffer by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.sentAt) { if (state.sentAt != 0L) showOffer = false }

    val listing = state.listing
    val recommendation = state.recommendation
    val option = state.option
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (option != null) {
                BottomActionBar {
                    val toOffer = state.toOffer
                    if (toOffer.isNotEmpty()) {
                        PrimaryButton(
                            text = pluralStringResource(R.plurals.action_send_offers, toOffer.size, toOffer.size),
                            onClick = { showOffer = true },
                            loading = state.sending,
                            icon = Icons.AutoMirrored.Rounded.Send,
                        )
                    }
                    Text(
                        when {
                            toOffer.isEmpty() -> stringResource(R.string.builder_all_offered)
                            state.otherOffers > 0 -> pluralStringResource(R.plurals.builder_other_offers, state.otherOffers, state.otherOffers)
                            else -> stringResource(R.string.builder_offer_note)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> FullScreenLoading()
            state.error != null -> Column(Modifier.statusBarsPadding()) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                ErrorState(state.error!!, viewModel::retry)
            }
            listing == null || recommendation == null -> Column(Modifier.statusBarsPadding()) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                EmptyState(
                    title = stringResource(R.string.listing_missing_title),
                    body = stringResource(R.string.listing_missing_body),
                    actionLabel = stringResource(R.string.action_go_back),
                    onAction = onBack,
                    openSeats = 0,
                )
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                BuilderHero(listing, option, onBack)
                if (option == null) {
                    NoTeamExplanation(listing, recommendation, onBack)
                } else {
                    Spacer(Modifier.height(16.dp))
                    OptionPicker(recommendation.options, state.selected, viewModel::select)
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(20.dp))
                        SkillsSection(option, recommendation)
                        Spacer(Modifier.height(20.dp))
                        PicksSection(
                            listing = listing,
                            option = option,
                            kept = state.kept,
                            onToggleKeep = viewModel::toggleKeep,
                            onOpen = { onOpenApplicant(listing.id, it) },
                        )
                        Gaps(listing, option, recommendation)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = viewModel::showOtherOptions) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_other_options))
                            }
                            if (state.exploring) {
                                TextButton(onClick = viewModel::resetOptions) {
                                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_best_options))
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showOffer && option != null) {
        OfferSheet(
            names = state.toOffer.map { it.application.applicantName },
            sending = state.sending,
            onSend = viewModel::sendOffers,
            onDismiss = { showOffer = false },
        )
    }
}

/** The team as it would be with this option: members already in, picks, and what stays open. */
@Composable
private fun BuilderHero(listing: Listing, option: TeamOption?, onBack: () -> Unit) {
    val colors = TakatufTheme.colors
    val recruitment = listing.recruitment
    TeamHero(label = listing.title, title = stringResource(R.string.builder_title), onBack = onBack) {
        if (recruitment == null) return@TeamHero
        val picks = option?.picks?.size ?: 0
        val total = recruitment.teamSize + recruitment.openPlaces
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeatRing(
                taken = recruitment.teamSize,
                open = recruitment.openPlaces,
                mine = picks,
                description = seatsDescription(recruitment.teamSize, recruitment.openPlaces),
                size = 88.dp,
                takenColor = colors.seatTakenOnHero,
                openColor = colors.seatOpenOnHero,
                mineColor = colors.seatOpenOnHero,
                trackColor = colors.onHero.copy(alpha = 0.2f),
                animate = true,
            )
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    stringResource(R.string.builder_hero_count, recruitment.teamSize + picks, total),
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.onHero,
                )
                Text(
                    pluralStringResource(R.plurals.builder_hero_body, picks, picks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHeroVariant,
                )
                if (option != null && option.needed.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(R.plurals.builder_hero_skills, option.needed.size, option.covered.size, option.needed.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.seatOpenOnHero,
                    )
                }
            }
        }
    }
}

/**
 * The alternatives side by side, each with its coverage and gaps, so they can be compared before
 * choosing one (US11).
 */
@Composable
private fun OptionPicker(options: List<TeamOption>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEachIndexed { index, option ->
            val others = options.filterIndexed { i, _ -> i != index }.flatMap { it.covered }.mapTo(HashSet(), SkillMatcher::normalize)
            val onlyHere = option.covered.filter { SkillMatcher.normalize(it) !in others }
            val isSelected = index == selected
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .width(176.dp)
                    .selectable(selected = isSelected, onClick = { onSelect(index) }, role = Role.Tab),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(strategyLabel(option.strategy)),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    if (option.needed.isNotEmpty()) {
                        Text(
                            pluralStringResource(R.plurals.option_skills, option.needed.size, option.covered.size, option.needed.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        if (option.isComplete) stringResource(R.string.option_fills_all)
                        else pluralStringResource(R.plurals.option_empty_places, option.emptyPlaces, option.emptyPlaces),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (onlyHere.isNotEmpty() && options.size > 1) {
                        Text(
                            stringResource(R.string.option_only_here, onlyHere.joinToString(", ")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun strategyLabel(strategy: Strategy): Int = when (strategy) {
    Strategy.Coverage -> R.string.strategy_coverage
    Strategy.Strongest -> R.string.strategy_strongest
    Strategy.Alternative -> R.string.strategy_alternative
}

@Composable
private fun SkillsSection(option: TeamOption, recommendation: Recommendation) {
    if (option.needed.isEmpty()) return
    SectionTitle(stringResource(R.string.builder_skills))
    SkillChips(option.needed, option.covered)
    Spacer(Modifier.height(8.dp))
    Text(
        if (option.missing.isEmpty()) stringResource(R.string.builder_skills_all)
        else stringResource(R.string.builder_skills_missing, option.missing.joinToString(", ")),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (recommendation.coveredByTeam.isNotEmpty()) {
        Text(
            stringResource(R.string.builder_skills_team, recommendation.coveredByTeam.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Picks grouped under the role they'd fill, with a pin to keep someone across options. */
@Composable
private fun PicksSection(
    listing: Listing,
    option: TeamOption,
    kept: Set<String>,
    onToggleKeep: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    SectionTitle(stringResource(R.string.builder_people))
    Text(
        stringResource(R.string.builder_keep_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val roles = listing.recruitment?.roles.orEmpty().filter { it.openings > 0 }
    roles.forEach { role ->
        val picks = option.picks.filter { it.roleId == role.id }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(role.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                pluralStringResource(R.plurals.role_places, role.openings, role.openings),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        picks.forEach { fit ->
            PickRow(fit, kept = fit.applicantId in kept, onToggleKeep = { onToggleKeep(fit.applicantId) }, onOpen = { onOpen(fit.applicantId) })
        }
        val empty = option.unfilled[role.id] ?: 0
        if (empty > 0) {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SeatRing(taken = 0, open = 1, description = "", size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    pluralStringResource(R.plurals.builder_role_empty, empty, empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PickRow(fit: ApplicantFit, kept: Boolean, onToggleKeep: () -> Unit, onOpen: () -> Unit) {
    val application = fit.application
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(application.applicantName, application.applicantId, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(application.applicantName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                FitBadge(fit)
                if (application.hasLiveOffer(Instant.now())) {
                    Tag(stringResource(R.string.status_offer_sent), tone = TagTone.Accent)
                }
            }
            if (fit.bringsToTeam.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.fit_brings, fit.bringsToTeam.joinToString(", ")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconToggleButton(checked = kept, onCheckedChange = { onToggleKeep() }) {
            Icon(
                if (kept) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                contentDescription = stringResource(if (kept) R.string.action_unkeep else R.string.action_keep, application.applicantName),
                tint = if (kept) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Plain reasons for anything this option can't do: roles nobody suitable applied for,
 * applicants the event doesn't admit, and pins that can't be honoured (US10, US11).
 */
@Composable
private fun Gaps(listing: Listing, option: TeamOption, recommendation: Recommendation) {
    val roles = listing.recruitment?.roles.orEmpty()
    val lines = buildList {
        recommendation.emptyRoles.forEach { id ->
            roles.firstOrNull { it.id == id }?.let { add(GapLine.EmptyRole(it.title)) }
        }
        option.unfilled.forEach { (id, count) ->
            if (id in recommendation.emptyRoles) return@forEach
            val role = roles.firstOrNull { it.id == id } ?: return@forEach
            val picked = option.picks.count { it.roleId == id }
            // Someone suitable exists but this mix left them out: say so rather than "nobody".
            if ((recommendation.applicantsPerRole[id] ?: 0) > picked) add(GapLine.OtherMix(role.title, count))
            else add(GapLine.Short(role.title, count))
        }
        if (recommendation.ineligible.isNotEmpty()) add(GapLine.Ineligible(recommendation.ineligible.size))
        recommendation.keptIssues.forEach { add(GapLine.Kept(it.name, it.roleTitle, it.reason)) }
    }
    if (lines.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (option.isComplete) R.string.builder_gaps_title_notes else R.string.builder_gaps_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            lines.forEach { line ->
                Text(gapText(line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private sealed interface GapLine {
    data class EmptyRole(val role: String) : GapLine
    data class Short(val role: String, val places: Int) : GapLine
    data class OtherMix(val role: String, val places: Int) : GapLine
    data class Ineligible(val count: Int) : GapLine
    data class Kept(val name: String, val role: String, val reason: KeptReason) : GapLine
}

@Composable
private fun gapText(line: GapLine): String = when (line) {
    is GapLine.EmptyRole -> stringResource(R.string.gap_empty_role, line.role)
    is GapLine.Short -> pluralStringResource(R.plurals.gap_short, line.places, line.role, line.places)
    is GapLine.OtherMix -> pluralStringResource(R.plurals.gap_other_mix, line.places, line.role, line.places)
    is GapLine.Ineligible -> pluralStringResource(R.plurals.gap_ineligible, line.count, line.count)
    is GapLine.Kept -> when (line.reason) {
        KeptReason.NotAvailable -> if (line.name.isBlank()) {
            stringResource(R.string.gap_kept_gone)
        } else {
            stringResource(R.string.gap_kept_unavailable, line.name)
        }
        KeptReason.RoleOverbooked -> stringResource(R.string.gap_kept_overbooked, line.name, line.role)
    }
}

/** No combination at all: say why, and point back to where the owner can act. */
@Composable
private fun NoTeamExplanation(listing: Listing, recommendation: Recommendation, onBack: () -> Unit) {
    val roles = listing.recruitment?.roles.orEmpty()
    val body = when {
        (listing.recruitment?.openPlaces ?: 0) == 0 -> stringResource(R.string.builder_none_full)
        recommendation.ineligible.isNotEmpty() ->
            pluralStringResource(R.plurals.builder_none_ineligible, recommendation.ineligible.size, recommendation.ineligible.size)
        recommendation.emptyRoles.isNotEmpty() -> stringResource(
            R.string.builder_none_roles,
            recommendation.emptyRoles.mapNotNull { id -> roles.firstOrNull { it.id == id }?.title }.joinToString(", "),
        )
        else -> stringResource(R.string.builder_none_body)
    }
    EmptyState(
        title = stringResource(R.string.builder_none_title),
        body = body,
        actionLabel = stringResource(R.string.action_back_to_applicants),
        onAction = onBack,
        openSeats = listing.recruitment?.openPlaces?.coerceIn(1, 6) ?: 3,
    )
}
