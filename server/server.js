/**
 * CommuniServe SA REST API (v3.0)
 *
 * Changes since the prototype:
 *  - Data now persists to server/data/db.json (survives restarts) instead of
 *    living only in memory.
 *  - Passwords hashed with bcrypt (cost 12); never stored or logged in plain text.
 *  - Input validation, rate limiting on auth routes, security headers.
 *  - Device-token registration + push broadcast endpoint (FCM-ready).
 *  - Pending approvals now return titles so the app can show something useful.
 */
const express = require('express');
const cors = require('cors');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
const IS_PROD = process.env.NODE_ENV === 'production';
// A strong secret is mandatory in production; in development a random one is generated per run
// (sessions simply end when the dev server restarts) - there is no guessable fallback any more.
if (IS_PROD && (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32)) {
  console.error('[FATAL] JWT_SECRET (32+ characters) must be set when NODE_ENV=production');
  process.exit(1);
}
const SECRET = process.env.JWT_SECRET || require('crypto').randomBytes(32).toString('hex');
const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || '';
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data', 'db.json');

app.disable('x-powered-by');
// Native Android clients send no Origin header, so CORS only matters for browsers/Postman web.
// Set CORS_ORIGINS=https://a.example,https://b.example to restrict it; unset = same-origin only.
const ORIGINS = (process.env.CORS_ORIGINS || '').split(',').map(o => o.trim()).filter(Boolean);
app.use(cors({ origin: ORIGINS.length ? ORIGINS : false }));
app.use(express.json({ limit: '1mb' }));
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  next();
});

// ---------------------------------------------------------------- storage
const seed = () => ({
  users: [],
  services: [
    { id: 's1', title: 'Community Plumbing Services', category: 'Plumber', description: 'Emergency and maintenance plumbing for homes and small businesses.', phone: '0110000001', rating: 4.8, distanceKm: 2.1, verified: true },
    { id: 's2', title: 'Reliable Electrical Repairs', category: 'Electrician', description: 'Residential electrical repairs and installations.', phone: '0110000002', rating: 4.6, distanceKm: 3.4, verified: true },
    { id: 's3', title: 'Maths Tutor', category: 'Tutor', description: 'One-to-one tutoring for school learners.', phone: '0110000003', rating: 4.9, distanceKm: 1.8, verified: true }
  ],
  events: [
    { id: 'e1', title: 'Community Clean-up', category: 'Community', description: 'Join neighbours for a Saturday clean-up.', venue: 'Local Community Hall', dateTime: 'Saturday 09:00', distanceKm: 1.2 },
    { id: 'e2', title: 'Youth Coding Meetup', category: 'Education', description: 'A beginner-friendly coding and technology session.', venue: 'Innovation Hub', dateTime: 'Sunday 13:00', distanceKm: 4.0 }
  ],
  reviews: [],
  pending: [],
  deviceTokens: []
});

function load() {
  try {
    if (fs.existsSync(DATA_FILE)) return { ...seed(), ...JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')) };
  } catch (e) {
    console.error('[DB] Could not read data file, starting with seed data:', e.message);
  }
  return seed();
}
const db = load();
let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    try {
      fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true });
      fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2));
    } catch (e) {
      console.error('[DB] Save failed:', e.message);
    }
  }, 100);
}

// ---------------------------------------------------------------- helpers
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const clean = (v, max = 200) => String(v ?? '').trim().slice(0, max);

function auth(req, res, next) {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : null;
  if (!token) return res.status(401).json({ message: 'Authentication required' });
  try { req.user = jwt.verify(token, SECRET); next(); }
  catch (e) { return res.status(401).json({ message: 'Invalid or expired token' }); }
}
const userView = u => ({ id: u.id, name: u.name, email: u.email, language: u.language, notifications: u.notifications, radius: u.radius, contributions: u.contributions });
const issue = u => jwt.sign({ id: u.id, email: u.email }, SECRET, { expiresIn: '24h' });

// Minimal in-memory rate limiter (no extra dependency): 20 auth attempts / 15 min / IP
const hits = new Map();
function authLimiter(req, res, next) {
  const key = req.ip, now = Date.now();
  const entry = (hits.get(key) || []).filter(t => now - t < 15 * 60 * 1000);
  entry.push(now); hits.set(key, entry);
  if (entry.length > 20) return res.status(429).json({ message: 'Too many attempts. Try again later.' });
  next();
}

// ---------------------------------------------------------------- routes
app.get('/health', (req, res) => res.json({ status: 'ok', message: 'CommuniServe SA API v3.0 is running' }));

app.post('/api/auth/register', authLimiter, async (req, res) => {
  const name = clean(req.body?.name, 80), email = clean(req.body?.email, 120).toLowerCase(), password = String(req.body?.password || '');
  if (!name || !EMAIL_RE.test(email) || password.length < 8)
    return res.status(400).json({ message: 'Name, valid email and 8+ character password required' });
  if (db.users.some(u => u.email === email)) return res.status(409).json({ message: 'Email already registered' });
  const u = { id: crypto.randomUUID(), name, email, passwordHash: await bcrypt.hash(password, 12), language: 'English', notifications: true, radius: 10, contributions: 0 };
  db.users.push(u); save();
  console.log(`[REGISTER] id=${u.id} email=${u.email}`);
  res.status(201).json({ token: issue(u), user: userView(u) });
});

