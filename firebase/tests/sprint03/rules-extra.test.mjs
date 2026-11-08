// Sprint 03 extended rules tests: boundaries, the role-by-resource access matrix (US22), and
// probes of permissions the main suite doesn't cover. Each test states the behaviour the charter
// requires, so a failure here is a finding, not a broken test. Run with:
//
//   firebase emulators:exec --project demo-takatuf-rules --only firestore "node --test tests/sprint03/rules-extra.test.mjs"
import { after, before, beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
  Timestamp, arrayRemove, arrayUnion, collection, deleteField, doc, getDoc, getDocs, limit, orderBy, query, serverTimestamp, setDoc,
  updateDoc, where, writeBatch,
} from 'firebase/firestore';

let env;
const DAY = 86_400_000;
const inDays = (n) => Timestamp.fromMillis(Date.now() + n * DAY);
const names = { sara: 'Sara', nora: 'Nora', fajer: 'Fajer', amal: 'Amal', omar: 'Omar', admin: 'Admin' };
const as = (uid) => env.authenticatedContext(uid).firestore();
const anonymous = () => env.unauthenticatedContext().firestore();
const seed = (fn) => env.withSecurityRulesDisabled((ctx) => fn(ctx.firestore()));
const chars = (n) => 'x'.repeat(n);

