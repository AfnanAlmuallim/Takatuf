package com.z.takatuf.ui.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.ModerationRepository
import com.z.takatuf.data.NotificationRepository
import com.z.takatuf.data.RecordsRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Feedback
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ModerationAction
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import com.z.takatuf.data.model.Report
import com.z.takatuf.data.model.ReportStatus
import com.z.takatuf.data.model.ReportTarget
import com.z.takatuf.data.toAppError
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.ChoiceChip
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.LoadingRows
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.UserMessage
import com.z.takatuf.ui.common.relativeTime
import com.z.takatuf.ui.navigation.ReportRoute
import com.z.takatuf.ui.records.FeedbackRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// ---------- the queue ----------

data class ModerationUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val reports: List<Report> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ModerationViewModel(private val moderation: ModerationRepository) : ViewModel() {
    private val closed = MutableStateFlow(false)
    private val retry = MutableStateFlow(0)

    val showingClosed: StateFlow<Boolean> = closed

    val state: StateFlow<ModerationUiState> = combine(closed, retry) { c, _ -> c }
        .flatMapLatest { c ->
            moderation.observeReports(c)
                .map { ModerationUiState(loading = false, reports = it) }
                .onStart { emit(ModerationUiState()) }
                .catch { emit(ModerationUiState(loading = false, error = it.toAppError())) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModerationUiState())

    fun show(closed: Boolean) = this.closed.update { closed }
    fun retry() = retry.update { it + 1 }
}

/** The administrator's report queue (US20). Only reachable by administrators. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationScreen(onBack: () -> Unit, onOpenReport: (String) -> Unit) {
    val viewModel = appViewModel { c, _ -> ModerationViewModel(c.moderation) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val closed by viewModel.showingClosed.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.moderation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = if (closed) 1 else 0, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = !closed, onClick = { viewModel.show(false) }, text = { Text(stringResource(R.string.moderation_tab_open)) })
                Tab(selected = closed, onClick = { viewModel.show(true) }, text = { Text(stringResource(R.string.moderation_tab_closed)) })
            }
            when {
                state.loading -> LoadingRows()
                state.error != null -> ErrorState(state.error!!, viewModel::retry)
                state.reports.isEmpty() -> EmptyState(
                    title = stringResource(if (closed) R.string.moderation_empty_closed else R.string.moderation_empty_open),
                    body = stringResource(R.string.moderation_empty_body),
                    openSeats = 0,
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    itemsIndexed(state.reports, key = { _, it -> it.id }) { index, report ->
                        ReportRow(report, onClick = { onOpenReport(report.id) })
                        if (index < state.reports.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(report: Report, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(targetLabel(report.target), tone = TagTone.Quiet)
                Spacer(Modifier.width(6.dp))
                ReportStatusTag(report.status)
            }
            Spacer(Modifier.height(6.dp))
            Text(report.targetTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
                stringResource(R.string.report_row_meta, reasonLabel(report.reason), report.reporterName, relativeTime(report.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ReportStatusTag(status: ReportStatus) {
    when (status) {
        ReportStatus.Open -> Tag(stringResource(R.string.report_status_open), tone = TagTone.Attention)
        ReportStatus.Reviewing -> Tag(stringResource(R.string.report_status_reviewing), tone = TagTone.Accent)
        ReportStatus.Resolved -> Tag(stringResource(R.string.report_status_resolved), tone = TagTone.Quiet)
        ReportStatus.Dismissed -> Tag(stringResource(R.string.report_status_dismissed), tone = TagTone.Quiet)
    }
}

@Composable
private fun targetLabel(target: ReportTarget): String = stringResource(
    when (target) {
        ReportTarget.Listing -> R.string.report_target_listing
        ReportTarget.User -> R.string.report_target_user
        ReportTarget.Feedback -> R.string.report_target_feedback
    },
)

// ---------- one report ----------

data class ReportDetailUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val report: Report? = null,
    val listing: Listing? = null,
    val feedback: Feedback? = null,
    val busy: Boolean = false,
    val message: UserMessage? = null,
)

private data class Extras(val busy: Boolean = false, val message: UserMessage? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportDetailViewModel(
    private val reportId: String,
    private val session: Session,
    private val moderation: ModerationRepository,
    private val listings: ListingRepository,
    private val records: RecordsRepository,
    private val notifications: NotificationRepository,
) : ViewModel() {

    private val extras = MutableStateFlow(Extras())

    private val report = moderation.observeReport(reportId)
        .map { Result.success(it) }
        .catch { emit(Result.failure(it)) }

    private val listing = report.flatMapLatest { r ->
        val id = r.getOrNull()?.listingId.orEmpty()
        if (id.isEmpty()) flowOf(null) else listings.observe(id).catch { emit(null) }
    }.onStart { emit(null) }

    private val feedback = report.flatMapLatest { r ->
        val rep = r.getOrNull()
        if (rep?.target != ReportTarget.Feedback) flowOf(null) else records.observeFeedbackAt(rep.targetPath).catch { emit(null) }
    }.onStart { emit(null) }

    val state: StateFlow<ReportDetailUiState> = combine(report, listing, feedback, extras) { r, l, f, e ->
        ReportDetailUiState(
            loading = false,
            error = r.exceptionOrNull()?.toAppError() ?: if (r.getOrNull() == null) AppError.NotFound else null,
            report = r.getOrNull(),
            listing = l,
            feedback = f,
            busy = e.busy,
            message = e.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportDetailUiState())

    /** Applies a decision with its reason, then tells the reporter and the person affected. */
    fun act(action: ModerationAction, reason: String) {
        val report = state.value.report ?: return
        val me = session.profile ?: return
        if (extras.value.busy) return
        val actionId = UUID.randomUUID().toString()
        extras.update { it.copy(busy = true) }
        viewModelScope.launch {
            attempt { moderation.moderate(me, report, action, reason) }
                .onSuccess {
                    if (action.closes != null) {
                        notifications.send(
                            "$actionId-reporter", listOf(report.reporterUid), NotificationType.ReportResolved, report.listingId,
                            report.targetTitle, me.uid, MODERATORS, NotificationTarget.Activity, "", reason,
                        )
                    }
                    if (action in setOf(ModerationAction.HideListing, ModerationAction.FlagListing, ModerationAction.HideFeedback, ModerationAction.WarnUser)) {
                        // A warning is about the person, and a conduct report has no listing to open.
                        val aboutPerson = action == ModerationAction.HideFeedback || action == ModerationAction.WarnUser
                        val target = if (aboutPerson) NotificationTarget.Passport else NotificationTarget.Listing
                        notifications.send(
                            "$actionId-owner", listOf(report.targetOwnerUid), NotificationType.ModerationNotice, report.listingId,
                            report.targetTitle, me.uid, MODERATORS, target, report.listingId, reason,
                        )
                    }
                    extras.update { it.copy(busy = false, message = UserMessage(R.string.msg_moderation_saved)) }
                }
                .onFailure { e ->
                    extras.update { it.copy(busy = false, message = UserMessage(e.toAppError().message, retry = { act(action, reason) })) }
                }
        }
    }

