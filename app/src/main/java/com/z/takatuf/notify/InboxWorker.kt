package com.z.takatuf.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.z.takatuf.R
import com.z.takatuf.TakatufApplication
import com.z.takatuf.data.attempt
import com.z.takatuf.domain.Dates
import com.z.takatuf.ui.common.formatDayPlain
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * The background check (US19): shows unread inbox items the student hasn't seen yet, and
 * reminds them about milestones assigned to them that are due within a day. Runs about every
 * 15 minutes while there is a connection, which is the most Android allows.
 */
class InboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val container = (applicationContext as TakatufApplication).container
        attempt { container.notifications.unread(uid) }.getOrNull()?.forEach {
            SystemNotifications.post(applicationContext, it)
        }
        attempt { remindDueMilestones(uid) }
        return Result.success()
    }

    private suspend fun remindDueMilestones(uid: String) {
        val container = (applicationContext as TakatufApplication).container
        val now = Instant.now()
        val teams = (container.teams.observeMemberOf(uid).first() + container.listings.observeOwnedBy(uid).first())
            .filter { it.kind.recruits && !it.isCompleted && it.isOnTeam(uid) }
            .distinctBy { it.id }
        teams.forEach { team ->
            val milestones = attempt { container.workspace.observeMilestones(team.id).first() }.getOrDefault(emptyList())
            milestones
                .filter { it.assigneeId == uid && !it.isDone && it.dueDate != null }
                .filter { Duration.between(now, it.dueDate).toHours() in 0..24 }
                .forEach { m ->
                    val due = m.dueDate ?: return@forEach
                    val res = applicationContext.resources
                    SystemNotifications.postReminder(
                        applicationContext,
                        key = "milestone_${m.id}_${due.epochSecond}",
                        listingId = team.id,
                        title = res.getString(R.string.reminder_milestone_title, m.title),
                        body = res.getString(R.string.reminder_milestone_body, team.title, formatDayPlain(Dates.lastDay(due))),
                    )
                }
        }
    }

    companion object {
        private const val NAME = "inbox-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<InboxWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
