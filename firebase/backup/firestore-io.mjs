// Shared pieces for backup and restore: connecting with firebase-admin and turning Firestore
// values into plain JSON and back without losing their types.
import { readFileSync } from 'node:fs';
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore, Timestamp, GeoPoint, DocumentReference, FieldValue } from 'firebase-admin/firestore';

export const PROJECT = process.env.GCLOUD_PROJECT
  ?? JSON.parse(readFileSync(new URL('../.firebaserc', import.meta.url), 'utf8')).projects.default;

/** True when the scripts are pointed at the local emulator rather than a real project. */
export const usingEmulator = Boolean(process.env.FIRESTORE_EMULATOR_HOST);

/**
 * Opens Firestore. Against the emulator no credentials are needed. Against the real project,
 * GOOGLE_APPLICATION_CREDENTIALS must point at a service-account key with Firestore access;
 * the key stays outside the repository.
 */
export function openFirestore() {
  const app = usingEmulator ? initializeApp({ projectId: PROJECT }) : initializeApp({ credential: applicationDefault(), projectId: PROJECT });
  return getFirestore(app);
}

/** A Firestore value as JSON. Special types carry a "__type" tag so restore can rebuild them. */
export function toJson(value) {
  if (value === null || value === undefined) return null;
  if (value instanceof Timestamp) return { __type: 'timestamp', seconds: value.seconds, nanoseconds: value.nanoseconds };
  if (value instanceof GeoPoint) return { __type: 'geopoint', latitude: value.latitude, longitude: value.longitude };
  if (value instanceof DocumentReference) return { __type: 'reference', path: value.path };
  if (Buffer.isBuffer(value) || value instanceof Uint8Array) return { __type: 'bytes', base64: Buffer.from(value).toString('base64') };
  if (Array.isArray(value)) return value.map(toJson);
  if (typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toJson(v)]));
  return value;
}

export function fromJson(value, db) {
  if (value === null || value === undefined) return null;
  if (Array.isArray(value)) return value.map((v) => fromJson(v, db));
  if (typeof value === 'object') {
    switch (value.__type) {
      case 'timestamp': return new Timestamp(value.seconds, value.nanoseconds);
      case 'geopoint': return new GeoPoint(value.latitude, value.longitude);
      case 'reference': return db.doc(value.path);
      case 'bytes': return Buffer.from(value.base64, 'base64');
      default: return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, fromJson(v, db)]));
    }
  }
  return value;
}

/**
 * Every document in the database, depth first, including subcollections and documents that
 * only exist as parents of subcollections (listDocuments finds those; a query would not).
 */
export async function readAll(db) {
  const docs = {};
  async function walk(collections) {
    for (const collection of collections) {
      const refs = await collection.listDocuments();
      for (const ref of refs) {
        const snap = await ref.get();
        if (snap.exists) docs[ref.path] = toJson(snap.data());
        await walk(await ref.listCollections());
      }
    }
  }
  await walk(await db.listCollections());
  return docs;
}

/** Writes documents in batches of 400; existing documents at the same paths are replaced. */
export async function writeAll(db, docs) {
  const paths = Object.keys(docs);
  for (let i = 0; i < paths.length; i += 400) {
    const batch = db.batch();
    for (const path of paths.slice(i, i + 400)) batch.set(db.doc(path), fromJson(docs[path], db));
    await batch.commit();
  }
  return paths.length;
}

/** Deletes every document, subcollections included. Only ever used against the emulator. */
export async function wipeAll(db) {
  if (!usingEmulator) throw new Error('Refusing to wipe a real project.');
  for (const collection of await db.listCollections()) await db.recursiveDelete(collection);
}

/** Documents per top-level collection, for the backup summary. */
export function countByCollection(docs) {
  const counts = {};
  for (const path of Object.keys(docs)) {
    const top = path.split('/')[0];
    counts[top] = (counts[top] ?? 0) + 1;
  }
  return counts;
}

export { FieldValue };
