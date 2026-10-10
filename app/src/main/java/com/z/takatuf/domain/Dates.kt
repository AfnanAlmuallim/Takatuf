package com.z.takatuf.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Date-only values (deadlines, event days) are stored as UTC midnights so they read the same
 * on every phone. A start is the first instant of its day; an end or deadline is the first
 * instant *after* its last day, so "closes Oct 30" stays open all of Oct 30 and the security
 * rules can simply compare `request.time < deadline`.
 */
object Dates {
    private val zone = ZoneOffset.UTC

    fun startOf(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    fun endOf(date: LocalDate): Instant = date.plusDays(1).atStartOfDay(zone).toInstant()

    fun startDate(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** The last calendar day covered by an exclusive end instant. */
    fun lastDay(end: Instant): LocalDate = end.minusMillis(1).atZone(zone).toLocalDate()

    /** Material date pickers report the chosen day as UTC-midnight milliseconds. */
    fun fromPicker(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun toPicker(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun today(): LocalDate = LocalDate.now()

    /** Whole days from today until the last day of [end]; 0 means it closes today. */
    fun daysLeft(end: Instant, today: LocalDate = today()): Long = ChronoUnit.DAYS.between(today, lastDay(end))
}