    fun messageShown() = extras.update { it.copy(message = null) }

    private companion object {
        const val MODERATORS = "Takatuf moderators"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportDetailScreen(onBack: () -> Unit, onOpenListing: (String) -> Unit, onOpenPerson: (String) -> Unit) {
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        ReportDetailViewModel(handle.toRoute<ReportRoute>().id, c.session, c.moderation, c.listings, c.records, c.notifications)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var action by rememberSaveable { mutableStateOf<ModerationAction?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val report = state.report
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.error != null || report == null -> Column(Modifier.padding(padding)) { ErrorState(state.error ?: AppError.NotFound, onBack) }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(targetLabel(report.target), tone = TagTone.Quiet)
                    Spacer(Modifier.width(6.dp))
                    ReportStatusTag(report.status)
                }
                Spacer(Modifier.height(8.dp))
                Text(report.targetTitle, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.report_row_meta, reasonLabel(report.reason), report.reporterName, relativeTime(report.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report.details.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Text(report.details, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle(stringResource(R.string.report_reported_item))
                when (report.target) {
                    ReportTarget.Listing -> {
                        state.listing?.let { l ->
                            Text(
                                when {
                                    l.hidden -> stringResource(R.string.moderation_listing_hidden)
                                    l.flag != null -> stringResource(R.string.moderation_listing_flagged, l.flag.reason)
                                    else -> stringResource(R.string.moderation_listing_visible)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton(onClick = { onOpenListing(report.listingId) }) { Text(stringResource(R.string.action_view_listing)) }
                    }
                    ReportTarget.User -> TextButton(onClick = { onOpenPerson(report.targetOwnerUid) }) { Text(stringResource(R.string.action_view_passport)) }
                    ReportTarget.Feedback -> state.feedback?.let { FeedbackRow(it, isMine = true, onDispute = null) }
                }
                if (report.status.closed) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.report_resolution, report.resolution), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(20.dp))
                SectionTitle(stringResource(R.string.moderation_decide))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Only decisions that would change something: no "show again" for a visible listing.
                    val possible = ModerationAction.forTarget(report.target).filter { option ->
                        when (option) {
                            ModerationAction.HideListing -> state.listing?.hidden != true
                            ModerationAction.UnhideListing -> state.listing?.hidden == true
                            ModerationAction.FlagListing -> state.listing?.flag == null && state.listing?.hidden != true
                            ModerationAction.HideFeedback -> state.feedback?.hidden != true
                            ModerationAction.RestoreFeedback -> state.feedback?.hidden == true
                            else -> true
                        }
                    }
                    val shown = if (report.status == ReportStatus.Open) listOf(ModerationAction.Reviewing) + possible else possible
                    shown.forEach { option ->
                        ChoiceChip(selected = action == option, onClick = { action = option }, label = { Text(actionLabel(option)) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { if (it.length <= 500) reason = it },
                    label = { Text(stringResource(R.string.moderation_reason)) },
                    supportingText = { Text(stringResource(R.string.moderation_reason_hint)) },
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = stringResource(R.string.action_record_decision),
                    onClick = { action?.let { viewModel.act(it, reason.trim()); reason = "" } },
                    enabled = action != null && reason.trim().length >= 3,
                    loading = state.busy,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun actionLabel(action: ModerationAction): String = stringResource(
    when (action) {
        ModerationAction.Reviewing -> R.string.moderation_action_reviewing
        ModerationAction.Dismiss -> R.string.moderation_action_dismiss
        ModerationAction.Resolve -> R.string.moderation_action_resolve
        ModerationAction.HideListing -> R.string.moderation_action_hide_listing
        ModerationAction.UnhideListing -> R.string.moderation_action_unhide_listing
        ModerationAction.FlagListing -> R.string.moderation_action_flag_listing
        ModerationAction.HideFeedback -> R.string.moderation_action_hide_feedback
        ModerationAction.RestoreFeedback -> R.string.moderation_action_restore_feedback
        ModerationAction.WarnUser -> R.string.moderation_action_warn_user
    },
)
