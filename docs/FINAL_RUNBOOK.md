# Final runbook - the steps only the group can do

Code changes cannot create evidence. Work top to bottom; tick a box only after you have seen it work.

1. [ ] Make the GitHub repo **public** (or add the lecturer as collaborator and confirm acceptance). Part 2 lost marks because the lecturer could not see the code.
2. [ ] Render -> New -> Blueprint -> select the repo. Open `https://<service>.onrender.com/health` and confirm "v3.0 is running". If the URL differs from `communiservesa-api.onrender.com`, set `RELEASE_API_URL` (see DEPLOY_API_RENDER.md).
3. [ ] Firebase: Google sign-in enabled; **debug and release SHA-1** registered; fresh `app/google-services.json` downloaded.
4. [ ] `./gradlew testDebugUnitTest assembleDebug` and `cd server && npm install && npm test` - keep the real output; record it in `docs/TEST_EVIDENCE.md`.
5. [ ] Push; confirm the **GitHub Actions run is green**; screenshot it.
6. [ ] Install on the phone and run the demo script (`docs/DEMO_VIDEO_SCRIPT.md`): register, login, Google SSO, settings, three languages, offline save + queued action + sync, live push from Firebase Console, listing submit + approval, Firebase Authentication Users page, Render data/API.
7. [ ] Record the video with voice-over; upload unlisted to YouTube; put the link in README (replace "Pending").
8. [ ] Replace the two API-unavailable photos with screenshots of the working scenarios.
9. [ ] Build signed release (`docs/PLAY_STORE_PUBLISHING.md`); keep the signed APK/AAB, store screenshots and Play Console screenshot as evidence.
10. [ ] Fill in the "What we did ourselves" section of `docs/AI_USE_WRITEUP.md` truthfully.
11. [ ] Regenerate the final Word/PDF report so it matches the app as tested.
12. [ ] Tag the last commit: `git tag -a final-poe -m "Final PoE" && git push origin final-poe`. Do not commit any .zip files.