const role = { id: 'r1', title: 'Mobile developer', description: '', skills: ['Kotlin'], openings: 1 };
const member = (name) => ({ name, major: 'SE', roleId: 'r1', roleTitle: 'Mobile developer', joinedAt: Timestamp.now() });
const listing = {
  kind: 'project', status: 'open', title: 'Smart parking finder', description: '', ownerId: 'sara', ownerName: 'Sara',
  ownerMajor: 'SE', createdAt: Timestamp.now(), updatedAt: Timestamp.now(), outcome: 'A working Android prototype', roles: [role],
  hoursPerWeek: 6, duration: '8 weeks', teamSize: 2, openPlaces: 1, deadline: null, skills: ['kotlin'],
  members: { nora: member('Nora') }, memberIds: ['nora'], hidden: false,
};
const application = (applicantId, status) => ({
  listingId: 'p1', ownerId: 'sara', applicantId, applicantName: names[applicantId], applicantMajor: 'CS', listingTitle: 'Smart parking finder',
  listingKind: 'project', roleId: 'r1', roleTitle: 'Mobile developer', contribution: 'I can own the booking flow for the team.',
  hoursPerWeek: 6, availabilityNote: '', status, reviewed: false, createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
});
const participation = (status, overrides = {}) => ({
  listingId: 'p1', listingTitle: 'Smart parking finder', kind: 'project', role: 'Mobile developer', wasLead: false, status,
  startedAt: Timestamp.now(), endedAt: status === 'active' ? null : Timestamp.now(), updatedAt: Timestamp.now(), ...overrides,
});
const contribution = (recordedBy, overrides = {}) => ({
  listingId: 'p1', listingTitle: 'Smart parking finder', title: 'Built the booking flow', detail: '', links: [], recordedBy,
  recordedByName: names[recordedBy], confirmedBy: null, confirmedByName: null, confirmedAt: null,
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
});
const review = (reviewerUid, overrides = {}) => ({
  listingId: 'p1', listingTitle: 'Smart parking finder', reviewerUid, reviewerName: names[reviewerUid], contribution: 4, leadership: null,
  comment: '', disputed: false, disputeReason: '', disputedAt: null, hidden: false, moderationNote: '',
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
});
const notice = (fromUid, overrides = {}) => ({
  type: 'milestone_assigned', listingId: 'p1', listingTitle: 'Smart parking finder', fromUid, fromName: names[fromUid], target: 'workspace',
  targetId: 'p1', note: '', read: false, createdAt: serverTimestamp(), ...overrides,
});
const report = (reporterUid, overrides = {}) => ({
  reporterUid, reporterName: names[reporterUid], targetType: 'listing', targetKey: 'p1', targetPath: 'listings/p1', targetTitle: 'Smart parking finder',
  targetOwnerUid: 'sara', listingId: 'p1', reason: 'inaccurate', details: '', status: 'open', resolution: '', resolvedBy: null, resolvedAt: null,
  createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...overrides,
});
const completed = () => seed((db) => updateDoc(doc(db, 'listings/p1'), { completedAt: Timestamp.now(), completion: { summary: 'Shipped.', link: '' } }));

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-takatuf-rules',
    firestore: { rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(() => env?.cleanup());

// Sara leads p1, Nora is a member, Fajer left, Amal has applied, Omar is unrelated, Admin moderates.
beforeEach(async () => {
  await env.clearFirestore();
  await seed(async (db) => {
    for (const [uid, name] of Object.entries(names)) {
      await setDoc(doc(db, `profiles/${uid}`), {
        displayName: name, major: 'SE', year: 3, headline: '', visibility: 'private', onboarded: true, updatedAt: Timestamp.now(),
      });
      await setDoc(doc(db, `passports/${uid}`), {
        skills: [], interests: [], experience: [], availability: { hoursPerWeek: 6, days: [], note: '' }, links: [], updatedAt: Timestamp.now(),
      });
    }
    await setDoc(doc(db, 'admins/admin'), { name: 'Admin' });
    await setDoc(doc(db, 'listings/p1'), listing);
    await setDoc(doc(db, 'listings/p1/applications/nora'), application('nora', 'accepted'));
    await setDoc(doc(db, 'listings/p1/applications/fajer'), application('fajer', 'left'));
    await setDoc(doc(db, 'listings/p1/applications/amal'), application('amal', 'submitted'));
    await setDoc(doc(db, 'passports/sara/participation/p1'), participation('active', { role: 'Lead', wasLead: true }));
    await setDoc(doc(db, 'passports/nora/participation/p1'), participation('active'));
    await setDoc(doc(db, 'passports/fajer/participation/p1'), participation('left'));
    await setDoc(doc(db, 'listings/p1/milestones/m1'), {
      title: 'Prototype', assigneeId: 'nora', assigneeName: 'Nora', dueDate: inDays(7), status: 'todo', createdBy: 'sara',
      createdAt: Timestamp.now(), updatedAt: Timestamp.now(), completedAt: null,
    });
    await setDoc(doc(db, 'listings/p1/updates/u1'), { authorId: 'nora', authorName: 'Nora', text: 'Started.', link: '', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'listings/p1/deliverables/d1'), { label: 'Repo', url: 'https://github.com/x/y', addedBy: 'nora', addedByName: 'Nora', createdAt: Timestamp.now() });
    await setDoc(doc(db, 'listings/p1/responsibilities/nora'), { text: 'Android', updatedBy: 'sara', updatedAt: Timestamp.now() });
    await setDoc(doc(db, 'passports/nora/contributions/c1'), { ...contribution('nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await setDoc(doc(db, 'users/nora/notifications/n1'), { ...notice('sara'), createdAt: Timestamp.now() });
    await setDoc(doc(db, 'users/nora/saved/p1'), { savedAt: Timestamp.now() });
    await setDoc(doc(db, 'reports/listing_p1_amal'), { ...report('amal'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
    await setDoc(doc(db, 'moderationActions/a1'), { reportId: 'listing_p1_amal', adminUid: 'admin', action: 'reviewing', reason: 'Checking', targetPath: 'listings/p1', at: Timestamp.now() });
    await setDoc(doc(db, 'auditLog/l1'), { action: 'offer_sent', actorUid: 'sara', listingId: 'p1', subjectUid: 'nora', detail: '', at: Timestamp.now() });
  });
});

// ---------- US22: every role against every private resource ----------

const everyone = ['sara', 'nora', 'fajer', 'amal', 'omar', 'admin'];
const label = { sara: 'owner', nora: 'member', fajer: 'former member', amal: 'applicant', omar: 'unrelated student', admin: 'administrator' };
const matrix = [
  ['workspace milestone', 'listings/p1/milestones/m1', ['sara', 'nora']],
  ['workspace update', 'listings/p1/updates/u1', ['sara', 'nora']],
  ['workspace deliverable', 'listings/p1/deliverables/d1', ['sara', 'nora']],
  ['workspace responsibility', 'listings/p1/responsibilities/nora', ['sara', 'nora']],
  ['another applicant’s application', 'listings/p1/applications/amal', ['sara', 'amal']],
  ['a member’s private passport', 'passports/nora', ['nora']],
  ['a member’s contribution on a private passport', 'passports/nora/contributions/c1', ['sara', 'nora', 'admin']],
  ['a member’s notification', 'users/nora/notifications/n1', ['nora']],
  ['a member’s saved listing', 'users/nora/saved/p1', ['nora']],
  ['a report', 'reports/listing_p1_amal', ['amal', 'admin']],
  ['a moderation action', 'moderationActions/a1', ['admin']],
  ['an action-log entry', 'auditLog/l1', ['admin']],
];
describe('US22 access matrix: reads', () => {
  for (const [name, path, allowed] of matrix) {
    for (const uid of everyone) {
      const may = allowed.includes(uid);
      it(`${label[uid]} ${may ? 'can' : 'cannot'} read ${name}`, async () => {
        await (may ? assertSucceeds : assertFails)(getDoc(doc(as(uid), path)));
      });
    }
    it(`a signed-out visitor cannot read ${name}`, () => assertFails(getDoc(doc(anonymous(), path))));
  }
});

describe('US22 access matrix: writes', () => {
  const update = (who) => setDoc(doc(as(who), 'listings/p1/updates/u2'), { authorId: who, authorName: names[who], text: 'Hello', link: '', createdAt: serverTimestamp() });
  for (const uid of everyone) {
    const may = ['sara', 'nora'].includes(uid);
    it(`${label[uid]} ${may ? 'can' : 'cannot'} post a workspace update`, async () => {
      await (may ? assertSucceeds : assertFails)(update(uid));
    });
  }
  for (const uid of ['sara', 'nora', 'fajer', 'amal', 'omar']) {
    it(`${label[uid]} cannot change a report`, () => assertFails(updateDoc(doc(as(uid), 'reports/listing_p1_amal'), {
      status: 'resolved', resolution: 'Closed by me', resolvedBy: uid, resolvedAt: serverTimestamp(), updatedAt: serverTimestamp(),
    })));
    it(`${label[uid]} cannot record a moderation action`, () => assertFails(setDoc(doc(collection(as(uid), 'moderationActions')), {
      reportId: 'listing_p1_amal', adminUid: uid, action: 'resolve', reason: 'Closed by me', targetPath: 'listings/p1', at: serverTimestamp(),
    })));
    it(`${label[uid]} cannot list the report queue`, () => assertFails(getDocs(collection(as(uid), 'reports'))));
    it(`${label[uid]} cannot grant themselves administrator access`, () => assertFails(setDoc(doc(as(uid), `admins/${uid}`), { name: names[uid] })));
    it(`${label[uid]} cannot hide a listing`, () => assertFails(updateDoc(doc(as(uid), 'listings/p1'), { hidden: true, moderationNote: 'Hide it', updatedAt: serverTimestamp() })));
  }
  it('the administrator cannot rewrite a listing’s content', () => assertFails(updateDoc(doc(as('admin'), 'listings/p1'), { title: 'Rewritten', updatedAt: serverTimestamp() })));
  it('the administrator cannot edit or delete a recorded moderation action', async () => {
    await assertFails(updateDoc(doc(as('admin'), 'moderationActions/a1'), { reason: 'Changed later' }));
  });
});

// ---------- US17: boundaries and state integrity ----------

describe('US17 contribution boundaries', () => {
  const add = (id, over) => setDoc(doc(as('nora'), `passports/nora/contributions/${id}`), contribution('nora', over));
  it('title length: 2 refused, 3 accepted', async () => {
    await assertFails(add('a', { title: chars(2) }));
    await assertSucceeds(add('b', { title: chars(3) }));
  });
  it('title length: 80 accepted, 81 refused', async () => {
    await assertSucceeds(add('a', { title: chars(80) }));
    await assertFails(add('b', { title: chars(81) }));
  });
  it('detail length: 500 accepted, 501 refused', async () => {
    await assertSucceeds(add('a', { detail: chars(500) }));
    await assertFails(add('b', { detail: chars(501) }));
  });
  it('supporting links: three accepted, a 301-character link refused', async () => {
    await assertSucceeds(add('a', { links: ['https://a.org', 'https://b.org', 'https://c.org'] }));
    await assertFails(add('b', { links: [`https://a.org/${chars(290)}`] }));
  });
  it('an unrelated student cannot record a contribution to a project they were never on', () =>
    assertFails(setDoc(doc(as('omar'), 'passports/omar/contributions/a'), contribution('omar'))));
  it('an applicant who never joined cannot record a contribution', () =>
    assertFails(setDoc(doc(as('amal'), 'passports/amal/contributions/a'), contribution('amal'))));
  it('a former lead is not a lead: a former member cannot confirm a contribution', () =>
    assertFails(updateDoc(doc(as('fajer'), 'passports/nora/contributions/c1'), {
      confirmedBy: 'fajer', confirmedByName: 'Fajer', confirmedAt: serverTimestamp(), updatedAt: serverTimestamp(),
    })));
  it('a student cannot attach a project title other than the real one', () =>
    assertFails(add('a', { listingTitle: 'Internship at a large company' })));
  it('a self-reported claim cannot carry a confirmer’s name', () =>
    assertFails(add('a', { confirmedByName: 'Dr Reviewer' })));
});

describe('US17 completion and participation stay distinct', () => {
  const record = (who) => doc(as(who), `passports/${who}/participation/p1`);
  it('a member cannot move a completed record back to active', async () => {
    await completed();
    await seed((db) => setDoc(doc(db, 'passports/nora/participation/p1'), participation('completed')));
    await assertFails(updateDoc(record('nora'), { status: 'active', endedAt: null, updatedAt: serverTimestamp() }));
  });
  it('a former member cannot rewrite left as completed', async () => {
    await completed();
    await assertFails(updateDoc(record('fajer'), { status: 'completed', updatedAt: serverTimestamp() }));
  });
  it('a member cannot rename their role on an active record to something they did not hold', () =>
    assertFails(updateDoc(record('nora'), { role: 'Project director', updatedAt: serverTimestamp() })));
  it('a member who left can become active again by rejoining, in their real role', async () => {
    await seed(async (db) => {
      await updateDoc(doc(db, 'listings/p1'), { 'members.fajer': member('Fajer'), memberIds: ['nora', 'fajer'] });
    });
    await assertSucceeds(updateDoc(record('fajer'), { status: 'active', endedAt: null, updatedAt: serverTimestamp() }));
  });
  it('a member with no role title is recorded as Member', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { 'members.nora.roleTitle': '' }));
    await assertSucceeds(updateDoc(record('nora'), { role: 'Member', updatedAt: serverTimestamp() }));
    await assertFails(updateDoc(record('nora'), { role: 'Project director', updatedAt: serverTimestamp() }));
  });
  it('completion cannot change the role on the record', async () => {
    await completed();
    await assertFails(updateDoc(record('nora'), { status: 'completed', role: 'Project director', endedAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  });
  it('completion needs the lead: a member cannot stamp completedAt', () =>
    assertFails(updateDoc(doc(as('nora'), 'listings/p1'), { completedAt: serverTimestamp(), updatedAt: serverTimestamp() })));
  it('nobody can join a completed project through a stale accepted application', async () => {
    await completed();
    await assertFails(setDoc(doc(as('omar'), 'listings/p1/applications/omar'), { ...application('omar', 'submitted'), createdAt: serverTimestamp(), updatedAt: serverTimestamp() }));
  });
});

// ---------- US18 ----------

describe('US18 feedback eligibility and boundaries', () => {
  const ref = (who, subject) => doc(as(who), `passports/${subject}/feedback/p1_${who}`);
  it('a former member can review the lead after leaving, including leadership', () =>
    assertSucceeds(setDoc(ref('fajer', 'sara'), review('fajer', { leadership: 3 }))));
  it('an applicant who never joined cannot review anyone', async () => {
    await completed();
    await assertFails(setDoc(ref('amal', 'sara'), review('amal')));
  });
  it('an unrelated student cannot review anyone', async () => {
    await completed();
    await assertFails(setDoc(ref('omar', 'sara'), review('omar')));
  });
  it('two active members cannot review each other before completion', () =>
    assertFails(setDoc(ref('nora', 'sara'), review('nora'))));
  it('ratings: 0 and 6 refused; 1 and 5 accepted', async () => {
    await completed();
    await assertFails(setDoc(ref('nora', 'sara'), review('nora', { contribution: 0 })));
    await assertFails(setDoc(ref('nora', 'sara'), review('nora', { contribution: 6 })));
    await assertSucceeds(setDoc(ref('nora', 'sara'), review('nora', { contribution: 1 })));
    await assertSucceeds(setDoc(ref('sara', 'nora'), review('sara', { contribution: 5 })));
  });
  it('comment length: 500 accepted, 501 refused', async () => {
    await completed();
    await assertFails(setDoc(ref('nora', 'sara'), review('nora', { comment: chars(501) })));
    await assertSucceeds(setDoc(ref('nora', 'sara'), review('nora', { comment: chars(500) })));
  });
  it('a reviewer cannot write in someone else’s name', async () => {
    await completed();
    await assertFails(setDoc(ref('nora', 'sara'), review('nora', { reviewerName: 'Fajer' })));
  });
  it('a repeated first review is refused rather than duplicated', async () => {
    await completed();
    await assertSucceeds(setDoc(ref('nora', 'sara'), review('nora')));
    await assertFails(setDoc(ref('nora', 'sara'), review('nora')));
  });
  it('dispute reason: 2 refused, 3 accepted', async () => {
    await completed();
    await seed((db) => setDoc(doc(db, 'passports/sara/feedback/p1_nora'), { ...review('nora'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    const dispute = (reason) => updateDoc(doc(as('sara'), 'passports/sara/feedback/p1_nora'), { disputed: true, disputeReason: reason, disputedAt: serverTimestamp() });
    await assertFails(dispute(chars(2)));
    await assertSucceeds(dispute(chars(3)));
  });
});

// ---------- US19 ----------

describe('US19 notifications reach only the right people', () => {
  const send = (from, to, id, over) => setDoc(doc(as(from), `users/${to}/notifications/${id}`), notice(from, over));
  it('a member can tell a teammate about a milestone', () => assertSucceeds(send('sara', 'nora', 'n2')));
  it('an unrelated student cannot notify a member', () => assertFails(send('omar', 'nora', 'n2')));
  it('nobody can notify an unrelated student about this team', () => assertFails(send('sara', 'omar', 'n2')));
  it('a retry of the same action is refused, so nothing is duplicated', async () => {
    await assertSucceeds(send('sara', 'nora', 'n2'));
    await assertFails(send('sara', 'nora', 'n2'));
  });
  it('a former member cannot send a teammate a milestone notice', () => assertFails(send('fajer', 'nora', 'n2')));
  it('an applicant cannot send a member a milestone notice', () => assertFails(send('amal', 'nora', 'n2')));
  it('a member can tell the lead a milestone is complete', () => assertSucceeds(send('nora', 'sara', 'n3', { type: 'milestone_completed' })));
  it('a former member can still tell the team they left', () => assertSucceeds(send('fajer', 'nora', 'n4', { type: 'member_left' })));
  it('a lead can pass an event change on to an applicant', () => assertSucceeds(send('sara', 'amal', 'n5', { type: 'event_changed', target: 'listing' })));
  it('a recipient can change only the read flag', async () => {
    await assertSucceeds(updateDoc(doc(as('nora'), 'users/nora/notifications/n1'), { read: true }));
    await assertFails(updateDoc(doc(as('nora'), 'users/nora/notifications/n1'), { note: 'Edited' }));
  });
});

// ---------- US20 ----------

describe('US20 moderation boundaries', () => {
  const close = (resolution, status = 'resolved') => updateDoc(doc(as('admin'), 'reports/listing_p1_amal'), {
    status, resolution, resolvedBy: 'admin', resolvedAt: serverTimestamp(), updatedAt: serverTimestamp(),
  });
  it('closing a report: empty and 2-character reasons refused', async () => {
    await assertFails(close(''));
    await assertFails(close(chars(2)));
  });
  it('closing a report: 3-character and 500-character reasons accepted', async () => {
    await assertSucceeds(close(chars(3)));
    await assertSucceeds(close(chars(500), 'dismissed'));
  });
  it('closing a report: a 501-character reason refused', () => assertFails(close(chars(501))));
  it('a recorded action needs a reason', () => assertFails(setDoc(doc(collection(as('admin'), 'moderationActions')), {
    reportId: 'listing_p1_amal', adminUid: 'admin', action: 'resolve', reason: '', targetPath: 'listings/p1', at: serverTimestamp(),
  })));
  it('an administrator cannot record an action in another administrator’s name', () => assertFails(setDoc(doc(collection(as('admin'), 'moderationActions')), {
    reportId: 'listing_p1_amal', adminUid: 'someone', action: 'resolve', reason: 'Valid reason', targetPath: 'listings/p1', at: serverTimestamp(),
  })));
  it('hiding a listing needs a note', () => assertFails(updateDoc(doc(as('admin'), 'listings/p1'), { hidden: true, moderationNote: '', updatedAt: serverTimestamp() })));
  it('a report cannot be filed in someone else’s name or under a mismatched id', async () => {
    await assertFails(setDoc(doc(as('omar'), 'reports/listing_p1_omar'), report('omar', { reporterName: 'Nora' })));
    await assertFails(setDoc(doc(as('omar'), 'reports/listing_p1_nora'), report('omar')));
  });
  it('a second report of the same thing by the same student is refused', async () => {
    await assertSucceeds(setDoc(doc(as('omar'), 'reports/listing_p1_omar'), report('omar')));
    await assertFails(setDoc(doc(as('omar'), 'reports/listing_p1_omar'), report('omar')));
  });
  it('details length: 500 accepted, 501 refused', async () => {
    await assertFails(setDoc(doc(as('omar'), 'reports/listing_p1_omar'), report('omar', { details: chars(501) })));
    await assertSucceeds(setDoc(doc(as('omar'), 'reports/listing_p1_omar'), report('omar', { details: chars(500) })));
  });
  it('a listing hidden by moderators cannot be read by an unrelated student through a direct request', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { hidden: true, moderationNote: 'Misleading' }));
    await assertFails(getDoc(doc(as('omar'), 'listings/p1')));
  });
  it('a hidden listing stays readable by its lead, member, applicant, former member and administrator', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { hidden: true, moderationNote: 'Misleading' }));
    for (const uid of ['sara', 'nora', 'amal', 'fajer', 'admin']) await assertSucceeds(getDoc(doc(as(uid), 'listings/p1')));
  });
  it('a listing that does not exist still reads as missing, not refused', () => assertSucceeds(getDoc(doc(as('omar'), 'listings/nothing'))));
  it('a listing that was never hidden stays readable by anyone signed in', () => assertSucceeds(getDoc(doc(as('omar'), 'listings/p1'))));
  it('an unfiltered list of listings is refused, so hidden ones cannot leak through a query', () =>
    assertFails(getDocs(query(collection(as('omar'), 'listings'), orderBy('createdAt', 'desc'), limit(50)))));
  it('a list of hidden listings is refused to a student', () =>
    assertFails(getDocs(query(collection(as('omar'), 'listings'), where('hidden', '==', true)))));
  it('the feed query returns visible listings and leaves hidden ones out', async () => {
    await seed(async (db) => {
      await setDoc(doc(db, 'listings/p2'), { ...listing, title: 'Hidden one', hidden: true, moderationNote: 'Misleading' });
    });
    const snap = await assertSucceeds(getDocs(query(collection(as('omar'), 'listings'), where('hidden', '==', false), orderBy('createdAt', 'desc'), limit(50))));
    assert.deepEqual(snap.docs.map((d) => d.id), ['p1']);
  });
  it('team requests for an event: the visible-only query is allowed', () =>
    assertSucceeds(getDocs(query(collection(as('omar'), 'listings'), where('eventId', '==', 'e1'), where('hidden', '==', false)))));
  it('a lead still lists their own listings, hidden ones included', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { hidden: true, moderationNote: 'Misleading' }));
    const snap = await assertSucceeds(getDocs(query(collection(as('sara'), 'listings'), where('ownerId', '==', 'sara'), orderBy('createdAt', 'desc'))));
    assert.equal(snap.size, 1);
  });
  it('a member still lists their teams, hidden ones included', async () => {
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { hidden: true, moderationNote: 'Misleading' }));
    const snap = await assertSucceeds(getDocs(query(collection(as('nora'), 'listings'), where('memberIds', 'array-contains', 'nora'))));
    assert.equal(snap.size, 1);
  });
  it('a student cannot list someone else’s listings or teams to reach hidden ones', async () => {
    await assertFails(getDocs(query(collection(as('omar'), 'listings'), where('ownerId', '==', 'sara'))));
    await assertFails(getDocs(query(collection(as('omar'), 'listings'), where('memberIds', 'array-contains', 'nora'))));
  });
  it('a new listing must start visible', async () => {
    const fresh = { ...listing, status: 'open', members: {}, memberIds: [], createdAt: serverTimestamp(), updatedAt: serverTimestamp() };
    await assertFails(setDoc(doc(as('sara'), 'listings/p9'), { ...fresh, hidden: true }));
    const { hidden, ...noFlag } = fresh;
    await assertFails(setDoc(doc(as('sara'), 'listings/p9'), noFlag));
    await assertSucceeds(setDoc(doc(as('sara'), 'listings/p9'), fresh));
  });
});

