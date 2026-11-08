// Firestore security rules tests. Run with `npm run test:rules` (starts the Firestore emulator).
// Each block maps to a Sprint 1 or Sprint 2 acceptance criterion from the project charter.
import { after, before, beforeEach, describe, it } from 'node:test';
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
