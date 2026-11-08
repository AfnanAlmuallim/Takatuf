package com.z.takatuf.notify

import android.content.Context
import android.util.Log
import com.z.takatuf.data.ApplicationRepository
import com.z.takatuf.data.AuthRepository
import com.z.takatuf.data.NotificationRepository
import com.z.takatuf.data.Session
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Whether a Takatuf screen is on display right now; set by the activity without delay. */
object AppVisibility {
    @Volatile
    var visible: Boolean = false
}

/**
 * Turns new inbox items into Android notifications as they arrive, while the app process is
 * alive (US19). On screen, the badge and inbox already show them, so they are only
 * remembered; otherwise they are posted.
 *
 * An event's owner can't see another team's applicants, so a team lead's app passes an event
 * change on to that team's applicants. Only the lead can list them; for anyone else this does
 * nothing. The id comes from the original notice, so passing it on twice sends one copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveNotifier(
    private val context: Context,
    private val auth: AuthRepository,
    private val notifications: NotificationRepository,
    private val applications: ApplicationRepository,
    private val session: Session,
    private val scope: CoroutineScope,
) {
    private val relayed = mutableSetOf<String>()

    fun start() {
        scope.launch {
            auth.uid
                .flatMapLatest { uid -> if (uid == null) emptyFlow() else notifications.observe(uid).catch { Log.w(TAG, "inbox listener stopped", it) } }
                .collect { list ->
                    val unread = list.filter { !it.read }
                    Log.d(TAG, "inbox: ${unread.size} unread, visible=${AppVisibility.visible}")
                    unread.forEach {
                        if (AppVisibility.visible) SystemNotifications.markPosted(context, it.id) else SystemNotifications.post(context, it)
                    }
                    unread.filter { it.type == NotificationType.EventChanged }.forEach { relayToApplicants(it) }
                }
        }
    }

    private suspend fun relayToApplicants(notice: AppNotification) {
        val me = session.profile ?: return
        if (!relayed.add(notice.id)) return
        val active = attempt { applications.observeForListing(notice.listingId).first() }.getOrNull().orEmpty()
            .filter { it.status.isActive }.map { it.applicantId }
        notifications.send(
            "${notice.id}_applicants", active, NotificationType.EventChanged, notice.listingId, notice.listingTitle,
            me.uid, me.displayName, NotificationTarget.Listing, notice.targetId,
        )
    }

    private companion object {
        const val TAG = "LiveNotifier"
    }
}
