# CommuniServe SA — Part 2 Upgraded Prototype (Submission Build)
# Group Members
Precious Moloi-ST10452719
Nyiko Matladi-ST10452719
Luvuyo Yizani-ST10442920
Naledi Paballo Tshelane-ST10446045

**OPSC6312 | Android/Kotlin | Part 2**

This version is an implementation-focused continuation of Part 1. It targets the Part 2 rubric through a working Android client, REST API, offline Room persistence, settings, location support, reviews, event discovery and three differentiating features.

## Implemented features
1. Email registration and login using the REST API, bcrypt password hashing and 24-hour JWT sessions.
2. Service discovery with search and a configurable 1–50 km radius.
3. Community event discovery and RSVP interaction.
4. Service/event detail views, calling and map/directions intents.
5. RoomDB saved/offline services and events.
6. Offline review queue with automatic/manual synchronisation after reconnection.
7. Multilingual interface labels: English, Afrikaans and isiZulu.
8. Settings: language, notifications, radius and dark-mode preference.
9. Contextual local notification channel.
10. Location SDK integration using Google Play Services Location.
11. Add Service/Event submission through protected REST endpoints; new listings are marked pending approval.
12. Reviews and Local Hero contribution counting.
13. Automated JUnit contract tests.
14. Firebase Authentication (email/password) plus Google SSO code. Google SSO/FCM require the team Firebase project configuration; no private Firebase credentials are included in this ZIP.
15. Crash-safe networking: every REST and Firebase call is wrapped so a server/config problem shows a message instead of crashing the app, with failures logged to Logcat (`ApiClient`, `FirebaseAuthManager`, `CommuniServeApp`).
16. Runtime-configurable API server address (Login/Profile screen) so the same build works on both the emulator and a physical device.
17. Pending approval workflow: new service/event submissions are queued as pending, and a dedicated Pending Approvals screen lets an authenticated user review and approve them, including events.


## Design

The app uses a small custom design system (see `MainActivity.kt`, "Design system" section) rather than default system widgets everywhere:
- **Brand colours** (`res/values/colors.xml`): deep teal (`#0F5C4D`) and warm orange (`#E8A33D`), matching the palette from the Part 1 UI mockups.
- **App icon and in-app logo**: a location-pin mark with a "connect" signal arc, generated as a full adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml` with separate background/foreground layers for API 26+) plus legacy PNGs for every density down to API 24, so it renders correctly on both the emulator and older devices.
- **Branded header banner** on every screen (logo + title + optional subtitle on a rounded teal card).
- **Consistent buttons** with five intent-based styles (Primary / Secondary / Text / Danger / Firebase) instead of default grey buttons everywhere.
- **Card-style list rows** for services, events, saved items and reviews, with star ratings, a "✓ Verified" chip, and a coloured accent bar.
- All spacing uses a `dp()` helper (density-aware), not raw pixels, so layout looks consistent across different screen densities.

## Run the API
```bash
cd server
npm install
JWT_SECRET="replace-with-a-long-random-secret" npm start
```

The default development API is `http://10.0.2.2:3000/`, which **only works on the Android Emulator** (it's a special alias back to the host computer - a physical phone cannot resolve it). For a physical phone: find the computer's LAN IP (Windows: `ipconfig`), allow the port through the firewall, e.g. `netsh advfirewall firewall add rule name="CommuniServe API" dir=in action=allow protocol=TCP localport=3000`, then open the app, go to the **Server address** field on the Login or Profile screen, enter `http://<computer-LAN-IP>:3000/`, and tap **Save & Test Connection**. The phone and computer must be on the same Wi-Fi network. This is a runtime setting (stored on-device) - no rebuild needed.

For production/Render deployment, use HTTPS and a strong environment `JWT_SECRET`. Do not submit development secrets to GitHub.

## Firebase Authentication setup

The app ships with Firebase Authentication, Google SSO and FCM code. Firebase remains optional in this portable ZIP because private project credentials must not be committed to GitHub. To turn it on:

