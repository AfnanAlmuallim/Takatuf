// End-to-end check of a real Firebase project after `npm run deploy:firestore`.
// It signs up three throwaway QA accounts, performs the same reads and writes the Android app
// makes, and checks each Sprint 1 and Sprint 2 permission. Everything it creates is recorded in
// tests/.live-smoke-created.json so `npm run smoke:cleanup` can remove it.
//
//   npm run smoke
import { readFileSync, writeFileSync } from 'node:fs';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, createUserWithEmailAndPassword } from 'firebase/auth';
import {
  Timestamp, arrayRemove, arrayUnion, collection, collectionGroup, deleteField, doc, getDoc, getDocs,
  getFirestore, limit, orderBy, query, runTransaction, serverTimestamp, setDoc, terminate, updateDoc,
  where, writeBatch,
} from 'firebase/firestore';

const services = JSON.parse(readFileSync(new URL('../../app/google-services.json', import.meta.url), 'utf8'));
const config = {
  apiKey: services.client[0].api_key[0].current_key,
  projectId: services.project_info.project_id,
  authDomain: `${services.project_info.project_id}.firebaseapp.com`,
};
const RUN = Date.now().toString(36);
const PASSWORD = `Qa-${RUN}-pass1`;
const created = { project: config.projectId, run: RUN, users: [], docs: [] };
const record = () => writeFileSync(new URL('./.live-smoke-created.json', import.meta.url), JSON.stringify(created, null, 2));

let passed = 0;
let failed = 0;
async function check(name, fn) {
  try {
    await fn();
    passed++;
    console.log(`  ok    ${name}`);
  } catch (e) {
    failed++;
    console.log(`  FAIL  ${name}\n        ${e.message?.split('\n')[0]}`);
  }
}
/** New composite indexes take a few minutes to build after a deploy; wait for them. */
async function whenIndexReady(fn, timeoutMs = 10 * 60_000) {
  const until = Date.now() + timeoutMs;
  for (;;) {
    try {
      return await fn();
    } catch (e) {
      if (e.code !== 'failed-precondition' || Date.now() > until) throw e;
      console.log('        (index still building, retrying in 20 s)');
      await new Promise((r) => setTimeout(r, 20_000));
    }
  }
}

async function denied(promise) {
  try {
    await promise;
  } catch (e) {
    if (e.code === 'permission-denied') return;
    throw new Error(`expected permission-denied, got ${e.code}: ${e.message}`);
  }
  throw new Error('expected permission-denied, but it was allowed');
}

async function student(role) {
  const app = initializeApp(config, `${role}-${RUN}`);
  const auth = getAuth(app);
  const email = `qa-${role}-${RUN}@takatuf.test`;
  const { user } = await createUserWithEmailAndPassword(auth, email, PASSWORD);
  created.users.push({ email, uid: user.uid });
  record();
  return { app, auth, uid: user.uid, db: getFirestore(app), email };
}

const DAY = 86_400_000;
const endOfDay = (days) => {
  const d = new Date(Date.now() + days * DAY);
  return Timestamp.fromDate(new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 1)));
};
const startOfDay = (days) => {
  const d = new Date(Date.now() + days * DAY);
  return Timestamp.fromDate(new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate())));
};

console.log(`Live smoke test against "${config.projectId}" (run ${RUN})`);
const owner = await student('owner');
const applicant = await student('applicant');
const outsider = await student('outsider');

