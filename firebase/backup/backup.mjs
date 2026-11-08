// Backs up every Firestore document to one JSON file (NFR14). The Spark plan has no managed
// export, so this reads the database with firebase-admin instead.
//
//   Emulator:    FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 npm run backup
//   Production:  GOOGLE_APPLICATION_CREDENTIALS=C:\keys\takatuf-backup.json npm run backup
//
// Accounts are backed up separately with the Firebase CLI: firebase auth:export auth.json
import { mkdirSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { openFirestore, readAll, countByCollection, PROJECT, usingEmulator } from './firestore-io.mjs';

const db = openFirestore();
const started = Date.now();
const docs = await readAll(db);
const body = JSON.stringify(docs, null, 1);
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const dir = new URL('../backups/', import.meta.url);
mkdirSync(dir, { recursive: true });
const out = process.argv[2] ?? new URL(`firestore-${usingEmulator ? 'emulator' : PROJECT}-${stamp}.json`, dir).pathname.replace(/^\/([A-Za-z]:)/, '$1');
writeFileSync(out, body);

console.log(`Backed up ${Object.keys(docs).length} documents from ${usingEmulator ? 'the emulator' : PROJECT} in ${Date.now() - started} ms`);
console.log(countByCollection(docs));
console.log(`File: ${out}`);
console.log(`SHA-256: ${createHash('sha256').update(body).digest('hex')}`);
