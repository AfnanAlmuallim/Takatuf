package com.z.takatuf.ui.records

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.data.model.Evidence
import com.z.takatuf.data.model.Feedback
import com.z.takatuf.data.model.FeedbackSummary
import com.z.takatuf.data.model.Participation
import com.z.takatuf.data.model.ParticipationStatus
import com.z.takatuf.domain.Dates
import com.z.takatuf.ui.common.ChoiceChip
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.Tag
import com.z.takatuf.ui.common.TagTone
import com.z.takatuf.ui.common.formatDay
import com.z.takatuf.ui.common.relativeTime
import com.z.takatuf.ui.team.hostOf
import com.z.takatuf.ui.team.rememberOpenUrl

/** How strongly a contribution is backed up, in words (US17: evidence versus claims). */
@Composable
fun EvidenceTag(evidence: Evidence, confirmedBy: String?, modifier: Modifier = Modifier) {
    when (evidence) {
        Evidence.Confirmed -> Tag(
            stringResource(R.string.evidence_confirmed, confirmedBy.orEmpty()),
            modifier,
            Icons.Rounded.Verified,
            TagTone.Accent,
        )
        Evidence.Linked -> Tag(stringResource(R.string.evidence_linked), modifier, Icons.Rounded.Link)
        Evidence.SelfReported -> Tag(stringResource(R.string.evidence_self_reported), modifier, Icons.Rounded.RecordVoiceOver, TagTone.Quiet)
    }
}

@Composable
fun ParticipationStatusTag(status: ParticipationStatus, modifier: Modifier = Modifier) {
    when (status) {
        ParticipationStatus.Active -> Tag(stringResource(R.string.participation_active), modifier, Icons.Rounded.PlayCircle, TagTone.Accent)
        ParticipationStatus.Completed -> Tag(stringResource(R.string.participation_completed), modifier, Icons.Rounded.TaskAlt, TagTone.Accent)
        ParticipationStatus.Left -> Tag(stringResource(R.string.participation_left), modifier, Icons.AutoMirrored.Rounded.Logout, TagTone.Quiet)
        ParticipationStatus.Removed -> Tag(stringResource(R.string.participation_removed), modifier, Icons.Rounded.Block, TagTone.Quiet)
    }
}

/** A rating as five stars plus the number in words, so it never relies on the icons alone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingDisplay(label: String, value: Double, modifier: Modifier = Modifier) {
    val text = String.format(LocalConfiguration.current.locales[0], "%.1f", value)
    val description = stringResource(R.string.rating_a11y, label, text)
    // The label keeps its words whole at any text size; the stars move to a new line if needed.
    FlowRow(modifier.clearAndSetSemantics { contentDescription = description }, itemVerticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, softWrap = false, modifier = Modifier.widthIn(min = 110.dp).padding(end = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { i ->
                Icon(
                    if (value >= i + 0.75) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.rating_out_of_five, text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false)
        }
    }
}

/** Five labelled choices; the label says what each number means. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RatingPicker(label: String, value: Int?, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val meanings = listOf(R.string.rating_1, R.string.rating_2, R.string.rating_3, R.string.rating_4, R.string.rating_5)
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n ->
                ChoiceChip(
                    selected = value == n,
                    onClick = { onChange(n) },
                    label = { Text(stringResource(R.string.rating_choice, n, stringResource(meanings[n - 1]))) },
                )
            }
        }
    }
}

@Composable
fun ContributionRow(
    contribution: Contribution,
    modifier: Modifier = Modifier,
    showProject: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
) {
    val openUrl = rememberOpenUrl()
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(contribution.title, style = MaterialTheme.typography.titleSmall)
            if (showProject) {
                Text(contribution.listingTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (contribution.detail.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(contribution.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EvidenceTag(contribution.evidence, contribution.confirmedByName)
                if (contribution.pending) {
                    Spacer(Modifier.width(6.dp))
                    Tag(stringResource(R.string.pending_send), tone = TagTone.Quiet)
                }
            }
            contribution.links.forEach { link ->
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .clickable { openUrl(link) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(hostOf(link), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        trailing?.invoke()
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit_contribution, contribution.title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One team in a student's record: their role, how it ended, and what they contributed. */
@Composable
fun ProjectRecordCard(
    record: Participation,
    contributions: List<Contribution>,
    onAddContribution: (() -> Unit)?,
    onEditContribution: ((Contribution) -> Unit)?,
    onReview: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
            Text(record.listingTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 8.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                periodText(record),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ParticipationStatusTag(record.status)
                Spacer(Modifier.width(6.dp))
                Tag(if (record.wasLead) stringResource(R.string.record_led_team) else record.role, tone = TagTone.Quiet)
            }
            if (contributions.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.record_no_contributions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            contributions.forEach { c ->
                ContributionRow(c, onEdit = onEditContribution?.let { edit -> { edit(c) } })
            }
            Row {
                if (onAddContribution != null) {
                    TextButton(onClick = onAddContribution) { Text(stringResource(R.string.action_add_contribution)) }
                }
                if (onReview != null && record.status.ended) {
                    TextButton(onClick = onReview) { Text(stringResource(R.string.action_review_teammates)) }
                }
            }
        }
    }
}

