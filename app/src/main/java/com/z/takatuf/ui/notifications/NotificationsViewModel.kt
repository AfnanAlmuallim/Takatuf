package com.z.takatuf.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.takatuf.data.AppError
import com.z.takatuf.data.NotificationRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.data.toAppError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val items: List<AppNotification> = emptyList(),
) {
    val unread: Int get() = items.count { !it.read }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModel(
    private val session: Session,
    private val notifications: NotificationRepository,
) : ViewModel() {

    private val uid = session.uid.orEmpty()
    private val retry = MutableStateFlow(0)

    val state: StateFlow<NotificationsUiState> = retry.flatMapLatest {
        notifications.observe(uid)
            .map { NotificationsUiState(loading = false, items = it) }
            .onStart { emit(NotificationsUiState()) }
            .catch { emit(NotificationsUiState(loading = false, error = it.toAppError())) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    fun retry() = retry.update { it + 1 }

    /** Opening a notification marks it read; the screen then goes to the related item. */
    fun open(item: AppNotification) {
        if (!item.read) viewModelScope.launch { attempt { notifications.markRead(uid, item.id) } }
    }

    fun markAllRead() {
        val ids = state.value.items.filterNot { it.read }.map { it.id }
        viewModelScope.launch { attempt { notifications.markAllRead(uid, ids) } }
    }

    fun delete(item: AppNotification) {
        viewModelScope.launch { attempt { notifications.delete(uid, item.id) } }
    }
}
