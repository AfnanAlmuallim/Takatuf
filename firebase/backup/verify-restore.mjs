// Proves a backup can be restored (NFR14, US24): reads everything in the emulator, wipes the
// database, restores from the backup, reads everything again and checks the two match
// document for document. Runs only against the emulator.
//
//   npm run emulators && npm run seed     (in another terminal)
//   npm run backup:verify
import { writeFileSync, mkdirSync } from 'node:fs';
import { openFirestore, readAll, writeAll, wipeAll, countByCollection, usingEmulator } from './firestore-io.mjs';

if (!usingEmulator) {
  console.error('Set FIRESTORE_EMULATOR_HOST; this check wipes the database it runs against.');
  process.exit(1);
}
const db = openFirestore();
const canonical = (docs) => JSON.stringify(Object.keys(docs).sort().map((k) => [k, docs[k]]));

const t0 = Date.now();
const before = await readAll(db);
const t1 = Date.now();
await wipeAll(db);
const empty = await readAll(db);
const t2 = Date.now();
await writeAll(db, before);
const t3 = Date.now();
const after = await readAll(db);

const missing = Object.keys(before).filter((p) => !(p in after));
const extra = Object.keys(after).filter((p) => !(p in before));
const changed = Object.keys(before).filter((p) => p in after && JSON.stringify(before[p]) !== JSON.stringify(after[p]));
const ok = Object.keys(before).length > 0 && Object.keys(empty).length === 0 && missing.length === 0 && extra.length === 0 && changed.length === 0;

const report = {
  checkedAt: new Date().toISOString(),
  documents: Object.keys(before).length,
  collections: countByCollection(before),
  afterWipe: Object.keys(empty).length,
  missing, extra, changed,
  identical: canonical(before) === canonical(after),
  timingsMs: { backup: t1 - t0, restore: t3 - t2 },
  result: ok ? 'PASS' : 'FAIL',
};
mkdirSync(new URL('../backups/', import.meta.url), { recursive: true });
writeFileSync(new URL('../backups/restore-verification.json', import.meta.url), JSON.stringify(report, null, 2));
console.log(JSON.stringify(report, null, 2));
process.exit(ok ? 0 : 1);
