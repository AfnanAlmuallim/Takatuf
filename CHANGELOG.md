# Changelog

## 0.3.2 — Sprint 3

Sprint 3 extends the Sprint 2 repository with US17–US24:

- Participation, contribution records and supporting links in Skill Passports; project completion
  and evidence distinguish active, completed and departed participation.
- Participant contribution/leadership feedback, missing-review states and disputed feedback.
- Read/unread inbox notices, related-item navigation, Android notifications and background
  inbox/milestone reminder checks.
- Reports on listings, conduct and feedback; administrator moderation with recorded reasons.
- Supported-form draft recovery, pending-sync feedback and clearer retry/error states.
- Role-based access checks, former-member records and action logging.
- Backup/restore tools and synthetic recommendation evaluation.
- Integrated fixes for live parent-event dates, exclusive deadline boundaries, required milestone
  dates, web-link validation, hidden listing access, event-change notices and enlarged-text labels.

This update preserves the existing Git history and Android version code 6 / version name 0.3.2.
Sprint 1 and Sprint 2 regression tests are retained. The older evaluation now writes under
`app/build/evaluation/` instead of an author-specific absolute path. The rules test command runs
both suites sequentially on the isolated demo emulator to avoid shared database-reset races.

Assessment reports/evidence, one-off live administration scripts, local account state, credentials,
database exports and generated output stay outside the source update. Reusable backup tools and
the extended emulator rules suite are included. Publishing source does not deploy Firebase.

### Preparation validation — 2026-11-08

- `:app:testDebugUnitTest`: 83 passed, none failed or skipped.
- Main and extended Firebase rules suites on `demo-takatuf-rules`: 301 passed, none failed or skipped.
- `:app:assembleDebug :app:assembleRelease :app:lintDebug`: passed.
- Live-cloud smoke tests, backup restoration and device UI journeys were not rerun during
  repository preparation. Existing Sprint 3 assessment evidence remains local.

## 0.2.0 — Sprint 2

Sprint 2 extends the Sprint 1 codebase with user stories US09–US16:

- Applicant fit scoring and complementary team suggestions.
- Comparing team options and keeping selected people across suggestions.
- Offers, expiry, acceptance and joining confirmation.
- A shared team workspace with people, responsibilities, updates and links.
- Milestones, reopening places with applicant reconfirmation, leaving teams and leadership handover.
- Optional AI suggestions for roles and skills; applicant matching remains rule-based.
- Updated Firebase rules, seed data, smoke checks and unit tests.

The repository preparation also retains the Sprint 1 acceptance tests, fixes exact-deadline
closure for listings and events, and sets Android version code 2 / version name 0.2.0.

### Configuration

Use the setup and test commands in [README.md](README.md). AI suggestions are disabled when
`.env` has no API key. `.env.example` contains placeholders only. Any configured key is embedded
in the built APK; keep builds containing a real key private.

Local credentials, account-state files, IDE settings, build output, report-generation tooling
and assessment deliverables are excluded from this source update. Firebase configuration
continues to target the existing project; publishing source does not deploy backend changes.

### Preparation validation — 2026-11-08

- `:app:testDebugUnitTest`: 48 passed, none failed or skipped.
- `:app:assembleDebug :app:assembleRelease`: passed.
- Firebase rules on the isolated `demo-takatuf-rules` emulator: 44 passed, none failed or skipped.
- Live-cloud smoke tests and device UI journeys were not rerun during repository preparation.

## 0.1.0 — Sprint 1

Accounts, Skill Passports, listings, discovery, saved items, applications and applicant shortlisting.