// ---------- US17, US18: people who left stay on the record ----------

describe('US17, US18 former members stay on the listing', () => {
  const teamOfTwo = async () => {
    await seed(async (db) => {
      await updateDoc(doc(db, 'listings/p1'), { 'members.fajer': member('Fajer'), memberIds: arrayUnion('fajer'), teamSize: 3 });
    });
    let members;
    await seed(async (db) => { members = (await getDoc(doc(db, 'listings/p1'))).data().members; });
    return members;
  };
  const leaving = (entry) => ({
    'members.fajer': deleteField(), memberIds: arrayRemove('fajer'), teamSize: 2, 'formerMembers.fajer': entry, updatedAt: serverTimestamp(),
  });
  it('a member who leaves is kept as a former member, in their real entry', async () => {
    const members = await teamOfTwo();
    await assertSucceeds(updateDoc(doc(as('fajer'), 'listings/p1'), leaving(members.fajer)));
  });
  it('a member who leaves cannot change the name or role kept for them', async () => {
    const members = await teamOfTwo();
    await assertFails(updateDoc(doc(as('fajer'), 'listings/p1'), leaving({ ...members.fajer, roleTitle: 'Project director' })));
  });
  it('a member who leaves cannot add someone else as a former member', async () => {
    const members = await teamOfTwo();
    await assertFails(updateDoc(doc(as('fajer'), 'listings/p1'), { ...leaving(members.fajer), 'formerMembers.nora': members.nora }));
  });
  it('a lead who removes a member keeps them as a former member, in their real entry', async () => {
    const members = await teamOfTwo();
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1'), leaving(members.fajer)));
  });
  it('a lead cannot change the entry of someone they remove', async () => {
    const members = await teamOfTwo();
    await assertFails(updateDoc(doc(as('sara'), 'listings/p1'), leaving({ ...members.fajer, name: 'Somebody else' })));
  });
  it('a lead cannot list a current member as former', async () => {
    const members = await teamOfTwo();
    await assertFails(updateDoc(doc(as('sara'), 'listings/p1'), { 'formerMembers.nora': members.nora, updatedAt: serverTimestamp() }));
  });
  it('a lead cannot erase a former member from the listing', async () => {
    const members = await teamOfTwo();
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1'), leaving(members.fajer)));
    await assertFails(updateDoc(doc(as('sara'), 'listings/p1'), { 'formerMembers.fajer': deleteField(), updatedAt: serverTimestamp() }));
  });
  it('a former list cannot be planted when a listing is created', async () => {
    const fresh = { ...listing, members: {}, memberIds: [], createdAt: serverTimestamp(), updatedAt: serverTimestamp() };
    await assertFails(setDoc(doc(as('sara'), 'listings/p9'), { ...fresh, formerMembers: { nora: member('Nora') } }));
  });
  it('the largest team (eight roles) can still leave, be removed and hand over within the rules limits', async () => {
    const roles = Array.from({ length: 8 }, (_, i) => ({ ...role, id: `r${i + 1}`, skills: ['Kotlin', 'Firebase', 'Compose'] }));
    const members = await teamOfTwo();
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { roles, skills: Array.from({ length: 10 }, (_, i) => `skill${i}`) }));
    await assertSucceeds(updateDoc(doc(as('fajer'), 'listings/p1'), leaving(members.fajer)));
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { 'members.amal': member('Amal'), memberIds: arrayUnion('amal'), teamSize: 3 }));
    let amal;
    await seed(async (db) => { amal = (await getDoc(doc(db, 'listings/p1'))).data().members.amal; });
    await assertSucceeds(updateDoc(doc(as('sara'), 'listings/p1'), {
      'members.amal': deleteField(), memberIds: arrayRemove('amal'), teamSize: 2, 'formerMembers.amal': amal, updatedAt: serverTimestamp(),
    }));
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { pendingLeader: { uid: 'nora', name: 'Nora', requestedAt: Timestamp.now() } }));
    await assertSucceeds(updateDoc(doc(as('nora'), 'listings/p1'), {
      ownerId: 'nora', ownerName: 'Nora', ownerMajor: 'SE', pendingLeader: null, memberIds: ['sara'],
      'members.nora': deleteField(), 'members.sara': member('Sara'), updatedAt: serverTimestamp(),
    }));
  });
  it('someone who rejoined and left again gets a fresh entry', async () => {
    const members = await teamOfTwo();
    await seed((db) => updateDoc(doc(db, 'listings/p1'), { formerMembers: { fajer: { ...members.fajer, roleTitle: 'Earlier role' } } }));
    await assertSucceeds(updateDoc(doc(as('fajer'), 'listings/p1'), leaving(members.fajer)));
  });
  it('a current member can review someone who left, before the project ends', () =>
    assertSucceeds(setDoc(doc(as('nora'), 'passports/fajer/feedback/p1_nora'), review('nora'))));
  it('the lead can confirm a former member’s contribution', async () => {
    await seed((db) => setDoc(doc(db, 'passports/fajer/contributions/c2'), { ...contribution('fajer'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    await assertSucceeds(updateDoc(doc(as('sara'), 'passports/fajer/contributions/c2'), {
      confirmedBy: 'sara', confirmedByName: 'Sara', confirmedAt: serverTimestamp(), updatedAt: serverTimestamp(),
    }));
  });
  it('the lead can record a contribution for a former member', () =>
    assertSucceeds(setDoc(doc(as('sara'), 'passports/fajer/contributions/c3'), contribution('sara', {
      confirmedBy: 'sara', confirmedByName: 'Sara', confirmedAt: serverTimestamp(),
    }))));
  it('a current member can read a former member’s contributions for this project', async () => {
    await seed((db) => setDoc(doc(db, 'passports/fajer/contributions/c2'), { ...contribution('fajer'), createdAt: Timestamp.now(), updatedAt: Timestamp.now() }));
    await assertSucceeds(getDocs(query(collection(as('nora'), 'passports/fajer/contributions'), where('listingId', '==', 'p1'))));
  });
  it('a contribution with the real project title is accepted', () =>
    assertSucceeds(setDoc(doc(as('nora'), 'passports/nora/contributions/c4'), contribution('nora'))));
});

// ---------- US22 traceability ----------

describe('US22 the action log can be trusted', () => {
  const entry = (who, action) => setDoc(doc(collection(as(who), 'auditLog')), {
    action, actorUid: who, listingId: 'p1', subjectUid: '', detail: '', at: serverTimestamp(),
  });
  it('a student can log their own action', () => assertSucceeds(entry('amal', 'report_created')));
  it('a student cannot log a moderation action', () => assertFails(entry('omar', 'moderation')));
  it('an unrelated student cannot log that they completed someone else’s project', () => assertFails(entry('omar', 'project_completed')));
  it('an administrator can log a moderation action', () => assertSucceeds(entry('admin', 'moderation')));
  it('the lead can log completing their own project', () => assertSucceeds(entry('sara', 'project_completed')));
  it('a member cannot log the lead’s actions', async () => {
    for (const action of ['offer_sent', 'member_removed', 'lead_nominated', 'place_reopened', 'contribution_confirmed']) await assertFails(entry('nora', action));
  });
  it('a member can log leaving their own team', () => assertSucceeds(entry('nora', 'member_left')));
  it('an applicant can log answering their own offer', () => assertSucceeds(entry('amal', 'offer_declined')));
  it('an unrelated student cannot log team actions on a listing they have no part in', async () => {
    for (const action of ['member_left', 'offer_accepted', 'offer_declined', 'lead_declined', 'lead_accepted']) await assertFails(entry('omar', action));
  });
  it('nobody can change or delete an entry', async () => {
    await assertFails(updateDoc(doc(as('admin'), 'auditLog/l1'), { detail: 'Changed' }));
  });
  it('after leaving, a former member’s passport share stops the workspace too', async () => {
    const db = as('nora');
    const batch = writeBatch(db);
    batch.update(doc(db, 'listings/p1'), { 'members.nora': deleteField(), memberIds: arrayRemove('nora'), teamSize: 1, updatedAt: serverTimestamp() });
    batch.update(doc(db, 'listings/p1/applications/nora'), { status: 'left', updatedAt: serverTimestamp() });
    batch.update(doc(db, 'passports/nora/participation/p1'), { status: 'left', endedAt: serverTimestamp(), updatedAt: serverTimestamp() });
    await assertSucceeds(batch.commit());
    await assertFails(getDoc(doc(as('nora'), 'listings/p1/updates/u1')));
    await assertFails(setDoc(doc(as('nora'), 'listings/p1/updates/u3'), { authorId: 'nora', authorName: 'Nora', text: 'Still here', link: '', createdAt: serverTimestamp() }));
  });
});
