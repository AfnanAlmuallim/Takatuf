package com.z.takatuf.domain

import com.z.takatuf.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Executable Sprint 1 checks. IDs are retained for the implementation report. */
class Sprint1AcceptanceUnitTest {
    private val instant = Instant.parse("2026-10-05T00:00:00Z")
    private fun project(deadline: Instant? = null, status: ListingStatus = ListingStatus.Open) = Listing(
        id = "unit-project", kind = ListingKind.Project, status = status,
        title = "Sprint 1 test", description = "", ownerId = "owner", ownerName = "Afnan",
        ownerMajor = "", createdAt = instant, updatedAt = instant,
        recruitment = Recruitment("Prototype", listOf(
            Role("mobile", "Mobile developer", skills = listOf("Kotlin", "Figma"), openings = 2),
            Role("design", "Designer", skills = listOf("figma"), openings = 1)
        ), 6, "4 weeks", 1, deadline)
    )
    private fun event(end: Instant) = project().copy(kind = ListingKind.Event, recruitment = null,
        event = EventDetails(EventType.Hackathon, "Test organizer", "https://example.org",
            instant.minusSeconds(86400), end, null, "Students", 2, 4, EventFormat.Online, "")
    )

    @Test fun U01_passwordAtMinimumLength() {
        assertTrue(Validators.isStrongPassword("abcde123"))
    }
    @Test fun U02_passwordBelowMinimumLength() {
        assertFalse(Validators.isStrongPassword("abcd123"))
    }
    @Test fun U03_passwordRequiresBothCharacterClasses() {
        assertFalse(Validators.isStrongPassword("abcdefgh"))
        assertFalse(Validators.isStrongPassword("12345678"))
        assertFalse(Validators.isStrongPassword(""))
    }
    @Test fun U04_evidenceLinkDefaultsToHttps() {
        assertEquals("https://github.com/afnan/project", Validators.normalizeUrl(" github.com/afnan/project "))
    }
    @Test fun U05_nonWebSchemeIsRejected() {
        assertNull(Validators.normalizeUrl("ftp://example.org/file"))
    }
    @Test fun U06_invalidEvidenceLinkIsRejected() {
        assertNull(Validators.normalizeUrl("not a link"))
        assertNull(Validators.normalizeUrl("https://localhost"))
        assertNull(Validators.normalizeUrl(""))
    }
    @Test fun U07_passportEmptyAndPopulatedStates() {
        assertTrue(Passport("student").isEmpty)
        assertFalse(Passport("student", links = listOf(EvidenceLink("link", "Portfolio", "https://example.org"))).isEmpty)
    }
    @Test fun U08_availabilityZeroAndPositiveBoundary() {
        assertFalse(Availability(hoursPerWeek = 0).isSet)
        assertTrue(Availability(hoursPerWeek = 1).isSet)
        assertFalse(Passport("student", availability = Availability(1)).isEmpty)
    }
    @Test fun U09_proficiencyValuesRoundTrip() {
        assertEquals(SkillLevel.Beginner, SkillLevel.from(1))
        assertEquals(SkillLevel.Intermediate, SkillLevel.from(2))
        assertEquals(SkillLevel.Advanced, SkillLevel.from(3))
    }
    @Test fun U10_vacanciesAggregateAcrossRoles() {
        assertEquals(3, project().recruitment!!.openPlaces)
    }
    @Test fun U11_requestedSkillsIgnoreCaseDuplicates() {
        assertEquals(listOf("Kotlin", "Figma"), project().skillsWanted)
    }
    @Test fun U12_ownerClosedRequestIsClosed() {
        assertEquals(ClosedReason.ClosedByOwner, project(status = ListingStatus.Closed).closedReason(instant))
    }
    @Test fun U13_requestClosesAtExclusiveDeadline() {
        assertNull(project(deadline = instant).closedReason(instant.minusMillis(1)))
        assertEquals(ClosedReason.DeadlinePassed, project(deadline = instant).closedReason(instant))
    }
    @Test fun U14_eventExpiresAtExclusiveEnd() {
        assertNull(event(instant).closedReason(instant.minusMillis(1)))
        assertEquals(ClosedReason.Ended, event(instant).closedReason(instant))
    }
    @Test fun U15_linkedRequestClosesAtEventEnd() {
        val listing = project().copy(kind = ListingKind.Team,
            linkedEvent = LinkedEvent("event", "Hackathon", "Organizer", EventType.Hackathon,
                instant.minusSeconds(86400), instant, 2, 4))
        assertNull(listing.closedReason(instant.minusMillis(1)))
        assertEquals(ClosedReason.EventOver, listing.closedReason(instant))
    }
    @Test fun U16_dateBoundariesCoverLeapDayAndYearEnd() {
        val leapDay = LocalDate.of(2024, 2, 29)
        val yearEnd = LocalDate.of(2026, 12, 31)
        assertEquals(leapDay, Dates.fromPicker(Dates.toPicker(leapDay)))
        assertEquals(leapDay, Dates.lastDay(Dates.endOf(leapDay)))
        assertEquals(Instant.parse("2027-01-01T00:00:00Z"), Dates.endOf(yearEnd))
        assertEquals(0L, Dates.daysLeft(Dates.endOf(yearEnd), yearEnd))
    }
}
