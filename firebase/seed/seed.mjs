// Fills the local Firebase emulators with synthetic students, events, projects and
// applications (charter §6.5: synthetic profiles for development and testing).
//
//   npm run emulators          # in one terminal
//   npm run seed               # in another
//
// Every account signs in with the password below. Nothing here touches a real project:
// requests only go to the emulator hosts.
import { readFileSync } from 'node:fs';

const PASSWORD = 'Takatuf2026';
const PROJECT = process.env.GCLOUD_PROJECT
  ?? JSON.parse(readFileSync(new URL('../.firebaserc', import.meta.url), 'utf8')).projects.default;
const AUTH = `http://${process.env.FIREBASE_AUTH_EMULATOR_HOST ?? '127.0.0.1:9099'}`;
const FIRESTORE = `http://${process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080'}`;
const DOCS = `${FIRESTORE}/v1/projects/${PROJECT}/databases/(default)/documents`;

const DAY = 24 * 60 * 60 * 1000;
const midnightUtc = (offsetDays) => {
  const d = new Date(Date.now() + offsetDays * DAY);
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
};
/** First instant of a day, for starts. */
const dayStart = (offsetDays) => midnightUtc(offsetDays);
/** First instant after a day, for deadlines and ends (the app treats them as exclusive). */
const dayEnd = (offsetDays) => midnightUtc(offsetDays + 1);
const ago = (hours) => new Date(Date.now() - hours * 60 * 60 * 1000);

// ---------- Firestore REST encoding ----------
function encode(value) {
  if (value === null || value === undefined) return { nullValue: null };
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(encode) } };
  switch (typeof value) {
    case 'string': return { stringValue: value };
    case 'boolean': return { booleanValue: value };
    case 'number': return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
    case 'object': return { mapValue: { fields: encodeFields(value) } };
    default: throw new Error(`Cannot encode ${typeof value}`);
  }
}
const encodeFields = (obj) => Object.fromEntries(Object.entries(obj).map(([k, v]) => [k, encode(v)]));

