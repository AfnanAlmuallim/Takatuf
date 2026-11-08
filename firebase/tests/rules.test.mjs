// Firestore security rules tests. Run with `npm run test:rules` (starts the Firestore emulator).
// Each block maps to a Sprint 1, 2 or 3 acceptance criterion from the project charter.
import { after, before, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  arrayRemove,
  arrayUnion,
  collection,
  collectionGroup,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  limit,
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

/** Display names match the seeded profiles: 'sara' is Sara. The rules check this. */
const nameOf = (uid) => uid.charAt(0).toUpperCase() + uid.slice(1);

const project = (ownerId, overrides = {}) => ({
  kind: 'project',
  status: 'open',
  hidden: false,
  title: 'Smart parking finder',
  description: '',
  ownerId,
  ownerName: nameOf(ownerId),
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
  hidden: false,
  title: 'Najd Smart City Challenge',
  description: '',
  ownerId,
  ownerName: nameOf(ownerId),
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
  applicantName: nameOf(applicantId),
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
      await setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora'));
    });
    await assertSucceeds(getDoc(doc(as('sara'), 'passports/nora')));
    await assertFails(getDoc(doc(as('omar'), 'passports/nora')));
  });

  it('teams I apply to: access ends when the student withdraws or the lead hands the team over', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'profiles/nora'), profile('Nora', 'applied'));
      await setDoc(doc(db, 'passports/nora'), passport());
      await setDoc(doc(db, 'passports/nora/viewers/sara'), { listingId: 'p1', grantedAt: serverTimestamp() });
      await setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora', { status: 'withdrawn' }));
    });
    await assertFails(getDoc(doc(as('sara'), 'passports/nora')));
    await seed(async (db) => {
      await updateDoc(doc(db, 'listings/p1/applications/nora'), { status: 'submitted' });
      await updateDoc(doc(db, 'listings/p1'), { ownerId: 'omar', ownerName: 'Omar' });
    });
    await assertFails(getDoc(doc(as('sara'), 'passports/nora')));
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

// ---------- Sprint 2 ----------
// Writes below mirror the app's repositories exactly, so a rule change that breaks the app
// fails here first.

const memberEntry = (name, roleId = 'r1', roleTitle = 'Mobile developer') => ({
  name,
  major: 'Computer Science',
  roleId,
  roleTitle,
  joinedAt: Timestamp.now(),
});

const offer = (overrides = {}) => ({
  status: 'offered',
  reviewed: true,
  offeredAt: Timestamp.now(),
  offerExpiresAt: inDays(3),
  ...overrides,
});

// Nora is on Sara's team; one place is left.
async function seedTeam() {
  await seed(async (db) => {
    await updateDoc(doc(db, 'listings/p1'), {
      roles: [{ ...role, openings: 1 }],
      openPlaces: 1,
      teamSize: 2,
      members: { nora: memberEntry('Nora') },
      memberIds: ['nora'],
    });
    await setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora', { status: 'accepted' }));
  });
}

