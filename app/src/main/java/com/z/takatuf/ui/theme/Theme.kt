package com.z.takatuf.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Radii follow hierarchy rather than one value everywhere: controls are pills, inputs and
 * small containers are gently rounded, sheets and the detail hero get the largest curve.
 */
val TakatufShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** True when the user turned animations off in system settings; one-off motion is skipped. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun TakatufTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val reduceMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalTakatufColors provides if (darkTheme) DarkTakatufColors else LightTakatufColors,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = TakatufTypography,
            shapes = TakatufShapes,
            content = content,
        )
    }
}

object TakatufTheme {
    val colors: TakatufColors
        @Composable @ReadOnlyComposable get() = LocalTakatufColors.current
}
