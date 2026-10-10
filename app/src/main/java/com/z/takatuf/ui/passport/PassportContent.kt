package com.z.takatuf.ui.passport

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.z.takatuf.R
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.Profile
import com.z.takatuf.data.model.Visibility
import com.z.takatuf.domain.SkillMatcher
import com.z.takatuf.ui.common.Fact
import com.z.takatuf.ui.common.Monogram
import com.z.takatuf.ui.common.SectionTitle
import com.z.takatuf.ui.common.SkillChip
import com.z.takatuf.ui.common.SkillLevelMeter
import com.z.takatuf.ui.common.formatDays

enum class PassportSection { Skills, Interests, Availability, Experience, Links }

/** Name, major and year: the part of a profile every signed-in student can see. */
@Composable
fun IdentityHeader(profile: Profile, modifier: Modifier = Modifier, trailing: @Composable (RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Monogram(profile.displayName, profile.uid, size = 64.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.displayName, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.secondary)
            val line = listOfNotNull(profile.major.takeIf { it.isNotBlank() }, profile.year?.let { yearLabel(it) }).joinToString(", ")
            if (line.isNotEmpty()) {
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke(this)
    }
    if (profile.headline.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(profile.headline, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The Skill Passport's sections. [highlight] marks skills a role asks for (with a check, not
 * just colour). [onEdit] adds an edit action to each section for the passport's owner, and
 * empty sections then turn into prompts instead of disappearing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassportSections(
    passport: Passport,
    modifier: Modifier = Modifier,
    highlight: Collection<String> = emptyList(),
    onEdit: ((PassportSection) -> Unit)? = null,
    /** Sections already shown elsewhere on the screen. */
    hide: Set<PassportSection> = emptySet(),
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier) {
        if (passport.skills.isNotEmpty() || onEdit != null) {
            SectionHeader(stringResource(R.string.passport_skills), PassportSection.Skills, onEdit, isEmpty = passport.skills.isEmpty())
            if (passport.skills.isEmpty()) {
                EmptyPrompt(stringResource(R.string.passport_skills_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    passport.skills
                        .sortedWith(compareByDescending<com.z.takatuf.data.model.Skill> { SkillMatcher.isCovered(highlight, it.name) }.thenByDescending { it.level.value })
                        .forEach { skill ->
                            val matched = highlight.isNotEmpty() && SkillMatcher.isCovered(highlight, skill.name)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (matched) {
                                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.skill_matches_role), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    skill.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                SkillLevelMeter(skill.level)
                            }
                        }
                }
            }
            SectionGap()
        }
        if (passport.interests.isNotEmpty() || onEdit != null) {
            SectionHeader(stringResource(R.string.passport_interests), PassportSection.Interests, onEdit, isEmpty = passport.interests.isEmpty())
            if (passport.interests.isEmpty()) {
                EmptyPrompt(stringResource(R.string.passport_interests_empty))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    passport.interests.forEach { SkillChip(it) }
                }
            }
            SectionGap()
        }
        val availability = passport.availability
        if (PassportSection.Availability !in hide && (availability.isSet || availability.days.isNotEmpty() || onEdit != null)) {
            SectionHeader(stringResource(R.string.passport_availability), PassportSection.Availability, onEdit, isEmpty = !availability.isSet)
            if (!availability.isSet && availability.days.isEmpty()) {
                EmptyPrompt(stringResource(R.string.passport_availability_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (availability.isSet) Fact(Icons.Rounded.Schedule, pluralStringResource(R.plurals.hours_per_week_long, availability.hoursPerWeek, availability.hoursPerWeek), color = MaterialTheme.colorScheme.onSurface)
                    if (availability.days.isNotEmpty()) Fact(Icons.Rounded.CalendarMonth, formatDays(availability.days), color = MaterialTheme.colorScheme.onSurface)
                    if (availability.note.isNotBlank()) Fact(Icons.AutoMirrored.Rounded.Notes, availability.note, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            SectionGap()
        }
        if (passport.experience.isNotEmpty() || onEdit != null) {
            SectionHeader(stringResource(R.string.passport_experience), PassportSection.Experience, onEdit, isEmpty = passport.experience.isEmpty(), addLabel = true)
            if (passport.experience.isEmpty()) {
                EmptyPrompt(stringResource(R.string.passport_experience_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    passport.experience.forEach { item ->
                        Column {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            val sub = listOf(item.organization, item.period).filter { it.isNotBlank() }.joinToString(", ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.description.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(item.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            SectionGap()
        }
        if (passport.links.isNotEmpty() || onEdit != null) {
            SectionHeader(stringResource(R.string.passport_links), PassportSection.Links, onEdit, isEmpty = passport.links.isEmpty(), addLabel = true)
            if (passport.links.isEmpty()) {
                EmptyPrompt(stringResource(R.string.passport_links_empty))
            } else {
                Column {
                    passport.links.forEachIndexed { index, link ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { runCatching { uriHandler.openUri(link.url) } }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(link.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    link.url.removePrefix("https://").removePrefix("http://").removePrefix("www."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.action_open_link, link.label), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, section: PassportSection, onEdit: ((PassportSection) -> Unit)?, isEmpty: Boolean, addLabel: Boolean = false) {
    SectionTitle(title) {
        if (onEdit != null) {
            androidx.compose.material3.TextButton(onClick = { onEdit(section) }) {
                Text(stringResource(if (addLabel || isEmpty) R.string.action_add else R.string.action_edit))
            }
        }
    }
}

@Composable
private fun EmptyPrompt(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionGap() {
    Spacer(Modifier.height(20.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(8.dp))
}

/** Shown in place of a passport the viewer may not open. */
@Composable
fun HiddenPassport(name: String, visibility: Visibility?, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.passport_hidden_title, name), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(if (visibility == Visibility.Applied) R.string.passport_hidden_applied else R.string.passport_hidden_private),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