// What ApplicationRepository.acceptOffer writes, as one atomic batch.
function acceptBatch(uid, before = { openings: 2, openPlaces: 2, teamSize: 1 }) {
  const db = as(uid);
  const batch = writeBatch(db);
  batch.update(doc(db, 'listings/p1'), {
    roles: [{ ...role, openings: before.openings - 1 }],
    openPlaces: before.openPlaces - 1,
    teamSize: before.teamSize + 1,
    [`members.${uid}`]: {
      name: 'Nora',
      major: 'Computer Science',
      roleId: 'r1',
      roleTitle: 'Mobile developer',
      joinedAt: serverTimestamp(),
    },
    memberIds: arrayUnion(uid),
    updatedAt: serverTimestamp(),
  });
  batch.update(doc(db, `listings/p1/applications/${uid}`), {
    status: 'accepted',
    respondedAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  return batch.commit();
}

describe('US12 offers and joining confirmation', () => {
  beforeEach(async () => {
    await seed((db) => setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora')));
  });

  it('the owner offers a place for up to a week, and nobody else can', async () => {
    const send = (uid, days) => updateDoc(doc(as(uid), 'listings/p1/applications/nora'), {
      status: 'offered',
      reviewed: true,
      offeredAt: serverTimestamp(),
      offerExpiresAt: inDays(days),
    });
    await assertFails(send('nora', 3));
    await assertFails(send('omar', 3));
    await assertFails(send('sara', 10));
    await assertSucceeds(send('sara', 3));
  });

  it('only the intended applicant accepts, and joining happens in the same write', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1/applications/nora'), offer()));
    // Marking the offer accepted without joining, or joining without accepting, both fail.
    await assertFails(updateDoc(doc(as('nora'), 'listings/p1/applications/nora'), {
      status: 'accepted', respondedAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(as('nora'), 'listings/p1'), {
      teamSize: 2, openPlaces: 1, memberIds: arrayUnion('nora'), updatedAt: serverTimestamp(),
    }));
    await assertFails(acceptBatch('omar'));
    await assertSucceeds(acceptBatch('nora'));
  });

  it('an expired offer cannot be accepted', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1/applications/nora'), offer({ offerExpiresAt: inDays(-1) })));
    await assertFails(acceptBatch('nora'));
  });

  it('a full team refuses another acceptance', async () => {
    await seed(async (db) => {
      await updateDoc(doc(db, 'listings/p1'), { roles: [{ ...role, openings: 0 }], openPlaces: 0 });
      await updateDoc(doc(db, 'listings/p1/applications/nora'), offer());
    });
    await assertFails(acceptBatch('nora', { openings: 0, openPlaces: 0, teamSize: 1 }));
  });

  it('the applicant declines, and the owner can take an offer back', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1/applications/nora'), offer()));
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1/applications/nora'), { status: 'shortlisted' }));
    await seed((db) => updateDoc(doc(db, 'listings/p1/applications/nora'), offer()));
    await assertSucceeds(updateDoc(doc(as('nora'), 'listings/p1/applications/nora'), {
      status: 'declined', respondedAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });
});

const milestone = (createdBy, assigneeId, overrides = {}) => ({
  title: 'First working prototype',
  assigneeId,
  assigneeName: 'Nora',
  dueDate: inDays(7),
  status: 'todo',
  createdBy,
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  completedAt: null,
  ...overrides,
});

describe('US13 and US14 the shared workspace and milestones', () => {
  beforeEach(seedTeam);

  it('the lead and members record milestones; outsiders can neither read nor write', async () => {
    await assertSucceeds(setDoc(doc(as('nora'), 'listings/p1/milestones/m1'), milestone('nora', 'nora')));
    await assertSucceeds(setDoc(doc(as('sara'), 'listings/p1/milestones/m2'), milestone('sara', 'nora')));
    await assertFails(setDoc(doc(as('omar'), 'listings/p1/milestones/m3'), milestone('omar', 'omar')));
    await assertFails(getDocs(collection(as('omar'), 'listings/p1/milestones')));
    await assertSucceeds(getDocs(collection(as('nora'), 'listings/p1/milestones')));
  });

  it('a milestone is assigned to someone on the team', async () => {
    await assertFails(setDoc(doc(as('sara'), 'listings/p1/milestones/m1'), milestone('sara', 'omar', { assigneeName: 'Omar' })));
  });

  it('the assignee updates progress, and done stays distinguishable', async () => {
    await seed((db) => setDoc(doc(db, 'listings/p1/milestones/m1'), { ...milestone('sara', 'nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    await assertSucceeds(updateDoc(doc(as('nora'), 'listings/p1/milestones/m1'), {
      status: 'done', completedAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(as('omar'), 'listings/p1/milestones/m1'), { status: 'todo', updatedAt: serverTimestamp() }));
  });

  it('updates, links and responsibilities are private to the team', async () => {
    await assertSucceeds(setDoc(doc(as('nora'), 'listings/p1/updates/u1'), {
      authorId: 'nora', authorName: 'Nora', text: 'Login screen done.', link: '', createdAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(as('nora'), 'listings/p1/deliverables/d1'), {
      label: 'Repository', url: 'https://github.com/example/parking', addedBy: 'nora', addedByName: 'Nora', createdAt: serverTimestamp(),
    }));
    await assertSucceeds(setDoc(doc(as('nora'), 'listings/p1/responsibilities/nora'), {
      text: 'Booking flow', updatedBy: 'nora', updatedAt: serverTimestamp(),
    }));
    // Members set only their own responsibility; the lead sets anyone's.
    await assertFails(setDoc(doc(as('nora'), 'listings/p1/responsibilities/sara'), {
      text: 'Everything else', updatedBy: 'nora', updatedAt: serverTimestamp(),
    }));
    await assertFails(getDocs(collection(as('omar'), 'listings/p1/updates')));
    await assertFails(getDocs(collection(as('omar'), 'listings/p1/deliverables')));
  });
});

describe('US15 reopening a vacancy', () => {
  beforeEach(async () => {
    await seedTeam();
    await seed(async (db) => {
      await updateDoc(doc(db, 'listings/p1'), { roles: [{ ...role, openings: 0 }], openPlaces: 0 });
      await setDoc(doc(db, 'listings/p1/applications/omar'), application('p1', 'sara', 'omar', { applicantName: 'Omar' }));
      await setDoc(doc(db, 'listings/p1/milestones/m1'), { ...milestone('sara', 'nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    });
  });

  it('the lead reopens a place and earlier applicants must reconfirm before being reconsidered', async () => {
    const db = as('sara');
    const batch = writeBatch(db);
    batch.update(doc(db, 'listings/p1'), {
      roles: [{ ...role, openings: 1 }],
      openPlaces: 1,
      status: 'open',
      reopenedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    batch.update(doc(db, 'listings/p1/applications/omar'), {
      status: 'reconfirm', reviewed: false, reconfirmRequestedAt: serverTimestamp(),
    });
    await assertSucceeds(batch.commit());
    await assertSucceeds(updateDoc(doc(as('omar'), 'listings/p1/applications/omar'), {
      status: 'submitted', reviewed: false, updatedAt: serverTimestamp(),
    }));
    // The workspace carries on untouched.
    await assertSucceeds(getDoc(doc(as('nora'), 'listings/p1/milestones/m1')));
  });

  it('an applicant whose offer expired can be asked to reconfirm too', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1/applications/omar'), offer({ offerExpiresAt: inDays(-1) })));
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1/applications/omar'), {
      status: 'reconfirm', reviewed: false, reconfirmRequestedAt: serverTimestamp(),
    }));
  });

  it('even the lead cannot add a member directly; joining takes an accepted offer', async () => {
    await assertFails(updateDoc(doc(as('sara'), 'listings/p1'), {
      memberIds: arrayUnion('omar'), teamSize: 3, updatedAt: serverTimestamp(),
    }));
  });
});

describe('US16 departure and leadership transfer', () => {
  beforeEach(seedTeam);

  it('a member leaves, and their workspace access ends with it', async () => {
    const db = as('nora');
    const batch = writeBatch(db);
    batch.update(doc(db, 'listings/p1'), {
      'members.nora': deleteField(),
      memberIds: arrayRemove('nora'),
      teamSize: 1,
      updatedAt: serverTimestamp(),
    });
    batch.update(doc(db, 'listings/p1/applications/nora'), { status: 'left', updatedAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
    await assertFails(getDocs(collection(as('nora'), 'listings/p1/milestones')));
  });

  it('leadership changes only after the nominee accepts', async () => {
    const nominate = { pendingLeader: { uid: 'nora', name: 'Nora', requestedAt: serverTimestamp() }, updatedAt: serverTimestamp() };
    await assertFails(updateDoc(doc(as('nora'), 'listings/p1'), nominate));
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1'), nominate));
    const takeOver = (uid) => updateDoc(doc(as(uid), 'listings/p1'), {
      ownerId: uid,
      ownerName: 'Nora',
      ownerMajor: 'Computer Science',
      pendingLeader: null,
      [`members.${uid}`]: deleteField(),
      'members.sara': { name: 'Sara', major: 'Software Engineering', roleId: '', roleTitle: '', joinedAt: Timestamp.now() },
      memberIds: ['sara'],
      updatedAt: serverTimestamp(),
    });
    await assertFails(takeOver('omar'));
    await assertSucceeds(takeOver('nora'));
  });

  it('a declined transfer keeps the current lead', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { pendingLeader: { uid: 'nora', name: 'Nora', requestedAt: Timestamp.now() } }));
    await assertSucceeds(updateDoc(doc(as('nora'), 'listings/p1'), { pendingLeader: null, updatedAt: serverTimestamp() }));
    const after = await getDoc(doc(as('sara'), 'listings/p1'));
    if (after.data().ownerId !== 'sara') throw new Error('The lead changed after a declined transfer');
  });

  it('after a handover, the new lead takes over the team’s applications', async () => {
    await seed(async (db) => {
      await updateDoc(doc(db, 'listings/p1'), {
        ownerId: 'nora', ownerName: 'Nora',
        members: { sara: { ...memberEntry('Sara', '', '') } },
        memberIds: ['sara'],
      });
      await setDoc(doc(db, 'listings/p1/applications/omar'), application('p1', 'sara', 'omar', { applicantName: 'Omar' }));
    });
    await assertFails(updateDoc(doc(as('omar'), 'listings/p1/applications/omar'), { ownerId: 'omar' }));
    await assertSucceeds(updateDoc(doc(as('nora'), 'listings/p1/applications/omar'), { ownerId: 'nora' }));
    // Omar shared his passport with the listing's lead when applying, so the new lead may see it.
    await seed((db) => setDoc(doc(db, 'profiles/omar'), profile('Omar', 'applied')));
    await seed((db) => setDoc(doc(db, 'passports/omar'), passport()));
    const grant = { listingId: 'p1', grantedAt: serverTimestamp() };
    await assertFails(getDoc(doc(as('nora'), 'passports/omar')));
    await assertFails(setDoc(doc(as('sara'), 'passports/omar/viewers/nora'), grant));
    await assertFails(setDoc(doc(as('nora'), 'passports/sara/viewers/nora'), grant));
    await assertSucceeds(setDoc(doc(as('nora'), 'passports/omar/viewers/nora'), grant));
    await assertSucceeds(getDoc(doc(as('nora'), 'passports/omar')));
  });

  it('the lead removes a member, who then loses access', async () => {
    const db = as('sara');
    const batch = writeBatch(db);
    batch.update(doc(db, 'listings/p1'), {
      'members.nora': deleteField(),
      memberIds: arrayRemove('nora'),
      teamSize: 1,
      updatedAt: serverTimestamp(),
    });
    batch.update(doc(db, 'listings/p1/applications/nora'), { status: 'removed' });
    await assertSucceeds(batch.commit());
    await assertFails(getDocs(collection(as('nora'), 'listings/p1/updates')));
  });
});

// ---------- Defect retests (Takatuf_Sprint_1_and_2_Defects.md) ----------

describe('Defect 1 (I10): linked recruitment follows its parent event', () => {
  const apply = (uid) => setDoc(doc(as(uid), 'listings/t1/applications', uid), application('t1', 'sara', uid, {
    listingKind: 'team', listingTitle: 'Team for Najd Smart City Challenge',
  }));

  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'listings/e1'), event('omar'));
      // The request still carries the event's original, future dates.
      await setDoc(doc(db, 'listings/t1'), teamRequest('sara', 'e1', {
        event: { title: 'Najd Smart City Challenge', organizer: 'Riyadh Tech Society', type: 'hackathon', teamSizeMin: 2, teamSizeMax: 4, startDate: inDays(20), endDate: inDays(22) },
        createdAt: Timestamp.now(),
        updatedAt: Timestamp.now(),
      }));
    });
  });

  it('accepts applications while the event is still on', async () => {
    await assertSucceeds(apply('nora'));
  });

  it('refuses applications once the organiser moves the event into the past', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/e1'), { startDate: inDays(-3), endDate: inDays(-1), registrationDeadline: inDays(-4) }));
    await assertFails(apply('nora'));
  });

  it('refuses re-applying and reconfirming after the event ended', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'listings/t1/applications/nora'), application('t1', 'sara', 'nora', { listingKind: 'team', status: 'withdrawn' }));
      await setDoc(doc(db, 'listings/t1/applications/omar'), application('t1', 'sara', 'omar', { listingKind: 'team', status: 'reconfirm' }));
      await updateDoc(doc(db, 'listings/e1'), { startDate: inDays(-3), endDate: inDays(-1), registrationDeadline: inDays(-4) });
    });
    await assertFails(updateDoc(doc(as('nora'), 'listings/t1/applications/nora'), {
      status: 'submitted', reviewed: false, contribution: 'Trying again after the event ended.', updatedAt: serverTimestamp(),
    }));
    await assertFails(updateDoc(doc(as('omar'), 'listings/t1/applications/omar'), { status: 'submitted', reviewed: false, updatedAt: serverTimestamp() }));
  });
});

