# AI use write-up (≤ 500 words)

> Edit this so it reflects what *your group* actually did — markers check that it is honest and specific.

**Tool used:** Claude (Anthropic), via the Claude mobile/web app, during Part 3.

**What AI was used for**
1. **Code review and gap analysis.** I gave Claude the Part 3 brief, our rubric and the Part 2 feedback together with the project source. It compared the code to the rubric and listed missing features (SSO, real push notifications, offline sync of listings, full multi-language coverage, persistence on the API).
2. **Code generation.** Claude wrote the first version of: the Google SSO flow (`FirebaseAuthManager.signInWithGoogle`, `/api/auth/sso`), the FCM service and `PushManager`, the Room `PendingListing` queue and connectivity-triggered sync, the `Strings.kt` translation layer, the `Validators` helper, the dark-mode palette, and the JSON-file persistence in `server.js`.
3. **Debugging.** Claude flagged two bugs while reviewing: `isSingleLine` after setting a password input type would un-mask the password field, and async results in `showReviews` could be drawn onto whichever screen was open. Both were fixed.
4. **Platform compliance.** Claude searched Google’s current policy and told us new apps must now target API 36, which led to the AGP/Gradle/SDK upgrade and the edge-to-edge/back-navigation changes.
5. **Translations.** Claude produced the Afrikaans and isiZulu strings; these are a starting point and need proof-reading by a first-language speaker.
6. **Security review and test repair.** Claude found that `npm test` could not discover the test folder, that `/api/auth/sso` trusted an unverified e-mail, and that the report and README contradicted each other on storage. It wrote the Firebase ID-token verification, the restart-persistence test and the corrected test script; we reviewed and ran them.
7. **Connectivity fix (4 Nov 2026).** After physical-device photos showed “server unavailable”, Claude diagnosed that the debug build defaulted to the emulator-only address `10.0.2.2`, and wrote `ServerConfig`, the launch warm-up ping, the single SSO retry, `render.yaml` and `ServerConfigTest`; we reviewed the changes and retested on the phone.
8. **Documentation.** Claude drafted the README, release notes, and Play Store listing text, which we edited.

**What we did ourselves** *(fill in)*: created the Firebase project and added `google-services.json`; registered our SHA-1; ran every feature on a physical phone; recorded the video; verified/edited translations; committed to GitHub.

**How we verified AI output:** unit tests (`StringsTest`, `ValidatorsTest`), API tests (`server/test`), GitHub Actions builds, and manual testing on a physical device. Where something did not work, we changed it or asked Claude to fix it — we did not submit code we had not run.

**Citations**
- Anthropic. 2026. *Claude* [Large language model]. https://claude.ai
- Google. 2026. *Target API level requirements for Google Play apps*. https://developer.android.com/google/play/requirements/target-sdk
- Firebase. *Cloud Messaging for Android*. https://firebase.google.com/docs/cloud-messaging/android/receive
- Firebase. *Authenticate using Google on Android (OAuth provider)*. https://firebase.google.com/docs/auth/android/google-signin
