<p align="center">
  <img src="app/src/main/res/drawable-nodpi/takatuf_mark.webp" alt="Takatuf logo" width="88">
</p>

<h1 align="center">Takatuf | تكاتف</h1>

<p align="center">
  <strong>Find the right teammates. Build something together.</strong><br>
  An Android platform for student team formation and collaboration.
</p>

<p align="center">
  <img alt="Project status: completed" src="https://img.shields.io/badge/Project-Completed-237A57">
  <img alt="Version 0.3.2" src="https://img.shields.io/badge/Version-0.3.2-315C99">
  <img alt="Android 8.0 and above" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84">
</p>

<p align="center">
  <a href="#what-you-can-do">Features</a> ·
  <a href="#run-locally">Run locally</a> ·
  <a href="docs/DEVELOPMENT.md">Developer guide</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

## About

Finding a student project is only the beginning. Building a team means understanding what
people can contribute, choosing complementary skills, and keeping everyone connected as the
work progresses.

Takatuf brings that journey into one place. Students discover projects and events, present
their capabilities through a **Skill Passport**, apply for roles, and collaborate in a shared
team workspace. Contributions and feedback then become part of their participation history.

**This completed senior project was developed at Prince Sultan University.** Version 0.3.2
brings together the functionality delivered across all three project sprints.

## What you can do

| Area | What Takatuf offers |
| --- | --- |
| **Discover opportunities** | Browse, search, filter and save student projects, competitions, hackathons and event-linked team requests. |
| **Present your skills** | Build a Skill Passport with skills, experience, interests, availability and evidence links, with profile visibility controls. |
| **Form a team** | Apply for roles, shortlist applicants, compare complementary team suggestions and confirm membership through offers. |
| **Work together** | Share responsibilities, milestones, progress updates and deliverable links; manage vacancies, departures and leadership handover. |
| **Record participation** | Capture contributions and completion evidence, give participant feedback and identify disputed reviews. |
| **Stay informed** | Receive recruitment and workspace notices, recover supported drafts, and report content or conduct for administrator review. |

### How team matching works

Team recommendations consider the request's roles, applicants' declared capabilities and
availability, eligibility, and the skills already present in the team. Suggestions explain
coverage and remaining gaps so owners can compare their options.

**The owner chooses the team, and each applicant confirms joining.** Matching runs on the
device using rule-based scoring. Optional AI assistance helps describe roles and skills in a
listing; students choose which suggestions to use.

## From opportunity to participation

1. **Create your passport** — describe your skills, interests and availability.
2. **Find or publish an opportunity** — explore projects and events, or recruit for your own.
3. **Build a complementary team** — review applicants, compare suggestions and send offers.
4. **Collaborate in the workspace** — agree on responsibilities and track milestones and updates.
5. **Record the outcome** — keep contribution evidence and share feedback on the team experience.

## Technology

| Layer | Implementation |
| --- | --- |
| Android app | Kotlin, Jetpack Compose and Material 3 |
| App structure | ViewModels, coroutines, Flow and repository-based data access |
| Backend | Firebase Authentication and Cloud Firestore |
| Permissions | Firestore security rules for identity, visibility, ownership and membership |
| Background work | WorkManager for inbox checks and milestone reminders |
| Testing | JUnit, Firebase Emulator Suite and synthetic recommendation evaluation |

The app supports **Android 8.0 (API 26) and above**, with compile and target SDK 36.

## Run locally

Use Android Studio with its bundled Gradle JDK, Android SDK 36, Node.js/npm, and the Firebase
CLI. Start with a local Android emulator and the included synthetic data.

**1. Clone and open the project in Android Studio.**

```sh
git clone https://github.com/AfnanAlmuallim/Takatuf.git
cd Takatuf
```

**2. Start Firebase emulators** in a terminal at the project root:

```sh
cd firebase
npm ci
npm run emulators
```

**3. Load demo data** in a second terminal at the project root:

```sh
cd firebase
npm run seed
```

**4. Start an Android emulator**, then install the app from a third terminal at the project root:

```sh
./gradlew :app:installDebug -Ptakatuf.emulatorHost=10.0.2.2
```

Open Takatuf and sign in with **`sara@takatuf.test` / `Takatuf2026`**.
These credentials belong to the synthetic local dataset. It includes recruitment, working
and completed teams, contributions, feedback, notifications and moderation examples.

No AI key is needed to explore the core app. See the [developer guide](docs/DEVELOPMENT.md)
for Firebase CLI setup, cloud configuration, optional AI assistance and troubleshooting.

## Quality and validation

The final source update was validated on **8 November 2026**:

| Check | Result |
| --- | --- |
| Android unit tests | **83 passed** |
| Firebase security-rule tests | **301 passed** |
| Debug and release builds | **Passed** |
| Android lint task | **Passed** |

These checks cover domain logic, regression cases and backend permission boundaries.
Recommendation evaluation uses synthetic scenarios. Live-cloud checks and device journeys
were separate from this repository validation; see the [changelog](CHANGELOG.md) for its scope.

## Explore the repository

- [Developer guide](docs/DEVELOPMENT.md) — setup, architecture, tests, notifications and maintenance.
- [Changelog](CHANGELOG.md) — delivery history across the three sprints.
- [Android source](app/src/main/java/com/z/takatuf) — app features, data access and matching logic.
- [Android tests](app/src/test/java/com/z/takatuf) — unit, regression and recommendation checks.
- [Firebase backend](firebase) — rules, indexes, emulator data and maintenance tools.