describe('Defect 5 (D01): milestones need a target date', () => {
  beforeEach(seedTeam);
  const ms = (uid, id) => doc(as(uid), 'listings/p1/milestones', id);

  it('rejects a new milestone with no date or a null date', async () => {
    const { dueDate, ...noDate } = milestone('nora', 'nora');
    await assertFails(setDoc(ms('nora', 'm1'), noDate));
    await assertFails(setDoc(ms('nora', 'm2'), milestone('nora', 'nora', { dueDate: null })));
  });

  it('accepts a dated milestone and authorised progress updates', async () => {
    await assertSucceeds(setDoc(ms('nora', 'm1'), milestone('nora', 'nora')));
    await assertSucceeds(updateDoc(ms('nora', 'm1'), { status: 'doing', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ms('sara', 'm1'), { dueDate: inDays(10), updatedAt: serverTimestamp() }));
  });

  it('rejects removing the date from a milestone', async () => {
    await seed((db) => setDoc(doc(db, 'listings/p1/milestones/m1'), { ...milestone('nora', 'nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    await assertFails(updateDoc(ms('nora', 'm1'), { dueDate: null, updatedAt: serverTimestamp() }));
  });

  it('lets an undated milestone saved before the fix still record progress', async () => {
    await seed((db) => setDoc(doc(db, 'listings/p1/milestones/old'), {
      ...milestone('nora', 'nora'), dueDate: null, createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    }));
    await assertSucceeds(updateDoc(ms('nora', 'old'), { status: 'done', completedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    // Giving it a date is allowed; it then counts as dated like any other.
    await assertSucceeds(updateDoc(ms('sara', 'old'), { dueDate: inDays(5), updatedAt: serverTimestamp() }));
  });
});

describe('Defect 6 (D02): progress-update links must be web links', () => {
  beforeEach(seedTeam);
  const post = (id, extra) => setDoc(doc(as('nora'), 'listings/p1/updates', id), {
    authorId: 'nora', authorName: 'Nora', text: 'Login screen done.', createdAt: serverTimestamp(), ...extra,
  });

  it('rejects malformed links sent directly to the backend', async () => {
    for (const [i, link] of ['not-a-url', 'https://', 'ftp://example.org/file', 'https://localhost/x', 'https://exa mple.org', `https://example.org/${'a'.repeat(300)}`].entries()) {
      await assertFails(post(`bad${i}`, { link }));
    }
  });

  it('accepts valid links and updates without a link', async () => {
    await assertSucceeds(post('u1', { link: 'https://github.com/example/parking' }));
    await assertSucceeds(post('u2', { link: 'http://docs.example.org' }));
    await assertSucceeds(post('u3', { link: '' }));
    await assertSucceeds(post('u4', {}));
  });

  it('deliverable links follow the same policy', async () => {
    const add = (id, url) => setDoc(doc(as('nora'), 'listings/p1/deliverables', id), {
      label: 'Repository', url, addedBy: 'nora', addedByName: 'Nora', createdAt: serverTimestamp(),
    });
    await assertFails(add('d1', 'not-a-url'));
    await assertFails(add('d2', 'https://localhost'));
    await assertSucceeds(add('d3', 'https://github.com/example/parking'));
  });
});

describe('Additional verification: direct requests', () => {
  it('two simultaneous applications from the same student create only one', async () => {
    const db = as('nora');
    const results = await Promise.allSettled([
      setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora')),
      setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora', { contribution: 'A second copy sent at the same moment.' })),
    ]);
    const succeeded = results.filter((r) => r.status === 'fulfilled').length;
    if (succeeded !== 1) throw new Error(`expected exactly one to succeed, got ${succeeded}`);
  });

  it('nobody but the owner can change any listing field', async () => {
    for (const change of [
      { title: 'Hijacked title' },
      { roles: [{ ...role, openings: 9 }], openPlaces: 9 },
      { status: 'closed' },
      { ownerId: 'omar', ownerName: 'Omar' },
      { deadline: inDays(30) },
    ]) {
      await assertFails(updateDoc(doc(as('omar'), 'listings/p1'), { ...change, updatedAt: serverTimestamp() }));
    }
  });

  it('an owner edits every editable field of their listing', async () => {
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1'), {
      title: 'Smart parking finder v2',
      description: 'Updated description',
      outcome: 'A tested Android prototype with live data',
      roles: [{ ...role, title: 'Android developer', skills: ['Kotlin', 'Compose'], openings: 3 }],
      openPlaces: 3,
      hoursPerWeek: 8,
      duration: 'One semester',
      teamSize: 2,
      deadline: inDays(20),
      skills: ['kotlin', 'compose'],
      updatedAt: serverTimestamp(),
    }));
  });

  it('a student cannot read or write another student’s saved listings', async () => {
    await seed((db) => setDoc(doc(db, 'users/nora/saved/p1'), { savedAt: Timestamp.now() }));
    await assertFails(getDoc(doc(as('omar'), 'users/nora/saved/p1')));
    await assertFails(setDoc(doc(as('omar'), 'users/nora/saved/p2'), { savedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as('omar'), 'users/nora/saved/p1')));
  });
});

// ---------- Sprint 3 ----------

const participation = (listingId, role, overrides = {}) => ({
  listingId,
  listingTitle: 'Smart parking finder',
  kind: 'project',
  role,
  wasLead: false,
  status: 'active',
  startedAt: Timestamp.now(),
  endedAt: null,
  updatedAt: serverTimestamp(),
  ...overrides,
});

const contribution = (recordedBy, overrides = {}) => ({
  listingId: 'p1',
  listingTitle: 'Smart parking finder',
  title: 'Built the booking flow',
  detail: 'Designed and coded the booking screens.',
  links: ['https://github.com/example/parking/pull/12'],
  recordedBy,
  recordedByName: recordedBy === 'sara' ? 'Sara' : 'Nora',
  confirmedBy: null,
  confirmedByName: null,
  confirmedAt: null,
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...overrides,
});

const review = (reviewerUid, overrides = {}) => ({
  listingId: 'p1',
  listingTitle: 'Smart parking finder',
  reviewerUid,
  reviewerName: reviewerUid === 'sara' ? 'Sara' : 'Nora',
  contribution: 4,
  leadership: null,
  comment: 'Reliable and clear in reviews.',
  disputed: false,
  disputeReason: '',
  disputedAt: null,
  hidden: false,
  moderationNote: '',
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...overrides,
});

// Sara leads p1 and Nora is a member; both have active participation records.
async function seedRecords() {
  await seedTeam();
  await seed(async (db) => {
    await setDoc(doc(db, 'passports/sara'), passport());
    await setDoc(doc(db, 'passports/nora'), passport());
    await setDoc(doc(db, 'passports/sara/participation/p1'), { ...participation('p1', 'Lead', { wasLead: true }), updatedAt: Timestamp.now() });
    await setDoc(doc(db, 'passports/nora/participation/p1'), { ...participation('p1', 'Mobile developer'), updatedAt: Timestamp.now() });
  });
}

const completeProject = () => seed((db) => updateDoc(doc(db, 'listings/p1'), {
  completedAt: Timestamp.now(), completion: { summary: 'Shipped and tested with 30 students.', link: 'https://example.org/demo' },
}));

describe('US17 participation records', () => {
  beforeEach(seedTeam);

  it('the lead and members record their own active participation; strangers cannot', async () => {
    await assertSucceeds(setDoc(doc(as('sara'), 'passports/sara/participation/p1'), participation('p1', 'Lead', { wasLead: true })));
    await assertSucceeds(setDoc(doc(as('nora'), 'passports/nora/participation/p1'), participation('p1', 'Mobile developer')));
    await assertFails(setDoc(doc(as('omar'), 'passports/omar/participation/p1'), participation('p1', 'Mobile developer')));
    // A member cannot claim to have led the team.
    await assertFails(setDoc(doc(as('nora'), 'passports/nora/participation/p1'), participation('p1', 'Lead', { wasLead: true })));
    // Nobody writes someone else's record outside the lead's own transitions.
    await assertFails(setDoc(doc(as('sara'), 'passports/nora/participation/p1'), participation('p1', 'Mobile developer')));
  });

  it('completion is only recorded once the lead completes the project', async () => {
    await seed((db) => setDoc(doc(db, 'passports/nora/participation/p1'), { ...participation('p1', 'Mobile developer'), updatedAt: Timestamp.now() }));
    const done = { status: 'completed', endedAt: serverTimestamp(), updatedAt: serverTimestamp() };
    await assertFails(updateDoc(doc(as('sara'), 'passports/nora/participation/p1'), done));
    await assertFails(updateDoc(doc(as('nora'), 'listings/p1'), { completedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1'), {
      completedAt: serverTimestamp(), completion: { summary: 'Shipped.', link: '' }, updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(as('sara'), 'passports/nora/participation/p1'), done));
    await assertFails(updateDoc(doc(as('omar'), 'passports/nora/participation/p1'), { updatedAt: serverTimestamp() }));
  });

  it('a completed project takes no more applications', async () => {
    await completeProject();
    await assertFails(setDoc(doc(as('omar'), 'listings/p1/applications/omar'), application('p1', 'sara', 'omar')));
  });

  it('leaving and removal end the record, and only when the person is off the team', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'passports/nora/participation/p1'), { ...participation('p1', 'Mobile developer'), updatedAt: Timestamp.now() });
    });
    const left = { status: 'left', endedAt: serverTimestamp(), updatedAt: serverTimestamp() };
    await assertFails(updateDoc(doc(as('nora'), 'passports/nora/participation/p1'), left));
    const db = as('nora');
    const batch = writeBatch(db);
    batch.update(doc(db, 'listings/p1'), { 'members.nora': deleteField(), memberIds: arrayRemove('nora'), teamSize: 1, updatedAt: serverTimestamp() });
    batch.update(doc(db, 'listings/p1/applications/nora'), { status: 'left', updatedAt: serverTimestamp() });
    batch.update(doc(db, 'passports/nora/participation/p1'), left);
    await assertSucceeds(batch.commit());
  });

  it('the lead records a removal in the same write that removes the member', async () => {
    await seed((db) => setDoc(doc(db, 'passports/nora/participation/p1'), { ...participation('p1', 'Mobile developer'), updatedAt: Timestamp.now() }));
    const db = as('sara');
    const batch = writeBatch(db);
    batch.update(doc(db, 'listings/p1'), { 'members.nora': deleteField(), memberIds: arrayRemove('nora'), teamSize: 1, updatedAt: serverTimestamp() });
    batch.update(doc(db, 'listings/p1/applications/nora'), { status: 'removed' });
    batch.update(doc(db, 'passports/nora/participation/p1'), { status: 'removed', endedAt: serverTimestamp(), updatedAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
  });

  it('records follow passport visibility', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'profiles/nora'), profile('Nora', 'private'));
      await setDoc(doc(db, 'passports/nora/participation/p1'), { ...participation('p1', 'Mobile developer'), updatedAt: Timestamp.now() });
    });
    await assertFails(getDoc(doc(as('omar'), 'passports/nora/participation/p1')));
    await assertSucceeds(getDoc(doc(as('nora'), 'passports/nora/participation/p1')));
    await assertSucceeds(getDoc(doc(as('sara'), 'passports/nora/participation/p1')));
  });
});

