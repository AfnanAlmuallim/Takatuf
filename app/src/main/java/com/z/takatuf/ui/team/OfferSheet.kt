package com.z.takatuf.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.z.takatuf.ui.common.ActionSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.domain.Dates
import com.z.takatuf.ui.common.ChoiceChip
import com.z.takatuf.ui.common.PrimaryButton
import com.z.takatuf.ui.common.formatDay

private val offerLengths = listOf(2, 3, 7)

/**
 * Sending one or more offers. The applicant has until the chosen day to accept; nothing about
 * the team changes until they do (US12).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfferSheet(
    names: List<String>,
    sending: Boolean,
    onSend: (days: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by rememberSaveable { mutableIntStateOf(3) }
    ActionSheet(onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.offer_sheet_title, names.size, names.size),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                names.joinToString(", "),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.offer_sheet_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.offer_sheet_answer_within), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                offerLengths.forEach { length ->
                    ChoiceChip(
                        selected = days == length,
                        onClick = { days = length },
                        label = { Text(pluralStringResource(R.plurals.offer_days, length, length)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.offer_sheet_until, formatDay(Dates.today().plusDays(days.toLong()))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                text = pluralStringResource(R.plurals.action_send_offers, names.size, names.size),
                onClick = { onSend(days) },
                loading = sending,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
