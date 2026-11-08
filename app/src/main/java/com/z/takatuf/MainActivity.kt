package com.z.takatuf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.z.takatuf.data.attempt
import com.z.takatuf.notify.AppVisibility
import com.z.takatuf.notify.SystemNotifications
import kotlinx.coroutines.launch
import com.z.takatuf.data.SessionState
import com.z.takatuf.ui.TakatufApp
import com.z.takatuf.ui.theme.TakatufTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    /** Deep links that arrive while the app is already open. */
    private val newIntents = MutableSharedFlow<Intent>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val session = (application as TakatufApplication).container.session
        // Hold the splash until we know whether someone is signed in.
        splash.setKeepOnScreenCondition { session.state.value is SessionState.Loading }
        enableEdgeToEdge()
        if (savedInstanceState == null) markOpened(intent)
        setContent {
            TakatufTheme {
                TakatufApp(newIntents)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.visible = true
    }

    override fun onStop() {
        super.onStop()
        AppVisibility.visible = false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        markOpened(intent)
        newIntents.tryEmit(intent)
    }

    /** Opening an Android notification marks its inbox item read (US19). */
    private fun markOpened(intent: Intent?) {
        val id = intent?.getStringExtra(SystemNotifications.EXTRA_NOTIFICATION_ID) ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notifications = (application as TakatufApplication).container.notifications
        lifecycleScope.launch { attempt { notifications.markRead(uid, id) } }
    }
}
