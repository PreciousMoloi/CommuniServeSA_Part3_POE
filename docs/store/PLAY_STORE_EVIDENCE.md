# Google Play Store – Publishing Readiness Evidence

**Requirement (lecturer):** the PoE requires publishing on Google Play, which needs a one-off **US$25** developer registration fee. Following the lecturer's instruction, we took a screenshot of each step up to the payment step to show the app is ready to be published.

We stopped at the **"Complete your purchase – Developer Registration Fee US$25.00"** dialog (step 17). Personal details (emails, phone number, street address) are blurred.

## Account set-up steps (Google Play Console)

| # | Step | Picture |
|---|---|---|
| 1 | Choose an account type | ![](store-evidence/01-account-type.jpg) |
| 2 | Choose "Yourself" (personal account) | ![](store-evidence/02-choose-yourself.jpg) |
| 3 | What you'll need (email, phone, US$25 fee) | ![](store-evidence/03-what-you-will-need.jpg) |
| 4 | Developer name | ![](store-evidence/04-developer-name.jpg) |
| 5 | Link a payments profile | ![](store-evidence/05-payments-profile.jpg) |
| 6 | Payments profile confirmed | ![](store-evidence/06-payments-profile-confirm.jpg) |
| 7 | Public profile | ![](store-evidence/07-public-profile.jpg) |
| 8 | Public profile consent | ![](store-evidence/08-public-profile-consent.jpg) |
| 9 | About you (Android experience) | ![](store-evidence/09-about-you.jpg) |
| 10 | Other Google accounts | ![](store-evidence/10-about-you-other-accounts.jpg) |
| 11 | Apps (number of apps, earning money) | ![](store-evidence/11-apps.jpg) |
| 12 | Website | ![](store-evidence/12-apps-website.jpg) |
| 13 | How Google contacts you | ![](store-evidence/13-how-google-contacts-you.jpg) |
| 14 | Contact phone number | ![](store-evidence/14-contact-phone.jpg) |
| 15 | Terms accepted | ![](store-evidence/15-terms.jpg) |
| 16 | Registration fee notice – "Create account and pay" | ![](store-evidence/16-registration-fee-notice.jpg) |
| 17 | **Complete your purchase – US$25.00 (stopped here)** | ![](store-evidence/17-complete-your-purchase.jpg) |

## What is already ready for the store

| Requirement | Status | Where |
|---|---|---|
| Signed release APK | Done | `docs/test-evidence/signed-release-apk.jpg` |
| Release build uses HTTPS API | Done | `app/build.gradle.kts` |
| Target SDK 36, version 3.0 (code 3) | Done | `app/build.gradle.kts` |
| App icon 512x512 and feature graphic 1024x500 | Done | `docs/store/` |
| Privacy policy | Done | `docs/PRIVACY_POLICY.md` |
| Store listing text and publishing steps | Done | `docs/PLAY_STORE_PUBLISHING.md` |
| Phone screenshots for the listing | Done | `docs/screenshots/` |
| Automated tests and CI | Green | `docs/TEST_EVIDENCE.md` |
