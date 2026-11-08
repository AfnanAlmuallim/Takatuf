package com.z.takatuf.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.z.takatuf.data.model.AppNotification
import com.z.takatuf.data.model.Contribution
import com.z.takatuf.data.model.Feedback
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.NotificationTarget
import com.z.takatuf.data.model.NotificationType
import com.z.takatuf.data.model.Participation
import com.z.takatuf.data.model.ParticipationStatus
import com.z.takatuf.data.model.Report
import com.z.takatuf.data.model.ReportReason
import com.z.takatuf.data.model.ReportStatus
import com.z.takatuf.data.model.ReportTarget

internal fun DocumentSnapshot.toParticipation(): Participation? {
    if (!exists()) return null
    val f = fields()
    return Participation(
        listingId = id,
        listingTitle = f.str("listingTitle"),
        kind = ListingKind.from(f["kind"] as? String),
        role = f.str("role"),
        wasLead = f.bool("wasLead"),
        status = ParticipationStatus.from(f["status"] as? String),
        startedAt = f.instant("startedAt"),
        endedAt = f.instant("endedAt"),
    )
}

internal fun DocumentSnapshot.toContribution(): Contribution? {
    if (!exists()) return null
    val f = fields()
    return Contribution(
        id = id,
        ownerUid = reference.parent.parent?.id.orEmpty(),
        listingId = f.str("listingId"),
        listingTitle = f.str("listingTitle"),
        title = f.str("title"),
        detail = f.str("detail"),
        links = f.strings("links"),
        recordedBy = f.str("recordedBy"),
        recordedByName = f.str("recordedByName"),
        confirmedBy = f.strOrNull("confirmedBy"),
        confirmedByName = f.strOrNull("confirmedByName"),
        confirmedAt = f.instant("confirmedAt"),
        createdAt = f.instant("createdAt"),
        pending = metadata.hasPendingWrites(),
    )
}

internal fun DocumentSnapshot.toFeedback(): Feedback? {
    if (!exists()) return null
    val f = fields()
    return Feedback(
        id = id,
        subjectUid = reference.parent.parent?.id.orEmpty(),
        listingId = f.str("listingId"),
        listingTitle = f.str("listingTitle"),
        reviewerUid = f.str("reviewerUid"),
        reviewerName = f.str("reviewerName"),
        contribution = f.int("contribution", 3),
        leadership = f.intOrNull("leadership"),
        comment = f.str("comment"),
        disputed = f.bool("disputed"),
        disputeReason = f.str("disputeReason"),
        hidden = f.bool("hidden"),
        moderationNote = f.str("moderationNote"),
        createdAt = f.instant("createdAt"),
    )
}

internal fun DocumentSnapshot.toNotification(): AppNotification? {
    if (!exists()) return null
    val f = fields()
    val type = NotificationType.from(f["type"] as? String) ?: return null
    return AppNotification(
        id = id,
        type = type,
        listingId = f.str("listingId"),
        listingTitle = f.str("listingTitle"),
        fromUid = f.str("fromUid"),
        fromName = f.str("fromName"),
        target = NotificationTarget.from(f["target"] as? String),
        targetId = f.str("targetId"),
        note = f.str("note"),
        read = f.bool("read"),
        createdAt = f.instant("createdAt"),
    )
}

internal fun DocumentSnapshot.toReport(): Report? {
    if (!exists()) return null
    val f = fields()
    return Report(
        id = id,
        reporterUid = f.str("reporterUid"),
        reporterName = f.str("reporterName"),
        target = ReportTarget.from(f["targetType"] as? String),
        targetKey = f.str("targetKey"),
        targetPath = f.str("targetPath"),
        targetTitle = f.str("targetTitle"),
        targetOwnerUid = f.str("targetOwnerUid"),
        listingId = f.str("listingId"),
        reason = ReportReason.from(f["reason"] as? String),
        details = f.str("details"),
        status = ReportStatus.from(f["status"] as? String),
        resolution = f.str("resolution"),
        resolvedAt = f.instant("resolvedAt"),
        createdAt = f.instant("createdAt"),
    )
}
