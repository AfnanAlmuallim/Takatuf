package com.z.takatuf.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.z.takatuf.ui.theme.LocalReduceMotion
import com.z.takatuf.ui.theme.TakatufTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The team drawn as seats around a table, the way the logo arranges its figures in a circle.
 * Filled seats are members the team already has, an outlined seat is a place still open, and
 * [mine] marks the seat the viewer has applied for.
 *
 * Beyond [MaxSeats] the ring keeps its proportions so it stays legible; the exact numbers are
 * always given in text next to it and in [description].
 */
@Composable
fun SeatRing(
    taken: Int,
    open: Int,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    mine: Int = 0,
    takenColor: Color = TakatufTheme.colors.seatTaken,
    openColor: Color = TakatufTheme.colors.seatOpen,
    mineColor: Color = TakatufTheme.colors.seatMine,
    /** The faint circle the seats sit on: the table. */
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    animate: Boolean = false,
    center: @Composable (BoxScope.() -> Unit)? = null,
) {
    val reduceMotion = LocalReduceMotion.current
    val seats = remember(taken, open, mine) { seatPlan(taken, open, mine) }
    val progress = remember(seats) { Animatable(if (animate && !reduceMotion) 0f else seats.size.toFloat()) }
    if (animate && !reduceMotion) {
        LaunchedEffect(seats) {
            progress.animateTo(
                targetValue = seats.size.toFloat(),
                animationSpec = tween(durationMillis = 120 * seats.size + 240, easing = FastOutSlowInEasing),
            )
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            drawSeats(seats, progress.value, takenColor, openColor, mineColor, trackColor)
        }
        center?.invoke(this)
    }
}

private enum class Seat { Taken, Mine, Open }

const val MaxSeats = 12

private fun seatPlan(taken: Int, open: Int, mine: Int): List<Seat> {
    val total = (taken + open).coerceAtLeast(1)
    if (total <= MaxSeats) {
        val mineSeats = mine.coerceIn(0, open)
        return List(taken.coerceAtLeast(0)) { Seat.Taken } +
            List(mineSeats) { Seat.Mine } +
            List((open - mineSeats).coerceAtLeast(0)) { Seat.Open }
    }
    // Scale down while keeping at least one seat of each kind that exists.
    val scaledTaken = ((taken.toFloat() / total) * MaxSeats).toInt().coerceIn(if (taken > 0) 1 else 0, MaxSeats - 1)
    val scaledOpen = MaxSeats - scaledTaken
    val mineSeats = mine.coerceIn(0, scaledOpen)
    return List(scaledTaken) { Seat.Taken } + List(mineSeats) { Seat.Mine } + List(scaledOpen - mineSeats) { Seat.Open }
}

private fun DrawScope.drawSeats(
    seats: List<Seat>,
    progress: Float,
    takenColor: Color,
    openColor: Color,
    mineColor: Color,
    trackColor: Color,
) {
    val n = seats.size
    val outer = size.minDimension / 2f
    // Seat diameter: large enough to read, small enough that neighbours never touch.
    val gapFactor = 0.62f
    var seatRadius = min(outer * 0.30f, (PI.toFloat() * outer / n) * gapFactor / (1f + PI.toFloat() * gapFactor / n))
    if (n == 1) seatRadius = outer * 0.34f
    val ringRadius = outer - seatRadius
    val stroke = (seatRadius * 0.28f).coerceAtLeast(1.2.dp.toPx())
    val centerPoint = Offset(size.width / 2f, size.height / 2f)
    if (n > 1) {
        drawCircle(trackColor, radius = ringRadius, center = centerPoint, style = Stroke(width = 1.dp.toPx()))
    }
    seats.forEachIndexed { index, seat ->
        val appear = (progress - index).coerceIn(0f, 1f)
        if (appear <= 0f) return@forEachIndexed
        val angle = -PI / 2 + 2 * PI * index / n
        val position = if (n == 1) centerPoint else Offset(
            x = centerPoint.x + ringRadius * cos(angle).toFloat(),
            y = centerPoint.y + ringRadius * sin(angle).toFloat(),
        )
        val r = seatRadius * (0.6f + 0.4f * appear)
        when (seat) {
            Seat.Taken -> drawCircle(takenColor, radius = r, center = position, alpha = appear)
            Seat.Mine -> {
                drawCircle(mineColor, radius = r, center = position, alpha = appear)
                drawCircle(Color.White, radius = r * 0.36f, center = position, alpha = appear)
            }
            Seat.Open -> drawCircle(
                openColor,
                radius = r - stroke / 2f,
                center = position,
                alpha = appear,
                style = Stroke(width = stroke),
            )
        }
    }
}
