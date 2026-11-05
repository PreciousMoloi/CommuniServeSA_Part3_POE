# AI Use Write-Up – CommuniServe SA

**Group:** Precious Moloi (ST10452719), Nyiko Matladi (ST10450403), Luvuyo Yizani (ST10442920), Naledi Paballo Tshelane (ST10446045)

**Tool used:** Claude (Anthropic), a large language model, in a chat and coding assistant.

## How AI helped
1. **UI redesign.** Claude wrote most of the redesigned screens in `MainActivity.kt`: bottom navigation, login and registration layouts, category chips with near-me map shortcuts, Profile, and the unified search.
2. **Error messages and translations.** Claude rewrote the technical errors as plain-language messages and produced the Afrikaans and isiZulu strings. These need proof-reading by first-language speakers.
3. **Security and test repair.** Claude found that `npm test` could not discover the tests, that `/api/auth/sso` trusted an unverified email, and wrote the Firebase ID-token verification and restart-persistence test.
4. **Connectivity fixes.** Claude diagnosed the emulator-only address, the old Render deployment and the wrong default API URL, and wrote the fixes.
5. **Documentation.** Claude drafted the README, release notes, test evidence and final report from the results we gave it.

## What we did ourselves
We created the Firebase project and `google-services.json`, registered our SHA-1, deployed and redeployed the Render API, ran every feature on a physical phone, took the screenshots, ran the tests, built the signed release APK, recorded the demo and committed to GitHub.

## How we verified AI output
We ran the API tests (11/11), the Android unit tests (31/31), GitHub Actions, and tested on a physical phone. When something failed (for example the 404 on registration or the Windows persistence test) we reported the real output back and the fix was retested. Our own screenshots, including test accounts, were shared with the AI to diagnose problems.

## Limitations
AI-generated translations are unreviewed, and TC-09 (data survival on the Render free tier) is a hosting limitation we documented rather than hid.

## Citations
- Anthropic. 2026. *Claude* [Large language model]. https://claude.ai
- Google. 2026. *Target API level requirements for Google Play apps*. https://developer.android.com/google/play/requirements/target-sdk
- Firebase. *Cloud Messaging for Android*. https://firebase.google.com/docs/cloud-messaging/android/receive
- Firebase. *Authenticate using Google on Android*. https://firebase.google.com/docs/auth/android/google-signin
