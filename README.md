# Takatuf | تكاتف

Android app built with Kotlin, Jetpack Compose and Firebase for student team formation
and collaboration. This source update covers **Sprints 1–3**, Android version **0.3.2**
(version code **6**). See [CHANGELOG.md](CHANGELOG.md) for the changes and preparation checks.

- **Sprint 1 (US01–US08):** accounts, Skill Passports and visibility, project/event/team-request
  listings, discovery, saved items, applications and applicant shortlisting.
- **Sprint 2 (US09–US16):** applicant fit, complementary team suggestions, comparison and
  retained candidates, offers and joining confirmation, shared workspaces, milestones,
  reopening vacancies, departure and leadership handover.
- **Sprint 3 (US17–US24):** participation and contribution records, completion evidence,
  participant feedback and disputes, notifications, reporting and administrator moderation,
  supported-form draft recovery, pending-sync feedback, access checks and action logs.
  Includes recommendation evaluation and backup/restore tools.

## Project layout

```text
app/src/main/java/com/z/takatuf/
  data/          Firebase repositories, models, drafts, sync tracking and optional AI client
  domain/        Fit scoring, team matching, validation and date rules
  notify/        Android notifications and background inbox/reminder checks
  ui/            Compose screens, navigation, theme and shared components
app/src/test/    Unit, boundary, regression and recommendation-evaluation tests
firebase/
  firestore.rules         Identity, visibility, membership and role permissions
  firestore.indexes.json  Query indexes
  tests/                  Rules suites and opt-in live smoke scripts
  seed/                   Synthetic emulator data
  backup/                 Backup, guarded restore and emulator verification tools
```

## Build

Open this folder in Android Studio and use its bundled Gradle JDK with Android SDK 36
installed. The Gradle wrapper is included; the app supports Android 8.0 (API 26) and above.

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug
```

A fresh checkout builds without an `.env` file; optional AI suggestions are disabled.
Local credentials, IDE/build output, account-state files, database exports, assessment reports
and evidence are excluded from this source update. Publishing source does not deploy Firebase
rules or indexes.

## Firebase setup

`app/google-services.json` targets the existing `takatuf-5a37f` project.

1. In Firebase Authentication, enable **Email/Password**.
2. Ensure the default Cloud Firestore database exists.
3. Install Node.js/npm and the Firebase CLI; emulator tests also require Java.
4. Install the locked dependencies and deploy rules/indexes when updating the backend:

   ```sh
   cd firebase
   npm ci
   firebase login
   npm run deploy:firestore
   ```

The app's queries require the indexes. Backend deployment is a separate operation from a
GitHub push. Keep administrator provisioning in trusted Firebase administration; the app
does not let students grant themselves moderation access.

If `firebase` is not on PATH, run scripts with the npm-distributed CLI:

```sh
cd firebase
npm exec --yes --package=firebase-tools@latest -- npm run test:rules
```

## Local emulator development

```sh
cd firebase
npm run emulators
# In a second terminal, from firebase/:
npm run seed
```

Build for the Android emulator, where `10.0.2.2` refers to the host computer:

```sh
./gradlew installDebug -Ptakatuf.emulatorHost=10.0.2.2
```

Without that property, the app uses the Firebase project from `google-services.json`.
The synthetic seed includes students, events, recruitment, working and completed teams,
participation, contributions, feedback and inbox examples. Sign in with a seeded student,
for example `sara@takatuf.test` / `Takatuf2026` (local test data only).

## Notifications and recovery

The inbox shows read/unread notices and opens the related item. Android notifications require
system permission on supported Android versions. New inbox items are observed while the app
process runs; WorkManager checks in the background approximately every 15 minutes when
connected and schedules due-milestone reminders. This is not an immediate FCM push service.

Supported forms save local drafts. Pending writes and connection errors have visible states.
Firestore rules enforce access independently of the interface, including after departure or
leadership changes. Optional AI failure leaves the recruitment workflow usable.

## Optional AI role suggestions

The listing editor can suggest roles and skills from a project's outcome and description.
Students choose which suggestions to add. Applicant ranking and team matching remain
rule-based and run on the device.

Copy `.env.example` to `.env` and configure your own development key:

```dotenv
OPENAI_API_KEY=your-development-key
OPENAI_MODEL=gpt-6-luna
AI_DAILY_LIMIT=20
```

Gradle reads these settings at build time; rebuild after changing them. Without a key, the
editor hides suggestions. Network failures and daily limits produce a recoverable message.

> The key is embedded in the APK. Keep builds containing a real key private and use a
> project-specific budget cap. Public distribution needs a server-side API integration.
> Never commit real credentials.

## Tests and evaluation

```sh
./gradlew :app:testDebugUnitTest
cd firebase
npm ci
npm run test:rules
```

The rules command runs the main suite and Sprint 3 extended suite sequentially against
`demo-takatuf-rules`, an isolated emulator project. Sequential execution prevents the suites'
database resets from interfering with each other.

Unit tests include retained Sprint 1 and Sprint 2 checks, Sprint 3 record and validation
boundaries, and synthetic recommendation comparisons. Evaluation reports are generated under
`app/build/evaluation/`; synthetic results do not establish usefulness with real students.

The following scripts are opt-in **live-cloud** checks. They create throwaway accounts/data
in the configured project, so use them only when intending to exercise that backend and
clean up afterwards:

```sh
cd firebase
npm run smoke
npm run smoke:cleanup
```

Live checks and device journeys are separate from the repository preparation checks recorded
in the changelog.

## Backup and restore

Backup scripts use `firebase-admin`. For a live backup, set `GOOGLE_APPLICATION_CREDENTIALS`
to an external service-account key with the required access. Keep keys and exports outside Git.
Firebase Authentication account exports are separate from Firestore document backups.

For local emulator data:

```sh
cd firebase
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run backup
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run restore -- backups/<backup-file>.json
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run backup:verify
```

Verification wipes and restores the emulator dataset and compares the documents. Use it only
on a disposable emulator dataset. Restore replaces documents at the paths in the backup;
restoring to a live project additionally requires the explicit `--production` flag.
