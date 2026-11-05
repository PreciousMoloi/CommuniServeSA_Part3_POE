# Part 3 Final Submission Guide

## Assessment focus
The Part 3 brief asks the group to deliver a complete Android app, apply lecturer feedback, add and demonstrate required features, test on a physical Android phone, maintain source code on GitHub, and prepare a README and publication information.

## Feature evidence checklist
Use the existing `FEATURE_EVIDENCE_CHECKLIST.md` as a test record. Tick a requirement only after it has been demonstrated on a real device/API. Add a date, device/Android version, result, and screenshot/video filename for each test.

## Required user journey to demonstrate
1. Launch the app and show the app icon/splash screen.
2. Register a new test user and log in.
3. Demonstrate invalid-credential rejection without exposing a real password.
4. Change settings and demonstrate language support.
5. Search for a service, open its details, and use call/directions actions where valid data exists.
6. Discover an event and demonstrate RSVP.
7. Save an item and show it remains available offline.
8. Submit a review online; queue a review offline and show what happens after reconnection.
9. Demonstrate listing submission and its approval/unverified state.
10. Show notifications if they are implemented and enabled on the device.
11. End with the repository, test results, and any known limitations.

## API test sequence
Use `POSTMAN_TEST_PLAN.md`. Capture the HTTP status and response for health, registration, login, service query, protected submission, review, and profile/settings endpoints. Never include tokens, passwords, private keys, or other secrets in screenshots.

## Current project limitations (v3.0 — keep these honest in the report)
- The API now persists to a JSON file (`server/data/db.json`). That survives restarts, but a hosted deployment on an ephemeral disk (e.g. Render free tier) will still reset — use a managed database for production.
- Google SSO and push notifications need the group's own Firebase project (`docs/FIREBASE_SETUP.md`), matching Android app registration, SHA fingerprints, API `FIREBASE_PROJECT_ID`, and a valid `google-services.json`. Without correct configuration, they are not operational.
- The SSO endpoint verifies the Firebase ID token server-side against Google's published signing certificates, issuer, audience and expiry, then uses the verified email. Configure `FIREBASE_PROJECT_ID` on the API and test with the group's Firebase project before claiming SSO is operational.
- Source files and a checklist alone do not prove the app works on a physical device: capture real screenshots, test results and the demo video. The supplied phone photos show the Discover and Add Listing screens while the API is unavailable; they are evidence of a physical-device run, not proof of successful online submission.
- Afrikaans/isiZulu text needs proof-reading by a first-language speaker.

## Before submission
- [ ] Build succeeds from a clean checkout (not verified in this packaged environment because Gradle distribution download was blocked by network DNS).
- [ ] API health endpoint responds.
- [ ] Registration/login and validation tests pass.
- [ ] Offline and sync behaviour is demonstrated.
- [ ] Notification permission/delivery is demonstrated where implemented.
- [ ] Language switching is demonstrated.
- [ ] API and unit test results are captured.
- [ ] Physical-device video link is added to README.
- [ ] No credentials/secrets are committed.
- [ ] Known limitations are described accurately.
