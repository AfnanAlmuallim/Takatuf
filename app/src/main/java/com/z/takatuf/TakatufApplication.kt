package com.z.takatuf

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.z.takatuf.notify.InboxWorker
import com.z.takatuf.notify.LiveNotifier
import com.z.takatuf.notify.SystemNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TakatufApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Debug builds made with -Ptakatuf.emulatorHost=... talk to the local emulators.
        val emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST
        if (emulatorHost.isNotEmpty()) {
            FirebaseAuth.getInstance().useEmulator(emulatorHost, 9099)
            FirebaseFirestore.getInstance().useEmulator(emulatorHost, 8080)
        }
        // After a sign-out the local copy of Firestore data is wiped before anything reads it,
        // so the next account on this phone can't see the previous one's cached records (NFR03).
        // The sign-out is repeated here: the process exits right after signing out, which can
        // be before Firebase has saved the change, and the account would otherwise come back.
        val session = getSharedPreferences(SESSION_PREFS, MODE_PRIVATE)
        if (session.getBoolean(KEY_CLEAR_CACHE, false)) {
            FirebaseAuth.getInstance().signOut()
            FirebaseFirestore.getInstance().clearPersistence()
            session.edit(commit = true) { remove(KEY_CLEAR_CACHE) }
        }
        container = AppContainer(this)
        // Inbox items become Android notifications: live while running, otherwise from a periodic check (US19).
        SystemNotifications.createChannels(this)
        LiveNotifier(this, container.authRepository, container.notifications, container.applications, container.session, CoroutineScope(SupervisorJob() + Dispatchers.Main)).start()
        InboxWorker.schedule(this)
    }

    /**
     * Signs out and starts the app afresh: drafts, shown notifications and (on the restart) the
     * local Firestore cache are cleared, so nothing of this account stays on the phone.
     */
    fun signOutAndRestart(activity: Activity) {
        container.drafts.clearAll()
        NotificationManagerCompat.from(this).cancelAll()
        getSharedPreferences(SESSION_PREFS, MODE_PRIVATE).edit(commit = true) { putBoolean(KEY_CLEAR_CACHE, true) }
        container.authRepository.signOut()
        activity.startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }

    private companion object {
        const val SESSION_PREFS = "session"
        const val KEY_CLEAR_CACHE = "clear_cache"
    }
}
