package com.z.takatuf

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        setContent {
            TakatufTheme {
                TakatufApp(newIntents)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        newIntents.tryEmit(intent)
    }
}
