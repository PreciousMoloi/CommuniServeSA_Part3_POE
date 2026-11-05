# Release notes — CommuniServe SA v3.0 (final PoE)

Changes since the Part 2 prototype (v2.0). Each item is tagged with the rubric feature it supports.

## New features
- **Single sign-on (Google)** — “Continue with Google (SSO)” signs in through Firebase Authentication, then exchanges the verified identity for a CommuniServe session via the new `POST /api/auth/sso` endpoint. Google users can write reviews and add listings, just like email users.
- **Real-time push notifications (FCM)** — the app subscribes to the `community_alerts` topic and registers its device token with the API (`POST /api/devices`). Notifications arrive while the app is open, in the background or closed, are tappable, and respect the in-app notifications switch. Android 13+ permission prompt added.
- **Offline mode with automatic sync (Room)** — saved items, RSVPs, cached service/event lists, and now **queued reviews and queued listings** all work with no connection. The app watches connectivity and syncs the queue automatically when the phone comes back online (plus a manual *Sync Offline Actions* button and a “N actions waiting” indicator).
- **Multi-language — English, Afrikaans, isiZulu** — every screen, message and notification is translated. Language can be switched on the login screen and in Settings and applies instantly.
- **Innovative additions** — location capture is now stored with new listings; average ratings recalculate on the server when reviews arrive; listings stay hidden until approved; approvals list shows listing titles; saved items can be removed.

- **Nearby map** — new *Nearby Map* screen (OpenStreetMap, no API key) shows the person's live location, their search radius, and pins for services (teal) and events (orange) with toggles. Tap a pin for details and open the full listing; *Centre on me* re-locates.
- **Animated splash** — logo pops in, pulses with a ripple ring, and the name and tagline slide up.

## Improvements
- **SSO hardened** — the API now verifies the Firebase ID token (RS256 signature, issuer, audience, expiry) and takes the e-mail from the token; forged e-mails are rejected with 401.
- **Security defaults** — no default JWT secret in production (server refuses to start without a 32+ character one), CORS closed unless `CORS_ORIGINS` is set, pending writes flushed on shutdown.
- **Tests** — fixed `npm test` discovery (`node --test test/*.test.js`); added SSO, protected-route and restart-persistence tests.
- **Settings** — language, search radius (live label), push notifications, and a dark mode that now actually re-themes the whole app. Settings are also saved to the server profile.
- **Security** — passwords hashed with bcrypt (cost 12) on the API; rate-limited auth routes; stronger client + server validation; `allowBackup` off; HTTPS enforced for release builds (cleartext only in debug).
- **Persistent API storage** — the API now saves to `server/data/db.json` instead of memory, so data survives restarts.
- **UI/UX** — full dark palette, 48 dp touch targets, edge-to-edge layout with proper insets, system back / predictive-back navigation on every screen, input types per field (email keyboard, masked password).
- **Stability** — all intent launches (call, maps) are guarded; permission results are handled with the Activity Result API (location no longer needs a second tap); async results can no longer draw onto the wrong screen; no double-submit on sign-in.

## Platform / publishing
- Target SDK **36 (Android 16)**, compile SDK 36, AGP 8.9.1, Gradle 8.11.1 — required for new Google Play apps since 31 Aug 2026.
- Release build type with environment-based signing config (no secrets in Git).
- Store assets, listing text, data-safety answers and privacy policy in `docs/`.

## Testing and CI
- GitHub Actions now runs **API tests** (Node) and **Android unit tests + build** on every push.
- New unit tests: validators, translation completeness (every key in all 3 languages, placeholders match).

## Known limitations
- Local database upgrade from v2.0 rebuilds the offline cache (saved items are re-saved by the user).
- Password reset by e-mail is not implemented (the API returns 501 rather than pretending a link was sent).
- The JSON-file database suits this PoE; on hosts with an ephemeral disk it resets on redeploy.
- Afrikaans and isiZulu translations should be proof-read by a first-language speaker before a public launch.

## 3.1 – POE refinement (4 November 2026)

- Grouped Discover actions under translated section headings for clearer scanning.
- Added a Retry Connection action when Google sign-in has succeeded but the API session is unavailable.
- Replaced the misleading sign-in-again warning in the limited Google session with a clear explanation that locally queued listings are not yet submitted to the server.
- Updated the final report, submission guide, README evidence notes and test register to align with the supplied source implementation and observed physical-device state.
- Added owner-supplied physical-device photographs as development evidence; they show the API-unavailable state and are not presented as successful end-to-end test evidence.
- Automated API and Android tests remain pending because dependency/Gradle downloads were blocked in the preparation environment. See `docs/TEST_EVIDENCE.md`.

## 3.2 - Physical-device connectivity fix (4 November 2026)

- **Physical phone no longer points at the emulator address.** `10.0.2.2` only exists inside the Android Emulator, so a real phone could never reach the API. The app now defaults to the hosted HTTPS API on a physical device and keeps `10.0.2.2` for the emulator only (`util/ServerConfig.kt`, covered by `ServerConfigTest`). A saved emulator address is ignored on a real phone.
- **Server warm-up on launch.** The app pings `/health` as soon as it opens so a sleeping free-tier server is awake by the time the person signs in, submits a listing or syncs.
- **Google SSO retries once.** If the API exchange fails because the server is asleep or restarting, the app wakes it and retries before falling back to the limited Google-only session.
- **One-click API deployment.** Added `render.yaml` (Render Blueprint) with `NODE_ENV`, a generated `JWT_SECRET` and `FIREBASE_PROJECT_ID`.
- **Tests.** Added `ServerConfigTest` (6 cases). Results are recorded only in `docs/TEST_EVIDENCE.md` after being run.
## Testing
API 11/11, Android unit tests 31/31, CI green, 15 of 16 manual device tests pass. See `docs/TEST_EVIDENCE.md`.