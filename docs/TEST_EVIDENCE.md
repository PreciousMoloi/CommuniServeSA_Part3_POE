# Part 3 Test Evidence

Fill the **Actual result / Status / Evidence** columns only after running each test. A feature existing in the code is not a pass. If a test fails, record the defect, the fix and the retest.

Status key: PASS / FAIL / RETEST / NOT RUN

## A. Automated tests (run on every push by GitHub Actions)

| ID | Command | Covers | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AT-01 | `cd server && npm test` | health, validation, register/login, duplicate email, wrong password | | NOT RUN | screenshot of terminal + Actions run |
| AT-02 | `npm test` | SSO: valid token OK, forged token 401, missing token 400 | | NOT RUN | |
| AT-03 | `npm test` | protected routes: missing / malformed / tampered JWT → 401 | | NOT RUN | |
| AT-04 | `npm test` | listings need auth, hidden until approved; review validation; device token | | NOT RUN | |
| AT-05 | `npm test` (persistence.test.js) | data survives a real server restart | | NOT RUN | |
| AT-06 | `./gradlew testDebugUnitTest` | validators, translations, data contracts, SSO request body | | NOT RUN | `app/build/reports/tests` |
| AT-08 | `./gradlew testDebugUnitTest` (ServerConfigTest) | emulator alias never used on a physical phone | | NOT RUN | `app/build/reports/tests` |
| AT-07 | `./gradlew assembleDebug` | app compiles to an APK | | NOT RUN | Actions artifact |

## B. Manual tests on a physical phone

| ID | Scenario | Expected | Actual result | Status | Evidence file |
|---|---|---|---|---|---|
| TC-01 | Register a new account | Account created, session started | | NOT RUN | |
| TC-02 | Register with an existing email | Rejected with clear message (HTTP 409) | | NOT RUN | |
| TC-03 | Log in with a wrong password | Access denied (HTTP 401), app does not crash | | NOT RUN | |
| TC-04 | Search for a service | Matching services shown; empty state if none | | NOT RUN | |
| TC-05 | Save a service, then go offline | Saved item still visible offline | | NOT RUN | |
| TC-06 | Submit a review while offline | Review queued, "N actions waiting" shown | | NOT RUN | |
| TC-07 | Reconnect | Queue syncs once, no duplicate on server | | NOT RUN | |
| TC-08 | Change language to Afrikaans / isiZulu | Every screen translates | | NOT RUN | |
| TC-09 | Restart the API | Users, reviews, listings still present | | NOT RUN | |
| TC-10 | Google SSO on the phone | Account appears in Firebase Authentication > Users; API returns JWT | | NOT RUN | |
| TC-11 | Send a test push from Firebase Console | Notification arrives (open, background, closed) | | NOT RUN | |
| TC-12 | Enter invalid input (empty fields, bad email, 1-char password) | Validation messages, no crash | | NOT RUN | |
| TC-13 | Change settings (radius, dark mode, notifications), restart app | Settings persist | | NOT RUN | |
| TC-14 | Password reset | Documented as not implemented (API returns 501) | | NOT RUN | |

## Execution note for this packaged revision (4 November 2026)

The following verification attempts were made while preparing this ZIP. They are **environment blockers, not passing tests**:

- API dependencies: `npm install --no-audit --no-fund` did not complete before the environment timeout. Therefore `npm test` could not be run here.
- Android: `./gradlew testDebugUnitTest assembleDebug --no-daemon` could not start because the Gradle 8.11.1 distribution could not be downloaded (`UnknownHostException: services.gradle.org`). Therefore no Android test or APK build result is claimed.
- Physical-device evidence: two photographs supplied by the project owner are included in `docs/device-evidence/`. They show the Discover and Add Service/Event screens while the API was unavailable. They are not evidence that online listing submission, SSO-to-API exchange, or sync passed.

Run the commands on a network-enabled development machine and replace each `NOT RUN` only with the actual result and evidence filename. Do not mark tests as PASS based on source-code inspection.

## C. Defects found and corrected

| # | Defect | Fix | Retest |
|---|---|---|---|
| 1 | `npm test` used `node --test test/` and could not discover tests | Script now `node --test test/*.test.js` | |
| 2 | `/api/auth/sso` trusted an unverified e-mail | API verifies Firebase ID token; app sends `idToken` | |
| 3 | Default JWT secret `CHANGE_THIS_IN_PRODUCTION` | Required in production, random in dev | |
| 4 | CORS open to every origin | Closed unless `CORS_ORIGINS` set | |
| 5 | Forgot-password claimed a link was sent | Returns 501 and is disclosed | |
| 6 | Earlier report described in-memory storage and incomplete SSO inaccurately | Final report and submission guide now describe JSON-file persistence and server-side Firebase ID-token verification, with configuration caveats | Documentation corrected; runtime tests still pending |
| 7 | Google-only limited session displayed a generic sign-in-again warning on Add Listing | UI now explains that Google identity is signed in but no CommuniServe API session exists; added Retry Connection action and clarified local queue behaviour | Requires physical-device retest |
| 9 | Physical phone used the emulator-only address `10.0.2.2`, so the API was always "unavailable" | `ServerConfig` picks the hosted HTTPS API on a real phone; warm-up ping; one SSO retry; `render.yaml` | Requires physical-device retest |
| 8 | Discover actions appeared as one uninterrupted stack | Added translated section headings to group discovery, map/saved and contribution actions | Requires visual retest on target phone |
