package com.z.takatuf.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.data.model.ClosedReason
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.domain.Dates
import com.z.takatuf.domain.SkillMatcher
import java.time.Instant

/**
 * One opportunity in a list. Teams lead with their seat ring, events with their date, so
 * the kind is recognisable before a word is read.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListingRow(
    listing: Listing,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    saved: Boolean = false,
    onToggleSave: ((Boolean) -> Unit)? = null,
    mySkills: Collection<String> = emptyList(),
    myApplication: ApplicationStatus? = null,
    isOwn: Boolean = false,
    startPadding: Dp = 16.dp,
) {
    val closed = listing.closedReason(now)
    val muted = closed != null && closed != ClosedReason.RegistrationClosed
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = startPadding, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(56.dp).alpha(if (muted) 0.45f else 1f), contentAlignment = Alignment.TopStart) {
            val recruitment = listing.recruitment
            val event = listing.event
            when {
                recruitment != null -> SeatRing(
                    taken = recruitment.teamSize,
                    open = recruitment.openPlaces,
                    description = seatsDescription(recruitment.teamSize, recruitment.openPlaces),
                    size = 48.dp,
                    mine = if (myApplication?.isActive == true) 1 else 0,
                )
                event != null -> DateTile(Dates.startDate(event.startDate).monthValue, Dates.startDate(event.startDate).dayOfMonth)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindLabel(listing, Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                RowStateTag(closed, myApplication, isOwn)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = listing.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            SecondaryLine(listing)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RowFacts(listing, now, closed, if (isOwn) emptyList() else mySkills)
            }
        }
        if (onToggleSave != null) {
            val haptics = LocalHapticFeedback.current
            IconToggleButton(
                checked = saved,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onToggleSave(it)
                },
            ) {
                Icon(
                    imageVector = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = stringResource(if (saved) R.string.action_unsave else R.string.action_save),
                    tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
private fun RowStateTag(closed: ClosedReason?, myApplication: ApplicationStatus?, isOwn: Boolean) {
    when {
        isOwn -> Tag(stringResource(R.string.tag_yours), tone = TagTone.Quiet)
        myApplication == ApplicationStatus.Shortlisted -> Tag(stringResource(R.string.status_shortlisted), tone = TagTone.Accent)
        myApplication == ApplicationStatus.Submitted -> Tag(stringResource(R.string.tag_applied), tone = TagTone.Accent)
        closed != null -> Tag(closedLabel(closed), tone = TagTone.Quiet)
    }
}

@Composable
fun closedLabel(reason: ClosedReason): String = stringResource(
    when (reason) {
        ClosedReason.ClosedByOwner -> R.string.closed_by_owner_short
        ClosedReason.DeadlinePassed -> R.string.closed_deadline_short
        ClosedReason.EventOver -> R.string.closed_event_over_short
        ClosedReason.Ended -> R.string.closed_ended_short
        ClosedReason.RegistrationClosed -> R.string.closed_registration_short
    },
)

@Composable
private fun SecondaryLine(listing: Listing) {
    val text = when (listing.kind) {
        ListingKind.Event -> listing.event?.organizer?.let { stringResource(R.string.by_organizer, it) }
        else -> {
            val roles = listing.recruitment?.roles.orEmpty().filter { it.openings > 0 }.map { it.title }
            when {
                roles.isEmpty() -> null
                roles.size <= 2 -> stringResource(R.string.needs_roles, roles.joinToString(", "))
                else -> pluralStringResource(R.plurals.needs_roles_more, roles.size - 2, roles.take(2).joinToString(", "), roles.size - 2)
            }
        }
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
    val eventTitle = listing.linkedEvent?.title
    if (listing.kind == ListingKind.Team && eventTitle != null && !listing.title.contains(eventTitle, ignoreCase = true)) {
        Text(
            text = stringResource(R.string.for_event, eventTitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowFacts(listing: Listing, now: Instant, closed: ClosedReason?, mySkills: Collection<String>) {
    val recruitment = listing.recruitment
    val event = listing.event
    if (recruitment != null) {
        Fact(Icons.Rounded.Schedule, stringResource(R.string.hours_per_week_short, recruitment.hoursPerWeek))
    }
    if (event != null) {
        Fact(Icons.Rounded.Groups, teamSizeText(event.teamSizeMin, event.teamSizeMax))
    }
    if (closed == null) {
        DeadlineFact(listing.closesAt.takeIf { recruitment != null || event?.registrationDeadline != null }, now)
    }
    if (recruitment != null && mySkills.isNotEmpty()) {
        val covered = SkillMatcher.covered(mySkills, listing.skillsWanted).size
        if (covered > 0) {
            Fact(
                Icons.Rounded.CheckCircle,
                pluralStringResource(R.plurals.skills_match, covered, covered),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** "Closes Oct 30", or a saffron "Closes in 2 days" when it is close. */
@Composable
fun DeadlineFact(deadline: Instant?, now: Instant) {
    if (deadline == null || deadline.isBefore(now)) return
    val daysLeft = Dates.daysLeft(deadline)
    if (daysLeft <= 3) {
        val text = when (daysLeft) {
            0L -> stringResource(R.string.closes_today)
            else -> pluralStringResource(R.plurals.closes_in_days, daysLeft.toInt(), daysLeft.toInt())
        }
        Tag(text, icon = Icons.Rounded.HourglassBottom, tone = TagTone.Attention)
    } else {
        Fact(Icons.Rounded.HourglassBottom, stringResource(R.string.closes_on, formatDay(Dates.lastDay(deadline))))
    }
}

@Composable
fun teamSizeText(min: Int, max: Int): String =
    if (min == max) pluralStringResource(R.plurals.team_size_exact, max, max)
    else stringResource(R.string.team_size_range, min, max)

/** Month and day on a quiet tile, used where an event leads a row. */
@Composable
fun DateTile(month: Int, day: Int, modifier: Modifier = Modifier) {
    val monthName = java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.SHORT, LocalLocale.current.platformLocale)
    Surface(
        modifier = modifier.size(width = 48.dp, height = 54.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(monthName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(day.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