const profile = (name, visibility) => ({
  displayName: name, major: 'Software Engineering', year: 3, headline: '', visibility, onboarded: true,
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
});
const passport = {
  skills: [{ name: 'Kotlin', level: 3 }], interests: ['Education'], experience: [],
  availability: { hoursPerWeek: 6, days: ['sun', 'mon'], note: '' }, links: [], updatedAt: serverTimestamp(),
};
const role = { id: 'mob', title: 'Mobile developer', description: '', skills: ['Kotlin'], openings: 2 };
const projectListing = {
  kind: 'project', status: 'open', title: `[QA ${RUN}] Sprint 1 smoke test`, description: 'Automated test data; safe to delete.',
  ownerId: owner.uid, ownerName: 'QA Owner', ownerMajor: 'Software Engineering',
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  outcome: 'Proves the deployed rules and indexes work', roles: [role], hoursPerWeek: 6, duration: '4 weeks',
  teamSize: 1, openPlaces: 2, deadline: endOfDay(7), skills: ['kotlin'],
};
const eventListing = {
  kind: 'event', status: 'open', title: `[QA ${RUN}] Test hackathon`, description: '',
  ownerId: owner.uid, ownerName: 'QA Owner', ownerMajor: '', createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
  eventType: 'hackathon', organizer: 'QA Organizer', sourceUrl: 'https://example.org/qa-hackathon',
  startDate: startOfDay(10), endDate: endOfDay(11), registrationDeadline: endOfDay(8),
  eligibility: 'Test data', teamSizeMin: 2, teamSizeMax: 4, format: 'online', location: '',
};
const application = {
  listingId: '', ownerId: owner.uid, applicantId: applicant.uid, applicantName: 'QA Applicant', applicantMajor: 'Computer Science',
  listingTitle: projectListing.title, listingKind: 'project', roleId: 'mob', roleTitle: 'Mobile developer',
  contribution: 'Automated smoke test application with enough characters.', hoursPerWeek: 6, availabilityNote: '',
  status: 'submitted', reviewed: false, createdAt: serverTimestamp(), updatedAt: serverTimestamp(),
};

const projectRef = doc(collection(owner.db, 'listings'));
const eventRef = doc(collection(owner.db, 'listings'));
const teamRef = doc(collection(owner.db, 'listings'));
const appRef = (db) => doc(db, 'listings', projectRef.id, 'applications', applicant.uid);
created.docs.push(
  `profiles/${owner.uid}`, `profiles/${applicant.uid}`, `profiles/${outsider.uid}`,
  `passports/${owner.uid}`, `passports/${applicant.uid}`, `passports/${outsider.uid}`,
  `users/${applicant.uid}`, `listings/${projectRef.id}`, `listings/${eventRef.id}`, `listings/${teamRef.id}`,
);
record();

console.log('\nUS01–US02 accounts, passports and visibility');
await check('students create their own profile and passport', async () => {
  await setDoc(doc(owner.db, 'profiles', owner.uid), profile('QA Owner', 'everyone'));
  await setDoc(doc(owner.db, 'passports', owner.uid), passport);
  await setDoc(doc(applicant.db, 'profiles', applicant.uid), profile('QA Applicant', 'applied'));
  await setDoc(doc(applicant.db, 'passports', applicant.uid), passport);
  await setDoc(doc(outsider.db, 'profiles', outsider.uid), profile('QA Outsider', 'everyone'));
  await setDoc(doc(outsider.db, 'passports', outsider.uid), passport);
});
await check('nobody writes someone else’s profile', () => denied(setDoc(doc(outsider.db, 'profiles', owner.uid), profile('Hijack', 'everyone'))));
await check('an "everyone" passport is readable by other students', async () => {
  const snap = await getDoc(doc(outsider.db, 'passports', owner.uid));
  if (!snap.exists()) throw new Error('passport missing');
});
await check('a "teams I apply to" passport is hidden before applying', () => denied(getDoc(doc(owner.db, 'passports', applicant.uid))));

console.log('\nUS03–US05 listings');
await check('an owner publishes a project', () => setDoc(projectRef, projectListing));
await check('an owner shares an event', () => setDoc(eventRef, eventListing));
await check('a team request links to the event within its team size', () =>
  setDoc(teamRef, {
    ...projectListing, kind: 'team', title: `[QA ${RUN}] Team for test hackathon`, outcome: '', eventId: eventRef.id,
    event: { title: eventListing.title, organizer: 'QA Organizer', type: 'hackathon', teamSizeMin: 2, teamSizeMax: 4 },
  }));
const oversizedRef = doc(collection(owner.db, 'listings'));
created.docs.push(`listings/${oversizedRef.id}`); // only exists if the rule wrongly allowed it
record();
await check('a team larger than the event allows is refused', () =>
  denied(setDoc(oversizedRef, {
    ...projectListing, kind: 'team', outcome: '', teamSize: 3, eventId: eventRef.id, event: { title: 'x' },
  })));
await check('other students cannot edit the listing', () =>
  denied(updateDoc(doc(outsider.db, 'listings', projectRef.id), { title: 'Hijacked title', updatedAt: serverTimestamp() })));

