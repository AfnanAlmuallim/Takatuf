// Firestore security rules tests. Run with `npm run test:rules` (starts the Firestore emulator).
// Each block maps to a Sprint 1 acceptance criterion from the project charter.
import { after, before, beforeEach, describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch,
} from 'firebase/firestore';

let env;

const DAY = 24 * 60 * 60 * 1000;
const inDays = (n) => Timestamp.fromMillis(Date.now() + n * DAY);

const profile = (name, visibility = 'everyone') => ({
  displayName: name,
  major: 'Software Engineering',
  year: 3,
  headline: '',
  visibility,
  onboarded: true,
  updatedAt: serverTimestamp(),
});

const passport = () => ({
  skills: [{ name: 'Kotlin', level: 3 }],
  interests: ['Education'],
  experience: [],
  availability: { hoursPerWeek: 6, days: ['sun', 'mon'], note: '' },
  links: [],
  updatedAt: serverTimestamp(),
});

const role = { id: 'r1', title: 'Mobile developer', description: '', skills: ['Kotlin'], openings: 2 };

const project = (ownerId, overrides = {}) => ({
  kind: 'project',
  status: 'open',
  title: 'Smart parking finder',
  description: '',
  ownerId,
  ownerName: 'Sara',
  ownerMajor: 'Software Engineering',
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  outcome: 'A working Android prototype',
  roles: [role],
  hoursPerWeek: 6,
  duration: '8 weeks',
  teamSize: 1,
  openPlaces: 2,
  deadline: null,
  skills: ['kotlin'],
  ...overrides,
});

const event = (ownerId, overrides = {}) => ({
  kind: 'event',
  status: 'open',
  title: 'Najd Smart City Challenge',
  description: '',
  ownerId,
  ownerName: 'Sara',
  ownerMajor: '',
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  eventType: 'hackathon',
  organizer: 'Riyadh Tech Society',
  sourceUrl: 'https://example.com/hackathon',
  startDate: inDays(20),
  endDate: inDays(22),
  registrationDeadline: inDays(15),
  eligibility: 'University students in Saudi Arabia',
  teamSizeMin: 2,
  teamSizeMax: 4,
  format: 'in_person',
  location: 'Riyadh',
  ...overrides,
});

const teamRequest = (ownerId, eventId, overrides = {}) => ({
  ...project(ownerId),
  kind: 'team',
  title: 'Team for Najd Smart City Challenge',
  outcome: '',
  eventId,
  event: { title: 'Najd Smart City Challenge', organizer: 'Riyadh Tech Society', type: 'hackathon', teamSizeMin: 2, teamSizeMax: 4 },
  ...overrides,
});

const application = (listingId, ownerId, applicantId, overrides = {}) => ({
  listingId,
  ownerId,
  applicantId,
  applicantName: 'Nora',
  applicantMajor: 'Computer Science',
  listingTitle: 'Smart parking finder',
  listingKind: 'project',
  roleId: 'r1',
  roleTitle: 'Mobile developer',
  contribution: 'I built two Compose apps and can own the booking flow.',
  hoursPerWeek: 6,
  availabilityNote: '',
  status: 'submitted',
  reviewed: false,
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...overrides,
});

const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-takatuf-rules',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seed(async (db) => {
    await setDoc(doc(db, 'profiles/sara'), profile('Sara'));
    await setDoc(doc(db, 'profiles/nora'), profile('Nora'));
    await setDoc(doc(db, 'profiles/omar'), profile('Omar'));
    await setDoc(doc(db, 'listings/p1'), project('sara'));
  });
});

describe('US01 protected actions require an account', () => {
  it('signed-out visitors cannot read listings or profiles', async () => {
    await assertFails(getDoc(doc(anonymous(), 'listings/p1')));
    await assertFails(getDoc(doc(anonymous(), 'profiles/sara')));
  });

  it('a student creates only their own profile', async () => {
    await assertSucceeds(setDoc(doc(as('layla'), 'profiles/layla'), { ...profile('Layla'), createdAt: serverTimestamp() }));
    await assertFails(setDoc(doc(as('layla'), 'profiles/omar2'), profile('Not me')));
  });

  it('rejects an unknown visibility value', async () => {
    await assertFails(setDoc(doc(as('layla'), 'profiles/layla'), profile('Layla', 'friends')));
  });
});

