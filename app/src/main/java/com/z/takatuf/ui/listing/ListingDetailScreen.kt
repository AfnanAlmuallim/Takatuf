package com.z.takatuf.ui.listing

import android.content.ActivityNotFoundException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.R
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.ClosedReason
import com.z.takatuf.data.model.EventFormat
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.Role
import com.z.takatuf.domain.Dates
import com.z.takatuf.domain.SkillMatcher
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.BottomActionBar
import com.z.takatuf.ui.common.DeadlineFact
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.Fact
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.KindLabel
import com.z.takatuf.ui.common.StatusBarIcons
import com.z.takatuf.ui.common.ListingRow
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.Monogram
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SeatRing
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.SkillChips
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.closedLabel
import com.z.takatuf.ui.common.formatDay
import com.z.takatuf.ui.common.formatEventRange
import com.z.takatuf.ui.common.relativeTime
import com.z.takatuf.ui.common.seatsDescription
import com.z.takatuf.ui.common.teamSizeText
import com.z.takatuf.ui.navigation.ListingRoute
import com.z.takatuf.ui.theme.TakatufTheme
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import java.time.Instant

@Composable
fun ListingDetailScreen(
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onReviewApplicants: (String) -> Unit,
    onEdit: (Listing) -> Unit,
    onFindTeammates: (eventId: String) -> Unit,
) {
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        ListingDetailViewModel(handle.toRoute<ListingRoute>().id, c.session, c.listings, c.applications, c.saved)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)

    var applyRoleId by rememberSaveable { mutableStateOf<String?>(null) }
    var showApply by rememberSaveable { mutableStateOf(false) }
    var confirmWithdraw by rememberSaveable { mutableStateOf(false) }
    var confirmClose by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Unit = { url ->
        try {
            uriHandler.openUri(url)
        } catch (_: ActivityNotFoundException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    // Close the sheet once the application went through.
    val appliedAt = state.appliedAt
    androidx.compose.runtime.LaunchedEffect(appliedAt) { if (appliedAt != 0L) showApply = false }

    val listing = state.listing
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (listing != null) {
                DetailActionBar(
                    state = state,
                    listing = listing,
                    onApply = { applyRoleId = null; showApply = true },
                    onWithdraw = { confirmWithdraw = true },
                    onReviewApplicants = { onReviewApplicants(listing.id) },
                    onFindTeammates = { onFindTeammates(listing.id) },
                    onOpenSource = { listing.event?.sourceUrl?.let(openUrl) },
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> FullScreenLoading()
            state.error != null -> Column(Modifier.statusBarsPadding()) {
                BackRow(onBack)
                ErrorState(state.error!!, viewModel::retry)
            }
            listing == null -> Column(Modifier.statusBarsPadding()) {
                BackRow(onBack)
                EmptyState(
                    title = stringResource(R.string.listing_missing_title),
                    body = stringResource(R.string.listing_missing_body),
                    actionLabel = stringResource(R.string.action_go_back),
                    onAction = onBack,
                    openSeats = 0,
                )
            }
            else -> {
                val scroll = rememberScrollState()
                var heroHeight by remember { mutableIntStateOf(0) }
                val density = LocalDensity.current
                val barPx = WindowInsets.statusBars.getTop(density) + with(density) { 64.dp.roundToPx() }
                // Once the hero has scrolled under the top bar, a compact bar takes over.
                val collapsed by remember { derivedStateOf { heroHeight > 0 && scroll.value > heroHeight - barPx } }
                StatusBarIcons(light = !collapsed)
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scroll),
                    ) {
                        Hero(
                            state = state,
                            listing = listing,
                            onBack = onBack,
                            onToggleSave = viewModel::toggleSave,
                            onOpenPerson = onOpenPerson,
                            onEdit = { onEdit(listing) },
                            onCloseRecruitment = { confirmClose = true },
                            onReopenRecruitment = { viewModel.setRecruitmentOpen(true) },
                            modifier = Modifier.onSizeChanged { heroHeight = it.height },
                        )
                        Body(
                            state = state,
                            listing = listing,
                            onApplyForRole = { role -> applyRoleId = role.id; showApply = true },
                            onOpenListing = onOpenListing,
                            onOpenPerson = onOpenPerson,
                            onOpenUrl = openUrl,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                    CompactBar(
                        visible = collapsed,
                        title = listing.title,
                        saved = state.saved,
                        onBack = onBack,
                        onToggleSave = viewModel::toggleSave,
                    )
                }
            }
        }
    }

    if (showApply && listing != null) {
        ApplySheet(
            listing = listing,
            initialRoleId = applyRoleId ?: state.myApplication?.roleId,
            myHours = state.myHours,
            mySkills = state.mySkills,
            visibility = state.myVisibility,
            sending = state.applying,
            error = state.applyError,
            onSend = viewModel::apply,
            onDismiss = { showApply = false; viewModel.clearApplyError() },
        )
    }
    if (confirmWithdraw) {
        AlertDialog(
            onDismissRequest = { confirmWithdraw = false },
            title = { Text(stringResource(R.string.withdraw_title)) },
            text = { Text(stringResource(R.string.withdraw_body)) },
            confirmButton = {
                TextButton(onClick = { confirmWithdraw = false; viewModel.withdraw() }) {
                    Text(stringResource(R.string.action_withdraw))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithdraw = false }) { Text(stringResource(R.string.action_keep_application)) }
            },
        )
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.close_recruitment_title)) },
            text = { Text(stringResource(R.string.close_recruitment_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; viewModel.setRecruitmentOpen(false) }) {
                    Text(stringResource(R.string.action_close_recruitment))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Back, title and save, shown once the hero has scrolled out of view. */
@Composable
private fun CompactBar(
    visible: Boolean,
    title: String,
    saved: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconToggleButton(checked = saved, onCheckedChange = { onToggleSave() }) {
                        Icon(
                            imageVector = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = stringResource(if (saved) R.string.action_unsave else R.string.action_save),
                            tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    IconButton(onClick = onBack, modifier = Modifier.padding(4.dp)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

/** The navy block: what this is, who posted it, and the team at a glance. */
@Composable
private fun Hero(
    state: ListingDetailUiState,
    listing: Listing,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onEdit: () -> Unit,
    onCloseRecruitment: () -> Unit,
    onReopenRecruitment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TakatufTheme.colors
    val context = LocalContext.current
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.hero, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .statusBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        val heroButtons = IconButtonDefaults.iconButtonColors(contentColor = colors.onHero)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, colors = heroButtons) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Spacer(Modifier.weight(1f))
            IconToggleButton(checked = state.saved, onCheckedChange = { onToggleSave() }) {
                Icon(
                    imageVector = if (state.saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = stringResource(if (state.saved) R.string.action_unsave else R.string.action_save),
                    tint = colors.onHero,
                )
            }
            if (state.isOwner) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, colors = heroButtons) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (listing.kind == ListingKind.Event) R.string.action_edit_event else R.string.action_edit_listing)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menu = false; onEdit() },
                        )
                        if (listing.kind.recruits) {
                            if (listing.status == ListingStatus.Open) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_close_recruitment)) },
                                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                                    onClick = { menu = false; onCloseRecruitment() },
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_reopen_recruitment)) },
                                    leadingIcon = { Icon(Icons.Rounded.LockOpen, contentDescription = null) },
                                    onClick = { menu = false; onReopenRecruitment() },
                                )
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            KindLabel(listing, color = colors.onHeroVariant, iconTint = colors.seatOpenOnHero)
            Spacer(Modifier.height(8.dp))
            Text(
                text = listing.title,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onHero,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            val event = listing.event
            if (event != null) {
                Text(
                    text = stringResource(R.string.organized_by, event.organizer),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onHero,
                )
            } else {
                OwnerLine(listing, onOpenPerson)
            }
            Spacer(Modifier.height(24.dp))
            val recruitment = listing.recruitment
            when {
                recruitment != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val mine = if (state.myApplication?.status?.isActive == true) 1 else 0
                    SeatRing(
                        taken = recruitment.teamSize,
                        open = recruitment.openPlaces,
                        description = seatsDescription(recruitment.teamSize, recruitment.openPlaces),
                        size = 104.dp,
                        mine = mine,
                        takenColor = colors.seatTakenOnHero,
                        openColor = colors.seatOpenOnHero,
                        mineColor = colors.seatOpenOnHero,
                        trackColor = colors.onHero.copy(alpha = 0.2f),
                        animate = true,
                    )
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(
                            text = recruitment.openPlaces.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = colors.onHero,
                        )
                        Text(
                            text = pluralStringResource(R.plurals.open_places_in_team, recruitment.openPlaces, recruitment.teamSize + recruitment.openPlaces),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onHeroVariant,
                        )
                        if (mine == 1) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.hero_your_seat),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.seatOpenOnHero,
                            )
                        }
                    }
                }
                event != null -> Column {
                    Text(
                        text = formatEventRange(context, event.startDate, event.endDate),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.onHero,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = teamSizeText(event.teamSizeMin, event.teamSizeMax),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onHeroVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnerLine(listing: Listing, onOpenPerson: (String) -> Unit) {
    Row(
        modifier = Modifier
            .clickable { onOpenPerson(listing.ownerId) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(listing.ownerName, listing.ownerId, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = stringResource(R.string.posted_by, listing.ownerName),
                style = MaterialTheme.typography.labelLarge,
                color = TakatufTheme.colors.onHero,
            )
            if (listing.ownerMajor.isNotBlank()) {
                Text(listing.ownerMajor, style = MaterialTheme.typography.bodySmall, color = TakatufTheme.colors.onHeroVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Body(
    state: ListingDetailUiState,
    listing: Listing,
    onApplyForRole: (Role) -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val now = remember { Instant.now() }
    val closed = listing.closedReason(now)
    Column(Modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listing.recruitment?.let { r ->
                if (r.hoursPerWeek > 0) Fact(Icons.Rounded.Schedule, pluralStringResource(R.plurals.hours_per_week_long, r.hoursPerWeek, r.hoursPerWeek), emphasized = true)
                if (r.duration.isNotBlank()) Fact(Icons.Rounded.Timelapse, r.duration, emphasized = true)
            }
            listing.event?.let { e ->
                Fact(Icons.Rounded.Place, eventFormatText(e.format, e.location), emphasized = true)
            }
            if (closed == null) DeadlineFact(listing.closesAt.takeIf { listing.recruitment != null || listing.event?.registrationDeadline != null }, now)
        }
        if (closed != null) {
            Spacer(Modifier.height(16.dp))
            ClosedNotice(listing, closed)
        }
        when (listing.kind) {
            ListingKind.Event -> EventBody(state, listing, onOpenListing, onOpenPerson, onOpenUrl)
            else -> RecruitingBody(state, listing, closed, onApplyForRole, onOpenListing, onOpenUrl)
        }
    }
}

@Composable
private fun ClosedNotice(listing: Listing, reason: ClosedReason) {
    val text = when (reason) {
        ClosedReason.ClosedByOwner -> stringResource(R.string.closed_by_owner_long)
        ClosedReason.DeadlinePassed -> stringResource(R.string.closed_deadline_long, formatDay(Dates.lastDay(listing.recruitment?.deadline ?: Instant.now())))
        ClosedReason.EventOver -> stringResource(R.string.closed_event_over_long)
        ClosedReason.Ended -> stringResource(R.string.closed_ended_long, formatDay(Dates.lastDay(listing.event?.endDate ?: Instant.now())))
        ClosedReason.RegistrationClosed -> stringResource(R.string.closed_registration_long)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(closedLabel(reason), style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecruitingBody(
    state: ListingDetailUiState,
    listing: Listing,
    closed: ClosedReason?,
    onApplyForRole: (Role) -> Unit,
    onOpenListing: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val recruitment = listing.recruitment ?: return
    val canApply = !state.isOwner && closed == null && state.myApplication?.status?.isActive != true
    if (listing.kind == ListingKind.Team) {
        Spacer(Modifier.height(24.dp))
        LinkedEventCard(listing, state.event, onOpenListing, onOpenUrl)
    }
    if (recruitment.outcome.isNotBlank()) {
        Spacer(Modifier.height(24.dp))
        SectionTitle(stringResource(if (listing.kind == ListingKind.Team) R.string.section_team_goal else R.string.section_outcome))
        Text(recruitment.outcome, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(24.dp))
    SectionTitle(stringResource(R.string.section_roles))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        recruitment.roles.forEach { role ->
            RoleBlock(
                role = role,
                mySkills = if (state.isOwner) emptyList() else state.mySkills,
                appliedFor = state.myApplication?.takeIf { it.status.isActive && it.roleId == role.id },
                canApply = canApply && role.openings > 0,
                onApply = { onApplyForRole(role) },
            )
        }
    }
    if (listing.description.isNotBlank()) {
        Spacer(Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.section_about))
        Text(listing.description, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.posted_when, relativeTime(listing.createdAt)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RoleBlock(
    role: Role,
    mySkills: List<String>,
    appliedFor: Application?,
    canApply: Boolean,
    onApply: () -> Unit,
) {
    val covered = SkillMatcher.covered(mySkills, role.skills)
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (appliedFor != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (canApply) 4.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(role.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                if (role.openings > 0) {
                    Tag(pluralStringResource(R.plurals.places_count, role.openings, role.openings), icon = Icons.Rounded.Groups)
                } else {
                    Tag(stringResource(R.string.role_filled), tone = TagTone.Quiet)
                }
            }
            if (role.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(role.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (role.skills.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SkillChips(role.skills, mySkills)
                if (covered.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = pluralStringResource(R.plurals.role_you_cover, role.skills.size, covered.size, role.skills.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (appliedFor != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.role_you_applied), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (canApply) {
                TextButton(onClick = onApply, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_apply_for_role))
                }
            }
        }
    }
}

/** What an applicant needs to know about the event: rules, dates and the official source. */
@Composable
private fun LinkedEventCard(listing: Listing, event: Listing?, onOpenListing: (String) -> Unit, onOpenUrl: (String) -> Unit) {
    val linked = listing.linkedEvent ?: return
    val details = event?.event
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (event != null) KindLabel(event)
            Spacer(Modifier.height(4.dp))
            Text(event?.title ?: linked.title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.organized_by, details?.organizer ?: linked.organizer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val start = details?.startDate ?: linked.startDate
            val end = details?.endDate ?: linked.endDate
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (start != null && end != null) Fact(Icons.Rounded.CalendarMonth, formatEventRange(context, start, end))
                Fact(Icons.Rounded.Groups, teamSizeText(details?.teamSizeMin ?: linked.teamSizeMin, details?.teamSizeMax ?: linked.teamSizeMax))
                details?.registrationDeadline?.let { Fact(Icons.Rounded.HourglassBottom, stringResource(R.string.registration_closes_on, formatDay(Dates.lastDay(it)))) }
            }
            if (!details?.eligibility.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.section_eligibility), style = MaterialTheme.typography.labelLarge)
                Text(details!!.eligibility, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = { onOpenListing(linked.id) }) { Text(stringResource(R.string.action_view_event)) }
                details?.sourceUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    TextButton(onClick = { onOpenUrl(url) }) {
                        Text(stringResource(R.string.action_official_page))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventBody(
    state: ListingDetailUiState,
    listing: Listing,
    onOpenListing: (String) -> Unit,
    onOpenPerson: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val event = listing.event ?: return
    Spacer(Modifier.height(24.dp))
    // Source first: students should check the official page before relying on a shared copy.
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = event.sourceUrl.isNotBlank()) { onOpenUrl(event.sourceUrl) },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.section_official_source), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = hostOf(event.sourceUrl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.action_official_page), tint = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier
            .clickable { onOpenPerson(listing.ownerId) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(listing.ownerName, listing.ownerId, size = 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.shared_by, listing.ownerName, relativeTime(listing.createdAt)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (event.eligibility.isNotBlank()) {
        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.section_eligibility))
        Text(event.eligibility, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(20.dp))
    SectionTitle(stringResource(R.string.section_rules))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Fact(Icons.Rounded.Groups, teamSizeText(event.teamSizeMin, event.teamSizeMax))
        event.registrationDeadline?.let { Fact(Icons.Rounded.HourglassBottom, stringResource(R.string.registration_closes_on, formatDay(Dates.lastDay(it)))) }
    }
    if (listing.description.isNotBlank()) {
        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.section_about))
        Text(listing.description, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(24.dp))
    SectionTitle(pluralStringResource(R.plurals.teams_recruiting, state.teams.size, state.teams.size))
    if (state.teams.isEmpty()) {
        Text(
            stringResource(R.string.teams_recruiting_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(Modifier.padding(horizontal = 0.dp)) {
            state.teams.forEachIndexed { index, team ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.padding(horizontal = 0.dp)) {
                    ListingRow(
                        listing = team,
                        onClick = { onOpenListing(team.id) },
                        mySkills = state.mySkills,
                        myApplication = null,
                        isOwn = team.ownerId == state.myUid,
                        startPadding = 0.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun eventFormatText(format: EventFormat, location: String): String {
    val base = stringResource(
        when (format) {
            EventFormat.InPerson -> R.string.format_in_person
            EventFormat.Online -> R.string.format_online
            EventFormat.Hybrid -> R.string.format_hybrid
        },
    )
    return if (location.isNotBlank() && format != EventFormat.Online) stringResource(R.string.format_with_location, base, location) else base
}

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

/** The one action that matters for this viewer, pinned to the bottom. */
@Composable
private fun DetailActionBar(
    state: ListingDetailUiState,
    listing: Listing,
    onApply: () -> Unit,
    onWithdraw: () -> Unit,
    onReviewApplicants: () -> Unit,
    onFindTeammates: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val now = remember { Instant.now() }
    val closed = listing.closedReason(now)
    BottomActionBar {
        when {
            listing.kind == ListingKind.Event -> {
                val ended = closed == ClosedReason.Ended
                if (state.isOwner || !ended) {
                    PrimaryButton(
                        text = stringResource(R.string.action_find_teammates),
                        onClick = onFindTeammates,
                        enabled = !ended,
                        icon = Icons.Rounded.Groups,
                    )
                }
                if (ended) {
                    Text(
                        stringResource(R.string.closed_ended_short),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenSource, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_official_page))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            state.isOwner -> {
                val counts = state.applicantCounts
                PrimaryButton(
                    text = when {
                        counts == null || counts.active == 0 -> stringResource(R.string.action_review_applicants)
                        counts.new > 0 -> pluralStringResource(R.plurals.review_applicants_new, counts.new, counts.new)
                        else -> pluralStringResource(R.plurals.review_applicants_count, counts.active, counts.active)
                    },
                    onClick = onReviewApplicants,
                )
            }
            state.myApplication?.status?.isActive == true -> AppliedBar(state.myApplication, busy = state.busy, onWithdraw = onWithdraw)
            closed != null -> PrimaryButton(text = closedLabel(closed), onClick = {}, enabled = false)
            else -> PrimaryButton(
                text = stringResource(
                    if (state.myApplication?.status == ApplicationStatus.Withdrawn) R.string.action_apply_again else R.string.action_apply,
                ),
                onClick = onApply,
            )
        }
    }
}

@Composable
private fun AppliedBar(application: Application, busy: Boolean, onWithdraw: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (application.status == ApplicationStatus.Shortlisted) {
                Tag(stringResource(R.string.status_shortlisted), icon = Icons.Rounded.Star, tone = TagTone.Accent)
            } else {
                Tag(stringResource(R.string.status_under_review), icon = Icons.Rounded.Schedule, tone = TagTone.Neutral)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.applied_for_role_when, application.roleTitle, relativeTime(application.updatedAt)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onWithdraw, enabled = !busy) {
            Text(stringResource(R.string.action_withdraw), color = if (busy) Color.Unspecified else MaterialTheme.colorScheme.error)
        }
    }
}
