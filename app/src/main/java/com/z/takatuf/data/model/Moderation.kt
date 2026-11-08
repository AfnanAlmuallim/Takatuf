package com.z.takatuf.data.model

import java.time.Instant

enum class ReportTarget(val key: String) {
    Listing("listing"), User("user"), Feedback("feedback");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Listing
    }
}

enum class ReportReason(val key: String) {
    Inaccurate("inaccurate"),
    Inappropriate("inappropriate"),
    Spam("spam"),
    Misconduct("misconduct"),
    UnfairFeedback("unfair_feedback"),
    Other("other");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Other

        /** The reasons that make sense for each kind of report. */
        fun forTarget(target: ReportTarget): List<ReportReason> = when (target) {
            ReportTarget.Listing -> listOf(Inaccurate, Inappropriate, Spam, Other)
            ReportTarget.User -> listOf(Misconduct, Inappropriate, Spam, Other)
            ReportTarget.Feedback -> listOf(UnfairFeedback, Inappropriate, Other)
        }
    }
}

enum class ReportStatus(val key: String) {
    Open("open"), Reviewing("reviewing"), Resolved("resolved"), Dismissed("dismissed");

    val closed: Boolean get() = this == Resolved || this == Dismissed

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Open
    }
}

/** A user's report, at `reports/{targetType}_{targetKey}_{reporterUid}` (US20). */
data class Report(
    val id: String,
    val reporterUid: String,
    val reporterName: String,
    val target: ReportTarget,
    val targetKey: String,
    val targetPath: String,
    val targetTitle: String,
    val targetOwnerUid: String,
    val listingId: String,
    val reason: ReportReason,
    val details: String,
    val status: ReportStatus,
    val resolution: String,
    val resolvedAt: Instant?,
    val createdAt: Instant?,
)

/** What an administrator can do about a report. Each is recorded with a reason. */
enum class ModerationAction(val key: String, val closes: ReportStatus?) {
    Reviewing("reviewing", null),
    Dismiss("dismiss", ReportStatus.Dismissed),
    Resolve("resolve", ReportStatus.Resolved),
    HideListing("hide_listing", ReportStatus.Resolved),
    UnhideListing("unhide_listing", ReportStatus.Resolved),
    FlagListing("flag_listing", ReportStatus.Resolved),
    HideFeedback("hide_feedback", ReportStatus.Resolved),
    RestoreFeedback("restore_feedback", ReportStatus.Resolved),
    WarnUser("warn_user", ReportStatus.Resolved);

    companion object {
        fun forTarget(target: ReportTarget): List<ModerationAction> = when (target) {
            ReportTarget.Listing -> listOf(FlagListing, HideListing, UnhideListing, Resolve, Dismiss)
            ReportTarget.User -> listOf(WarnUser, Resolve, Dismiss)
            ReportTarget.Feedback -> listOf(HideFeedback, RestoreFeedback, Dismiss)
        }
    }
}
