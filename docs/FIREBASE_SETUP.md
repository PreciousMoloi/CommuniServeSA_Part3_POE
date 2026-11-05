# Firebase setup (Google SSO + push notifications) — ~10 minutes

The app builds and runs without this; SSO and push just show “not configured”. Do this once for the demo video.

1. Go to <https://console.firebase.google.com> → **Add project** (e.g. `communiserve-sa`).
2. **Add app → Android**. Package name: `za.co.communiservesa`.
3. Add your **debug SHA-1**: in Android Studio open the Gradle panel → `app > Tasks > android > signingReport` and copy the SHA-1 (add the release SHA-1 later too). Without it Google sign-in fails.
4. Download **google-services.json** and put it in the `app/` folder (next to `app/build.gradle.kts`). The build detects it automatically — no code changes. Sync Gradle.
5. **Build → Authentication → Sign-in method → Google → Enable** (choose a support email → Save).
6. **Push test:** run the app on the phone, sign in (Demo Mode is fine), allow notifications. Then Firebase Console → **Engage → Messaging → New campaign → Firebase Notification messages** → title/text → **Send test message** or target **Topic: `community_alerts`**. The notification appears on the phone in real time (app open, background, or closed).
7. For the video, also show **Authentication → Users** and the API’s `data/db.json` / console log to prove where data is stored.

**Important for the native Google chooser:** enable the Google provider and add the SHA-1 *first*, then download `google-services.json` **again** and replace the old one in `app/`. The new file contains the web client ID the app needs (`default_web_client_id`). A phone needs at least one Google account added (Settings → Accounts).

Troubleshooting: *“Google sign-in failed”* → SHA-1 missing/wrong or Google provider not enabled. *No push* → notification permission denied (Settings → Apps → CommuniServe SA → Notifications) or no `google-services.json`.