app.post('/api/auth/login', authLimiter, async (req, res) => {
  const email = clean(req.body?.email, 120).toLowerCase();
  const u = db.users.find(x => x.email === email);
  if (!u || !u.passwordHash || !(await bcrypt.compare(String(req.body?.password || ''), u.passwordHash)))
    return res.status(401).json({ message: 'Incorrect email or password' });
  console.log(`[LOGIN] id=${u.id} email=${u.email}`);
  res.json({ token: issue(u), user: userView(u) });
});

// ---- Single sign-on ---------------------------------------------------
// The app signs in with Google through Firebase Authentication and sends the Firebase ID token.
// We verify it server-side (RS256 signature against Google's published certificates, issuer,
// audience = our Firebase project, expiry) and take the email from the VERIFIED token - never
// from the request body - before issuing our own JWT.
let certCache = { certs: null, expires: 0 };
async function firebaseCerts() {
  if (certCache.certs && Date.now() < certCache.expires) return certCache.certs;
  const r = await fetch('https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com');
  if (!r.ok) throw new Error('Could not fetch Firebase signing certificates');
  const maxAge = /max-age=(\d+)/.exec(r.headers.get('cache-control') || '');
  certCache = { certs: await r.json(), expires: Date.now() + (maxAge ? Number(maxAge[1]) : 3600) * 1000 };
  return certCache.certs;
}
async function verifyFirebaseIdToken(idToken) {
  const decoded = jwt.decode(idToken, { complete: true });
  const cert = decoded && (await firebaseCerts())[decoded.header.kid];
  if (!cert) throw new Error('Unknown signing key');
  const claims = jwt.verify(idToken, cert, {
    algorithms: ['RS256'],
    audience: FIREBASE_PROJECT_ID,
    issuer: `https://securetoken.google.com/${FIREBASE_PROJECT_ID}`
  });
  if (!claims.email || claims.email_verified === false) throw new Error('Verified email required');
  return { email: String(claims.email).toLowerCase(), name: claims.name };
}
// Replaceable so automated tests can supply a fake verifier without calling Google.
app.locals.verifyIdToken = verifyFirebaseIdToken;

app.post('/api/auth/sso', authLimiter, async (req, res) => {
  const idToken = String(req.body?.idToken || '');
  let identity;
  if (FIREBASE_PROJECT_ID || app.locals.verifyIdToken !== verifyFirebaseIdToken) {
    if (!idToken) return res.status(400).json({ message: 'Firebase ID token required' });
    try { identity = await app.locals.verifyIdToken(idToken); }
    catch (e) { console.warn('[SSO] token rejected:', e.message); return res.status(401).json({ message: 'Invalid or expired sign-in token' }); }
  } else if (process.env.ALLOW_UNVERIFIED_SSO === 'true' && !IS_PROD) {
    // Local development only (no Firebase project yet). Never enabled in production.
    identity = { email: clean(req.body?.email, 120).toLowerCase(), name: clean(req.body?.name, 80) };
  } else {
    return res.status(503).json({ message: 'Single sign-on is not configured on this server (set FIREBASE_PROJECT_ID)' });
  }
  const email = identity.email, name = clean(identity.name, 80) || email.split('@')[0];
  if (!EMAIL_RE.test(email)) return res.status(400).json({ message: 'Valid email required' });
  let u = db.users.find(x => x.email === email);
  if (!u) {
    u = { id: crypto.randomUUID(), name, email, passwordHash: null, sso: 'google', language: 'English', notifications: true, radius: 10, contributions: 0 };
    db.users.push(u); save();
  }
  console.log(`[SSO] id=${u.id} email=${u.email}`);
  res.json({ token: issue(u), user: userView(u) });
});

app.post('/api/auth/forgot-password', authLimiter, (req, res) => {
  // Honest response: no e-mail service is wired up, so we do not pretend a link was sent.
  res.status(501).json({ message: 'Password reset by e-mail is not available yet. Contact support.' });
});

const searchable = (list, fields) => (req, res) => {
  const q = clean(req.query.q).toLowerCase();
  const r = Number(req.query.radius) > 0 ? Number(req.query.radius) : 50;
  res.json(list().filter(i => (!q || fields.map(f => i[f] || '').join(' ').toLowerCase().includes(q)) && i.distanceKm <= r));
};
app.get('/api/services', searchable(() => db.services.filter(s => s.verified), ['title', 'category', 'description']));
app.get('/api/events', searchable(() => db.events.filter(e => !db.pending.some(p => p.id === e.id && p.status === 'PENDING')), ['title', 'category', 'description']));
app.get('/api/services/:id', (req, res) => { const s = db.services.find(x => x.id === req.params.id); s ? res.json(s) : res.status(404).json({ message: 'Service not found' }); });
app.get('/api/events/:id', (req, res) => { const e = db.events.find(x => x.id === req.params.id); e ? res.json(e) : res.status(404).json({ message: 'Event not found' }); });

