package com.z.takatuf.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WorkHistory
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.ApplicationStatus
import com.z.takatuf.domain.ApplicantFit
import com.z.takatuf.domain.Dates
import com.z.takatuf.domain.Eligibility
import com.z.takatuf.domain.FitBand
import java.time.Instant

/**
 * An application's state in words. [forOwner] switches wording between the applicant
 * ("Offer, answer by Oct 14") and the lead ("Offer sent").
 */
@Composable
fun ApplicationStatusTag(application: Application, forOwner: Boolean, modifier: Modifier = Modifier) {
    val now = Instant.now()
    when (application.status) {
        ApplicationStatus.Submitted -> Tag(stringResource(R.string.status_under_review), modifier, Icons.Rounded.Schedule)
        ApplicationStatus.Shortlisted -> Tag(stringResource(R.string.status_shortlisted), modifier, Icons.Rounded.Star, TagTone.Accent)
        ApplicationStatus.Offered -> when {
            application.offerExpired(now) -> Tag(stringResource(R.string.status_offer_expired), modifier, Icons.Rounded.HourglassBottom, TagTone.Quiet)
            forOwner -> Tag(stringResource(R.string.status_offer_sent), modifier, Icons.Rounded.MarkEmailUnread, TagTone.Accent)
            else -> Tag(offerDeadlineText(application.offerExpiresAt), modifier, Icons.Rounded.HourglassBottom, TagTone.Attention)
        }
        ApplicationStatus.Reconfirm -> if (forOwner) {
            Tag(stringResource(R.string.status_reconfirm_owner), modifier, Icons.AutoMirrored.Rounded.HelpOutline)
        } else {
            Tag(stringResource(R.string.status_reconfirm_applicant), modifier, Icons.AutoMirrored.Rounded.HelpOutline, TagTone.Attention)
        }
        ApplicationStatus.Accepted -> Tag(stringResource(R.string.status_joined), modifier, Icons.Rounded.CheckCircle, TagTone.Accent)
        ApplicationStatus.Declined -> Tag(stringResource(R.string.status_declined), modifier, Icons.Rounded.Close, TagTone.Quiet)
        ApplicationStatus.Withdrawn -> Tag(stringResource(R.string.status_withdrawn), modifier, Icons.AutoMirrored.Rounded.Undo, TagTone.Quiet)
        ApplicationStatus.Left -> Tag(stringResource(R.string.status_left), modifier, Icons.AutoMirrored.Rounded.Logout, TagTone.Quiet)
        ApplicationStatus.Removed -> Tag(stringResource(R.string.status_removed), modifier, Icons.Rounded.Block, TagTone.Quiet)
        ApplicationStatus.Closed -> Tag(stringResource(R.string.status_closed), modifier, Icons.Rounded.TaskAlt, TagTone.Quiet)
        ApplicationStatus.NotSelected -> Tag(stringResource(R.string.status_not_selected), modifier, Icons.Rounded.Close, TagTone.Quiet)
    }
}

/** "Answer today", "Answer by Oct 14". */
@Composable
fun offerDeadlineText(expiresAt: Instant?): String {
    if (expiresAt == null) return stringResource(R.string.status_offer_received)
    val days = Dates.daysLeft(expiresAt)
    return when {
        days <= 0L -> stringResource(R.string.offer_answer_today)
        else -> stringResource(R.string.offer_answer_by, formatDay(Dates.lastDay(expiresAt)))
    }
}

/** One label for how well an applicant fits, with the reason it can't be judged when relevant. */
@Composable
fun FitBadge(fit: ApplicantFit, modifier: Modifier = Modifier) {
    when {
        fit.eligibility == Eligibility.Ineligible ->
            Tag(stringResource(R.string.fit_not_eligible), modifier, Icons.Rounded.Block, TagTone.Quiet)
        fit.limitedInfo ->
            Tag(stringResource(R.string.fit_limited_info), modifier, Icons.Rounded.Lock, TagTone.Quiet)
        fit.band == FitBand.Strong ->
            Tag(stringResource(R.string.fit_strong), modifier, Icons.Rounded.TaskAlt, TagTone.Accent)
        fit.band == FitBand.Good ->
            Tag(stringResource(R.string.fit_good), modifier, Icons.Rounded.TaskAlt)
        else ->
            Tag(stringResource(R.string.fit_partial), modifier, tone = TagTone.Quiet)
    }
}

/** The factors behind a fit, as short facts an owner can scan (US09, NFR10). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FitFacts(fit: ApplicantFit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val roleSkills = fit.skillsCovered.size + fit.skillsMissing.size
        if (!fit.limitedInfo && roleSkills > 0) {
            Fact(
                Icons.Rounded.CheckCircle,
                pluralStringResource(R.plurals.fit_skills, roleSkills, fit.skillsCovered.size, roleSkills),
                color = if (fit.skillsCovered.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (fit.hoursAsked > 0) {
            Fact(Icons.Rounded.Schedule, stringResource(R.string.fit_hours, fit.hoursOffered, fit.hoursAsked))
        }
        if (fit.relevantExperience > 0) {
            Fact(Icons.Rounded.WorkHistory, pluralStringResource(R.plurals.fit_experience, fit.relevantExperience, fit.relevantExperience))
        }
        if (fit.eligibility == Eligibility.Unknown) {
            Fact(Icons.AutoMirrored.Rounded.HelpOutline, stringResource(R.string.fit_check_eligibility))
        }
    }
}
