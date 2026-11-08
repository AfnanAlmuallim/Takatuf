package com.z.takatuf.ui.team

import com.z.takatuf.data.ProfileRepository
import com.z.takatuf.data.attempt
import com.z.takatuf.data.model.Application
import com.z.takatuf.data.model.Listing
import com.z.takatuf.data.model.Passport
import com.z.takatuf.data.model.PassportAccess
import com.z.takatuf.data.model.Profile
import com.z.takatuf.domain.ApplicantFit
import com.z.takatuf.domain.TeamContext
import com.z.takatuf.domain.TeamMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the viewer may see about each person: their passport (if allowed) and public profile. */
data class People(
    val passports: Map<String, PassportAccess> = emptyMap(),
    val profiles: Map<String, Profile?> = emptyMap(),
) {
    fun passport(uid: String): Passport? = (passports[uid] as? PassportAccess.Visible)?.passport
}

/**
 * Loads passports and profiles once per person. Each passport read goes through the security
 * rules, so an assessment only ever uses what the owner is allowed to see.
 */
class PeopleCache(private val scope: CoroutineScope, private val profiles: ProfileRepository) {
    private val requested = HashSet<String>()
    private val _people = MutableStateFlow(People())
    val people: StateFlow<People> = _people.asStateFlow()

    fun request(uids: Collection<String>) {
        uids.filter { it.isNotEmpty() && requested.add(it) }.forEach { uid ->
            scope.launch {
                val passport = attempt { profiles.passportFor(uid) }.getOrDefault(PassportAccess.Hidden)
                val profile = attempt { profiles.getProfile(uid) }.getOrNull()
                _people.update { it.copy(passports = it.passports + (uid to passport), profiles = it.profiles + (uid to profile)) }
            }
        }
    }
}

/** Skills the team already has: the lead's and every member's that the viewer can see. */
fun teamSkills(listing: Listing, people: People, myUid: String, myPassport: Passport?): List<String> {
    val lead = if (listing.ownerId == myUid) myPassport else people.passport(listing.ownerId)
    val members = listing.members.mapNotNull { member ->
        if (member.uid == myUid) myPassport else people.passport(member.uid)
    }
    return (listOfNotNull(lead) + members).flatMap { passport -> passport.skills.map { it.name } }
}

/** Fit for every application, computed with the same team context everywhere. */
fun fitsFor(
    listing: Listing,
    applications: List<Application>,
    people: People,
    myUid: String,
    myPassport: Passport?,
): Map<String, ApplicantFit> {
    val context = TeamContext(listing, teamSkills(listing, people, myUid, myPassport))
    return applications.associate { app ->
        app.applicantId to TeamMatcher.fit(app, people.passport(app.applicantId), people.profiles[app.applicantId], context)
    }
}
