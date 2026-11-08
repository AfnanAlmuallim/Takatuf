package com.z.takatuf.data.model

import java.time.Instant

enum class MilestoneStatus(val key: String) {
    Todo("todo"), Doing("doing"), Done("done");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: Todo
    }
}

/**
 * Stored at `listings/{listingId}/milestones/{id}`. [dueDate] is exclusive like every other
 * date-only value: a milestone due Oct 20 is on time all of Oct 20.
 */
data class Milestone(
    val id: String,
    val title: String,
    val assigneeId: String,
    val assigneeName: String,
    val dueDate: Instant?,
    val status: MilestoneStatus,
    val createdBy: String,
    val completedAt: Instant?,
    val updatedAt: Instant?,
    /** Saved on this device but not yet confirmed by the server (US21). */
    val pending: Boolean = false,
) {
    val isDone: Boolean get() = status == MilestoneStatus.Done

    fun isOverdue(now: Instant): Boolean = !isDone && dueDate != null && !now.isBefore(dueDate)
}

/** A progress note posted to the team, at `listings/{listingId}/updates/{id}`. */
data class TeamUpdate(
    val id: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val link: String,
    val createdAt: Instant?,
    /** Saved on this device but not yet confirmed by the server (US21). */
    val pending: Boolean = false,
)

/** A link to work the team produced, at `listings/{listingId}/deliverables/{id}`. */
data class Deliverable(
    val id: String,
    val label: String,
    val url: String,
    val addedBy: String,
    val addedByName: String,
    val createdAt: Instant?,
    /** Saved on this device but not yet confirmed by the server (US21). */
    val pending: Boolean = false,
)

/** A role the AI suggested from a project description, for the owner to confirm. */
data class RoleSuggestion(
    val title: String,
    val description: String,
    val skills: List<String>,
    val openings: Int,
)
