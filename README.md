# CommuniServe SA

**Discover Local. Connect Community.** — an Android app (Kotlin) for finding trusted local services and community events in South Africa, with a Node.js REST API.

| | |
|---|---|
| **Module** | OPSC6312 — Part 3 (final PoE) |
| **Version** | 3.0 (see [RELEASE_NOTES.md](RELEASE_NOTES.md)) |
| **Demo video** | Pending: add the group's unlisted demonstration URL after recording and checking the complete user journey. |
| **Target / min SDK** | 36 (Android 16) / 24 (Android 7.0) |

## Group members
- Precious Moloi — ST10452719
- Nyiko Matladi — ST10450403
- Luvuyo Yizani — ST10442920
- Naledi Paballo Tshelane — ST10446045

## Purpose
Many South Africans find reliable plumbers, tutors, electricians and local events through word of mouth. CommuniServe SA puts verified local services and community events in one place, works on patchy connections (offline mode), and speaks the user's language (English, Afrikaans, isiZulu).

## Features
| Feature | How it works | Code |
|---|---|---|
| Register / log in | Email + password; the API stores only a **bcrypt** hash and returns a 24 h JWT | `MainActivity.authenticate`, `server.js /api/auth/*` |
| **Single sign-on** | “Continue with Google” → Firebase Authentication → the app sends the **Firebase ID token** to `POST /api/auth/sso`; the API verifies its signature, issuer, audience and expiry before issuing our JWT | `FirebaseAuthManager.signInWithGoogle` |
| **Settings** | Language, search radius, push notifications, dark mode; synced to the server profile | `MainActivity.showProfile` |
| **REST API** | Express API: services, events, reviews, approvals, profile, devices, health | `server/server.js` |
| **Offline mode + sync** | Room stores saved items, RSVPs, **queued reviews and listings**; cached lists show offline; auto-sync on reconnect | `data/`, `MainActivity.syncAll` |
| **Real-time notifications** | Firebase Cloud Messaging topic `community_alerts`; token registered with the API | `push/PushManager.kt`, `CommuniServeMessagingService.kt` |
| **Multi-language** | English, Afrikaans, isiZulu — every screen and message | `i18n/Strings.kt` |
| Innovative features | Location-tagged listings, community approval queue, live average ratings, RSVPs, call/directions | see release notes |
| **Nearby map** | OpenStreetMap (osmdroid): live location, radius circle, service/event pins | `MainActivity.showMap`, `util/MapMath.kt` |
| Animated splash | Pop-in, pulse and ripple animation on launch | `MainActivity.showSplash` |
| Input safety | Client + server validation; bad input never crashes the app | `util/Validators.kt` |

## Architecture
```
Android app (Kotlin, single Activity)         Node.js / Express REST API
 ├─ UI built in code (design system)  ──HTTPS──►  /api/auth  /api/services  /api/events
 ├─ Retrofit + OkHttp (ApiClient)                 /api/reviews  /api/pending  /api/devices
 ├─ Room (offline cache + sync queue)             persists to server/data/db.json
 ├─ Firebase Auth (Google SSO)
 └─ Firebase Cloud Messaging (push)
```

## Run the REST API
```bash
cd server
npm install
# Development: JWT_SECRET is optional (a random one is generated per run).
# Production (NODE_ENV=production): JWT_SECRET (32+ chars) is REQUIRED or the server refuses to start.
# PowerShell:  $env:FIREBASE_PROJECT_ID = "communiserve-sa"
# bash:        export FIREBASE_PROJECT_ID="communiserve-sa"
npm start            # http://localhost:3000/health
npm test             # API test suite (includes a restart-persistence test)
```
| Variable | Purpose |
|---|---|
| `JWT_SECRET` | Signs sessions. Required (32+ chars) in production. |
| `FIREBASE_PROJECT_ID` | Enables server-side verification of Google SSO tokens. Without it `/api/auth/sso` returns 503. |
| `CORS_ORIGINS` | Comma-separated browser origins allowed; unset = none (the Android app is unaffected). |
| `DATA_FILE` | Path of the JSON database (default `server/data/db.json`). |

