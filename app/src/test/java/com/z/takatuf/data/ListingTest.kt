package com.z.takatuf.data

import com.z.takatuf.data.model.ClosedReason
import com.z.takatuf.data.model.EventDetails
import com.z.takatuf.data.model.EventFormat
import com.z.takatuf.data.model.EventType
import com.z.takatuf.data.model.LinkedEvent
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.ListingKind
import com.z.takatuf.data.model.ListingStatus
import com.z.takatuf.data.model.Recruitment
import com.z.takatuf.data.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ListingTest {
    private val now = Instant.parse("2026-09-26T09:00:00Z")
    private fun days(n: Long): Instant = now.plus(Duration.ofDays(n))

    private fun project(status: ListingStatus = ListingStatus.Open, deadline: Instant? = null) = Listing(
        id = "p", kind = ListingKind.Project, status = status, title = "Campus parking finder", description = "",
        ownerId = "o", ownerName = "Sara", ownerMajor = "", createdAt = now, updatedAt = now,
        recruitment = Recruitment(
            outcome = "A tested prototype",
            roles = listOf(Role("a", "Designer", skills = listOf("Figma"), openings = 1), Role("b", "Dev", skills = listOf("Kotlin", "figma"), openings = 2)),
            hoursPerWeek = 6, duration = "8 weeks", teamSize = 2, deadline = deadline,
        ),
    )

    private fun event(start: Instant, end: Instant, registration: Instant?) = Listing(
        id = "e", kind = ListingKind.Event, status = ListingStatus.Open, title = "Hackathon", description = "",
        ownerId = "o", ownerName = "Sara", ownerMajor = "", createdAt = now, updatedAt = now,
        event = EventDetails(EventType.Hackathon, "Org", "https://example.org", start, end, registration, "Students", 2, 4, EventFormat.Online, ""),
    )

    @Test
    fun `open places add up across roles and skills are de-duplicated`() {
        val listing = project()
        assertEquals(3, listing.recruitment!!.openPlaces)
        assertEquals(listOf("Figma", "Kotlin"), listing.skillsWanted)
    }

    @Test
    fun `a listing is closed by its owner or by its deadline`() {
        assertNull(project().closedReason(now))
        assertNull(project(deadline = days(1)).closedReason(now))
        assertEquals(ClosedReason.DeadlinePassed, project(deadline = days(-1)).closedReason(now))
        assertEquals(ClosedReason.ClosedByOwner, project(status = ListingStatus.Closed).closedReason(now))
    }

    @Test
    fun `events report ended and registration closed separately`() {
        assertNull(event(days(5), days(7), days(2)).closedReason(now))
        assertEquals(ClosedReason.RegistrationClosed, event(days(5), days(7), days(-1)).closedReason(now))
        assertEquals(ClosedReason.Ended, event(days(-5), days(-3), days(-8)).closedReason(now))
    }

    @Test
    fun `a team request closes when its event is over`() {
        val team = project().copy(
            kind = ListingKind.Team,
            linkedEvent = LinkedEvent("e", "Hackathon", "Org", EventType.Hackathon, days(-4), days(-2), 2, 4),
        )
        assertEquals(ClosedReason.EventOver, team.closedReason(now))
    }

    // Deadlines and end instants are exclusive: open strictly before, closed at and after.
    private val ms = Duration.ofMillis(1)

    @Test
    fun `U13 recruitment is open before its deadline and closed at and after it`() {
        val deadline = days(2)
        val listing = project(deadline = deadline)
        assertNull(listing.closedReason(deadline.minus(ms)))
        assertEquals(ClosedReason.DeadlinePassed, listing.closedReason(deadline))
        assertEquals(ClosedReason.DeadlinePassed, listing.closedReason(deadline.plus(ms)))
    }

    @Test
    fun `U14 an event is ended at its end instant`() {
        val end = days(3)
        val listing = event(days(1), end, registration = null)
        assertNull(listing.closedReason(end.minus(ms)))
        assertEquals(ClosedReason.Ended, listing.closedReason(end))
        assertEquals(ClosedReason.Ended, listing.closedReason(end.plus(ms)))
    }

    @Test
    fun `registration closes at its deadline instant`() {
        val registration = days(1)
        val listing = event(days(2), days(3), registration)
        assertNull(listing.closedReason(registration.minus(ms)))
        assertEquals(ClosedReason.RegistrationClosed, listing.closedReason(registration))
    }

    @Test
    fun `U15 linked recruitment closes at the linked event's end instant`() {
        val end = days(3)
        val team = project().copy(
            kind = ListingKind.Team,
            linkedEvent = LinkedEvent("e", "Hackathon", "Org", EventType.Hackathon, days(1), end, 2, 4),
        )
        assertNull(team.closedReason(end.minus(ms)))
        assertEquals(ClosedReason.EventOver, team.closedReason(end))
        assertEquals(ClosedReason.EventOver, team.closedReason(end.plus(ms)))
    }

    @Test
    fun `I10 linked recruitment follows the parent event's current dates, not its copy`() {
        // The request kept the dates the event had when it was linked...
        val team = project().copy(
            kind = ListingKind.Team,
            linkedEvent = LinkedEvent("e", "Hackathon", "Org", EventType.Hackathon, days(5), days(7), 2, 4),
        )
        assertNull(team.closedReason(now))
        // ...then the organiser moved the event into the past.
        val moved = event(days(-3), days(-1), registration = null).copy(id = "e")
        assertEquals(ClosedReason.EventOver, team.closedReason(now, parentEvent = moved))
        // And the other way: an extended event reopens a request whose copy looked over.
        val stale = team.copy(linkedEvent = team.linkedEvent!!.copy(endDate = days(-1)))
        val extended = event(days(1), days(4), registration = null).copy(id = "e")
        assertNull(stale.closedReason(now, parentEvent = extended))
    }
}