async function put(path, data) {
  const res = await fetch(`${DOCS}/${path}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer owner' },
    body: JSON.stringify({ fields: encodeFields(data) }),
  });
  if (!res.ok) throw new Error(`${path}: ${res.status} ${await res.text()}`);
}

async function createUser(email, displayName) {
  const res = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=local-emulator`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD, displayName, returnSecureToken: true }),
  });
  const body = await res.json();
  if (res.ok) return body.localId;
  if (body?.error?.message === 'EMAIL_EXISTS') {
    const signIn = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=local-emulator`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
    });
    return (await signIn.json()).localId;
  }
  throw new Error(`${email}: ${JSON.stringify(body)}`);
}

// ---------- people ----------
const people = {
  sara: {
    name: 'Sara Alqahtani', email: 'sara@takatuf.test', major: 'Software Engineering (AI)', year: 3,
    headline: 'Android developer who likes shipping things students actually use.', visibility: 'everyone',
    skills: [['Kotlin', 3], ['Jetpack Compose', 3], ['Firebase', 2], ['Python', 2], ['Figma', 1]],
    interests: ['Education', 'Artificial intelligence', 'Smart cities'],
    availability: { hoursPerWeek: 8, days: ['sun', 'mon', 'tue', 'wed', 'thu'], note: 'Free most evenings after 5 pm' },
    experience: [{ id: 'x1', title: 'Android developer', organization: 'PSU Clubs app', period: 'Fall 2025', description: 'Built the events feed and sign-up flow used by 400 students.' }],
    links: [{ id: 'l1', label: 'GitHub', url: 'https://github.com/example-sara' }],
  },
  nora: {
    name: 'Nora Alharbi', email: 'nora@takatuf.test', major: 'Computer Science', year: 3,
    headline: 'Designer who codes. Into health tech.', visibility: 'applied',
    skills: [['Figma', 3], ['Prototyping', 3], ['UX research', 2], ['Usability testing', 2], ['HTML and CSS', 2]],
    interests: ['Health', 'Accessibility', 'Education'],
    availability: { hoursPerWeek: 6, days: ['sun', 'tue', 'thu'], note: '' },
    experience: [{ id: 'x1', title: 'UI designer', organization: 'Campus Health Week', period: 'Spring 2026', description: 'Designed the booking flow and tested it with 15 students.' }],
    links: [{ id: 'l1', label: 'Portfolio', url: 'https://example.com/nora' }],
  },
  omar: {
    name: 'Omar Alshehri', email: 'omar@takatuf.test', major: 'Cybersecurity', year: 4,
    headline: 'CTF player. Happy to break your app before someone else does.', visibility: 'everyone',
    skills: [['Threat modeling', 3], ['Secure coding', 2], ['Network security', 3], ['Python', 2], ['Linux', 3]],
    interests: ['Cybersecurity', 'Fintech'],
    availability: { hoursPerWeek: 10, days: ['sun', 'mon', 'wed'], note: '' },
    experience: [], links: [],
  },
  reem: {
    name: 'Reem Aldosari', email: 'reem@takatuf.test', major: 'Data Science', year: 2,
    headline: 'Turning messy data into decisions.', visibility: 'everyone',
    skills: [['Python', 3], ['SQL', 2], ['Data analysis', 3], ['Data visualization', 2], ['NLP', 1]],
    interests: ['Artificial intelligence', 'Education', 'Health'],
    availability: { hoursPerWeek: 6, days: ['mon', 'tue', 'wed'], note: '' },
    experience: [], links: [],
  },
  faisal: {
    name: 'Faisal Alotaibi', email: 'faisal@takatuf.test', major: 'Software Engineering', year: 4,
    headline: 'Backend and APIs.', visibility: 'private',
    skills: [['Python', 3], ['FastAPI', 3], ['SQL', 3], ['REST APIs', 3], ['Git', 2]],
    interests: ['Fintech', 'E-commerce'],
    availability: { hoursPerWeek: 8, days: ['sun', 'mon', 'tue'], note: '' },
    experience: [], links: [],
  },
  lama: {
    name: 'Lama Alzahrani', email: 'lama@takatuf.test', major: 'Marketing', year: 3,
    headline: 'Pitches, stories and launch plans.', visibility: 'everyone',
    skills: [['Market research', 3], ['Pitching', 3], ['Public speaking', 3], ['Copywriting', 2]],
    interests: ['Entrepreneurship', 'Social impact', 'Arts and culture'],
    availability: { hoursPerWeek: 5, days: ['wed', 'thu'], note: '' },
    experience: [], links: [],
  },
  yousef: {
    name: 'Yousef Almutairi', email: 'yousef@takatuf.test', major: 'Electrical Engineering', year: 3,
    headline: 'Sensors, boards and the code that runs on them.', visibility: 'everyone',
    skills: [['Arduino', 3], ['Embedded C', 2], ['IoT', 3], ['Circuit design', 2]],
    interests: ['Smart cities', 'Energy', 'Robotics'],
    availability: { hoursPerWeek: 6, days: ['sun', 'tue', 'thu'], note: 'Lab hours on Mondays' },
    experience: [], links: [],
  },
  hessa: {
    name: 'Hessa Alsubaie', email: 'hessa@takatuf.test', major: 'Information Systems', year: 2,
    headline: 'Keeps teams on schedule.', visibility: 'everyone',
    skills: [['Project management', 2], ['Agile and Scrum', 2], ['Technical writing', 2], ['Excel', 3]],
    interests: ['Education', 'Social impact'],
    availability: { hoursPerWeek: 4, days: ['sun', 'mon'], note: '' },
    experience: [], links: [],
  },
};

const uid = {};
for (const [key, p] of Object.entries(people)) {
  uid[key] = await createUser(p.email, p.name);
  await put(`profiles/${uid[key]}`, {
    displayName: p.name, major: p.major, year: p.year, headline: p.headline,
    visibility: p.visibility, onboarded: true, createdAt: ago(24 * 30), updatedAt: ago(24),
  });
  await put(`passports/${uid[key]}`, {
    skills: p.skills.map(([name, level]) => ({ name, level })),
    interests: p.interests, experience: p.experience, availability: p.availability, links: p.links,
    updatedAt: ago(24),
  });
}

const owner = (key) => ({ ownerId: uid[key], ownerName: people[key].name, ownerMajor: people[key].major });
const norm = (roles) => [...new Set(roles.flatMap((r) => r.skills.map((s) => s.toLowerCase())))];
const recruiting = (roles) => ({ roles, openPlaces: roles.reduce((n, r) => n + r.openings, 0), skills: norm(roles) });

// ---------- events ----------
const events = {
  najd: {
    title: 'Najd Smart City Hackathon', eventType: 'hackathon', organizer: 'Riyadh Tech Society',
    sourceUrl: 'https://example.org/najd-smart-city', start: 18, end: 20, reg: 9,
    eligibility: 'Undergraduate students at universities in Saudi Arabia. At least one team member must be in a computing major.',
    min: 3, max: 5, format: 'in_person', location: 'Riyadh Front', by: 'omar', createdHoursAgo: 60,
    description: 'Forty-eight hours to prototype services for a smarter Riyadh: mobility, energy and public spaces. Mentors from local startups on site.',
  },
  cyber: {
    title: 'National Cybersecurity Challenge 2026', eventType: 'competition', organizer: 'Saudi Cyber Club Network',
    sourceUrl: 'https://example.org/cyber-challenge', start: 35, end: 36, reg: 25,
    eligibility: 'Students enrolled in a Saudi university. Teams register under one university.',
    min: 2, max: 4, format: 'online', location: '', by: 'omar', createdHoursAgo: 120,
    description: 'Capture-the-flag rounds on web, forensics and cryptography, followed by a final for the top 20 teams.',
  },
  health: {
    title: 'HealthTech Innovation Sprint', eventType: 'hackathon', organizer: 'Riyadh Health Innovation Lab',
    sourceUrl: 'https://example.org/healthtech-sprint', start: 6, end: 8, reg: 2,
    eligibility: 'Open to students from any major. Mixed-discipline teams are encouraged.',
    min: 2, max: 4, format: 'hybrid', location: 'PSU campus', by: 'reem', createdHoursAgo: 30,
    description: 'Design and test a digital tool that helps patients manage chronic conditions between clinic visits.',
  },
  ramadan: {
    title: 'Ramadan Social Impact Challenge', eventType: 'competition', organizer: 'Community Innovation Forum',
    sourceUrl: 'https://example.org/ramadan-impact', start: -40, end: -38, reg: -45,
    eligibility: 'University students in the GCC.',
    min: 2, max: 5, format: 'online', location: '', by: 'lama', createdHoursAgo: 24 * 70,
    description: 'Ideas that make volunteering during Ramadan easier to organize.',
  },
};
const eventIds = {};
for (const [key, e] of Object.entries(events)) {
  eventIds[key] = `event-${key}`;
  await put(`listings/${eventIds[key]}`, {
    kind: 'event', status: 'open', title: e.title, description: e.description, ...owner(e.by),
    createdAt: ago(e.createdHoursAgo), updatedAt: ago(e.createdHoursAgo),
    eventType: e.eventType, organizer: e.organizer, sourceUrl: e.sourceUrl,
    startDate: dayStart(e.start), endDate: dayEnd(e.end), registrationDeadline: dayEnd(e.reg),
    eligibility: e.eligibility, teamSizeMin: e.min, teamSizeMax: e.max, format: e.format, location: e.location,
  });
}
const eventSnapshot = (key) => {
  const e = events[key];
  return {
    title: e.title, organizer: e.organizer, type: e.eventType,
    startDate: dayStart(e.start), endDate: dayEnd(e.end), teamSizeMin: e.min, teamSizeMax: e.max,
  };
};

// ---------- projects and team requests ----------
const role = (id, title, skills, openings = 1, description = '') => ({ id, title, description, skills, openings });

const listings = {
  parking: {
    kind: 'project', by: 'sara', hoursAgo: 50, title: 'Campus parking finder',
    outcome: 'An Android app that shows free parking spots at PSU in real time, tested with 30 students.',
    roles: [
      role('ux', 'UI/UX designer', ['Figma', 'UX research', 'Prototyping'], 1, 'Designs the map and booking flow and tests them with drivers.'),
      role('be', 'Backend developer', ['Python', 'FastAPI', 'SQL'], 1, 'Builds the API that collects sensor readings.'),
      role('iot', 'Hardware and IoT engineer', ['Arduino', 'IoT', 'Circuit design'], 1, 'Wires the parking-bay sensors in lot B.'),
    ],
    hours: 6, duration: 'One semester', teamSize: 2, deadline: 14,
    description: 'We have a working map prototype and permission to trial sensors in one parking lot. Weekly check-ins on Sundays.',
  },
  chatbot: {
    kind: 'project', by: 'reem', hoursAgo: 20, title: 'Arabic study-buddy chatbot',
    outcome: 'A mobile tutor that answers first-year questions in Arabic and English, evaluated with two course sections.',
    roles: [
      role('ml', 'Machine learning engineer', ['Python', 'NLP', 'PyTorch'], 1),
      role('mob', 'Mobile developer', ['Kotlin', 'Jetpack Compose', 'Firebase'], 1, 'Builds the chat app and connects it to the model API.'),
    ],
    hours: 8, duration: 'One semester', teamSize: 1, deadline: 21,
    description: 'Course coordinators have agreed to share anonymized FAQ data.',
  },
  lostfound: {
    kind: 'project', by: 'faisal', hoursAgo: 90, title: 'Lost and found for PSU',
    outcome: 'A web and Android service where students report and claim lost items, piloted with campus security.',
    roles: [
      role('fe', 'Frontend developer', ['React', 'TypeScript', 'HTML and CSS'], 1),
      role('mob', 'Mobile developer', ['Kotlin', 'Firebase'], 1),
    ],
    hours: 5, duration: '8 weeks', teamSize: 2, deadline: 10,
    description: '',
  },
  dashboard: {
    kind: 'project', by: 'hessa', hoursAgo: 8, title: 'Club events dashboard',
    outcome: 'A dashboard showing attendance trends for student clubs, handed over to Student Affairs.',
    roles: [
      role('da', 'Data analyst', ['Python', 'SQL', 'Data visualization'], 1),
      role('ux', 'UI/UX designer', ['Figma', 'Prototyping'], 1),
    ],
    hours: 4, duration: '4 weeks', teamSize: 1, deadline: 2,
    description: 'Short project with a clear finish line before finals.',
  },
  campaign: {
    kind: 'project', by: 'lama', hoursAgo: 24 * 12, title: 'Sustainable campus campaign', status: 'closed',
    outcome: 'A month-long campaign that cuts single-use cups in the cafeteria by a third.',
    roles: [role('cc', 'Content creator', ['Copywriting', 'Video editing', 'Social media'], 2)],
    hours: 4, duration: '4 weeks', teamSize: 2, deadline: null,
    description: '',
  },
  najdTeam: {
    kind: 'team', by: 'omar', hoursAgo: 14, title: 'Team for Najd Smart City Hackathon', event: 'najd',
    outcome: 'A crowd-sourced map of shaded walking routes between metro stations.',
    roles: [
      role('mob', 'Mobile developer', ['Kotlin', 'Jetpack Compose'], 1),
      role('ux', 'UI/UX designer', ['Figma', 'Prototyping'], 1),
      role('biz', 'Business and pitch lead', ['Pitching', 'Market research'], 1),
    ],
    hours: 10, duration: 'Until the event', teamSize: 2, deadline: 9,
    description: 'We have two developers and an idea judges liked at last year’s demo day.',
  },
  healthTeam: {
    kind: 'team', by: 'yousef', hoursAgo: 5, title: 'Team for HealthTech Innovation Sprint', event: 'health',
    outcome: '',
    roles: [
      role('da', 'Data analyst', ['Python', 'Data analysis'], 1),
      role('mob', 'Mobile developer', ['Kotlin', 'Firebase'], 1),
    ],
    hours: 6, duration: 'Until the event', teamSize: 1, deadline: 2,
    description: 'Looking to build a medication reminder that works with a smart pill box.',
  },
  cyberTeam: {
    kind: 'team', by: 'sara', hoursAgo: 36, title: 'Team for National Cybersecurity Challenge', event: 'cyber',
    outcome: 'Reach the final round.',
    roles: [role('sec', 'Cybersecurity specialist', ['Threat modeling', 'Network security', 'Linux'], 2)],
    hours: 6, duration: 'Until the event', teamSize: 1, deadline: 25,
    description: '',
  },
};
const listingIds = {};
for (const [key, l] of Object.entries(listings)) {
  listingIds[key] = `${l.kind}-${key}`;
  await put(`listings/${listingIds[key]}`, {
    kind: l.kind, status: l.status ?? 'open', title: l.title, description: l.description, ...owner(l.by),
    createdAt: ago(l.hoursAgo), updatedAt: ago(l.hoursAgo),
    outcome: l.outcome, ...recruiting(l.roles), hoursPerWeek: l.hours, duration: l.duration,
    teamSize: l.teamSize, deadline: l.deadline === null ? null : dayEnd(l.deadline),
    ...(l.event ? { eventId: eventIds[l.event], event: eventSnapshot(l.event) } : {}),
  });
}

// ---------- applications ----------
async function apply(listingKey, applicantKey, roleId, contribution, hours, status = 'submitted', reviewed = false, hoursAgo = 10, note = '') {
  const l = listings[listingKey];
  const r = l.roles.find((x) => x.id === roleId);
  const ownerUid = uid[l.by];
  await put(`listings/${listingIds[listingKey]}/applications/${uid[applicantKey]}`, {
    listingId: listingIds[listingKey], ownerId: ownerUid, applicantId: uid[applicantKey],
    applicantName: people[applicantKey].name, applicantMajor: people[applicantKey].major,
    listingTitle: l.title, listingKind: l.kind, roleId, roleTitle: r.title,
    contribution, hoursPerWeek: hours, availabilityNote: note, status, reviewed,
    createdAt: ago(hoursAgo + 2), updatedAt: ago(hoursAgo),
  });
  await put(`passports/${uid[applicantKey]}/viewers/${ownerUid}`, { listingId: listingIds[listingKey], grantedAt: ago(hoursAgo) });
}

await apply('parking', 'nora', 'ux', 'I designed the booking flow for Campus Health Week and tested it with 15 students. I can take the parking map from wireframes to a tested prototype.', 6, 'submitted', false, 3);
await apply('parking', 'faisal', 'be', 'I have built three FastAPI services with PostgreSQL, including one that ingests sensor data every minute.', 8, 'shortlisted', true, 20);
await apply('parking', 'yousef', 'iot', 'I have wired ultrasonic sensors to an ESP32 for a lab project and can prototype the bay sensors.', 6, 'submitted', true, 26, 'Lab hours on Mondays');
await apply('parking', 'reem', 'be', 'Interested in the data side of the project.', 4, 'withdrawn', true, 40);
await apply('chatbot', 'sara', 'mob', 'I build Android apps in Compose and Firebase every day and can have a chat prototype running in two weeks.', 8, 'submitted', false, 6);
await apply('lostfound', 'sara', 'mob', 'I shipped the PSU Clubs app on Android and can own the claim flow end to end.', 5, 'shortlisted', true, 30);
await apply('cyberTeam', 'omar', 'sec', 'I play CTFs every weekend and placed in the top 10 in two regional rounds this year.', 10, 'submitted', false, 2);

// ---------- saved ----------
await put(`users/${uid.sara}/saved/${eventIds.najd}`, { savedAt: ago(12) });
await put(`users/${uid.sara}/saved/${listingIds.dashboard}`, { savedAt: ago(4) });

console.log(`Seeded project "${PROJECT}": ${Object.keys(people).length} students, ${Object.keys(events).length} events, ${Object.keys(listings).length} projects and team requests.`);
console.log(`Sign in as any of: ${Object.values(people).map((p) => p.email).join(', ')}`);
console.log(`Password: ${PASSWORD}`);
