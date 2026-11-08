package com.z.takatuf.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import com.z.takatuf.ui.common.ActionSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.DraftStore
import com.z.takatuf.data.model.ReportReason
import com.z.takatuf.data.model.ReportTarget
import com.z.takatuf.ui.common.AutosaveDraft
import com.z.takatuf.ui.common.ChoiceChip
import com.z.takatuf.ui.common.DraftRestoredNote
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.rememberDrafts

/** Reporting a listing, someone's conduct or a piece of feedback to the moderators (US20). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSheet(
    target: ReportTarget,
    targetKey: String,
    subject: String,
    sending: Boolean,
    onSend: (ReportReason, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val drafts = rememberDrafts()
    val draftKey = DraftStore.report("${target.key}_$targetKey")
    val restored = remember { drafts.load(draftKey) }
    var reason by rememberSaveable { mutableStateOf(restored?.get("reason")?.let(ReportReason::from)) }
    var details by rememberSaveable { mutableStateOf(restored?.get("details").orEmpty()) }
    var showRestored by rememberSaveable { mutableStateOf(restored != null) }
    AutosaveDraft(draftKey, mapOf("reason" to (reason?.key ?: ""), "details" to details))

    ActionSheet(onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 24.dp)) {
            Text(
                stringResource(
                    when (target) {
                        ReportTarget.Listing -> R.string.report_listing_title
                        ReportTarget.User -> R.string.report_user_title
                        ReportTarget.Feedback -> R.string.report_feedback_title
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(subject, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showRestored) {
                Spacer(Modifier.height(12.dp))
                DraftRestoredNote(onDiscard = { reason = null; details = ""; drafts.clear(draftKey); showRestored = false })
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.report_reason), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportReason.forTarget(target).forEach { option ->
                    ChoiceChip(selected = reason == option, onClick = { reason = option }, label = { Text(reasonLabel(option)) })
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = details,
                onValueChange = { if (it.length <= 500) details = it },
                label = { Text(stringResource(R.string.report_details)) },
                placeholder = { Text(stringResource(R.string.report_details_placeholder)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.report_privacy_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = stringResource(R.string.action_send_report),
                onClick = { reason?.let { onSend(it, details.trim()) } },
                enabled = reason != null,
                loading = sending,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun reasonLabel(reason: ReportReason): String = stringResource(
    when (reason) {
        ReportReason.Inaccurate -> R.string.report_reason_inaccurate
        ReportReason.Inappropriate -> R.string.report_reason_inappropriate
        ReportReason.Spam -> R.string.report_reason_spam
        ReportReason.Misconduct -> R.string.report_reason_misconduct
        ReportReason.UnfairFeedback -> R.string.report_reason_unfair_feedback
        ReportReason.Other -> R.string.report_reason_other
    },
)