describe('US17 contributions', () => {
  beforeEach(seedRecords);
  const ref = (who, uid, id) => doc(as(who), `passports/${uid}/contributions/${id}`);

  it('participants record their own contributions as self-reported', async () => {
    await assertSucceeds(setDoc(ref('nora', 'nora', 'c1'), contribution('nora')));
    await assertFails(setDoc(ref('nora', 'nora', 'c2'), contribution('nora', { confirmedBy: 'nora', confirmedByName: 'Nora', confirmedAt: serverTimestamp() })));
    await assertFails(setDoc(ref('omar', 'omar', 'c3'), contribution('omar', { recordedByName: 'Omar' })));
  });

  it('the lead records or confirms a member contribution; others cannot', async () => {
    await assertSucceeds(setDoc(ref('sara', 'nora', 'c1'), contribution('sara', {
      recordedByName: 'Sara', confirmedBy: 'sara', confirmedByName: 'Sara', confirmedAt: serverTimestamp(),
    })));
    await seed((db) => setDoc(doc(db, 'passports/nora/contributions/c2'), { ...contribution('nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    await assertFails(updateDoc(ref('omar', 'nora', 'c2'), { confirmedBy: 'omar', confirmedByName: 'Omar', confirmedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref('sara', 'nora', 'c2'), { confirmedBy: 'sara', confirmedByName: 'Sara', confirmedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
    // Changing a confirmed claim clears the confirmation.
    await assertFails(updateDoc(ref('nora', 'nora', 'c2'), { title: 'Built the whole app', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(ref('nora', 'nora', 'c2'), {
      title: 'Built the booking and payment flow', confirmedBy: null, confirmedByName: null, confirmedAt: null, updatedAt: serverTimestamp(),
    }));
  });

  it('supporting links must be web links', async () => {
    await assertFails(setDoc(ref('nora', 'nora', 'c1'), contribution('nora', { links: ['not-a-url'] })));
    await assertFails(setDoc(ref('nora', 'nora', 'c2'), contribution('nora', { links: ['https://a.org', 'https://b.org', 'https://c.org', 'https://d.org'] })));
  });

  it('the lead lists a member contributions for their project even when the passport is private', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'profiles/nora'), profile('Nora', 'private'));
      await setDoc(doc(db, 'passports/nora/contributions/c1'), { ...contribution('nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    });
    await assertSucceeds(getDocs(query(collection(as('sara'), 'passports/nora/contributions'), where('listingId', '==', 'p1'))));
    await assertFails(getDocs(query(collection(as('omar'), 'passports/nora/contributions'), where('listingId', '==', 'p1'))));
  });
});

describe('US18 feedback', () => {
  beforeEach(seedRecords);
  const ref = (who, subject) => doc(as(who), `passports/${subject}/feedback/p1_${who}`);

  it('is closed while the project is running', async () => {
    await assertFails(setDoc(ref('nora', 'sara'), review('nora')));
  });

  it('opens to recorded participants after completion, once per reviewer', async () => {
    await completeProject();
    await assertSucceeds(setDoc(ref('nora', 'sara'), review('nora', { leadership: 5 })));
    await assertFails(setDoc(ref('omar', 'sara'), review('omar', { reviewerName: 'Omar' })));
    await assertFails(setDoc(doc(as('sara'), 'passports/sara/feedback/p1_sara'), review('sara')));
    await assertFails(setDoc(doc(as('nora'), 'passports/sara/feedback/other_id'), review('nora')));
  });

  it('leadership feedback is only for someone who led the team', async () => {
    await completeProject();
    await assertFails(setDoc(ref('sara', 'nora'), review('sara', { leadership: 4 })));
    await assertSucceeds(setDoc(ref('sara', 'nora'), review('sara')));
  });

  it('opens after a departure too', async () => {
    await seed((db) => updateDoc(doc(db, 'passports/nora/participation/p1'), { status: 'left', endedAt: Timestamp.now() }));
    await assertSucceeds(setDoc(ref('sara', 'nora'), review('sara')));
  });

  it('the person reviewed can dispute it; the record stays and only moderators can hide it', async () => {
    await completeProject();
    await seed(async (db) => {
      await setDoc(doc(db, 'passports/sara/feedback/p1_nora'), { ...review('nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
      await setDoc(doc(db, 'admins/admin'), { name: 'Admin' });
    });
    await assertFails(updateDoc(doc(as('omar'), 'passports/sara/feedback/p1_nora'), { disputed: true, disputeReason: 'Not accurate', disputedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as('sara'), 'passports/sara/feedback/p1_nora'), { disputed: true, disputeReason: 'This mixes me up with someone else.', disputedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as('nora'), 'passports/sara/feedback/p1_nora'), { comment: 'Edited after the dispute', updatedAt: serverTimestamp() }));
    await assertFails(deleteDoc(doc(as('sara'), 'passports/sara/feedback/p1_nora')));
    await assertFails(updateDoc(doc(as('sara'), 'passports/sara/feedback/p1_nora'), { hidden: true, moderationNote: 'Hide it', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as('admin'), 'passports/sara/feedback/p1_nora'), { hidden: true, moderationNote: 'Confirmed as a mix-up.', updatedAt: serverTimestamp() }));
  });
});

const notification = (fromUid, overrides = {}) => ({
  type: 'offer_received',
  listingId: 'p1',
  listingTitle: 'Smart parking finder',
  fromUid,
  fromName: 'Sara',
  target: 'listing',
  targetId: 'p1',
  note: '',
  read: false,
  createdAt: serverTimestamp(),
  ...overrides,
});

describe('US19 notifications', () => {
  beforeEach(async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora'));
      await setDoc(doc(db, 'admins/admin'), { name: 'Admin' });
    });
  });
  const inbox = (who, uid, id) => doc(as(who), `users/${uid}/notifications/${id}`);

  it('people connected to a listing notify each other; strangers cannot', async () => {
    await assertSucceeds(setDoc(inbox('sara', 'nora', 'a1'), notification('sara')));
    await assertSucceeds(setDoc(inbox('nora', 'sara', 'a2'), notification('nora', { type: 'offer_accepted', fromName: 'Nora' })));
    await assertFails(setDoc(inbox('omar', 'nora', 'a3'), notification('omar', { fromName: 'Omar' })));
    await assertFails(setDoc(inbox('sara', 'omar', 'a4'), notification('sara')));
    await assertFails(setDoc(inbox('sara', 'nora', 'a5'), notification('nora')));
  });

  it('a retried action does not create a second notification', async () => {
    await assertSucceeds(setDoc(inbox('sara', 'nora', 'same-action'), notification('sara')));
    await assertFails(setDoc(inbox('sara', 'nora', 'same-action'), notification('sara')));
  });

  it('only the recipient reads it, marks it read or removes it', async () => {
    await seed((db) => setDoc(doc(db, 'users/nora/notifications/n1'), { ...notification('sara'), createdAt: Timestamp.now() }));
    await assertSucceeds(getDocs(query(collection(as('nora'), 'users/nora/notifications'), orderBy('createdAt', 'desc'))));
    await assertFails(getDoc(inbox('sara', 'nora', 'n1')));
    await assertSucceeds(updateDoc(inbox('nora', 'nora', 'n1'), { read: true }));
    await assertFails(updateDoc(inbox('nora', 'nora', 'n1'), { note: 'changed' }));
    await assertSucceeds(deleteDoc(inbox('nora', 'nora', 'n1')));
  });

  it('an event owner tells linked teams about a material change; administrators reach anyone', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'listings/e1'), event('omar'));
      await setDoc(doc(db, 'listings/t1'), teamRequest('sara', 'e1'));
    });
    await assertSucceeds(setDoc(inbox('omar', 'sara', 'ev1'), notification('omar', { type: 'event_changed', listingId: 't1', fromName: 'Omar', targetId: 't1' })));
    await assertSucceeds(setDoc(inbox('admin', 'omar', 'mod1'), notification('admin', { type: 'moderation_notice', listingId: '', listingTitle: '', fromName: 'Takatuf moderators', target: 'passport', targetId: '' })));
  });
});

const report = (reporterUid, overrides = {}) => ({
  reporterUid,
  reporterName: 'Omar',
  targetType: 'listing',
  targetKey: 'p1',
  targetPath: 'listings/p1',
  targetTitle: 'Smart parking finder',
  targetOwnerUid: 'sara',
  listingId: 'p1',
  reason: 'inaccurate',
  details: 'The deadline in the description does not match.',
  status: 'open',
  resolution: '',
  resolvedBy: null,
  resolvedAt: null,
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...overrides,
});

describe('US20 reporting and moderation', () => {
  beforeEach(async () => {
    await seed((db) => setDoc(doc(db, 'admins/admin'), { name: 'Admin' }));
  });

  it('a student reports once; only they and administrators can see it', async () => {
    await assertSucceeds(setDoc(doc(as('omar'), 'reports/listing_p1_omar'), report('omar')));
    await assertFails(setDoc(doc(as('omar'), 'reports/listing_p1_nora'), report('omar')));
    await assertFails(setDoc(doc(as('omar'), 'reports/listing_p1_omar'), report('omar')));
    await assertSucceeds(getDoc(doc(as('omar'), 'reports/listing_p1_omar')));
    await assertFails(getDoc(doc(as('sara'), 'reports/listing_p1_omar')));
    await assertSucceeds(getDocs(query(collection(as('admin'), 'reports'), where('status', '==', 'open'))));
    await assertFails(getDocs(collection(as('omar'), 'reports')));
  });

  it('only administrators change a report, and closing it needs a recorded reason', async () => {
    await seed((db) => setDoc(doc(db, 'reports/listing_p1_omar'), { ...report('omar'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    const close = (who, resolution) => updateDoc(doc(as(who), 'reports/listing_p1_omar'), {
      status: 'resolved', resolution, resolvedBy: who, resolvedAt: serverTimestamp(), updatedAt: serverTimestamp(),
    });
    await assertFails(close('sara', 'Fixed the date.'));
    await assertFails(close('admin', ''));
    await assertSucceeds(close('admin', 'Owner corrected the deadline.'));
  });

  it('moderation actions are recorded by administrators and never changed', async () => {
    const action = { reportId: 'listing_p1_omar', adminUid: 'admin', action: 'flag_listing', reason: 'Event date is outdated.', targetPath: 'listings/p1', at: serverTimestamp() };
    await assertFails(setDoc(doc(as('sara'), 'moderationActions/m1'), { ...action, adminUid: 'sara' }));
    await assertSucceeds(setDoc(doc(as('admin'), 'moderationActions/m1'), action));
    await assertFails(updateDoc(doc(as('admin'), 'moderationActions/m1'), { reason: 'changed' }));
    await assertFails(deleteDoc(doc(as('admin'), 'moderationActions/m1')));
  });

  it('administrators hide or flag a listing; the owner cannot unhide it but can clear a flag', async () => {
    await assertFails(updateDoc(doc(as('omar'), 'listings/p1'), { hidden: true, moderationNote: 'spam', updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as('admin'), 'listings/p1'), {
      flag: { reason: 'Dates may be outdated.', at: serverTimestamp() }, moderationNote: 'Reported as outdated.', updatedAt: serverTimestamp(),
    }));
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1'), { flag: null, deadline: inDays(9), updatedAt: serverTimestamp() }));
    await assertSucceeds(updateDoc(doc(as('admin'), 'listings/p1'), { hidden: true, moderationNote: 'Misleading listing.', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(doc(as('sara'), 'listings/p1'), { hidden: false, updatedAt: serverTimestamp() }));
    await assertFails(setDoc(doc(as('nora'), 'listings/p1/applications/nora'), application('p1', 'sara', 'nora')));
  });

  it('nobody can make themselves an administrator', async () => {
    await assertFails(setDoc(doc(as('omar'), 'admins/omar'), { name: 'Omar' }));
    await assertSucceeds(getDoc(doc(as('admin'), 'admins/admin')));
    await assertFails(getDocs(collection(as('omar'), 'admins')));
  });
});

describe('US22 access stays accurate and actions are traceable', () => {
  beforeEach(async () => {
    await seedTeam();
    await seed((db) => setDoc(doc(db, 'admins/admin'), { name: 'Admin' }));
  });

  it('actions are logged in the actor’s own name and only administrators read the log', async () => {
    const entry = { action: 'member_left', actorUid: 'nora', listingId: 'p1', subjectUid: 'nora', detail: '', at: serverTimestamp() };
    await assertSucceeds(setDoc(doc(as('nora'), 'auditLog/x1'), entry));
    await assertFails(setDoc(doc(as('nora'), 'auditLog/x2'), { ...entry, actorUid: 'sara' }));
    await assertFails(getDoc(doc(as('nora'), 'auditLog/x1')));
    await assertSucceeds(getDoc(doc(as('admin'), 'auditLog/x1')));
    await assertFails(updateDoc(doc(as('admin'), 'auditLog/x1'), { detail: 'edited' }));
  });

  it('a former member keeps no access to the workspace or the team’s applications', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'listings/p1/milestones/m1'), { ...milestone('sara', 'nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
      await updateDoc(doc(db, 'listings/p1'), { members: {}, memberIds: [], teamSize: 1 });
      await updateDoc(doc(db, 'listings/p1/applications/nora'), { status: 'left' });
    });
    await assertFails(getDocs(collection(as('nora'), 'listings/p1/milestones')));
    await assertFails(getDocs(collection(as('nora'), 'listings/p1/applications')));
    await assertFails(setDoc(doc(as('nora'), 'listings/p1/updates/u1'), {
      authorId: 'nora', authorName: 'Nora', text: 'Still here?', link: '', createdAt: serverTimestamp(),
    }));
  });

  it('administrators review reports but get no special access to private workspaces', async () => {
    await assertFails(getDocs(collection(as('admin'), 'listings/p1/milestones')));
    await assertFails(getDocs(collection(as('admin'), 'listings/p1/applications')));
  });
});

describe('US21 retries check before writing again', () => {
  beforeEach(seedTeam);

  it('a team member can look up a milestone, update or link id before it exists; outsiders cannot', async () => {
    await assertSucceeds(getDoc(doc(as('nora'), 'listings/p1/milestones/new-id')));
    await assertSucceeds(getDoc(doc(as('sara'), 'listings/p1/updates/new-id')));
    await assertSucceeds(getDoc(doc(as('nora'), 'listings/p1/deliverables/new-id')));
    await assertFails(getDoc(doc(as('omar'), 'listings/p1/milestones/new-id')));
  });

  it('a second write of the same milestone id is not a new milestone', async () => {
    await assertSucceeds(setDoc(doc(as('nora'), 'listings/p1/milestones/same'), milestone('nora', 'nora')));
    await assertFails(setDoc(doc(as('nora'), 'listings/p1/milestones/same'), milestone('nora', 'nora')));
  });

  it('a reporter checks their own report id before sending, without seeing anyone else’s', async () => {
    await assertSucceeds(getDoc(doc(as('omar'), 'reports/listing_p1_omar')));
    await assertFails(getDoc(doc(as('omar'), 'reports/listing_p1_nora')));
    await seed((db) => setDoc(doc(db, 'reports/listing_p1_nora'), { ...report('nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    await assertFails(getDoc(doc(as('omar'), 'reports/listing_p1_nora')));
    await assertSucceeds(getDoc(doc(as('nora'), 'reports/listing_p1_nora')));
  });
});

describe('US19 change notices reach the people affected', () => {
  beforeEach(async () => {
    await seedTeam();
    await seed((db) => setDoc(doc(db, 'listings/p1/applications/reem'), application('p1', 'sara', 'reem')));
  });
  const inbox = (who, uid, id) => doc(as(who), `users/${uid}/notifications/${id}`);

  it('the owner tells members and applicants about changed terms, but not strangers', async () => {
    const changed = notification('sara', { type: 'listing_changed' });
    await assertSucceeds(setDoc(inbox('sara', 'nora', 'c1'), changed));
    await assertSucceeds(setDoc(inbox('sara', 'reem', 'c1'), changed));
    await assertFails(setDoc(inbox('sara', 'omar', 'c1'), changed));
  });

  it('a member tells the lead they left, and the lead tells a removed member', async () => {
    await assertSucceeds(setDoc(inbox('nora', 'sara', 'l1'), notification('nora', { type: 'member_left', fromName: 'Nora', target: 'workspace' })));
    await assertSucceeds(setDoc(inbox('sara', 'nora', 'r1'), notification('sara', { type: 'member_removed', target: 'passport' })));
  });

  it('notification content is checked: known types and targets only', async () => {
    await assertFails(setDoc(inbox('sara', 'nora', 'x1'), notification('sara', { type: 'anything' })));
    await assertFails(setDoc(inbox('sara', 'nora', 'x2'), notification('sara', { target: 'settings' })));
    await assertFails(setDoc(inbox('sara', 'nora', 'x3'), notification('sara', { read: true })));
  });
});

describe('US17 completing a project closes recruitment', () => {
  beforeEach(async () => {
    await seedTeam();
    await seed((db) => setDoc(doc(db, 'listings/p1/applications/reem'), application('p1', 'sara', 'reem')));
  });
  const close = (who) => updateDoc(doc(as(who), 'listings/p1/applications/reem'), { status: 'closed', updatedAt: serverTimestamp() });

  it('applications still waiting can only be closed once the project is completed, and only by the lead', async () => {
    await assertFails(close('sara'));
    await completeProject();
    await assertFails(close('reem'));
    await assertFails(close('nora'));
    await assertSucceeds(close('sara'));
  });

  it('a closed application cannot be revived by applying again', async () => {
    await completeProject();
    await assertSucceeds(close('sara'));
    await assertFails(updateDoc(doc(as('reem'), 'listings/p1/applications/reem'), {
      status: 'submitted', reviewed: false, updatedAt: serverTimestamp(),
    }));
  });
});

describe('Charter alignment checks', () => {
  it('US04 a listing or application shows the real name of whoever made it', async () => {
    await assertFails(setDoc(doc(as('nora'), 'listings/p9'), project('nora', { ownerName: 'PSU Student Affairs' })));
    await assertSucceeds(setDoc(doc(as('nora'), 'listings/p9'), project('nora')));
    await assertFails(setDoc(doc(as('omar'), 'listings/p1/applications/omar'), application('p1', 'sara', 'omar', { applicantName: 'Sara' })));
    await assertSucceeds(setDoc(doc(as('omar'), 'listings/p1/applications/omar'), application('p1', 'sara', 'omar')));
  });

  it('US12 two students accepting the last place at once: exactly one joins', async () => {
    await seed(async (db) => {
      await updateDoc(doc(db, 'listings/p1'), { roles: [{ ...role, openings: 1 }], openPlaces: 1 });
      await setDoc(doc(db, 'listings/p1/applications/nora'), { ...application('p1', 'sara', 'nora'), ...offer() });
      await setDoc(doc(db, 'listings/p1/applications/omar'), { ...application('p1', 'sara', 'omar'), ...offer() });
    });
    const before = { openings: 1, openPlaces: 1, teamSize: 1 };
    const results = await Promise.allSettled([acceptBatch('nora', before), acceptBatch('omar', before)]);
    assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
  });

  it('US12 a live offer cannot be used to join a completed project', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'listings/p1/applications/nora'), { ...application('p1', 'sara', 'nora'), ...offer() });
      await updateDoc(doc(db, 'listings/p1'), { completedAt: Timestamp.now() });
    });
    await assertFails(acceptBatch('nora'));
  });

  it('FR14 the lead can decide not to go ahead with an application; nobody else can', async () => {
    await seed((db) => setDoc(doc(db, 'listings/p1/applications/nora'), application('p1', 'sara', 'nora')));
    const decide = (who) => updateDoc(doc(as(who), 'listings/p1/applications/nora'), { status: 'not_selected', reviewed: true, updatedAt: serverTimestamp() });
    await assertFails(decide('nora'));
    await assertFails(decide('omar'));
    await assertSucceeds(decide('sara'));
    await assertFails(updateDoc(doc(as('nora'), 'listings/p1/applications/nora'), { status: 'submitted', reviewed: false, updatedAt: serverTimestamp() }));
  });

  it('US05 a team request must fit the event’s minimum team size too', async () => {
    await seed((db) => setDoc(doc(db, 'listings/e1'), event('omar', { teamSizeMin: 3, teamSizeMax: 5 })));
    await assertFails(setDoc(doc(as('sara'), 'listings/t1'), teamRequest('sara', 'e1', { teamSize: 1, openPlaces: 1, roles: [{ ...role, openings: 1 }] })));
    await assertSucceeds(setDoc(doc(as('sara'), 'listings/t1'), teamRequest('sara', 'e1', { teamSize: 1, openPlaces: 2, roles: [{ ...role, openings: 2 }] })));
  });

  it('US19 decisions come from the lead, moderation notices from moderators, and names are real', async () => {
    await seedTeam();
    const inbox = (who, uid, id) => doc(as(who), `users/${uid}/notifications/${id}`);
    await assertFails(setDoc(inbox('nora', 'sara', 'n1'), notification('nora', { fromName: 'Nora', type: 'offer_received' })));
    await assertFails(setDoc(inbox('nora', 'sara', 'n2'), notification('nora', { fromName: 'Nora', type: 'moderation_notice' })));
    await assertFails(setDoc(inbox('nora', 'sara', 'n3'), notification('nora', { fromName: 'Takatuf moderators', type: 'member_left' })));
    await assertSucceeds(setDoc(inbox('nora', 'sara', 'n4'), notification('nora', { fromName: 'Nora', type: 'member_left' })));
    await assertSucceeds(setDoc(inbox('sara', 'nora', 'n5'), notification('sara', { type: 'application_declined' })));
    await assertSucceeds(setDoc(inbox('nora', 'sara', 'n6'), notification('nora', { fromName: 'Nora', type: 'milestone_completed' })));
  });

  it('US20 a review hidden by moderators is no longer readable by other students', async () => {
    await seedRecords();
    await completeProject();
    await seed(async (db) => {
      await setDoc(doc(db, 'passports/sara/feedback/p1_nora'), { ...review('nora'), hidden: true, moderationNote: 'Removed', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
      await setDoc(doc(db, 'passports/sara/feedback/p1_omar'), { ...review('nora'), reviewerUid: 'omar', reviewerName: 'Omar', createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    });
    await assertFails(getDoc(doc(as('omar'), 'passports/sara/feedback/p1_nora')));
    await assertSucceeds(getDoc(doc(as('sara'), 'passports/sara/feedback/p1_nora')));
    await assertSucceeds(getDoc(doc(as('nora'), 'passports/sara/feedback/p1_nora')));
    await assertSucceeds(getDocs(query(collection(as('omar'), 'passports/sara/feedback'), where('hidden', '==', false))));
    await assertFails(getDocs(collection(as('omar'), 'passports/sara/feedback')));
  });
});
