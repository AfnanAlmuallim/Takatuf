package com.z.takatuf.ui.common

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat

/**
 * Chooses status bar icon colour for what is currently drawn under the bar: [light] icons over
 * the navy hero, the theme's usual icons once the hero has scrolled away. The theme's choice
 * is restored when the screen leaves.
 */
@Composable
fun StatusBarIcons(light: Boolean) {
    val activity = LocalActivity.current ?: return
    val darkTheme = isSystemInDarkTheme()
    val controller = remember(activity) { WindowCompat.getInsetsController(activity.window, activity.window.decorView) }
    SideEffect {
        // "Light status bar" means dark icons for a light background.
        controller.isAppearanceLightStatusBars = !light && !darkTheme
    }
    DisposableEffect(controller, darkTheme) {
        onDispose { controller.isAppearanceLightStatusBars = !darkTheme }
    }
}
