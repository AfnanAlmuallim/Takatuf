package com.z.takatuf.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.R
import com.z.takatuf.data.model.Listing
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.AnswerCard
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.Fact
import com.z.takatuf.ui.common.KindLabel
import com.z.takatuf.ui.common.LoadingRows
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.SeatRing
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.seatsDescription

/** Every team the student leads or joined, each a tap away from its workspace. */
@Composable
fun TeamsScreen(
    onOpenTeam: (String) -> Unit,
    onDiscover: () -> Unit,
) {
    val viewModel = appViewModel { c, _ -> TeamsViewModel(c.session, c.listings, c.teams) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var confirmDecline by remember { mutableStateOf<Listing?>(null) }

    Scaffold(contentWindowInsets = WindowInsets.statusBars, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = stringResource(R.string.tab_teams),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
            when {
                state.loading -> LoadingRows()
                state.error != null -> ErrorState(state.error!!, viewModel::retry)
                state.teams.isEmpty() -> EmptyState(
                    title = stringResource(R.string.teams_empty_title),
                    body = stringResource(R.string.teams_empty_body),
                    actionLabel = stringResource(R.string.activity_find_opportunities),
                    onAction = onDiscover,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (state.leadRequests.isNotEmpty()) {
                        items(state.leadRequests, key = { "lead-${it.id}" }) { listing ->
                            AnswerCard(
                                prompt = stringResource(R.string.lead_request_prompt, listing.ownerName, listing.title),
                                busy = state.busyListingId == listing.id,
                                primary = stringResource(R.string.action_become_lead),
                                onPrimary = { viewModel.acceptLead(listing) },
                                secondary = stringResource(R.string.action_decline),
                                onSecondary = { confirmDecline = listing },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                    val leading = state.teams.filter { it.leads }
                    val joined = state.teams.filterNot { it.leads }
                    if (joined.isNotEmpty()) {
                        item(key = "joined") { SectionTitle(stringResource(R.string.teams_joined), Modifier.padding(horizontal = 20.dp)) }
                        itemsIndexed(joined, key = { _, it -> it.listing.id }) { index, team ->
                            TeamRow(team, onClick = { onOpenTeam(team.listing.id) })
                            if (index < joined.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if (leading.isNotEmpty()) {
                        item(key = "leading") {
                            if (joined.isNotEmpty()) Spacer(Modifier.height(12.dp))
                            SectionTitle(stringResource(R.string.teams_leading), Modifier.padding(horizontal = 20.dp))
                        }
                        itemsIndexed(leading, key = { _, it -> it.listing.id }) { index, team ->
                            TeamRow(team, onClick = { onOpenTeam(team.listing.id) })
                            if (index < leading.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    confirmDecline?.let { listing ->
        AlertDialog(
            onDismissRequest = { confirmDecline = null },
            title = { Text(stringResource(R.string.decline_lead_title)) },
            text = { Text(stringResource(R.string.decline_lead_body, listing.ownerName)) },
            confirmButton = {
                TextButton(onClick = { confirmDecline = null; viewModel.declineLead(listing) }) {
                    Text(stringResource(R.string.action_decline))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDecline = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamRow(team: TeamSummary, onClick: () -> Unit) {
    val listing = team.listing
    val recruitment = listing.recruitment
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (recruitment != null) {
            SeatRing(
                taken = recruitment.teamSize,
                open = recruitment.openPlaces,
                description = seatsDescription(recruitment.teamSize, recruitment.openPlaces),
                size = 48.dp,
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            KindLabel(listing)
            Spacer(Modifier.height(2.dp))
            Text(listing.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    team.leads -> Fact(Icons.Rounded.MilitaryTech, stringResource(R.string.team_you_lead), color = MaterialTheme.colorScheme.primary)
                    team.roleTitle.isNotBlank() -> Fact(Icons.Rounded.HowToReg, team.roleTitle, color = MaterialTheme.colorScheme.onSurface)
                }
                val people = listing.headcount
                Fact(Icons.Rounded.Groups, pluralStringResource(R.plurals.team_people, people, people))
                if (team.leads && listing.pendingLeader != null) {
                    Tag(stringResource(R.string.lead_handover_pending), tone = TagTone.Quiet)
                }
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
