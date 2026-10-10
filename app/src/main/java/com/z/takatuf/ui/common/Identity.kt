package com.z.takatuf.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.z.takatuf.data.model.EventType
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.initials
import com.z.takatuf.ui.theme.Alexandria
import com.z.takatuf.ui.theme.Brand
import com.z.takatuf.ui.theme.TakatufTheme

/**
 * Students are shown by their initials rather than photos, so applicants are weighed on their
 * skills first. The tint is stable per person and drawn from the brand palette.
 */
@Composable
fun Monogram(
    name: String,
    seed: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val (background, foreground) = monogramColors[Math.floorMod(seed.hashCode(), monogramColors.size)]
    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.initials(),
            color = foreground,
            fontFamily = Alexandria,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.36f).sp,
            maxLines = 1,
        )
    }
}

private val monogramColors = listOf(
    Brand.Ink to Color.White,
    Brand.Royal to Color.White,
    Color(0xFFDCE8FB) to Brand.Ink,
    Color(0xFFFDE7C2) to Color(0xFF4A3000),
    Color(0xFF1F6FD1) to Color.White,
    Color(0xFFE4EBF6) to Brand.Ink,
)

fun kindIcon(listing: Listing): ImageVector = when (listing.kind) {
    ListingKind.Project -> Icons.Rounded.Lightbulb
    ListingKind.Team -> Icons.Rounded.Groups
    ListingKind.Event -> if (listing.event?.type == EventType.Hackathon) Icons.Rounded.Bolt else Icons.Rounded.EmojiEvents
}

@Composable
fun kindColor(kind: ListingKind): Color = when (kind) {
    ListingKind.Project -> TakatufTheme.colors.kindProject
    ListingKind.Team -> TakatufTheme.colors.kindTeam
    ListingKind.Event -> TakatufTheme.colors.kindEvent
}

/** The icon carries the kind's colour; the words stay in a readable neutral. */
@Composable
fun KindLabel(
    listing: Listing,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconTint: Color = kindColor(listing.kind),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(kindIcon(listing), contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Text(kindLabel(listing), style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}
