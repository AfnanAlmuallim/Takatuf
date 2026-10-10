package com.z.takatuf.ui

import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.z.takatuf.TakatufApplication
import com.z.takatuf.data.SessionState
import com.z.takatuf.ui.auth.AuthScreen
import com.z.takatuf.ui.navigation.TakatufNavHost
import com.z.takatuf.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.Flow

/** Chooses between signing in, setting up a passport and the app itself. */
@Composable
fun TakatufApp(newIntents: Flow<Intent>) {
    val container = (LocalContext.current.applicationContext as TakatufApplication).container
    val session by container.session.state.collectAsStateWithLifecycle()
    val screen = when (session) {
        SessionState.Loading -> Screen.Loading
        SessionState.SignedOut -> Screen.Auth
        is SessionState.Onboarding -> Screen.Onboarding
        is SessionState.Ready -> Screen.Main
    }
    Crossfade(targetState = screen, label = "session") { target ->
        when (target) {
            Screen.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            Screen.Auth -> AuthScreen()
            Screen.Onboarding -> (session as? SessionState.Onboarding)?.let { OnboardingScreen(it) }
                ?: Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            Screen.Main -> TakatufNavHost(newIntents)
        }
    }
}

private enum class Screen { Loading, Auth, Onboarding, Main }