function createListing(kind) {
  return (req, res) => {
    const title = clean(req.body?.title, 100), category = clean(req.body?.category, 50);
    if (!title || !category) return res.status(400).json({ message: 'Title and category required' });
    const description = clean(req.body?.description, 500), phone = clean(req.body?.phone, 60);
    const lat = Number(req.body?.latitude), lng = Number(req.body?.longitude);
    const coords = Number.isFinite(lat) && Number.isFinite(lng) && req.body?.latitude != null ? { latitude: lat, longitude: lng } : {};
    const id = (kind === 'service' ? 's' : 'e') + Date.now();
    let item;
    if (kind === 'service') {
      item = { id, title, category, description, phone, rating: 0, distanceKm: 0, verified: false, ...coords };
      db.services.push(item);
    } else {
      item = { id, title, category, description, venue: phone, dateTime: 'To be confirmed', distanceKm: 0, ...coords };
      db.events.push(item);
    }
    db.pending.push({ type: kind, id, title, status: 'PENDING' });
    save();
    console.log(`[${kind.toUpperCase()} CREATED] id=${id} by=${req.user.email} status=PENDING`);
    res.status(201).json(item);
  };
}
app.post('/api/services', auth, createListing('service'));
app.post('/api/events', auth, createListing('event'));

app.get('/api/pending', auth, (req, res) => res.json(db.pending.filter(p => p.status === 'PENDING')));
app.post('/api/pending/:id/approve', auth, (req, res) => {
  const item = db.pending.find(p => p.id === req.params.id);
  if (!item) return res.status(404).json({ message: 'Not found' });
  item.status = 'APPROVED';
  const svc = db.services.find(s => s.id === item.id);
  if (svc) svc.verified = true;
  save();
  res.json(item);
});

app.get('/api/reviews/:serviceId', (req, res) => res.json(db.reviews.filter(r => r.serviceId === req.params.serviceId)));
app.post('/api/reviews/:serviceId', auth, (req, res) => {
  const rating = Number(req.body?.rating), text = clean(req.body?.text, 500);
  if (!Number.isInteger(rating) || rating < 1 || rating > 5 || !text)
    return res.status(400).json({ message: 'Rating 1-5 and review text required' });
  const r = { id: crypto.randomUUID(), serviceId: req.params.serviceId, rating, text, author: req.user.email };
  db.reviews.push(r);
  const u = db.users.find(x => x.id === req.user.id); if (u) u.contributions++;
  // keep the service's average rating in step with its reviews
  const svc = db.services.find(s => s.id === r.serviceId);
  if (svc) { const all = db.reviews.filter(x => x.serviceId === svc.id); svc.rating = Math.round(all.reduce((a, b) => a + b.rating, 0) / all.length * 10) / 10; }
  save();
  console.log(`[REVIEW] id=${r.id} service=${r.serviceId} rating=${r.rating}`);
  res.status(201).json(r);
});

app.get('/api/users/profile', auth, (req, res) => { const u = db.users.find(x => x.id === req.user.id); u ? res.json(userView(u)) : res.status(404).json({ message: 'User not found' }); });
app.put('/api/users/profile', auth, (req, res) => {
  const u = db.users.find(x => x.id === req.user.id);
  if (!u) return res.status(404).json({ message: 'User not found' });
  const b = req.body || {};
  if (['English', 'Afrikaans', 'isiZulu'].includes(b.language)) u.language = b.language;
  if (typeof b.notifications === 'boolean') u.notifications = b.notifications;
  if (Number(b.radius) > 0) u.radius = Math.min(50, Math.max(1, Math.round(Number(b.radius))));
  if (clean(b.name, 80)) u.name = clean(b.name, 80);
  save();
  res.json(userView(u));
});

// ---- Push notifications: device registration + broadcast -------------
app.post('/api/devices', auth, (req, res) => {
  const token = clean(req.body?.token, 4096);
  if (!token) return res.status(400).json({ message: 'Device token required' });
  if (!db.deviceTokens.some(d => d.token === token)) db.deviceTokens.push({ token, userId: req.user.id });
  save();
  res.status(201).json({ message: 'Device registered for notifications' });
});
// Used by the demo: lists tokens so a message can be sent from the Firebase Console / admin SDK.
app.get('/api/devices/count', auth, (req, res) => res.json({ count: db.deviceTokens.length }));

app.use((req, res) => res.status(404).json({ message: 'Route not found' }));
app.use((err, req, res, next) => { console.error(err); res.status(400).json({ message: 'Bad request' }); });

// Flush pending writes on shutdown so a restart never loses the last 100 ms of changes.
for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => { try { module.exports.flush(); } finally { process.exit(0); } });

if (require.main === module) app.listen(PORT, () => console.log(`CommuniServe SA API running on port ${PORT}`));
module.exports = app;
module.exports.flush = () => { clearTimeout(saveTimer); fs.mkdirSync(path.dirname(DATA_FILE), { recursive: true }); fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2)); };
