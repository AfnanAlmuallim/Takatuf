package com.z.takatuf.ui.moderation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.z.takatuf.R
import com.z.takatuf.TakatufApplication
import com.z.takatuf.data.DraftStore
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.ReportReason
import com.z.takatuf.data.model.ReportTarget
import com.z.takatuf.data.toAppError
import kotlinx.coroutines.launch

/** What is being reported, enough to file it and for a moderator to find it. */
data class ReportSubject(
    val target: ReportTarget,
    val targetKey: String,
    val targetPath: String,
    val title: String,
    val ownerUid: String,
    val listingId: String,
)

/**
 * Shows the report sheet for [subject] and files the report. Confirmation and failures (with a
 * retry) appear in [snackbar]; the sheet closes once the report is sent.
 */
@Composable
fun ReportLauncher(subject: ReportSubject, snackbar: SnackbarHostState, onDismiss: () -> Unit) {
    val container = (LocalContext.current.applicationContext as TakatufApplication).container
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }

    fun send(reason: ReportReason, details: String) {
        val me = container.session.profile ?: return
        sending = true
        scope.launch {
            val result = attempt {
                container.moderation.report(
                    reporter = me,
                    target = subject.target,
                    targetKey = subject.targetKey,
                    targetPath = subject.targetPath,
                    targetTitle = subject.title,
                    targetOwnerUid = subject.ownerUid,
                    listingId = subject.listingId,
                    reason = reason,
                    details = details,
                )
            }
            sending = false
            result.onSuccess {
                container.drafts.clear(DraftStore.report("${subject.target.key}_${subject.targetKey}"))
                onDismiss()
                snackbar.showSnackbar(res.getString(R.string.msg_report_sent))
            }.onFailure { e ->
                val choice = snackbar.showSnackbar(
                    res.getString(e.toAppError().message),
                    actionLabel = res.getString(R.string.action_retry),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (choice == SnackbarResult.ActionPerformed) send(reason, details)
            }
        }
    }

    ReportSheet(
        target = subject.target,
        targetKey = subject.targetKey,
        subject = subject.title,
        sending = sending,
        onSend = ::send,
        onDismiss = onDismiss,
    )
}
