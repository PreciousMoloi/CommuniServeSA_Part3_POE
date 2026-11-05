# CommuniServe SA — Part 2 Final Submission Checklist

## Rubric-aligned implementation status

| Rubric area | Submission-build status | What must still be demonstrated |
|---|---|---|
| App runs on mobile device (5) | Implemented; API 24+ | Install the final APK on the physical phone and show a clean launch/run.
| Feature (10) | Service discovery, search, category/rating/radius filtering, details, call and map actions are implemented | Demonstrate the complete feature on the phone.
| Settings (10) | Language, notifications, radius, profile name and dark mode are persisted locally; REST profile sync is attempted when signed in | Change settings, leave/reopen the screen, and show that they persist.
| REST API (10) | Retrofit + Node/Express endpoints are implemented, including auth, services, events, reviews, profile, RSVP and approval | Start the server/hosted API and capture successful Postman responses.
| User Defined 1 (10) | RoomDB saved services/events and queued offline reviews; automatic sync is triggered when network becomes available | Demonstrate airplane/offline mode, save/read, queue a review, reconnect and sync.
| User Defined 2 (10) | English/Afrikaans/isiZulu labels are implemented through the app language selector | Change language and capture the changed UI.
| User Defined 3 (10) | Service/event submission enters a pending queue; authenticated users can approve; both services and events become verified | Submit, open Pending Approvals, approve, then show the verified listing.
| UI (10) | Consistent branded cards, buttons, spacing, typography, dark mode resources and map/call actions | Capture clear screenshots/video on the physical device.
| GitHub / README / testing (10) | README, source structure, JUnit tests and corrected GitHub Actions workflow are included | Push this build, obtain a green Actions run, and capture the result.
| Demonstration video (5) | Not included in this ZIP by design | Record the final walkthrough and add its link to the README/report.

## Credentialed services

Google SSO and remote Firebase Cloud Messaging require the team's real Firebase project configuration. The portable ZIP deliberately does not contain private credentials. Add the team's `google-services.json`, enable the Google Services plugin, configure the Firebase Web client ID, then test **Continue with Google (SSO)** and FCM before the final demonstration.

## Local verification performed while preparing this ZIP

- Node.js `server/server.js` passed `node --check` syntax validation.
- GitHub Actions YAML was parsed successfully and the Android SDK package indentation was corrected.
- Gradle wrapper was changed from Gradle 9.0 to Gradle 8.9 to match Android Gradle Plugin 8.7.x.
- A local Gradle build could not be executed in this environment because the Gradle distribution was not cached and outbound network access is unavailable. Therefore no claim is made here that the Android tests/APK build passed locally; the required verification is the GitHub Actions run after pushing this version.