describe('US02 passport visibility is enforced when someone else views it', () => {
  it('everyone: any signed-in student can read', async () => {
    await seed((db) => setDoc(doc(db, 'passports/nora'), passport()));
    await assertSucceeds(getDoc(doc(as('omar'), 'passports/nora')));
    await assertSucceeds(getDoc(doc(as('nora'), 'passports/nora')));
  });

  it('teams I apply to: only owners the student shared it with', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'profiles/nora'), profile('Nora', 'applied'));
      await setDoc(doc(db, 'passports/nora'), passport());
      await setDoc(doc(db, 'passports/nora/viewers/sara'), { listingId: 'p1', grantedAt: serverTimestamp() });
    });
    await assertSucceeds(getDoc(doc(as('sara'), 'passports/nora')));
    await assertFails(getDoc(doc(as('omar'), 'passports/nora')));
  });

  it('only me: hidden even from owners the student applied to', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'profiles/nora'), profile('Nora', 'private'));
      await setDoc(doc(db, 'passports/nora'), passport());
      await setDoc(doc(db, 'passports/nora/viewers/sara'), { listingId: 'p1', grantedAt: serverTimestamp() });
    });
    await assertFails(getDoc(doc(as('sara'), 'passports/nora')));
    await assertSucceeds(getDoc(doc(as('nora'), 'passports/nora')));
  });

  it('nobody edits another student’s passport', async () => {
    await assertSucceeds(setDoc(doc(as('nora'), 'passports/nora'), passport()));
    await assertFails(setDoc(doc(as('omar'), 'passports/nora'), passport()));
  });
});

describe('US03 project listings', () => {
  it('an owner publishes a valid listing in their own name', async () => {
    await assertSucceeds(setDoc(doc(as('nora'), 'listings/p2'), project('nora')));
    await assertFails(setDoc(doc(as('nora'), 'listings/p3'), project('sara')));
  });

  it('requires an outcome and at least one role', async () => {
    await assertFails(setDoc(doc(as('nora'), 'listings/p2'), project('nora', { outcome: '' })));
    await assertFails(setDoc(doc(as('nora'), 'listings/p2'), project('nora', { roles: [] })));
  });

  it('only the owner edits, and ownership cannot change', async () => {
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1'), { title: 'Campus parking finder', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as('omar'), 'listings/p1'), { title: 'Hijacked title', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as('sara'), 'listings/p1'), { ownerId: 'omar', updatedAt: serverTimestamp() }));
  });
});

describe('US04 competition and hackathon listings', () => {
  it('records organizer, source, dates, eligibility and team-size rules', async () => {
    await assertSucceeds(setDoc(doc(as('sara'), 'listings/e1'), event('sara')));
  });

  it('rejects a missing official source or impossible dates', async () => {
    await assertFails(setDoc(doc(as('sara'), 'listings/e1'), event('sara', { sourceUrl: 'not a link' })));
    await assertFails(setDoc(doc(as('sara'), 'listings/e1'), event('sara', { startDate: inDays(5), endDate: inDays(4) })));
    await assertFails(setDoc(doc(as('sara'), 'listings/e1'), event('sara', { teamSizeMin: 5, teamSizeMax: 3 })));
  });
});

describe('US05 team requests linked to an event', () => {
  beforeEach(async () => {
    await seed((db) => setDoc(doc(db, 'listings/e1'), event('omar')));
  });

  it('links to a real event within its team-size rule', async () => {
    await assertSucceeds(setDoc(doc(as('sara'), 'listings/t1'), teamRequest('sara', 'e1')));
  });

  it('refuses a team larger than the event allows', async () => {
    await assertFails(setDoc(doc(as('sara'), 'listings/t1'), teamRequest('sara', 'e1', { teamSize: 3, openPlaces: 2 })));
  });

  it('refuses a link to something that is not an event', async () => {
    await assertFails(setDoc(doc(as('sara'), 'listings/t1'), teamRequest('sara', 'p1')));
  });
});

