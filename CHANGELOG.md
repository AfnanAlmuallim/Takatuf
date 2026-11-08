# Changelog

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
