// Proves the database is PERMANENT: data written before a server restart is still there after it.
// Starts the real server.js as a child process twice against the same data file.
const test = require('node:test');
const assert = require('node:assert');
const { spawn } = require('node:child_process');
const os = require('os');
const path = require('path');
const fs = require('fs');

const DATA_FILE = path.join(os.tmpdir(), `communiserve-persist-${Date.now()}.json`);
const PORT = 3900 + Math.floor(Math.random() * 90);
const base = `http://127.0.0.1:${PORT}`;
const sleep = ms => new Promise(r => setTimeout(r, ms));

function start() {
  const child = spawn(process.execPath, [path.join(__dirname, '..', 'server.js')], {
    env: { ...process.env, PORT: String(PORT), DATA_FILE, JWT_SECRET: 'persistence-test-secret-persistence-test' },
    stdio: 'ignore'
  });
  return child;
}
async function waitUp() {
  for (let i = 0; i < 50; i++) {
    try { if ((await fetch(base + '/health')).ok) return; } catch { /* not up yet */ }
    await sleep(100);
  }
  throw new Error('server did not start');
}
const stop = child => new Promise(r => { child.once('exit', r); child.kill('SIGTERM'); });
const post = (p, body, token) => fetch(base + p, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(body) });

test('registrations, reviews and listings survive a server restart (TC-09)', async () => {
  const email = `persist${Date.now()}@example.co.za`;
  let server = start();
  try {
    await waitUp();
    let r = await post('/api/auth/register', { name: 'Persist', email, password: 'LongEnough1' });
    assert.strictEqual(r.status, 201);
    const { token } = await r.json();
    assert.strictEqual((await post('/api/reviews/s1', { rating: 4, text: 'Survives restart' }, token)).status, 201);
    assert.strictEqual((await post('/api/services', { title: 'Persistent Painter', category: 'Painter' }, token)).status, 201);
  } finally { await stop(server); }

  assert.ok(fs.existsSync(DATA_FILE), 'data file must exist on disk after shutdown');

  server = start();
  try {
    await waitUp();
    const login = await post('/api/auth/login', { email, password: 'LongEnough1' });
    assert.strictEqual(login.status, 200, 'registered user can still log in after restart');
    const reviews = await (await fetch(base + '/api/reviews/s1')).json();
    assert.ok(reviews.some(x => x.text === 'Survives restart'), 'review persisted');
    const { token } = await login.json();
    const pending = await (await fetch(base + '/api/pending', { headers: { Authorization: `Bearer ${token}` } })).json();
    assert.ok(pending.some(p => p.title === 'Persistent Painter'), 'submitted listing persisted');
  } finally { await stop(server); fs.rmSync(DATA_FILE, { force: true }); }
});
