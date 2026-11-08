// Restores a backup made by backup.mjs (NFR14). Documents in the file replace documents at
// the same paths; nothing else is touched.
//
//   Emulator:    FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run restore -- backups/<file>.json
//   Production:  GOOGLE_APPLICATION_CREDENTIALS=... npm run restore -- backups/<file>.json --production
//
// A real project is only written with --production, so a restore can't land there by accident.
import { readFileSync } from 'node:fs';
import { openFirestore, writeAll, countByCollection, PROJECT, usingEmulator } from './firestore-io.mjs';

const file = process.argv.find((a) => a.endsWith('.json'));
if (!file) {
  console.error('Usage: npm run restore -- <backup.json> [--production]');
  process.exit(1);
}
if (!usingEmulator && !process.argv.includes('--production')) {
  console.error(`Not restoring into ${PROJECT} without --production.`);
  process.exit(1);
}
const docs = JSON.parse(readFileSync(file, 'utf8'));
const db = openFirestore();
const started = Date.now();
const written = await writeAll(db, docs);
console.log(`Restored ${written} documents into ${usingEmulator ? 'the emulator' : PROJECT} in ${Date.now() - started} ms`);
console.log(countByCollection(docs));
