// Run with: npm test   (Node 18+; uses the built-in test runner and fetch)
const test = require('node:test');
const assert = require('node:assert');
const os = require('os');
const path = require('path');

process.env.DATA_FILE = path.join(os.tmpdir(), `communiserve-test-${Date.now()}.json`);
process.env.JWT_SECRET = 'test-secret';
const app = require('../server');

let server, base;
test.before(async () => {
  server = app.listen(0);
  await new Promise(r => server.once('listening', r));
  base = `http://127.0.0.1:${server.address().port}`;
});
test.after(() => server.close());

const call = (p, opts = {}) => fetch(base + p, { ...opts, headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) } });
const email = `user${Date.now()}@example.co.za`;
let token;

test('health check responds', async () => {
  const r = await call('/health');
  assert.strictEqual(r.status, 200);
  assert.strictEqual((await r.json()).status, 'ok');
});

test('registration rejects a weak password and a bad email', async () => {
  let r = await call('/api/auth/register', { method: 'POST', body: JSON.stringify({ name: 'Thabo', email, password: 'short' }) });
  assert.strictEqual(r.status, 400);
  r = await call('/api/auth/register', { method: 'POST', body: JSON.stringify({ name: 'Thabo', email: 'nope', password: 'LongEnough1' }) });
  assert.strictEqual(r.status, 400);
});

test('register then log in; duplicate email and wrong password are refused', async () => {
  let r = await call('/api/auth/register', { method: 'POST', body: JSON.stringify({ name: 'Thabo', email, password: 'LongEnough1' }) });
  assert.strictEqual(r.status, 201);
  const body = await r.json();
  assert.ok(body.token);
  assert.strictEqual(body.user.passwordHash, undefined, 'password hash must never be returned');
  r = await call('/api/auth/register', { method: 'POST', body: JSON.stringify({ name: 'Thabo', email, password: 'LongEnough1' }) });
  assert.strictEqual(r.status, 409);
  r = await call('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password: 'wrong-password' }) });
  assert.strictEqual(r.status, 401);
  r = await call('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password: 'LongEnough1' }) });
  assert.strictEqual(r.status, 200);
  token = (await r.json()).token;
});

test('single sign-on: verified Firebase token issues a session; email comes from the token', async () => {
  // Fake verifier stands in for Google's certificate check (the real one needs network + a Firebase project).
  app.locals.verifyIdToken = async t => {
    if (t !== 'valid-token') throw new Error('bad token');
    return { email: 'sso.user@gmail.com', name: 'Sso User' };
  };
  // A forged body email must be ignored - the verified token's email wins.
  let r = await call('/api/auth/sso', { method: 'POST', body: JSON.stringify({ idToken: 'valid-token', email: 'victim@example.com' }) });
  assert.strictEqual(r.status, 200);
  assert.strictEqual((await r.json()).user.email, 'sso.user@gmail.com');
  r = await call('/api/auth/sso', { method: 'POST', body: JSON.stringify({ idToken: 'forged', email: 'victim@example.com' }) });
  assert.strictEqual(r.status, 401);
  r = await call('/api/auth/sso', { method: 'POST', body: JSON.stringify({ email: 'victim@example.com' }) });
  assert.strictEqual(r.status, 400);
});

test('protected routes reject missing, malformed and tampered tokens', async () => {
  let r = await call('/api/users/profile');
  assert.strictEqual(r.status, 401);
  r = await call('/api/users/profile', { headers: { Authorization: 'Bearer not.a.jwt' } });
  assert.strictEqual(r.status, 401);
  r = await call('/api/users/profile', { headers: { Authorization: `Bearer ${token}x` } });
  assert.strictEqual(r.status, 401);
  r = await call('/api/users/profile', { headers: { Authorization: `Bearer ${token}` } });
  assert.strictEqual(r.status, 200);
});

test('forgot-password is honest that e-mail reset is not implemented', async () => {
  const r = await call('/api/auth/forgot-password', { method: 'POST', body: JSON.stringify({ email }) });
  assert.strictEqual(r.status, 501);
});

test('public listing search works and respects the radius', async () => {
  let r = await call('/api/services?q=plumb&radius=10');
  const list = await r.json();
  assert.ok(list.length >= 1);
  r = await call('/api/services?radius=1');
  assert.strictEqual((await r.json()).length, 0);
});

test('creating a listing needs auth; new listings are hidden until approved', async () => {
  const payload = JSON.stringify({ title: 'Garden Care', category: 'Gardening', description: 'Lawns', phone: '0110000009' });
  let r = await call('/api/services', { method: 'POST', body: payload });
  assert.strictEqual(r.status, 401);
  r = await call('/api/services', { method: 'POST', body: payload, headers: { Authorization: `Bearer ${token}` } });
  assert.strictEqual(r.status, 201);
  const created = await r.json();
  let services = await (await call('/api/services')).json();
  assert.ok(!services.some(s => s.id === created.id), 'unverified listing must not be public');
  r = await call(`/api/pending/${created.id}/approve`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
  assert.strictEqual(r.status, 200);
  services = await (await call('/api/services?radius=50')).json();
  assert.ok(services.some(s => s.id === created.id && s.verified));
});

test('reviews validate rating and text', async () => {
  const auth = { Authorization: `Bearer ${token}` };
  let r = await call('/api/reviews/s1', { method: 'POST', headers: auth, body: JSON.stringify({ rating: 9, text: 'x' }) });
  assert.strictEqual(r.status, 400);
  r = await call('/api/reviews/s1', { method: 'POST', headers: auth, body: JSON.stringify({ rating: 5, text: 'Great work' }) });
  assert.strictEqual(r.status, 201);
  const reviews = await (await call('/api/reviews/s1')).json();
  assert.ok(reviews.some(x => x.text === 'Great work'));
});

test('device registration stores an FCM token', async () => {
  const r = await call('/api/devices', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: JSON.stringify({ token: 'fcm-token-123' }) });
  assert.strictEqual(r.status, 201);
});
