// Removes what tests/live-smoke.mjs created: its Firestore documents (through the Firebase CLI,
// which needs `firebase login`) and its QA accounts (each account deletes itself).
//
//   npm run smoke:cleanup
import { existsSync, readFileSync, rmSync } from 'node:fs';
import { execSync } from 'node:child_process';

const file = new URL('./.live-smoke-created.json', import.meta.url);
if (!existsSync(file)) {
  console.log('Nothing to clean up.');
  process.exit(0);
}
const created = JSON.parse(readFileSync(file, 'utf8'));
const services = JSON.parse(readFileSync(new URL('../../app/google-services.json', import.meta.url), 'utf8'));
const KEY = services.client[0].api_key[0].current_key;
const PASSWORD = `Qa-${created.run}-pass1`;
let problems = 0;

for (const path of created.docs) {
  try {
    // Paths come from our own record file (collection/id pairs), so quoting them is enough.
    execSync(`firebase firestore:delete "${path}" --recursive --force --project ${created.project}`, { stdio: 'pipe' });
    console.log(`  deleted ${path}`);
  } catch (e) {
    problems++;
    console.log(`  could not delete ${path}: ${String(e.stderr || e.message).split('\n')[0]}`);
  }
}

for (const { email } of created.users) {
  const signIn = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${KEY}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD, returnSecureToken: true }),
  }).then((r) => r.json());
  if (!signIn.idToken) {
    console.log(`  account ${email} not found (already removed?)`);
    continue;
  }
  const res = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:delete?key=${KEY}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ idToken: signIn.idToken }),
  });
  if (res.ok) console.log(`  deleted account ${email}`);
  else { problems++; console.log(`  could not delete account ${email}`); }
}

if (problems === 0) rmSync(file);
console.log(problems ? `${problems} item(s) left; see above.` : 'Clean.');