describe('US07 apply and withdraw', () => {
  const ref = (uid) => doc(as(uid), 'listings/p1/applications', uid);

  it('a student applies to an open listing', async () => {
    await assertSucceeds(setDoc(ref('nora'), application('p1', 'sara', 'nora')));
  });

  it('prevents a duplicate active application', async () => {
    await setDoc(ref('nora'), application('p1', 'sara', 'nora'));
    await assertFails(setDoc(ref('nora'), application('p1', 'sara', 'nora')));
  });

  it('cannot apply in someone else’s name, to their own listing, or with a tampered owner', async () => {
    await assertFails(setDoc(doc(as('omar'), 'listings/p1/applications/nora'), application('p1', 'sara', 'nora')));
    await assertFails(setDoc(ref('sara'), application('p1', 'sara', 'sara')));
    await assertFails(setDoc(ref('nora'), application('p1', 'omar', 'nora')));
  });

  it('closed listings and passed deadlines accept no applications', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { status: 'closed' }));
    await assertFails(setDoc(ref('nora'), application('p1', 'sara', 'nora')));
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { status: 'open', deadline: inDays(-1) }));
    await assertFails(setDoc(ref('nora'), application('p1', 'sara', 'nora')));
  });

  it('withdrawal updates the status, and the student can apply again', async () => {
    await setDoc(ref('nora'), application('p1', 'sara', 'nora'));
    await assertSucceeds(updateDoc(ref('nora'), { status: 'withdrawn', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref('nora'), {
      status: 'submitted',
      reviewed: false,
      contribution: 'Still keen, and now free on Thursdays as well.',
      updatedAt: serverTimestamp(),
    }));
  });

  it('applying also shares the passport with the owner in one batch', async () => {
    const db = as('nora');
    const batch = writeBatch(db);
    batch.set(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora'));
    batch.set(doc(db, 'passports/nora/viewers/sara'), { listingId: 'p1', grantedAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
  });
});

describe('US08 review applicants', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora'));
    });
  });

  it('the owner lists applications and shortlists without touching anything else', async () => {
    await assertSucceeds(getDocs(collection(as('sara'), 'listings/p1/applications')));
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1/applications/nora'), { status: 'shortlisted', reviewed: true }));
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1/applications/nora'), { status: 'submitted' }));
    await assertFails(updateDoc(doc(as('sara'), 'listings/p1/applications/nora'), { contribution: 'Edited by the owner, which is not allowed.' }));
  });

  it('shortlisting cannot be used to change membership-like states', async () => {
    await assertFails(updateDoc(doc(as('sara'), 'listings/p1/applications/nora'), { status: 'accepted' }));
  });

  it('unrelated students can neither read nor manage applications', async () => {
    await assertFails(getDoc(doc(as('omar'), 'listings/p1/applications/nora')));
    await assertFails(getDocs(collection(as('omar'), 'listings/p1/applications')));
    await assertFails(updateDoc(doc(as('omar'), 'listings/p1/applications/nora'), { status: 'shortlisted' }));
  });

  it('applicants cannot shortlist themselves', async () => {
    await assertFails(updateDoc(doc(as('nora'), 'listings/p1/applications/nora'), { status: 'shortlisted' }));
  });

  it('collection-group queries only return a student’s own applications', async () => {
    await assertSucceeds(getDocs(query(collectionGroup(as('nora'), 'applications'), where('applicantId', '==', 'nora'), orderBy('updatedAt', 'desc'))));
    await assertSucceeds(getDocs(query(collectionGroup(as('sara'), 'applications'), where('ownerId', '==', 'sara'), orderBy('updatedAt', 'desc'))));
    await assertFails(getDocs(query(collectionGroup(as('omar'), 'applications'), where('applicantId', '==', 'nora'))));
    await assertFails(getDocs(collectionGroup(as('omar'), 'applications')));
  });
});

describe('US06 saved listings are private', () => {
  it('a student saves and removes their own; others cannot read them', async () => {
    await assertSucceeds(setDoc(doc(as('nora'), 'users/nora/saved/p1'), { savedAt: serverTimestamp() }));
    await assertFails(getDocs(collection(as('omar'), 'users/nora/saved')));
    await assertSucceeds(deleteDoc(doc(as('nora'), 'users/nora/saved/p1')));
  });
});
