# Takatuf | تكاتف

Android app (Jetpack Compose + Firebase) for student team formation. This build covers
**Sprints 1 and 2** of the project charter.

- **Sprint 1:** accounts, Skill Passports with visibility, project / event / team-request
  listings, discovery with search, filters and saved items, applications, and applicant review
  with shortlisting.
- **Sprint 2:** applicant fit (US09), suggested complementary teams (US10), comparing options
  and keeping people across them (US11), offers with joining confirmation (US12), the shared team
  workspace (US13), milestones (US14), reopening a place with reconfirmation (US15), and leaving
  or handing over leadership (US16). Optional AI role suggestions use OpenAI `gpt-6-luna`.

## Layout

```
app/                      Android app
  data/                   Firebase repositories, models, session, OpenAI client (data/ai)
  domain/                 Fit scoring and team matching, validation, date rules (unit tested)
  ui/                     Compose screens, theme and shared components
    team/                 Teams tab, suggested teams, offers, team workspace
firebase/                 Backend configuration
  firestore.rules         Security rules: every permission in the charter is enforced here
  firestore.indexes.json  Composite indexes the app's queries need
  tests/                  Rules tests (npm run test:rules)
  seed/                   Synthetic students, events, listings and a working team
```

## Sprint 2 source update

Android version **0.2.0** (version code **2**). See [CHANGELOG.md](CHANGELOG.md) for the
Sprint 2 changes.

Open this folder in Android Studio and use its bundled Gradle JDK with Android SDK 36 installed.
The Gradle wrapper is included. Firebase scripts also need Node.js, npm and the Firebase CLI
available on your PATH; emulator tests need Java. Use `npm ci` in `firebase/` to install the
locked test dependencies.

The source update excludes local credentials, IDE/build output, live test-account state, and
assessment reports/evidence. Publishing it to GitHub does not deploy Firebase rules or indexes.
AI role suggestions are optional, so a fresh checkout builds without an `.env` file.

## Connect your Firebase project (once)

`app/google-services.json` points at the `takatuf-5a37f` project.

1. Firebase console → **Authentication** → Sign-in method → enable **Email/Password**.
2. Firebase console → **Firestore Database** → create the default database (production mode).
3. Deploy the rules and indexes (the app's list queries fail until the indexes exist):

   ```sh
   cd firebase
   npm install
   firebase login --reauth        # if the CLI session has expired
   npm run deploy:firestore       # = firebase deploy --only firestore
   ```

Then run the app from Android Studio as usual.

## AI role suggestions (optional)

When a student writes a project's outcome and description, the editor can suggest roles and
skills. The student adds only the suggestions they choose; the LLM never ranks or picks people.
Matching applicants to teams is rule-based and runs on the phone (charter §6.4).

The app calls OpenAI's Responses API directly, so the project stays on Firebase's free Spark
plan. Put the key in `.env` at the project root (git ignores it; copy `.env.example` to start):

```sh
OPENAI_API_KEY=sk-...            # from https://platform.openai.com/api-keys
OPENAI_MODEL=gpt-6-luna
AI_DAILY_LIMIT=20                # suggestions each device may request per day
```

Gradle reads it at build time, so rebuild after changing it. With no key, the editor doesn't offer
suggestions; if OpenAI is unreachable or the daily limit is reached, it says so. Everything else
keeps working either way (NFR05).

> **The key is compiled into the APK**, and anyone who has the APK can extract it. Use a key made
> for this project, set a monthly budget limit on it in the OpenAI dashboard, and don't publish an
> APK built with it. For a public release the call would need to move behind a server.

## Develop against local emulators (no cloud needed)

```sh
cd firebase
npm run emulators                 # Auth :9099, Firestore :8080, UI http://127.0.0.1:4000
npm run seed                      # in a second terminal: 8 students, 4 events, 8 listings, 1 working team
```

Build the app pointed at the emulators (10.0.2.2 is your computer, seen from the Android emulator):

```sh
./gradlew installDebug -Ptakatuf.emulatorHost=10.0.2.2
```

Sign in with any seeded student, for example `sara@takatuf.test` / `Takatuf2026`.

- Sara owns a project with applicants, so both the applicant and owner journeys are ready to try.
- Faisal has already joined Sara's parking project, so its workspace has milestones, updates,
  links and responsibilities.
- Sara also has an offer waiting on Faisal's Lost and found project.

## Tests

```sh
./gradlew testDebugUnitTest       # fit scoring, team suggestions, OpenAI request/reply, validation, dates
cd firebase && npm run test:rules # 44 security-rule tests against the Firestore emulator
```

After deploying rules or indexes, check the real project end to end. The smoke test signs up three
throwaway `@takatuf.test` accounts, runs every Sprint 1 and Sprint 2 read, write and permission
against `takatuf-5a37f`, and `smoke:cleanup` deletes those accounts and everything they created:

```sh
cd firebase
npm run smoke                     # 38 checks
npm run smoke:cleanup             # needs `firebase login`
```
