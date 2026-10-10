package com.z.takatuf.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The three blues are sampled straight from the logo's three figures. They are used as
 * information, never decoration:
 *  - Ink (the top figure) is identity and text: headings, taken seats, the detail hero.
 *  - Royal (the left figure) is action: buttons, links, selected state, your own seat.
 *  - Sky (the right figure) is openness: open seats on a team, highlights.
 * Saffron is the only warm colour and is reserved for time-sensitive attention
 * (closing soon, new applicants).
 */
object Brand {
    val Ink = Color(0xFF0B2D64)
    val Royal = Color(0xFF0B5DC3)
    val Sky = Color(0xFF3188E6)
    val Saffron = Color(0xFFF0A92E)
    val Paper = Color(0xFFFFFFFF)
    val Night = Color(0xFF061229)
}

internal val LightColors = lightColorScheme(
    primary = Brand.Royal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FB),
    onPrimaryContainer = Color(0xFF062A5E),
    inversePrimary = Color(0xFF9EC6FA),
    secondary = Brand.Ink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EBF6),
    onSecondaryContainer = Brand.Ink,
    tertiary = Color(0xFF7A4E00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDE7C2),
    onTertiaryContainer = Color(0xFF3B2600),
    background = Brand.Paper,
    onBackground = Color(0xFF0D1F40),
    surface = Brand.Paper,
    onSurface = Color(0xFF0D1F40),
    surfaceVariant = Color(0xFFE8EDF5),
    onSurfaceVariant = Color(0xFF4A5A78),
    surfaceTint = Brand.Royal,
    inverseSurface = Brand.Ink,
    inverseOnSurface = Color(0xFFEAF1FB),
    error = Color(0xFFC42B1C),
    onError = Color.White,
    errorContainer = Color(0xFFFDE7E4),
    onErrorContainer = Color(0xFF5F1109),
    outline = Color(0xFF8391AC),
    outlineVariant = Color(0xFFDCE3EE),
    scrim = Color(0xFF020814),
    surfaceBright = Brand.Paper,
    surfaceDim = Color(0xFFDCE2EC),
    surfaceContainerLowest = Brand.Paper,
    surfaceContainerLow = Color(0xFFF5F8FC),
    surfaceContainer = Color(0xFFEFF3F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    surfaceContainerHighest = Color(0xFFE2E8F2),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF8EC0FA),
    onPrimary = Color(0xFF03214B),
    primaryContainer = Color(0xFF0E4A99),
    onPrimaryContainer = Color(0xFFDCE8FB),
    inversePrimary = Brand.Royal,
    secondary = Color(0xFFC3D4F0),
    onSecondary = Brand.Ink,
    secondaryContainer = Color(0xFF1A3563),
    onSecondaryContainer = Color(0xFFDCE6F8),
    tertiary = Color(0xFFF6C46E),
    onTertiary = Color(0xFF3B2600),
    tertiaryContainer = Color(0xFF5A3A00),
    onTertiaryContainer = Color(0xFFFDE7C2),
    background = Brand.Night,
    onBackground = Color(0xFFE6EDF8),
    surface = Brand.Night,
    onSurface = Color(0xFFE6EDF8),
    surfaceVariant = Color(0xFF1C2D4D),
    onSurfaceVariant = Color(0xFFA9B6CE),
    surfaceTint = Color(0xFF8EC0FA),
    inverseSurface = Color(0xFFE6EDF8),
    inverseOnSurface = Brand.Ink,
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF690003),
    errorContainer = Color(0xFF8C1D12),
    onErrorContainer = Color(0xFFFFDAD4),
    outline = Color(0xFF6F7E9A),
    outlineVariant = Color(0xFF243555),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF1F3257),
    surfaceDim = Brand.Night,
    surfaceContainerLowest = Color(0xFF040D1F),
    surfaceContainerLow = Color(0xFF0A1831),
    surfaceContainer = Color(0xFF0E1D3A),
    surfaceContainerHigh = Color(0xFF132444),
    surfaceContainerHighest = Color(0xFF192B4E),
)

/** Brand roles that Material's scheme has no slot for. */
@Immutable
data class TakatufColors(
    /** The navy block behind a listing's title on its detail screen. */
    val hero: Color,
    val onHero: Color,
    val onHeroVariant: Color,
    val seatTaken: Color,
    val seatOpen: Color,
    val seatMine: Color,
    val seatTakenOnHero: Color,
    val seatOpenOnHero: Color,
    val attention: Color,
    val onAttention: Color,
    val kindProject: Color,
    val kindTeam: Color,
    val kindEvent: Color,
)

internal val LightTakatufColors = TakatufColors(
    hero = Brand.Ink,
    onHero = Color.White,
    onHeroVariant = Color(0xFFB9CDEE),
    seatTaken = Brand.Ink,
    seatOpen = Brand.Sky,
    seatMine = Brand.Royal,
    seatTakenOnHero = Color.White,
    seatOpenOnHero = Color(0xFF5EA4F0),
    attention = Brand.Saffron,
    onAttention = Brand.Ink,
    kindProject = Brand.Royal,
    kindTeam = Brand.Sky,
    kindEvent = Brand.Ink,
)

internal val DarkTakatufColors = TakatufColors(
    hero = Color(0xFF0D2A5C),
    onHero = Color.White,
    onHeroVariant = Color(0xFFA9C1E8),
    seatTaken = Color(0xFFDCE6F8),
    seatOpen = Color(0xFF5EA4F0),
    seatMine = Color(0xFF8EC0FA),
    seatTakenOnHero = Color.White,
    seatOpenOnHero = Color(0xFF6FB0F5),
    attention = Color(0xFFF6B84C),
    onAttention = Brand.Ink,
    kindProject = Color(0xFF8EC0FA),
    kindTeam = Color(0xFF6FB0F5),
    kindEvent = Color(0xFFC3D4F0),
)

val LocalTakatufColors = staticCompositionLocalOf { LightTakatufColors }