Data is saved to `server/data/db.json` (git-ignored) and survives restarts — proven by `server/test/persistence.test.js`, which starts the real server, writes data, restarts it and reads the data back. On hosts with an ephemeral disk (e.g. Render free tier) the file is wiped on redeploy; use a persistent disk or a managed database for production.

## Run the app
1. Open the folder in **Android Studio** (Ladybug or newer) and let Gradle sync (JDK 21).
2. **Emulator:** the default API address `http://10.0.2.2:3000/` works.
3. **Physical phone:** the app automatically uses the hosted HTTPS API (deploy it once with `render.yaml`, see [docs/DEPLOY_API_RENDER.md](docs/DEPLOY_API_RENDER.md)). To use a local API instead, run it on your PC, join the same Wi-Fi, and in the debug app’s *Server address* box enter `http://<your-PC-LAN-IP>:3000/` → **Save & Test Connection**. The app wakes a sleeping free-tier server on launch.
4. For Google SSO and push, add Firebase — see [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md). Without `google-services.json` everything else still works and SSO/push report “not configured”.

## Testing & GitHub Actions
- Android unit tests: `./gradlew testDebugUnitTest` (validators, translations, data contracts, server-address rules).
- API tests: `cd server && npm test` (auth, SSO token verification, protected routes, validation, listings, reviews, restart persistence).
- **GitHub Actions** (`.github/workflows/build.yml`) is configured to run API tests, Android unit tests and a debug APK build on pushes/PRs to `main`. A successful run has not been independently verified in this packaged environment; link or screenshot the actual green Actions run after it completes.
- **Test evidence (expected vs actual, pass/fail):** [docs/TEST_EVIDENCE.md](docs/TEST_EVIDENCE.md).
- Manual test plans: [docs/POSTMAN_TEST_PLAN.md](docs/POSTMAN_TEST_PLAN.md), [docs/FEATURE_EVIDENCE_CHECKLIST.md](docs/FEATURE_EVIDENCE_CHECKLIST.md).
- * **Link to successful Github Actions run:**[View passing build here](https://github.com/PreciousMoloi/CommuniServeSA_Part3_POE/actions/runs/41997901823)

## Publishing (Google Play)
Release signing, store listing text, data-safety answers, privacy policy and graphics: [docs/PLAY_STORE_PUBLISHING.md](docs/PLAY_STORE_PUBLISHING.md). Graphics are in `docs/store/`. The app targets API 36 as Google Play now requires for new apps.

## Screenshots and demonstration evidence
The `docs/device-evidence/` folder contains two genuine physical-device photographs captured during development on 4 November 2026. They show the Discover screen and Add Service/Event form while the API was unavailable. They document the observed UI state; they do not prove successful server submission. Replace or supplement them with clean screenshots of successful login, online discovery, listing submission, offline queue/synchronisation, language settings and notifications after those scenarios have been retested. Add the unlisted demo URL above only after recording it.

## AI tools
See [docs/AI_USE_WRITEUP.md](docs/AI_USE_WRITEUP.md).

## Security notes
- Google SSO: the API trusts only the e-mail inside a **verified Firebase ID token**, never an e-mail typed into the request.
- Passwords are bcrypt-hashed (cost 12); auth routes are rate limited; there is no default JWT secret in production.
- Password reset by e-mail is **not implemented**; `/api/auth/forgot-password` honestly returns HTTP 501.
No secrets are committed: `JWT_SECRET`, keystores and passwords come from environment variables; `.gitignore` excludes keystores and the API data file. Production should run the API over HTTPS with a strong `JWT_SECRET` and a managed database.

## References
- Square. *Retrofit*. https://square.github.io/retrofit/
- Android Developers. *Room persistence library*. https://developer.android.com/training/data-storage/room
- osmdroid. *OpenStreetMap for Android*. https://github.com/osmdroid/osmdroid
- © OpenStreetMap contributors. https://www.openstreetmap.org/copyright
- Firebase. *Cloud Messaging*. https://firebase.google.com/docs/cloud-messaging
- Firebase. *Authentication – Google sign-in*. https://firebase.google.com/docs/auth/android/google-signin
