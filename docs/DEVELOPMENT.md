# Developer guide

This guide covers running, configuring, testing and maintaining Takatuf version 0.3.2.
For an introduction to the completed project, start with the [README](../README.md).

## Contents

- [Requirements](#requirements)
- [Local demo](#local-demo)
- [Cloud Firebase configuration](#cloud-firebase-configuration)
- [Optional AI role suggestions](#optional-ai-role-suggestions)
- [Architecture and source layout](#architecture-and-source-layout)
- [Notifications and draft recovery](#notifications-and-draft-recovery)
- [Tests and builds](#tests-and-builds)
- [Backup and restore](#backup-and-restore)
- [Troubleshooting](#troubleshooting)
- [Repository contents](#repository-contents)

## Requirements

| Tool | Purpose |
| --- | --- |
| Android Studio and its bundled Gradle JDK | Import, build and run the Android app |
| Android SDK 36 | Compile the app |
| Android emulator or device running API 26+ | Run Takatuf |
| Node.js and npm | Install and run Firebase test and seed dependencies |
| Firebase CLI and Java | Start the Firebase Emulator Suite and run rules tests |

Use the versions pinned in [the version catalog](../gradle/libs.versions.toml) and the included
Gradle wrapper. Install backend dependencies with `npm ci` from `firebase/` to respect the lockfile.

If the Firebase CLI is not installed, this command makes it available to an npm script
without a global installation:

```sh
# From firebase/:
npm exec --yes --package=firebase-tools@latest -- npm run emulators
```

The same prefix works with `npm run test:rules`. Running Gradle from Android Studio uses
the IDE's selected Gradle JDK; terminal builds also need a compatible JDK on PATH or JAVA_HOME.
On Windows, use `gradlew.bat` in place of `./gradlew`.

## Local demo

The [README quick start](../README.md#run-locally) launches Auth on port 9099 and Firestore
on port 8080. The emulator UI is available at `http://127.0.0.1:4000`.

The seed script writes synthetic data to the local emulator hosts. It includes student
profiles, listings, applications, an offer, workspaces, a completed project, participation
records, feedback, reports and notifications.

| Account | Password | Starting point |
| --- | --- | --- |
| `sara@takatuf.test` | `Takatuf2026` | Project ownership, recruitment and team examples |

For the standard Android emulator, `10.0.2.2` reaches the host computer:

```sh
# From the repository root, with an Android emulator running:
./gradlew :app:installDebug -Ptakatuf.emulatorHost=10.0.2.2
```

The host property applies to **debug builds**. Omitting it uses the Firebase configuration
in `app/google-services.json`. A physical device needs a reachable host address for the
computer running the emulators.

## Cloud Firebase configuration

The checked-in [Android configuration](../app/google-services.json) and
[Firebase CLI alias](../firebase/.firebaserc) reference `takatuf-5a37f`. Use a Firebase
project you have permission to administer.

For your own project, register an Android app with application ID `com.z.takatuf`, replace
`app/google-services.json` with that project's configuration, and update the Firebase CLI
project alias. Enable **Email/Password** in Firebase Authentication and create the default
Cloud Firestore database.

Deploy the rules and query indexes to the intended project:

```sh
# From firebase/:
firebase login
npm ci
npm run deploy:firestore
```

Index creation may take time. A GitHub push updates source only; backend deployment is separate.
Administrator provisioning belongs in trusted Firebase administration. The student app cannot
grant itself moderation access.

## Optional AI role suggestions

The listing editor can suggest roles and skills from a project's outcome and description.
The student decides which suggestions to add. Applicant fit and team recommendations use
the on-device matching logic.

Create a local `.env` file using [`.env.example`](../.env.example) as a template:

```dotenv
OPENAI_API_KEY=your-development-key
OPENAI_MODEL=gpt-6-luna
AI_DAILY_LIMIT=20
```

Gradle reads these values at build time, so rebuild after changing them. With no API key,
the editor hides AI suggestions and the core workflows remain available. Network failures
and the device's daily request limit produce a recovery message.

**Distribution note:** the current development integration embeds the configured key in the
APK. Keep builds containing a real key private, use a project-specific budget cap, and move
the API integration behind a server before public distribution. Real credentials belong
outside Git.

## Architecture and source layout

Compose screens use ViewModels to manage screen state and actions. Repositories provide
Firebase data access, while domain classes implement matching, validation and date rules.
Firestore security rules enforce permissions independently of the Android interface.

```text
app/src/main/java/com/z/takatuf/
  data/          Repositories, models, drafts, sync tracking and optional AI client
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
docs/            Development and maintenance documentation
```

The Gradle project has one Android application module, `:app`. Android version name is
`0.3.2`, version code is `6`, and minimum SDK is `26`.

## Notifications and draft recovery

The inbox distinguishes read and unread notices and opens the related item. Android
notifications require system permission on Android versions that request it.

New inbox items are observed while the app process runs. WorkManager checks in the background
approximately every 15 minutes when connected and handles due-milestone reminders; Android
controls when background work actually runs. Background notification delivery uses polling.

Supported forms save local drafts. Pending writes and connection errors have visible states.
Permissions are checked independently by Firestore rules, including after departure or
leadership changes.

## Tests and builds

From the repository root:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :app:assembleRelease :app:lintDebug
```

For backend permissions:

```sh
cd firebase
npm ci
npm run test:rules
```

The rules command starts an isolated Firestore emulator for `demo-takatuf-rules`. It runs the
main and extended Sprint 3 suites sequentially so their database resets cannot interfere
with each other. This test command does not target the live project.

Unit tests include retained Sprint 1 and Sprint 2 checks, Sprint 3 record and validation
boundaries, and synthetic recommendation comparisons. Evaluation reports are generated under
`app/build/evaluation/`. Synthetic results measure controlled behavior and do not establish
usefulness with real students.

### Live smoke checks

These scripts are opt-in checks against the configured live Firebase project. They create
throwaway accounts and records; clean them up after the run:

```sh
# From firebase/:
npm run smoke
npm run smoke:cleanup
```

Cleanup needs an authenticated Firebase CLI session. Live checks and device journeys are
separate from the repository preparation results recorded in the [changelog](../CHANGELOG.md).

## Backup and restore

The Firestore tools use `firebase-admin`. A live backup requires
`GOOGLE_APPLICATION_CREDENTIALS` pointing to an external service-account key with the
required permissions. Firebase Authentication exports are managed separately.

For local emulator data:

```sh
# From firebase/:
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run backup
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run restore -- backups/<backup-file>.json
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run backup:verify
```

Replace `<backup-file>` with an actual filename. The default backup output is in
`firebase/backups/`, which Git ignores.

Restore replaces documents at the paths present in the backup. A live-project restore
additionally requires the explicit `--production` flag. Verification wipes and restores the
emulator dataset and compares documents; run it only on a disposable emulator dataset.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `firebase: command not found` | Use the npm-distributed CLI command under [Requirements](#requirements), or install the Firebase CLI on PATH. |
| The local demo connects to the cloud | Rebuild and install with `-Ptakatuf.emulatorHost=10.0.2.2`; this property applies to debug builds. |
| The Android emulator cannot reach Firebase emulators | Keep the emulator process running and use `10.0.2.2`, rather than `localhost`, in the Android build property. |
| A cloud query reports a missing index | Deploy the checked-in indexes to the correct project and wait for index creation. |
| AI suggestions do not appear | Check the local `.env` configuration and rebuild; suggestions are hidden when no key is configured. |
| Notifications do not appear immediately | Check system notification permission and connectivity; background work runs on Android's schedule. |

## Repository contents

The repository contains application source, backend configuration, reusable tests, synthetic
data and maintenance tools. Local IDE state, credentials, account-state files, database
exports, assessment reports, evidence and generated builds are excluded.

Keep API keys, service-account keys and signing credentials outside Git. The completed
project's delivery history remains in the [changelog](../CHANGELOG.md).
