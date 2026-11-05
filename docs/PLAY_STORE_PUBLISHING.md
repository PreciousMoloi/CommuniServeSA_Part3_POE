# Publishing checklist — Google Play

## 1. Create a signing key (once; keep it OUT of Git)
```bash
keytool -genkey -v -keystore communiserve-release.jks -alias communiserve -keyalg RSA -keysize 2048 -validity 10000
```
Back the `.jks` up somewhere safe — losing it means you can't update the app.

## 2. Point the release build at your HTTPS API
Deploy `server/` (e.g. Render) and set the URL — the release build refuses cleartext HTTP.
```bash
# gradle.properties (local) or an environment variable
RELEASE_API_URL=https://your-api.onrender.com/
```

## 3. Build the signed bundle/APK
```bash
# PowerShell
$env:KEYSTORE_PATH="C:\path\communiserve-release.jks"; $env:KEYSTORE_PASSWORD="..."; $env:KEY_ALIAS="communiserve"; $env:KEY_PASSWORD="..."
./gradlew bundleRelease assembleRelease
```
Outputs: `app/build/outputs/bundle/release/app-release.aab` (upload to Play) and `app/build/outputs/apk/release/app-release.apk` (**signed APK = submission evidence**). Add the release SHA-1 to Firebase for Google sign-in.

## 4. Play Console steps
Create app → Store listing → upload `docs/store/play_store_icon_512.png`, `docs/store/feature_graphic_1024x500.png` and **at least 2 phone screenshots** → Content rating questionnaire → Target audience → **Data safety** (below) → Privacy policy URL (publish `docs/PRIVACY_POLICY.md`, e.g. via GitHub Pages) → Production or Internal testing → upload `.aab` → Review. Screenshot the console for the PoE (“uploaded to Play Console” counts even if not yet published).

## 5. Store listing text
**Title:** CommuniServe SA  
**Short description (≤80):** Find trusted local services and events near you — works offline.  
**Full description:**  
CommuniServe SA connects South Africans with trusted local services and community events. Search plumbers, electricians, tutors and more; read and write reviews; RSVP to events; and save anything for offline use. Add your own service or event and the community approves it. Available in English, Afrikaans and isiZulu, with push alerts for local updates, Google sign-in, and a dark mode.

## 6. Data safety answers
| Data | Collected | Purpose | Shared | Notes |
|---|---|---|---|---|
| Email, name | Yes | Account management | No | Encrypted in transit (HTTPS) |
| Approximate/precise location | Optional | Tag a listing you add | No | Only when you tap “Use My Location” |
| Reviews / user content | Yes | App functionality | No | |
| Device ID (FCM token) | Yes | Push notifications | No | |
Data encrypted in transit: **Yes**. Users can request deletion: **Yes** (contact email in the policy).

## 7. Pre-launch checklist
- [ ] `targetSdk 36` ✔ (already set) · [ ] versionCode bumped for each upload
- [ ] Tested on a physical phone · [ ] HTTPS API URL set · [ ] privacy policy hosted
- [ ] Two phone screenshots (and a 7"/10" tablet one if you opt in to tablets)
- [ ] Firebase release SHA-1 added
