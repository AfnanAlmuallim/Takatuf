package com.z.takatuf.ui.records

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.z.takatuf.R
import com.z.takatuf.data.AppError
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.DraftStore
import com.z.takatuf.data.ListingRepository
import com.z.takatuf.data.NotificationRepository
import com.z.takatuf.data.RecordsRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.Feedback
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import com.z.takatuf.data.toAppError
import com.z.takatuf.ui.appViewModel
import com.z.takatuf.ui.common.EmptyState
import com.z.takatuf.ui.common.ErrorState
import com.z.takatuf.ui.common.FullScreenLoading
import com.z.takatuf.ui.common.MessageEffect
import com.z.takatuf.ui.common.Monogram
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.UserMessage
import com.z.takatuf.ui.navigation.FeedbackRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Someone the student worked with on this project, and the review they left them, if any. */
data class Teammate(
    val uid: String,
    val name: String,
    val role: String,
    val led: Boolean,
    val left: Boolean,
    val myReview: Feedback?,
)

data class FeedbackUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val listing: Listing? = null,
    val teammates: List<Teammate> = emptyList(),
    /** Feedback opens after completion or a departure (US18). */
    val open: Boolean = false,
    val saving: Boolean = false,
    val savedAt: Long = 0,
    val message: UserMessage? = null,
)

class FeedbackViewModel(
    private val listingId: String,
    private val session: Session,
    private val listings: ListingRepository,
    private val applications: ApplicationRepository,
    private val records: RecordsRepository,
    private val notifications: NotificationRepository,
    private val drafts: DraftStore,
) : ViewModel() {

    private val uid = session.uid.orEmpty()
    private val _state = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            attempt {
                val listing = listings.observe(listingId).first() ?: throw com.z.takatuf.data.AppException(AppError.NotFound)
                val mine = attempt { records.observeParticipation(uid).first() }.getOrNull().orEmpty()
                    .firstOrNull { it.listingId == listingId }
                val current = buildList {
                    if (listing.ownerId != uid) add(Teammate(listing.ownerId, listing.ownerName, RecordsRepository.LEAD_ROLE, led = true, left = false, myReview = null))
                    listing.members.filter { it.uid != uid }.forEach {
                        add(Teammate(it.uid, it.name, it.roleTitle.ifBlank { RecordsRepository.MEMBER_ROLE }, led = it.roleTitle.isBlank(), left = false, myReview = null))
                    }
                }
                val departed = listing.departed.filter { it.uid != uid }.map {
                    Teammate(it.uid, it.name, it.roleTitle.ifBlank { RecordsRepository.MEMBER_ROLE }, led = false, left = true, myReview = null)
                }
                // Teams older than the former-member list: the lead finds them in the applications.
                val earlier = if (listing.ownerId == uid) {
                    attempt { applications.observeForListing(listingId).first() }.getOrNull().orEmpty()
                        .filter { it.status == ApplicationStatus.Left || it.status == ApplicationStatus.Removed }
                        .filter { app -> (current + departed).none { it.uid == app.applicantId } && app.applicantId != uid }
                        .map { Teammate(it.applicantId, it.applicantName, it.roleTitle, led = false, left = true, myReview = null) }
                } else {
                    emptyList()
                }
                val former = departed + earlier
                // Someone who left can be reviewed straight away; the rest wait for the project's end.
                val ended = listing.isCompleted || (mine?.status?.ended == true)
                val all = (if (ended) current + former else former).map { it.copy(myReview = records.reviewBy(uid, it.uid, listingId)) }
                Triple(listing, all, ended || former.isNotEmpty())
            }.onSuccess { (listing, teammates, open) ->
                _state.update { it.copy(loading = false, listing = listing, teammates = teammates, open = open) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.toAppError()) }
            }
        }
    }

    fun review(teammate: Teammate, contribution: Int, leadership: Int?, comment: String) {
        val listing = state.value.listing ?: return
        val me = session.profile ?: return
        if (state.value.saving) return
        val actionId = UUID.randomUUID().toString()
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            attempt {
                records.giveFeedback(teammate.uid, listing.id, listing.title, me, contribution, leadership, comment, teammate.myReview)
            }.onSuccess {
                drafts.clear(DraftStore.feedback(listing.id, teammate.uid))
                if (teammate.myReview == null) {
                    notifications.send(
                        actionId, listOf(teammate.uid), NotificationType.FeedbackReceived, listing.id, listing.title,
                        me.uid, me.displayName, NotificationTarget.Passport, "",
                    )
                }
                _state.update { it.copy(saving = false, savedAt = System.nanoTime(), message = UserMessage(R.string.msg_review_saved)) }
                load()
            }.onFailure { e ->
                _state.update {
                    it.copy(saving = false, message = UserMessage(e.toAppError().message, retry = { review(teammate, contribution, leadership, comment) }))
                }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { c, handle: SavedStateHandle ->
        FeedbackViewModel(handle.toRoute<FeedbackRoute>().listingId, c.session, c.listings, c.applications, c.records, c.notifications, c.drafts)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(state.message, snackbar, viewModel::messageShown)
    var reviewing by remember { mutableStateOf<Teammate?>(null) }
    LaunchedEffect(state.savedAt) { if (state.savedAt != 0L) reviewing = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedback_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val listing = state.listing
        when {
            state.loading -> FullScreenLoading(Modifier.padding(padding))
            state.error != null || listing == null -> Column(Modifier.padding(padding)) {
                ErrorState(state.error ?: AppError.NotFound, viewModel::load)
            }
            !state.open -> Column(Modifier.padding(padding)) {
                EmptyState(
                    title = stringResource(R.string.feedback_not_open_title),
                    body = stringResource(R.string.feedback_not_open_body),
                    openSeats = 0,
                )
            }
            state.teammates.isEmpty() -> Column(Modifier.padding(padding)) {
                EmptyState(title = stringResource(R.string.feedback_no_teammates_title), body = stringResource(R.string.feedback_no_teammates_body), openSeats = 0)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                item {
                    Text(listing.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.feedback_screen_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
                items(state.teammates, key = { it.uid }) { mate ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Monogram(mate.name, mate.uid, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mate.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (mate.led) stringResource(R.string.team_lead) else mate.role,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (mate.myReview != null) {
                                Spacer(Modifier.height(4.dp))
                                Tag(stringResource(R.string.feedback_reviewed), tone = TagTone.Accent)
                            } else if (mate.left) {
                                Spacer(Modifier.height(4.dp))
                                Tag(stringResource(R.string.participation_left), tone = TagTone.Quiet)
                            }
                        }
                        OutlinedButton(onClick = { reviewing = mate }, shape = CircleShape) {
                            Text(stringResource(if (mate.myReview == null) R.string.action_review else R.string.action_edit_review))
                        }
                    }
                }
            }
        }
    }

    val mate = reviewing
    val listing = state.listing
    if (mate != null && listing != null) {
        ReviewSheet(
            name = mate.name,
            project = listing.title,
            askLeadership = mate.led,
            draftKey = DraftStore.feedback(listing.id, mate.uid),
            initialContribution = mate.myReview?.contribution,
            initialLeadership = mate.myReview?.leadership,
            initialComment = mate.myReview?.comment,
            saving = state.saving,
            onSave = { c, l, comment -> viewModel.review(mate, c, l, comment) },
            onDismiss = { reviewing = null },
        )
    }
}
