package com.z.takatuf.ui.notifications

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.ui.graphics.vector.ImageVector
import com.z.takatuf.R
import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.data.model.NotificationType

/** The icon for each kind of notification; the words always carry the meaning too. */
fun NotificationType.icon(): ImageVector = when (this) {
    NotificationType.ApplicationReceived -> Icons.Rounded.Inbox
    NotificationType.OfferReceived -> Icons.Rounded.MarkEmailUnread
    NotificationType.OfferCancelled -> Icons.AutoMirrored.Rounded.Undo
    NotificationType.OfferAccepted -> Icons.Rounded.CheckCircle
    NotificationType.OfferDeclined -> Icons.Rounded.Close
    NotificationType.ReconfirmRequested -> Icons.AutoMirrored.Rounded.HelpOutline
    NotificationType.InterestConfirmed -> Icons.Rounded.CheckCircle
    NotificationType.MemberLeft -> Icons.AutoMirrored.Rounded.Logout
    NotificationType.MemberRemoved -> Icons.Rounded.PersonRemove
    NotificationType.LeadRequested, NotificationType.LeadAccepted, NotificationType.LeadDeclined -> Icons.Rounded.MilitaryTech
    NotificationType.MilestoneAssigned -> Icons.AutoMirrored.Rounded.Assignment
    NotificationType.ListingChanged, NotificationType.EventChanged -> Icons.Rounded.EditCalendar
    NotificationType.ProjectCompleted -> Icons.Rounded.TaskAlt
    NotificationType.FeedbackReceived -> Icons.Rounded.RateReview
    NotificationType.ReportResolved -> Icons.Rounded.Flag
    NotificationType.ModerationNotice -> Icons.Rounded.Block
    NotificationType.RecruitmentClosed -> Icons.Rounded.TaskAlt
    NotificationType.ApplicationDeclined -> Icons.Rounded.Close
    NotificationType.MilestoneCompleted -> Icons.Rounded.CheckCircle
}

/** One line saying what happened, in the reader's terms. Plain [Resources] so the worker can use it. */
fun AppNotification.title(res: Resources): String {
    val who = fromName
    val what = listingTitle
    return when (type) {
        NotificationType.ApplicationReceived -> res.getString(R.string.notif_application_received, who, what)
        NotificationType.OfferReceived -> res.getString(R.string.notif_offer_received, who, what)
        NotificationType.OfferCancelled -> res.getString(R.string.notif_offer_cancelled, who, what)
        NotificationType.OfferAccepted -> res.getString(R.string.notif_offer_accepted, who, what)
        NotificationType.OfferDeclined -> res.getString(R.string.notif_offer_declined, who, what)
        NotificationType.ReconfirmRequested -> res.getString(R.string.notif_reconfirm_requested, what)
        NotificationType.InterestConfirmed -> res.getString(R.string.notif_interest_confirmed, who, what)
        NotificationType.MemberLeft -> res.getString(R.string.notif_member_left, who, what)
        NotificationType.MemberRemoved -> res.getString(R.string.notif_member_removed, what)
        NotificationType.LeadRequested -> res.getString(R.string.notif_lead_requested, who, what)
        NotificationType.LeadAccepted -> res.getString(R.string.notif_lead_accepted, who, what)
        NotificationType.LeadDeclined -> res.getString(R.string.notif_lead_declined, who, what)
        NotificationType.MilestoneAssigned -> res.getString(R.string.notif_milestone_assigned, who, what)
        NotificationType.ListingChanged -> res.getString(R.string.notif_listing_changed, what)
        NotificationType.EventChanged -> res.getString(R.string.notif_event_changed, what)
        NotificationType.ProjectCompleted -> res.getString(R.string.notif_project_completed, what)
        NotificationType.FeedbackReceived -> res.getString(R.string.notif_feedback_received, who, what)
        NotificationType.ReportResolved -> res.getString(R.string.notif_report_resolved)
        NotificationType.ModerationNotice -> res.getString(R.string.notif_moderation_notice)
        NotificationType.RecruitmentClosed -> res.getString(R.string.notif_recruitment_closed, what)
        NotificationType.ApplicationDeclined -> res.getString(R.string.notif_application_declined, who, what)
        NotificationType.MilestoneCompleted -> res.getString(R.string.notif_milestone_completed, who, what)
    }
}
