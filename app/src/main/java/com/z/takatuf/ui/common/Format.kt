package com.z.takatuf.ui.common

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.z.takatuf.R
import com.z.takatuf.data.model.EventType
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.SkillLevel
import com.z.takatuf.data.model.Weekday
import com.z.takatuf.domain.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Formatter
import java.util.Locale

@Composable
@ReadOnlyComposable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/** "Oct 30", or "Oct 30, 2027" when the year differs from this one. */
@Composable
@ReadOnlyComposable
fun formatDay(date: LocalDate): String {
    val locale = locale()
    val skeleton = if (date.year == LocalDate.now().year) "MMMd" else "yMMMd"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
}

/** "Oct 12 – 14" style range for an event running from [start] until the exclusive [end]. */
fun formatEventRange(context: Context, start: Instant, end: Instant): String {
    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    val startMillis = start.toEpochMilli()
    // Android treats a range ending exactly at midnight as ending the previous day.
    val endMillis = maxOf(end.toEpochMilli(), startMillis)
    return DateUtils.formatDateRange(context, Formatter(StringBuilder(), Locale.getDefault()), startMillis, endMillis, flags, "UTC").toString()
}

@Composable
@ReadOnlyComposable
fun relativeTime(instant: Instant?): String {
    if (instant == null) return stringResource(R.string.time_just_now)
    val now = System.currentTimeMillis()
    val millis = minOf(instant.toEpochMilli(), now)
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.time_just_now)
    return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
@ReadOnlyComposable
fun kindLabel(listing: Listing): String = when (listing.kind) {
    ListingKind.Project -> stringResource(R.string.kind_project)
    ListingKind.Team -> stringResource(R.string.kind_team)
    ListingKind.Event -> eventTypeLabel(listing.event?.type ?: EventType.Competition)
}

@Composable
@ReadOnlyComposable
fun eventTypeLabel(type: EventType): String = when (type) {
    EventType.Competition -> stringResource(R.string.event_type_competition)
    EventType.Hackathon -> stringResource(R.string.event_type_hackathon)
}

@Composable
@ReadOnlyComposable
fun skillLevelLabel(level: SkillLevel): String = when (level) {
    SkillLevel.Beginner -> stringResource(R.string.level_beginner)
    SkillLevel.Intermediate -> stringResource(R.string.level_intermediate)
    SkillLevel.Advanced -> stringResource(R.string.level_advanced)
}

@Composable
@ReadOnlyComposable
fun weekdayShort(day: Weekday): String = when (day) {
    Weekday.Sun -> stringResource(R.string.day_sun)
    Weekday.Mon -> stringResource(R.string.day_mon)
    Weekday.Tue -> stringResource(R.string.day_tue)
    Weekday.Wed -> stringResource(R.string.day_wed)
    Weekday.Thu -> stringResource(R.string.day_thu)
    Weekday.Fri -> stringResource(R.string.day_fri)
    Weekday.Sat -> stringResource(R.string.day_sat)
}

/** "Sun–Thu" for a contiguous run of days, otherwise "Sun, Tue, Thu". */
@Composable
fun formatDays(days: Set<Weekday>): String {
    val ordered = Weekday.entries.filter { it in days }
    if (ordered.isEmpty()) return ""
    if (ordered.size == 7) return stringResource(R.string.days_every_day)
    val contiguous = ordered.size >= 3 && ordered.zipWithNext().all { (a, b) -> b.ordinal == a.ordinal + 1 }
    return if (contiguous) {
        stringResource(R.string.days_range, weekdayShort(ordered.first()), weekdayShort(ordered.last()))
    } else {
        ordered.map { weekdayShort(it) }.joinToString(", ")
    }
}

/** Last calendar day on which applications are accepted, from an exclusive deadline. */
fun lastDayOf(deadline: Instant): LocalDate = Dates.lastDay(deadline)
