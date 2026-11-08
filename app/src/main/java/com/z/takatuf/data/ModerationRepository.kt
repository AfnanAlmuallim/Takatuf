package com.z.takatuf.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.z.takatuf.data.firebase.awaitWrite
import com.z.takatuf.data.firebase.observe
import com.z.takatuf.data.firebase.toReport
import com.z.takatuf.data.model.ModerationAction
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.Report
import com.z.takatuf.data.model.ReportReason
import com.z.takatuf.data.model.ReportStatus
import com.z.takatuf.data.model.ReportTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

/**
 * Reports from students and the administrator's decisions about them (US20, FR22). Every
 * decision is recorded with a reason in `moderationActions`, and in the action log.
 */
class ModerationRepository(private val db: FirebaseFirestore) {

    private val reports = db.collection(REPORTS)

    /** True when the signed-in student is a platform administrator. */
    fun observeIsAdmin(uid: String): Flow<Boolean> =
        db.collection(ADMINS).document(uid).observe { it.exists() }.catch { emit(false) }

    /**
     * Files a report. One person reports one thing once: the id joins the target and the
     * reporter, so a retry, or a second report of the same thing, changes nothing.
     */
    suspend fun report(
        reporter: Profile,
        target: ReportTarget,
        targetKey: String,
        targetPath: String,
        targetTitle: String,
        targetOwnerUid: String,
        listingId: String,
        reason: ReportReason,
        details: String,
    ) {
        val ref = reports.document("${target.key}_${targetKey}_${reporter.uid}")
        if (attempt { ref.get().await().exists() }.getOrDefault(false)) return
        val batch = db.batch()
        batch.set(ref, reportFields(reporter, target, targetKey, targetPath, targetTitle, targetOwnerUid, listingId, reason, details))
        batch.set(AuditLog.ref(db), AuditLog.entry(AuditLog.Action.ReportCreated, reporter.uid, listingId, targetOwnerUid, "${target.key}: ${reason.key}"))
        batch.commit().awaitWrite()
    }

    /** The administrator's queue: open reports first, or the closed ones. */
    fun observeReports(closed: Boolean): Flow<List<Report>> {
        val statuses = if (closed) listOf(ReportStatus.Resolved, ReportStatus.Dismissed) else listOf(ReportStatus.Open, ReportStatus.Reviewing)
        return reports.whereIn("status", statuses.map { it.key })
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .observe { snapshot -> snapshot.documents.mapNotNull { it.toReport() } }
    }

    fun observeReport(id: String): Flow<Report?> = reports.document(id).observe { it.toReport() }

    /**
     * Applies a moderation decision in one write: the report's status, the change to the
     * reported item, the recorded action with its reason, and an action-log entry.
     */
    suspend fun moderate(admin: Profile, report: Report, action: ModerationAction, reason: String) {
        val batch = db.batch()
        val closes = action.closes
        batch.update(
            reports.document(report.id),
            buildMap {
                put("status", (closes ?: ReportStatus.Reviewing).key)
                put("updatedAt", FieldValue.serverTimestamp())
                if (closes != null) {
                    put("resolution", reason.trim())
                    put("resolvedBy", admin.uid)
                    put("resolvedAt", FieldValue.serverTimestamp())
                }
            },
        )
        when (action) {
            ModerationAction.HideListing, ModerationAction.UnhideListing -> batch.update(
                db.document(report.targetPath),
                mapOf(
                    "hidden" to (action == ModerationAction.HideListing),
                    "moderationNote" to reason.trim(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            ModerationAction.FlagListing -> batch.update(
                db.document(report.targetPath),
                mapOf(
                    "flag" to mapOf("reason" to reason.trim().take(300), "at" to FieldValue.serverTimestamp()),
                    "moderationNote" to reason.trim(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            ModerationAction.HideFeedback, ModerationAction.RestoreFeedback -> batch.update(
                db.document(report.targetPath),
                mapOf(
                    "hidden" to (action == ModerationAction.HideFeedback),
                    "moderationNote" to reason.trim(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            else -> Unit
        }
        batch.set(
            db.collection(MODERATION_ACTIONS).document(),
            mapOf(
                "reportId" to report.id,
                "adminUid" to admin.uid,
                "action" to action.key,
                "reason" to reason.trim(),
                "targetPath" to report.targetPath,
                "at" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            AuditLog.ref(db),
            AuditLog.entry(AuditLog.Action.Moderation, admin.uid, report.listingId, report.targetOwnerUid, "${action.key}: $reason"),
        )
        batch.commit().awaitWrite()
    }

    companion object {
        const val REPORTS = "reports"
        const val ADMINS = "admins"
        const val MODERATION_ACTIONS = "moderationActions"

        internal fun reportFields(
            reporter: Profile,
            target: ReportTarget,
            targetKey: String,
            targetPath: String,
            targetTitle: String,
            targetOwnerUid: String,
            listingId: String,
            reason: ReportReason,
            details: String,
        ) = mapOf(
            "reporterUid" to reporter.uid,
            "reporterName" to reporter.displayName.take(60),
            "targetType" to target.key,
            "targetKey" to targetKey.take(200),
            "targetPath" to targetPath.take(300),
            "targetTitle" to targetTitle.take(120).ifBlank { "Untitled" },
            "targetOwnerUid" to targetOwnerUid,
            "listingId" to listingId,
            "reason" to reason.key,
            "details" to details.trim().take(500),
            "status" to ReportStatus.Open.key,
            "resolution" to "",
            "resolvedBy" to null,
            "resolvedAt" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
    }
}