console.log('\nUS06 discover and save');
await check('the feed query returns the new listings', async () => {
  const snap = await getDocs(query(collection(applicant.db, 'listings'), orderBy('createdAt', 'desc'), limit(250)));
  const ids = snap.docs.map((d) => d.id);
  if (!ids.includes(projectRef.id) || !ids.includes(eventRef.id)) throw new Error('new listings not in feed');
});
await check('teams for an event are found by eventId', async () => {
  const snap = await getDocs(query(collection(applicant.db, 'listings'), where('eventId', '==', eventRef.id)));
  if (snap.size !== 1) throw new Error(`expected 1 team, got ${snap.size}`);
});
await check('a student saves a listing privately', async () => {
  await setDoc(doc(applicant.db, 'users', applicant.uid, 'saved', projectRef.id), { savedAt: serverTimestamp() });
  await denied(getDocs(collection(outsider.db, 'users', applicant.uid, 'saved')));
});

console.log('\nUS07 apply and withdraw');
await check('applying writes the application and shares the passport in one batch', async () => {
  const batch = writeBatch(applicant.db);
  batch.set(appRef(applicant.db), { ...application, listingId: projectRef.id });
  batch.set(doc(applicant.db, 'passports', applicant.uid, 'viewers', owner.uid), { listingId: projectRef.id, grantedAt: serverTimestamp() });
  await batch.commit();
});
await check('a duplicate active application is refused', () => denied(setDoc(appRef(applicant.db), { ...application, listingId: projectRef.id })));
await check('the owner can now open the applicant’s passport', async () => {
  const snap = await getDoc(doc(owner.db, 'passports', applicant.uid));
  if (!snap.exists()) throw new Error('passport missing');
});
await check('an unrelated student still cannot', () => denied(getDoc(doc(outsider.db, 'passports', applicant.uid))));

console.log('\nUS08 review applicants (needs the deployed indexes)');
await check('the owner lists the applications', async () => {
  const snap = await getDocs(query(collection(owner.db, 'listings', projectRef.id, 'applications'), orderBy('updatedAt', 'desc')));
  if (snap.size !== 1) throw new Error(`expected 1, got ${snap.size}`);
});
await check('unrelated students can neither read nor list applications', async () => {
  await denied(getDoc(appRef(outsider.db)));
  await denied(getDocs(collection(outsider.db, 'listings', projectRef.id, 'applications')));
});
await check('the owner shortlists; membership fields stay untouched', async () => {
  await updateDoc(appRef(owner.db), { status: 'shortlisted', reviewed: true });
  await denied(updateDoc(appRef(owner.db), { status: 'accepted' }));
});
await check('"my applications" collection-group query (index applicantId + updatedAt)', async () => {
  const snap = await whenIndexReady(() => getDocs(query(collectionGroup(applicant.db, 'applications'), where('applicantId', '==', applicant.uid), orderBy('updatedAt', 'desc'))));
  if (snap.size !== 1) throw new Error(`expected 1, got ${snap.size}`);
});
await check('"applications to my listings" collection-group query (index ownerId + updatedAt)', async () => {
  const snap = await whenIndexReady(() => getDocs(query(collectionGroup(owner.db, 'applications'), where('ownerId', '==', owner.uid), orderBy('updatedAt', 'desc'))));
  if (snap.size !== 1) throw new Error(`expected 1, got ${snap.size}`);
});
await check('"your listings" query (index ownerId + createdAt)', async () => {
  const snap = await whenIndexReady(() => getDocs(query(collection(owner.db, 'listings'), where('ownerId', '==', owner.uid), orderBy('createdAt', 'desc'))));
  if (snap.size < 3) throw new Error(`expected 3, got ${snap.size}`);
});
await check('the applicant withdraws', () => updateDoc(appRef(applicant.db), { status: 'withdrawn', updatedAt: serverTimestamp() }));
await check('a closed listing refuses a new application', async () => {
  await updateDoc(doc(owner.db, 'listings', projectRef.id), { status: 'closed', updatedAt: serverTimestamp() });
  await denied(updateDoc(appRef(applicant.db), {
    status: 'submitted', reviewed: false, contribution: 'Trying again after the listing closed.', updatedAt: serverTimestamp(),
  }));
});