@Composable
private fun periodText(record: Participation): String {
    val from = record.startedAt?.let { formatDay(Dates.startDate(it)) }
    val to = record.endedAt?.let { formatDay(Dates.startDate(it)) }
    return when {
        from != null && to != null -> stringResource(R.string.record_period, from, to)
        from != null -> stringResource(R.string.record_since, from)
        else -> ""
    }
}

/** Ratings from teammates. No reviews reads as "nothing yet", never as a low score (US18). */
@Composable
fun FeedbackSummaryBlock(summary: FeedbackSummary, name: String, isMine: Boolean) {
    if (!summary.hasReviews) {
        Text(
            if (isMine) stringResource(R.string.feedback_none_mine) else stringResource(R.string.feedback_none, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            pluralStringResource(R.plurals.feedback_count, summary.count, summary.count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        summary.contributionAverage?.let { RatingDisplay(stringResource(R.string.feedback_contribution), it) }
        summary.leadershipAverage?.let { RatingDisplay(stringResource(R.string.feedback_leadership), it) }
        if (summary.disputed > 0) {
            Text(
                pluralStringResource(R.plurals.feedback_disputed_count, summary.disputed, summary.disputed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun FeedbackRow(feedback: Feedback, isMine: Boolean, onDispute: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        if (feedback.hidden) {
            if (isMine) {
                Text(
                    stringResource(R.string.feedback_hidden_by_moderators, feedback.moderationNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.feedback_from, feedback.reviewerName, feedback.listingTitle),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (feedback.disputed) Tag(stringResource(R.string.feedback_disputed), icon = Icons.Rounded.Flag, tone = TagTone.Attention)
        }
        Spacer(Modifier.height(6.dp))
        RatingDisplay(stringResource(R.string.feedback_contribution), feedback.contribution.toDouble())
        feedback.leadership?.let { RatingDisplay(stringResource(R.string.feedback_leadership), it.toDouble()) }
        if (feedback.comment.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(feedback.comment, style = MaterialTheme.typography.bodyMedium)
        }
        if (feedback.disputed && feedback.disputeReason.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.feedback_dispute_reason, feedback.disputeReason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(relativeTime(feedback.createdAt), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (onDispute != null && !feedback.disputed) {
                TextButton(onClick = onDispute) { Text(stringResource(R.string.action_dispute_feedback)) }
            }
        }
    }
}

/** A passport's participation history and teammates' feedback (US17, US18). */
@Composable
fun RecordsSection(
    name: String,
    records: List<Participation>,
    contributions: List<Contribution>,
    feedback: List<Feedback>,
    isMine: Boolean,
    onAddContribution: ((Participation) -> Unit)? = null,
    onEditContribution: ((Contribution) -> Unit)? = null,
    onReview: ((Participation) -> Unit)? = null,
    onDispute: ((Feedback) -> Unit)? = null,
) {
    SectionTitle(stringResource(R.string.records_title))
    if (records.isEmpty()) {
        Text(
            if (isMine) stringResource(R.string.records_empty_mine) else stringResource(R.string.records_empty, name),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        records.forEach { record ->
            ProjectRecordCard(
                record = record,
                contributions = contributions.filter { it.listingId == record.listingId },
                onAddContribution = onAddContribution?.let { add -> { add(record) } },
                onEditContribution = onEditContribution,
                onReview = onReview?.let { review -> { review(record) } },
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    SectionTitle(stringResource(R.string.feedback_title))
    FeedbackSummaryBlock(FeedbackSummary.of(feedback), name, isMine)
    feedback.forEach { f ->
        FeedbackRow(f, isMine, onDispute = if (isMine) onDispute?.let { d -> { d(f) } } else null)
    }
    Spacer(Modifier.height(4.dp))
    if (feedback.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.feedback_from_teammates_only), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
