package com.z.takatuf.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.SkillLevel
import com.z.takatuf.domain.SkillMatcher
import com.z.takatuf.ui.theme.TakatufTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width

enum class TagTone { Neutral, Accent, Attention, Quiet }

/** A small status pill. Status is always spelled out; the icon and tone only reinforce it. */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: TagTone = TagTone.Neutral,
) {
    val (container, content, border) = when (tone) {
        TagTone.Neutral -> Triple(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant, null)
        TagTone.Accent -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, null)
        TagTone.Attention -> Triple(TakatufTheme.colors.attention, TakatufTheme.colors.onAttention, null)
        TagTone.Quiet -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = container,
        contentColor = content,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** A saffron dot marking something that arrived since the owner last looked. */
@Composable
fun NewDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(TakatufTheme.colors.attention),
    )
}

/**
 * A skill as a quiet chip. When [covered] the viewer has it: the chip fills in and gains a
 * check, so a match never depends on colour alone.
 */
@Composable
fun SkillChip(
    name: String,
    modifier: Modifier = Modifier,
    covered: Boolean = false,
) {
    val coveredText = stringResource(R.string.skill_covered_a11y, name)
    Surface(
        modifier = modifier.then(if (covered) Modifier.semantics { contentDescription = coveredText } else Modifier),
        shape = CircleShape,
        color = if (covered) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (covered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (covered) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(PaddingValues(start = if (covered) 8.dp else 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (covered) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Skills with the ones the viewer covers first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillChips(
    skills: List<String>,
    have: Collection<String>,
    modifier: Modifier = Modifier,
    max: Int = Int.MAX_VALUE,
) {
    val ordered = skills.sortedByDescending { SkillMatcher.isCovered(have, it) }
    val shown = ordered.take(max)
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        shown.forEach { SkillChip(it, covered = SkillMatcher.isCovered(have, it)) }
        if (ordered.size > shown.size) {
            Text(
                text = stringResource(R.string.more_count, ordered.size - shown.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 5.dp),
            )
        }
    }
}

/** Three steps, filled up to the level, followed by its name. */
@Composable
fun SkillLevelMeter(level: SkillLevel, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        SkillLevel.entries.forEach { step ->
            Box(
                Modifier
                    .size(width = 14.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (step.value <= level.value) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(skillLevelLabel(level), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun seatsDescription(taken: Int, open: Int): String =
    pluralStringResource(R.plurals.seats_a11y, open, taken + open, open)
