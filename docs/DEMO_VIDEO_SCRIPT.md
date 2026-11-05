# Demo video script (screen-record the PHONE, with voice-over, ~6–7 min)

Record on a physical Android phone (Android's built-in screen recorder + microphone). Keep the API running on your PC with its console visible in a second short clip.

| # | Show | Say (voice-over) |
|---|---|---|
| 1 | Home screen → tap app **icon** → login screen | “This is CommuniServe SA, version 3, running on a real Android phone.” |
| 2 | Tap **Afrikaans**, then **isiZulu**, then English | “The app supports English, Afrikaans and isiZulu; every screen changes instantly.” |
| 3 | **Create Account** → enter bad email, weak password (see validation) → valid details → Register | “Input is validated; the API stores only a bcrypt hash of the password.” |
| 4 | PC: show API console `[REGISTER]` line + `data/db.json` (hash, no plain password) | “Here is the user stored in the API database, with the password hashed.” |
| 5 | Sign out → **Continue with Google (SSO)** → choose account | “Single sign-on through Google and Firebase; the API issues our own session.” Show Firebase Console → Authentication → Users. |
| 6 | Discover → search “plumb” → open service → **Call**, **Directions**, **Save for Offline** | “Services come from the REST API.” |
| 7 | **Write Review** → submit; show it under **View Reviews**; PC console `[REVIEW]` | “Reviews are saved in the API database.” |
| 8 | **Add Service/Event** → **Use My Location** → submit; **Pending Approvals** → approve | “User-submitted listings wait for approval.” |
| 9 | Turn on **airplane mode** → open Discover (offline badge) → Saved list → write a review + add a listing (queued) → show “2 actions waiting” → turn airplane mode **off** | “Offline mode: cached data, saved items and queued actions sync automatically when the connection returns.” |
| 10 | Firebase Console → send notification to `community_alerts` → show it arriving on the phone (and tap it) | “Real-time push notifications with Firebase Cloud Messaging.” |
| 11 | **Profile & Settings**: change language, radius, toggle dark mode, **Send Test Notification**, Save | “Settings persist and re-theme the app.” |
| 12 | Briefly show GitHub repo, green Actions run, README, release notes | “Source is on GitHub with automated tests and release notes.” |

Upload to YouTube as **Unlisted** and paste the link into `README.md`.
