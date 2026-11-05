# Redeploying the API on Render (fixes "error 404" on Google sign-in)

The app's Google sign-in calls `POST /api/auth/sso`. A 404 means the server on Render is still running the **old** `server.js`.

1. Commit and push the updated `server/` folder to GitHub (`git add . && git commit -m "API v3" && git push`).
2. Render → your web service → **Manual Deploy → Deploy latest commit** (or wait for auto-deploy). Settings: root directory `server`, build `npm install`, start `npm start`.
3. Environment → add `NODE_ENV` = `production`, `JWT_SECRET` = a random string of 32+ characters (the server will not start without it), and `FIREBASE_PROJECT_ID` = your Firebase project ID (without it Google sign-in returns 503). Optionally `CORS_ORIGINS`.
4. Check `https://<your-service>.onrender.com/health` — it must say **"CommuniServe SA API v3.0 is running"**. If it still says v2, the new code is not deployed.
5. Free-tier services sleep: open the `/health` URL once before the demo and wait ~30 s.
6. The free tier's disk is wiped on redeploy, so registered users reset. Register again before recording the video.

## One-click deploy (recommended)
The repository root contains `render.yaml`. In Render choose **New -> Blueprint**, select the GitHub repo and approve. It creates the web service (ours is live at `https://communiservesa.onrender.com/`) with `NODE_ENV=production`, a generated `JWT_SECRET` and `FIREBASE_PROJECT_ID=communiserve-sa`.

## Point the app at your service
If Render gives your service a different name/URL than `https://communiservesa.onrender.com/`, build with it:
`./gradlew assembleDebug -PRELEASE_API_URL=https://<your-service>.onrender.com/` (or set it in `gradle.properties`). A physical phone then uses this address automatically; no typing on the phone is needed.
