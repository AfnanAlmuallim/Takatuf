package com.z.takatuf.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.z.takatuf.MainActivity
import com.z.takatuf.R
import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.ui.navigation.DeepLinkScheme
import com.z.takatuf.ui.notifications.title

/**
 * Shows inbox items as Android notifications (US19). Without a paid Firebase plan there is no
 * server to push them, so the app shows them itself: live while it is running, and from a
 * periodic background check otherwise. Each item is shown once per device.
 */
object SystemNotifications {

    private const val CHANNEL_UPDATES = "updates"
    private const val CHANNEL_REMINDERS = "reminders"
    private const val PREFS = "posted_notifications"
    private const val KEY_POSTED = "ids"
    private const val MAX_REMEMBERED = 300

    /** Set on the launch intent so opening a notification also marks it read. */
    const val EXTRA_NOTIFICATION_ID = "com.z.takatuf.NOTIFICATION_ID"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_updates_body)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_reminders_body)
            },
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun wasPosted(context: Context, key: String): Boolean = key in posted(context)

    /** Remembers [key] without showing anything: the student already saw it in the app. */
    fun markPosted(context: Context, key: String) {
        val current = posted(context)
        if (key in current) return
        val next = (current + key).toList().takeLast(MAX_REMEMBERED).toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putStringSet(KEY_POSTED, next) }
    }

    private fun posted(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_POSTED, emptySet()).orEmpty()

    /** Shows one inbox item; tapping it opens the item it is about and marks it read. */
    fun post(context: Context, notification: AppNotification) {
        if (wasPosted(context, notification.id)) return
        markPosted(context, notification.id)
        if (!canPost(context)) return
        val intent = Intent(Intent.ACTION_VIEW, linkFor(notification).toUri(), context, MainActivity::class.java)
            .putExtra(EXTRA_NOTIFICATION_ID, notification.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val text = notification.note.ifBlank { notification.listingTitle }
        show(context, notification.id.hashCode(), CHANNEL_UPDATES, notification.title(context.resources), text, intent)
    }

    /** A reminder that a milestone assigned to the student is due soon. */
    fun postReminder(context: Context, key: String, listingId: String, title: String, body: String) {
        if (wasPosted(context, key)) return
        markPosted(context, key)
        if (!canPost(context)) return
        val intent = Intent(Intent.ACTION_VIEW, "${DeepLinkScheme}team/$listingId".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        show(context, key.hashCode(), CHANNEL_REMINDERS, title, body, intent)
    }

    private fun show(context: Context, id: Int, channel: String, title: String, text: String, intent: Intent) {
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val built = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_takatuf)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, built)
        } catch (_: SecurityException) {
            // The permission was withdrawn between the check and the post.
        }
    }

    /** The deep link for what a notification is about, matching the app's navigation. */
    fun linkFor(n: AppNotification): String = DeepLinkScheme + when {
        n.listingId.isBlank() && n.target.needsListing -> "passport"
        else -> linkPath(n)
    }

    private fun linkPath(n: AppNotification): String = when (n.target) {
        NotificationTarget.Listing -> "listing/${n.listingId}"
        NotificationTarget.Workspace -> "team/${n.listingId}"
        NotificationTarget.Applicants -> "applicants/${n.listingId}"
        NotificationTarget.Applicant -> n.targetId.substringAfter('/', "").let {
            if (it.isBlank()) "applicants/${n.listingId}" else "applicant/${n.listingId}/$it"
        }
        NotificationTarget.Activity -> "activity"
        NotificationTarget.Teams -> "teams"
        NotificationTarget.Passport -> "passport"
        NotificationTarget.Reports -> if (n.targetId.isBlank()) "inbox" else "report/${n.targetId}"
    }
}