1. Go to the [Firebase Console](https://console.firebase.google.com), **Create a project** (any name, Analytics optional).
2. **Add app > Android**, package name **`za.co.communiservesa`** (must match exactly).
3. Download the generated **`google-services.json`** and place it at `app/google-services.json` (replacing/alongside the `app/google-services.json.template` placeholder in this repo - the template is not a real config and must not be used as-is).
4. In Firebase Console: **Build > Authentication > Get started > Sign-in method > Email/Password > Enable > Save**.
5. Uncomment the `id("com.google.gms.google-services")` line in `app/build.gradle.kts` (and note the matching version line already present in the root `build.gradle.kts`).
6. Sync Gradle and rebuild.

Until step 5 is done, the Firebase buttons are still visible and safe to tap (they won't crash), they'll just report "Firebase Authentication isn't set up yet."

**Why this is separate from the REST login above:** the Node API (`server/server.js`) issues and verifies its own JWTs; it has no way to verify a Firebase ID token without the Firebase Admin SDK and a service-account key, which is outside this project's scope. So:
- **Sign In / Create Account (REST)** — required to add services/events/reviews, since those endpoints are protected by the API's own JWT.
- **Sign In with Firebase / Register with Firebase** — a genuine, separate Firebase Authentication integration (visible in Firebase Console > Authentication > Users), demonstrating the SDK connection.

## Continuous Integration — GitHub Actions

`.github/workflows/build.yml` runs automatically on every push/pull request to `main` (and can be triggered manually from the Actions tab). This is what the PoE brief means by "make use of GitHub Actions to run tests and build your code to make sure it will work not just on your computer":

1. **Checks out the repository** on a clean Ubuntu runner - not the developer's own machine, so it proves the project isn't secretly relying on local-only setup.
2. **Sets up JDK 21** (matching `jvmTarget`/`sourceCompatibility` in `app/build.gradle.kts`) and the **Android SDK**.
3. **Runs the unit tests** (`./gradlew testDebugUnitTest`) - see `app/src/test/java/za/co/communiservesa/`: `ApiServiceContractTest.kt` and `DataContractTest.kt`, covering rating bounds, search-radius bounds, user/service/event data contracts, and listing/auth request validation. These are plain JVM tests (no emulator needed), so they run in a few seconds in CI.
4. **Builds the debug APK** (`./gradlew assembleDebug`) - if the project doesn't compile, this step fails and turns the run red.
5. **Uploads the built APK and the test report** as downloadable artifacts on the run's summary page, so both are inspectable without re-running anything locally.

After pushing, check the **Actions** tab on GitHub for a green check mark - that's the evidence this requirement is asking for, alongside a screenshot of a passing run for the README/report if needed.

## Android Studio
1. Open this folder in Android Studio.
2. Sync Gradle.
3. Run `app` on an Android 8+ emulator or physical device.
4. Grant location and notification permissions when prompted.
5. Start the Node API before testing live login/listings.


## Final configuration required for the two credentialed Firebase features

The only two features that cannot be made genuinely live inside a portable ZIP without the team's private cloud configuration are **Google SSO** and **remote FCM push**. The code is included and guarded against crashes. Before the final physical-device demonstration:

1. Create/use the team's Firebase project and register Android package `za.co.communiservesa`.
2. Enable Firebase Authentication > Google and Email/Password.
3. Download the real `google-services.json` into `app/google-services.json` (do not commit secrets if your institution/team policy prohibits it).
4. Uncomment `id("com.google.gms.google-services")` in `app/build.gradle.kts`.
5. Put the Firebase Web client ID in `res/values/strings.xml` as `google_server_client_id` if the generated resources do not already provide it.
6. Build and run the app; use **Continue with Google (SSO)** and **Check Push Notification Setup** in Profile & Settings.

Everything else in this submission build is designed to run with the local Node/Express API and RoomDB without those credentials.

## Evidence checklist for the final submission
- Physical Android device screenshot/video showing the app running.
- Registration and successful login screenshot.
- Settings changed and retained screenshot.
- REST API request/response screenshot from Postman.
- Saved item visible in offline mode.
- Offline review queued and synchronised after reconnecting.
- Location permission and map/directions interaction.
- Add Service/Event submitted and API response showing pending verification.
- JUnit test results screenshot.
- **A green GitHub Actions run (Actions tab) showing both the unit tests and the APK build passing.**
- GitHub repository showing source code and README.
- Demonstration video showing the complete user journey.

## Important honesty note
Firebase Authentication/Google Sign-In and Firebase Cloud Messaging are included as planned project technologies from Part 1, but this portable project does not contain a private `google-services.json`. The working authentication in this package is the REST API + bcrypt + JWT implementation. If the team's Firebase project is available, it can be connected before submission without publishing its configuration secrets.


### API 24 compatibility / KSP fix
This version targets Android API 24 (Android 7.0 Nougat) and uses Room 2.6.1 with KSP. If Android Studio reports `[ksp][MissingType]` for `AppDatabase`, first sync Gradle, then Clean Project and Rebuild Project. Do not delete the Room dependencies.

## Changelog — build-breaking issues found and fixed
The previous copy of this project did not compile. These were the causes and the fixes applied:

1. **Package-name typo.** `MainActivity.kt`, `AppDatabase.kt`, `SavedItem.kt`, `SavedItemDao.kt` and `PendingReview.kt` declared `package za.co.communiservea` (missing the second "s"), while the app's real namespace — set in `app/build.gradle.kts` and used by `AndroidManifest.xml` and every other class — is `za.co.communiservesa`. This mismatch meant the manifest pointed at a class that didn't exist, and every cross-file import inside `MainActivity.kt` failed. All five files were corrected to the real package name.
2. **Missing/mismatched Room DAO methods.** `MainActivity` called `db.savedDao()` and `db.pendingReviewDao()`, but `AppDatabase` only exposed `savedItemDao()`, and no `PendingReviewDao` existed at all. Added `PendingReviewDao.kt` and renamed the database's abstract functions to `savedDao()` / `pendingReviewDao()` so they match what the UI code calls.
3. **Mismatched entity fields.** `SavedItem` only stored `(id, title, content)` while the UI constructed it with five fields `(id, title, type, category, details)`; `PendingReview` stored `(id, itemId, reviewNotes)` while the UI constructed it with named fields `(serviceId, rating, text)`. Both entities and their DAOs were rewritten to match what the UI actually needs.
4. **Search results stacked on repeated searches.** `renderServices()` appended a brand-new results panel under the previous one every time, so searching twice showed two lists. Fixed by reusing a single results panel that is cleared and refilled.
5. **"Back" button disappeared on the Saved/Offline screen.** The button was removed as a side-effect of `removeViews()` once the Room `Flow` emitted data. Fixed by giving the item list its own container so the button is never touched.
6. **`gradlew` was not executable** (needed for command-line builds and GitHub Actions). Fixed with `chmod +x gradlew`.
7. Added an explicit `com.squareup.okhttp3:okhttp` dependency in `app/build.gradle.kts` so `ApiClient.kt`'s direct references to `OkHttpClient`/`Interceptor` are unambiguous.
8. **`Prefs.kt` had every property's getter and setter crammed onto a single line** (`var email: String? get() = ... set(v) { ... }`), which the Kotlin compiler failed to parse as a real setter — it read `set(v) { ... }` as an unresolved function call instead, producing "Property must be initialized", "Unresolved reference 'set'" and "Unresolved reference 'v'" for every property (this is what caused the ~35 errors some builds showed). Rewritten with each getter/setter on its own line, which is unambiguous and always compiles.

None of the app's intended features changed — the same screens, endpoints and offline/notification/multilingual behaviour described above are all still there. These fixes only make the existing design actually build and run.

## AI Tools Usage Write-Up – Part 2
During the completion of the CommuniServe SA Part 2 implementation, AI tools were used as
supporting resources during the build phase of the project. The main AI tools used were ChatGPT and
Claude.
ChatGPT was used primarily during the planning stage carried over from Part 1, supporting the
breakdown of implementation tasks, sequencing of features against the project's Gantt chart, and
clarification of how the Part 1 design decisions should translate into the build phase.
Claude was used more extensively during the Part 2 build itself. It supported Android app development,
including structuring Kotlin classes (e.g. Room DAOs and entities, the REST API client, and the app's
data layer), debugging build and compilation errors, and reviewing code for clarity and correctness.
Claude also assisted with generating UI mockup guidance used to keep screen layouts consistent with
the Part 1 design, helped troubleshoot the GitHub Actions CI pipeline (fixing an Android SDK setup
configuration issue and a YAML syntax error that were initially preventing automated builds and tests
from running successfully), improved user-facing error messages for authentication failures, and helped
design and implement a pending-approval workflow for submitted listings.
AI tools were also used to explain technical concepts relevant to the build, including GitHub Actions
workflows, Git branching and authentication (personal access tokens), Gradle/Android SDK
configuration, and JUnit test structuring. These explanations helped resolve real build failures
encountered during development rather than being used to generate the application logic wholesale.
AI tools were not used as a replacement for independent development or decision-making. Suggested
code and configuration changes were reviewed, tested against the actual GitHub Actions pipeline, and
adapted to the project's specific structure before being committed. Errors flagged by AI tools (such as
the obsolete tools SDK package and YAML indentation issues) were verified against the actual CI logs
before being treated as the root cause. The final implementation decisions, commit history, and working
build remained the responsibility of the developer.
Overall, ChatGPT was used mainly for planning continuity from Part 1, while Claude was used as a
hands-on development and debugging aid during the Part 2 build, including resolving CI/CD
configuration issues.
AI tools used: ChatGPT and Claude
## References
- Android Developers (2024a) *Material Design 3*. Available at: https://m3.material.io (Accessed: 22 September 2026).
- Android Developers (2024b) *Notification channels*. Available at: https://developer.android.com/develop/ui/views/notifications/channels (Accessed: 22 September 2026).
- Android Developers (2024c) *Room persistence library*. Available at: https://developer.android.com/training/data-storage/room (Accessed: 22 September 2026).
- GitHub (2024) *GitHub Actions documentation*. Available at: https://docs.github.com/actions (Accessed: 22 September 2026).
- android-actions (2024) *setup-android GitHub Action*. Available at: https://github.com/android-actions/setup-android (Accessed: 22 September 2026).
- Firebase (2024a) *Firebase authentication documentation*. Available at: https://firebase.google.com/docs/auth (Accessed: 22 September 2026).
- Firebase (2024b) *Firebase Cloud Messaging: receive messages on Android*. Available at: https://firebase.google.com/docs/cloud-messaging/android/receive (Accessed: 22 September 2026).
- Google (2024) *Google Play services location APIs*. Available at: https://developers.google.com/android/reference/com/google/android/gms/location/package-summary (Accessed: 22 September 2026).
- JUnit Team (2024) *JUnit 4 documentation*. Available at: https://junit.org/junit4 (Accessed: 22 September 2026).
- jwt.io (2024) *Introduction to JSON Web Tokens*. Available at: https://jwt.io/introduction (Accessed: 22 September 2026).
- MongoDB (2024) *MongoDB Atlas documentation*. Available at: https://www.mongodb.com/docs/atlas (Accessed: 22 September 2026).
- OpenJS Foundation (2024) *Express: Node.js web application framework*. Available at: https://expressjs.com (Accessed: 22 September 2026).
- OWASP (2024) *Password storage cheat sheet*. Available at: https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html (Accessed: 22 September 2026).
- Render (2024) *Render cloud hosting documentation*. Available at: https://render.com/docs (Accessed: 22 September 2026).
- Square (2024a) *OkHttp*. Available at: https://square.github.io/okhttp/ (Accessed: 22 September 2026).
- Square (2024b) *Retrofit: a type-safe HTTP client for Android and Java*. Available at: https://square.github.io/retrofit/ (Accessed: 22 September 2026).
