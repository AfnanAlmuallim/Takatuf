# Takatuf | تكاتف

Android app (Jetpack Compose + Firebase) for student team formation. This build covers
**Sprint 1** of the project charter: accounts, Skill Passports with visibility, project /
event / team-request listings, discovery with search, filters and saved items, applications,
and applicant review with shortlisting.

## Layout

```
app/                      Android app
  data/                   Firebase repositories, models, session
  domain/                 Skill matching experiment, validation, date rules (unit tested)
  ui/                     Compose screens, theme and shared components
firebase/                 Backend configuration
  firestore.rules         Security rules: every permission in the charter is enforced here
  firestore.indexes.json  Composite indexes the app's queries need
  tests/                  Rules tests (npm run test:rules)
  seed/                   Synthetic students, events and listings for the emulators
```

## Connect your Firebase project (once)

`app/google-services.json` points at the `takatuf-5a37f` project.

1. Firebase console → **Authentication** → Sign-in method → enable **Email/Password**.
2. Firebase console → **Firestore Database** → create the default database (production mode).
3. Deploy the rules and indexes (the app's list queries fail until the indexes exist):

   ```sh
   cd firebase
   npm install
   firebase login --reauth        # the CLI session on this machine has expired
   npm run deploy:firestore       # = firebase deploy --only firestore
   ```

Then run the app from Android Studio as usual.

## Develop against local emulators (no cloud needed)

```sh
cd firebase
npm run emulators                 # Auth :9099, Firestore :8080, UI http://127.0.0.1:4000
npm run seed                      # in a second terminal: 8 students, 4 events, 8 listings
```

Build the app pointed at the emulators (10.0.2.2 is your computer, seen from the Android emulator):

```sh
./gradlew installDebug -Ptakatuf.emulatorHost=10.0.2.2
```

Sign in with any seeded student, for example `sara@takatuf.test` / `Takatuf2026`.
Sara owns a project with four applicants, so both the applicant and owner journeys are ready to try.

## Tests

```sh
./gradlew testDebugUnitTest       # matching, validation, dates, listing states
cd firebase && npm run test:rules # 27 security-rule tests against the Firestore emulator
```

After deploying rules or indexes, check the real project end to end. The smoke test signs up three
throwaway `@takatuf.test` accounts, runs every Sprint 1 read, write and permission against
`takatuf-5a37f`, and `smoke:cleanup` deletes those accounts and everything they created:

```sh
cd firebase
npm run smoke                     # 24 checks
npm run smoke:cleanup             # needs `firebase login`
```