// ---------- Sprint 2 ----------
console.log('\nUS12–US16 offers, workspace, reopening and leadership');
const team2Ref = doc(collection(owner.db, 'listings'));
created.docs.push(`listings/${team2Ref.id}`);
record();
const team2 = (db) => doc(db, 'listings', team2Ref.id);
const app2 = (db, uid) => doc(db, 'listings', team2Ref.id, 'applications', uid);
const team2Listing = {
  ...projectListing,
  status: 'open',
  title: `[QA ${RUN}] Sprint 2 smoke test`,
  roles: [{ ...role, openings: 1 }, { id: 'ux', title: 'Designer', description: '', skills: ['Figma'], openings: 1 }],
  openPlaces: 2,
  skills: ['kotlin', 'figma'],
};

// What ApplicationRepository.acceptOffer does: one transaction for the listing and the offer.
const join = (s, roleId, roleTitle, name) => runTransaction(s.db, async (tx) => {
  const l = (await tx.get(team2(s.db))).data();
  tx.update(team2(s.db), {
    roles: l.roles.map((r) => (r.id === roleId ? { ...r, openings: r.openings - 1 } : r)),
    openPlaces: l.openPlaces - 1,
    teamSize: l.teamSize + 1,
    [`members.${s.uid}`]: { name, major: 'Computer Science', roleId, roleTitle, joinedAt: serverTimestamp() },
    memberIds: arrayUnion(s.uid),
    updatedAt: serverTimestamp(),
  });
  tx.update(app2(s.db, s.uid), { status: 'accepted', respondedAt: serverTimestamp(), updatedAt: serverTimestamp() });
});

await check('an owner publishes a second project', () => setDoc(team2Ref, team2Listing));
await check('two students apply, each sharing their passport', async () => {
  for (const [s, roleId, roleTitle, name] of [
    [applicant, 'mob', 'Mobile developer', 'QA Applicant'],
    [outsider, 'ux', 'Designer', 'QA Outsider'],
  ]) {
    const batch = writeBatch(s.db);
    batch.set(app2(s.db, s.uid), {
      ...application, applicantId: s.uid, applicantName: name, listingId: team2Ref.id,
      listingTitle: team2Listing.title, roleId, roleTitle,
    });
    batch.set(doc(s.db, 'passports', s.uid, 'viewers', owner.uid), { listingId: team2Ref.id, grantedAt: serverTimestamp() });
    await batch.commit();
  }
});
await check('the owner offers a place until the end of a chosen day', () =>
  updateDoc(app2(owner.db, applicant.uid), { status: 'offered', reviewed: true, offeredAt: serverTimestamp(), offerExpiresAt: endOfDay(3) }));
await check('a student without an offer cannot join', () => denied(join(outsider, 'ux', 'Designer', 'QA Outsider')));
await check('the offered student accepts and joins in one transaction', () => join(applicant, 'mob', 'Mobile developer', 'QA Applicant'));
await check('the team counts them and their role is full', async () => {
  const l = (await getDoc(team2(owner.db))).data();
  const mob = l.roles.find((r) => r.id === 'mob');
  if (!l.memberIds.includes(applicant.uid) || l.teamSize !== 2 || l.openPlaces !== 1 || mob.openings !== 0) {
    throw new Error(`unexpected team state: ${JSON.stringify({ memberIds: l.memberIds, teamSize: l.teamSize, openPlaces: l.openPlaces })}`);
  }
});

