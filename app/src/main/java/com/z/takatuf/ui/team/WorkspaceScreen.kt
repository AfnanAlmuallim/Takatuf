package com.z.takatuf.ui.team

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Milestone
import com.z.takatuf.domain.Dates
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.SeatRing
import com.z.takatuf.ui.common.formatDay
import com.z.takatuf.ui.common.kindLabel
import com.z.takatuf.ui.common.seatsDescription
import com.z.takatuf.ui.navigation.WorkspaceRoute
import com.z.takatuf.ui.theme.TakatufTheme

private enum class WorkspaceTab(val label: Int) {
    Plan(R.string.workspace_tab_plan),
    Updates(R.string.workspace_tab_updates),
    People(R.string.workspace_tab_people),
}

/** A team's private space: what's planned, what's been shared, and who does what (US13 to US16). */
@Composable
fun WorkspaceScreen(
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onReviewApplicants: (String) -> Unit,
) {
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        WorkspaceViewModel(handle.toRoute<WorkspaceRoute>().listingId, c.session, c.listings, c.applications, c.teams, c.workspace)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MilestoneEdit?>(null) }
    var composing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.left) { if (state.left) onBack() }
    LaunchedEffect(state.done) {
        if (state.done != 0L) {
            editing = null
            composing = false
        }
    }

    val listing = state.listing
    val current = WorkspaceTab.entries[tab]
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (listing != null && state.onTeam) {
                when (current) {
                    WorkspaceTab.Plan -> ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.action_add_milestone)) },
                        icon = { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add_milestone)) },
                        onClick = { editing = MilestoneEdit(null) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    WorkspaceTab.Updates -> ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.action_post_update)) },
                        icon = { Icon(Icons.Rounded.EditNote, contentDescription = stringResource(R.string.action_post_update)) },
                        onClick = { composing = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    WorkspaceTab.People -> Unit
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
            listing == null -> FullScreenLoading()
            !state.onTeam -> Column(Modifier.statusBarsPadding()) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                EmptyState(
                    title = stringResource(R.string.workspace_no_access_title),
                    body = stringResource(R.string.workspace_no_access_body),
                    actionLabel = stringResource(R.string.action_view_listing),
                    onAction = { onOpenListing(listing.id) },
                    openSeats = 0,
                )
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                WorkspaceHero(listing, state.outstanding, onBack, onViewListing = { onOpenListing(listing.id) })
                PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    WorkspaceTab.entries.forEachIndexed { index, entry ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(stringResource(entry.label), style = MaterialTheme.typography.labelLarge) },
                        )
                    }
                }
                when (current) {
                    WorkspaceTab.Plan -> PlanTab(
                        state = state,
                        onEdit = { editing = MilestoneEdit(it) },
                        onSetStatus = viewModel::setStatus,
                        onAdd = { editing = MilestoneEdit(null) },
                    )
                    WorkspaceTab.Updates -> UpdatesTab(
                        state = state,
                        onDeleteUpdate = viewModel::deleteUpdate,
                        onAddLink = viewModel::addLink,
                        onDeleteLink = viewModel::deleteLink,
                        onCompose = { composing = true },
                    )
                    WorkspaceTab.People -> PeopleTab(
                        state = state,
                        onOpenPerson = onOpenPerson,
                        onReviewApplicants = { onReviewApplicants(listing.id) },
                        onSetResponsibility = viewModel::setResponsibility,
                        onReopen = viewModel::reopen,
                        onNominate = viewModel::nominateLead,
                        onCancelNomination = viewModel::cancelNomination,
                        onAcceptLead = viewModel::acceptLead,
                        onDeclineLead = viewModel::declineLead,
                        onRemove = viewModel::removeMember,
                        onLeave = viewModel::leave,
                    )
                }
            }
        }
    }

    editing?.let { edit ->
        MilestoneSheet(
            milestone = edit.milestone,
            people = state.people,
            myUid = state.myUid,
            canDelete = edit.milestone?.let(state::canDelete) == true,
            saving = state.busy,
            onSave = { title, assignee, due, status -> viewModel.saveMilestone(edit.milestone, title, assignee, due, status) },
            onDelete = { edit.milestone?.let(viewModel::deleteMilestone) },
            onDismiss = { editing = null },
        )
    }
    if (composing) {
        UpdateSheet(
            posting = state.busy,
            onPost = viewModel::postUpdate,
            onDismiss = { composing = false },
        )
    }
}

/** Which milestone the sheet is editing; null inside means a new one. */
private data class MilestoneEdit(val milestone: Milestone?)

/** The team at a glance and the next thing due. */
@Composable
private fun WorkspaceHero(listing: Listing, outstanding: List<Milestone>, onBack: () -> Unit, onViewListing: () -> Unit) {
    val colors = TakatufTheme.colors
    TeamHero(
        label = kindLabel(listing),
        title = listing.title,
        onBack = onBack,
        actions = {
            IconButton(onClick = onViewListing, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onHero)) {
                Icon(Icons.AutoMirrored.Rounded.Article, contentDescription = stringResource(R.string.action_view_listing))
            }
        },
    ) {
        val recruitment = listing.recruitment
        val people = listing.headcount
        val open = recruitment?.openPlaces ?: 0
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeatRing(
                taken = people,
                open = open,
                description = seatsDescription(people, open),
                size = 56.dp,
                takenColor = colors.seatTakenOnHero,
                openColor = colors.seatOpenOnHero,
                trackColor = colors.onHero.copy(alpha = 0.2f),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (open > 0) pluralStringResource(R.plurals.workspace_people_open, open, people, open)
                    else pluralStringResource(R.plurals.team_people, people, people),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onHero,
                )
                val next = outstanding.firstOrNull()
                Text(
                    when {
                        next == null -> stringResource(R.string.workspace_nothing_due)
                        next.dueDate != null && next.isOverdue(java.time.Instant.now()) ->
                            stringResource(R.string.workspace_next_overdue, next.title, formatDay(Dates.lastDay(next.dueDate)))
                        next.dueDate != null -> stringResource(R.string.workspace_next_due, next.title, formatDay(Dates.lastDay(next.dueDate)))
                        else -> stringResource(R.string.workspace_next, next.title)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHeroVariant,
                    maxLines = 2,
                )
            }
        }
    }
}
