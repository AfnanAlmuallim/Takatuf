package com.z.takatuf.ui.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.R
import com.z.takatuf.ui.notifications.TabTitle
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.domain.Dates
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.DateTile
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.Fact
import com.z.takatuf.ui.common.KindLabel
import com.z.takatuf.ui.common.AnswerCard
import com.z.takatuf.ui.common.ApplicationStatusTag
import com.z.takatuf.ui.common.LoadingRows
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.NewDot
import com.z.takatuf.ui.common.SeatRing
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.closedLabel
import com.z.takatuf.ui.common.relativeTime
import com.z.takatuf.ui.common.seatsDescription
import com.z.takatuf.ui.discover.PostSheet
import java.time.Instant

@Composable
fun ActivityScreen(
    onOpenListing: (String) -> Unit,
    onOpenApplicants: (String) -> Unit,
    onDiscover: () -> Unit,
    onPost: (ListingKind) -> Unit,
    onOpenTeam: (String) -> Unit,
    onInbox: () -> Unit,
) {
    val viewModel = appViewModel { c, _ -> ActivityViewModel(c.session, c.listings, c.applications, c.notifications) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(-1) }
    var showPost by rememberSaveable { mutableStateOf(false) }
    var confirmDecline by remember { mutableStateOf<Application?>(null) }
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    // Accepting an offer goes straight to the team's workspace.
    LaunchedEffect(state.joinedListingId) {
        state.joinedListingId?.let { viewModel.joinHandled(); onOpenTeam(it) }
    }
    // Open on whichever side needs attention first.
    LaunchedEffect(state.loading) {
        if (!state.loading && tab == -1) {
            tab = when {
                state.needsAnswer > 0 -> 0
                state.newApplicants > 0 || state.applications.isEmpty() && state.owned.isNotEmpty() -> 1
                else -> 0
            }
        }
    }
    val selected = tab.coerceAtLeast(0)
    val now = remember(state) { Instant.now() }

    Scaffold(contentWindowInsets = WindowInsets.statusBars, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabTitle(stringResource(R.string.tab_activity), onInbox)
            PrimaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(
                    selected = selected == 0,
                    onClick = { tab = 0 },
                    text = {
                        if (state.needsAnswer > 0) {
                            TabLabel(stringResource(R.string.activity_tab_applications), count = state.needsAnswer, highlight = true)
                        } else {
                            TabLabel(stringResource(R.string.activity_tab_applications), count = state.activeApplications, highlight = false)
                        }
                    },
                )
                Tab(
                    selected = selected == 1,
                    onClick = { tab = 1 },
                    text = { TabLabel(stringResource(R.string.activity_tab_listings), count = state.newApplicants, highlight = true) },
                )
            }
            when {
                state.loading -> LoadingRows()
                state.error != null -> ErrorState(state.error!!, viewModel::retry)
                selected == 0 -> ApplicationsList(
                    applications = state.applications,
                    busyListingId = state.busyListingId,
                    onOpenListing = onOpenListing,
                    onDiscover = onDiscover,
                    onAccept = viewModel::acceptOffer,
                    onDecline = { confirmDecline = it },
                    onConfirmInterest = viewModel::confirmInterest,
                    onWithdraw = viewModel::withdraw,
                )
                else -> OwnedList(state.owned, now, onOpenListing, onOpenApplicants, onPost = { showPost = true })
            }
        }
    }
    if (showPost) {
        PostSheet(onPick = { showPost = false; onPost(it) }, onDismiss = { showPost = false })
    }
    confirmDecline?.let { application ->
        AlertDialog(
            onDismissRequest = { confirmDecline = null },
            title = { Text(stringResource(R.string.decline_offer_title)) },
            text = { Text(stringResource(R.string.decline_offer_body, application.listingTitle)) },
            confirmButton = {
                TextButton(onClick = { confirmDecline = null; viewModel.declineOffer(application) }) {
                    Text(stringResource(R.string.action_decline))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDecline = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun TabLabel(text: String, count: Int, highlight: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            if (highlight) {
                Badge(containerColor = com.z.takatuf.ui.theme.TakatufTheme.colors.attention, contentColor = com.z.takatuf.ui.theme.TakatufTheme.colors.onAttention) {
                    Text(count.toString())
                }
            } else {
                Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ApplicationsList(
    applications: List<Application>,
    busyListingId: String?,
    onOpenListing: (String) -> Unit,
    onDiscover: () -> Unit,
    onAccept: (Application) -> Unit,
    onDecline: (Application) -> Unit,
    onConfirmInterest: (Application) -> Unit,
    onWithdraw: (Application) -> Unit,
) {
    if (applications.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.activity_no_applications_title),
            body = stringResource(R.string.activity_no_applications_body),
            actionLabel = stringResource(R.string.activity_find_opportunities),
            onAction = onDiscover,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(applications, key = { _, it -> it.listingId }) { index, application ->
            ApplicationRow(
                application = application,
                busy = busyListingId == application.listingId,
                onClick = { onOpenListing(application.listingId) },
                onAccept = { onAccept(application) },
                onDecline = { onDecline(application) },
                onConfirmInterest = { onConfirmInterest(application) },
                onWithdraw = { onWithdraw(application) },
            )
            if (index < applications.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ApplicationRow(
    application: Application,
    busy: Boolean,
    onClick: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onConfirmInterest: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val now = Instant.now()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
    ) {
        ApplicationSummary(application)
        // Answers that can't wait sit on the row itself, one tap away.
        when {
            application.hasLiveOffer(now) -> AnswerRow(
                prompt = stringResource(R.string.offer_prompt, application.roleTitle),
                busy = busy,
                primary = stringResource(R.string.action_accept_offer),
                onPrimary = onAccept,
                secondary = stringResource(R.string.action_decline),
                onSecondary = onDecline,
            )
            application.status == ApplicationStatus.Reconfirm -> AnswerRow(
                prompt = stringResource(R.string.reconfirm_prompt),
                busy = busy,
                primary = stringResource(R.string.action_still_interested),
                onPrimary = onConfirmInterest,
                secondary = stringResource(R.string.action_withdraw),
                onSecondary = onWithdraw,
            )
        }
    }
}

@Composable
private fun AnswerRow(
    prompt: String,
    busy: Boolean,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    AnswerCard(prompt, busy, primary, onPrimary, secondary, onSecondary)
}

@Composable
private fun ApplicationSummary(application: Application) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                application.listingTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (application.status.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.applied_as, application.roleTitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApplicationStatusTag(application, forOwner = false)
                Spacer(Modifier.width(10.dp))
                Text(
                    relativeTime(application.lastActivityAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OwnedList(
    owned: List<OwnedListing>,
    now: Instant,
    onOpenListing: (String) -> Unit,
    onOpenApplicants: (String) -> Unit,
    onPost: () -> Unit,
) {
    if (owned.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.activity_no_listings_title),
            body = stringResource(R.string.activity_no_listings_body),
            actionLabel = stringResource(R.string.action_post_something),
            onAction = onPost,
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(owned, key = { _, it -> it.listing.id }) { index, item ->
            OwnedRow(
                item = item,
                now = now,
                onClick = {
                    if (item.listing.kind.recruits) onOpenApplicants(item.listing.id) else onOpenListing(item.listing.id)
                },
            )
            if (index < owned.lastIndex) HorizontalDivider(Modifier.padding(start = 88.dp, end = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnedRow(item: OwnedListing, now: Instant, onClick: () -> Unit) {
    val listing: Listing = item.listing
    val closed = listing.closedReason(now)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(56.dp)) {
            val r = listing.recruitment
            val e = listing.event
            when {
                r != null -> SeatRing(r.teamSize, r.openPlaces, seatsDescription(r.teamSize, r.openPlaces), size = 48.dp)
                e != null -> Dates.startDate(e.startDate).let { DateTile(it.monthValue, it.dayOfMonth) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindLabel(listing, Modifier.weight(1f, fill = false))
                if (closed != null) {
                    Spacer(Modifier.width(8.dp))
                    Tag(closedLabel(closed), tone = TagTone.Quiet)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(listing.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (listing.kind.recruits) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.active == 0) {
                        Fact(Icons.Rounded.Groups, stringResource(R.string.no_applicants_yet))
                    } else {
                        Fact(Icons.Rounded.Groups, pluralStringResource(R.plurals.applicants_count, item.active, item.active))
                    }
                    if (item.new > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NewDot()
                            Spacer(Modifier.width(6.dp))
                            Text(
                                pluralStringResource(R.plurals.new_count, item.new, item.new),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (item.shortlisted > 0) {
                        Fact(Icons.Rounded.Star, pluralStringResource(R.plurals.shortlisted_count, item.shortlisted, item.shortlisted), color = MaterialTheme.colorScheme.primary)
                    }
                    if (item.offered > 0) {
                        Fact(Icons.Rounded.MarkEmailUnread, pluralStringResource(R.plurals.offers_out_count, item.offered, item.offered))
                    }
                    if (listing.members.isNotEmpty()) {
                        Fact(Icons.Rounded.Diversity3, pluralStringResource(R.plurals.members_joined_count, listing.members.size, listing.members.size))
                    }
                }
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