const milestoneRef = doc(collection(applicant.db, 'listings', team2Ref.id, 'milestones'));
await check('members and the lead record milestones, updates, links and responsibilities', async () => {
  await setDoc(milestoneRef, {
    title: 'First prototype', assigneeId: applicant.uid, assigneeName: 'QA Applicant', dueDate: endOfDay(5),
    status: 'todo', createdBy: applicant.uid, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), completedAt: null,
  });
  await updateDoc(doc(owner.db, 'listings', team2Ref.id, 'milestones', milestoneRef.id), {
    status: 'done', completedAt: serverTimestamp(), updatedAt: serverTimestamp(),
  });
  await setDoc(doc(collection(applicant.db, 'listings', team2Ref.id, 'updates')), {
    authorId: applicant.uid, authorName: 'QA Applicant', text: 'Smoke test update', link: '', createdAt: serverTimestamp(),
  });
  await setDoc(doc(collection(owner.db, 'listings', team2Ref.id, 'deliverables')), {
    label: 'Repository', url: 'https://example.org/qa', addedBy: owner.uid, addedByName: 'QA Owner', createdAt: serverTimestamp(),
  });
  await setDoc(doc(applicant.db, 'listings', team2Ref.id, 'responsibilities', applicant.uid), {
    text: 'Android app', updatedBy: applicant.uid, updatedAt: serverTimestamp(),
  });
});
await check('students outside the team cannot read the workspace', async () => {
  await denied(getDocs(collection(outsider.db, 'listings', team2Ref.id, 'milestones')));
  await denied(getDocs(collection(outsider.db, 'listings', team2Ref.id, 'updates')));
  await denied(getDocs(collection(outsider.db, 'listings', team2Ref.id, 'deliverables')));
});

await check('a leadership request changes nothing until it is accepted', async () => {
  await updateDoc(team2(owner.db), {
    pendingLeader: { uid: applicant.uid, name: 'QA Applicant', requestedAt: serverTimestamp() }, updatedAt: serverTimestamp(),
  });
  await denied(updateDoc(team2(outsider.db), { ownerId: outsider.uid, ownerName: 'QA Outsider', pendingLeader: null, updatedAt: serverTimestamp() }));
  if ((await getDoc(team2(owner.db))).data().ownerId !== owner.uid) throw new Error('the lead changed early');
});
await check('the nominee accepts; the previous lead stays on as a member', () => runTransaction(applicant.db, async (tx) => {
  const l = (await tx.get(team2(applicant.db))).data();
  tx.update(team2(applicant.db), {
    ownerId: applicant.uid, ownerName: 'QA Applicant', ownerMajor: 'Computer Science', pendingLeader: null,
    [`members.${applicant.uid}`]: deleteField(),
    [`members.${owner.uid}`]: { name: l.ownerName, major: l.ownerMajor, roleId: '', roleTitle: '', joinedAt: l.createdAt },
    memberIds: [owner.uid],
    updatedAt: serverTimestamp(),
  });
}));
await check('the new lead takes over the applications and the passport access that came with them', async () => {
  await updateDoc(app2(applicant.db, outsider.uid), { ownerId: applicant.uid });
  await setDoc(doc(applicant.db, 'passports', outsider.uid, 'viewers', applicant.uid), { listingId: team2Ref.id, grantedAt: serverTimestamp() });
});

await check('the new lead reopens the filled role and asks earlier applicants to reconfirm', async () => {
  const l = (await getDoc(team2(applicant.db))).data();
  const batch = writeBatch(applicant.db);
  batch.update(team2(applicant.db), {
    roles: l.roles.map((r) => (r.id === 'mob' ? { ...r, openings: r.openings + 1 } : r)),
    openPlaces: l.openPlaces + 1, status: 'open', reopenedAt: serverTimestamp(), updatedAt: serverTimestamp(),
  });
  batch.update(app2(applicant.db, outsider.uid), { status: 'reconfirm', reviewed: false, reconfirmRequestedAt: serverTimestamp() });
  await batch.commit();
});
await check('the earlier applicant confirms they are still interested', () =>
  updateDoc(app2(outsider.db, outsider.uid), { status: 'submitted', reviewed: false, updatedAt: serverTimestamp() }));
await check('a member leaves and loses workspace access at once', async () => {
  await runTransaction(owner.db, async (tx) => {
    const l = (await tx.get(team2(owner.db))).data();
    tx.update(team2(owner.db), {
      [`members.${owner.uid}`]: deleteField(), memberIds: arrayRemove(owner.uid), teamSize: l.teamSize - 1, updatedAt: serverTimestamp(),
    });
  });
  await denied(getDocs(collection(owner.db, 'listings', team2Ref.id, 'milestones')));
});

for (const s of [owner, applicant, outsider]) {
  await terminate(s.db);
  await deleteApp(s.app);
}
console.log(`\n${passed} passed, ${failed} failed. Test data is listed in tests/.live-smoke-created.json`);
process.exit(failed ? 1 : 0);
