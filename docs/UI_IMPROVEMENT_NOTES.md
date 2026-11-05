# UI and usability refinement notes – 4 November 2026

## Changes included in this revision

1. Discover actions are grouped with translated section headings (Services, map/saved content, and contribution/account actions) to improve information hierarchy.
2. The app distinguishes a Firebase/Google identity-only limited session (`demo`) from a valid CommuniServe API JWT session.
3. In limited mode, Discover and Add Service/Event show the same explanation and a Retry Connection action.
4. The limited-mode explanation explicitly states that the listing is stored on the device and is not yet submitted to the server. The API must acknowledge a successful request before the app reports submission.
5. Existing brand colours, reusable banner, rounded controls and spacing conventions are retained for visual consistency.

## Further visual checks for the group

- Verify the complete Discover screen on the target phone at the smallest supported display size and with large system font settings.
- Confirm the bottom guidance text is reachable by scrolling and is not obscured by system navigation or the keyboard.
- Check text contrast in both light and dark mode.
- Confirm every visible action has a meaningful loading, success, empty or error state.
- Add real screenshots of successful online workflows only after those workflows pass.

## Remaining design opportunities

A richer category-card layout, thumbnail-based service cards, a persistent bottom navigation bar, and a dedicated connection-status component could further improve the visual experience. These are suggested future refinements and are not claimed as implemented in this revision.

## Update: bottom navigation and Discover redesign (implemented in code, NOT yet device-tested)

- Persistent bottom navigation with Material vector icons (Discover, Events, Map, Saved, Profile), translated labels.
- Discover: connection-status chip, category chips (one-tap filter), two-up quick-action tiles, initial-letter avatars on list cards.
- Record real phone screenshots in docs/device-evidence once the build is verified.
- Category chips use Material icons; Health, Food, Transport, Repairs and Cleaning open Google Maps near-me searches (pharmacies/hospitals/clinics, restaurants, taxi ranks/bus stops/train stations, repair shops, cleaning services). Needs a maps app or browser and location turned on.
- Friendlier messages: plain-language errors in EN/AF/ZU (no raw exception text), server address hidden behind 'Connection settings', limited-mode card with Retry.
- Login and registration redesigned: logo tile, card layout, icon fields with show/hide password, Remember me (email only), Forgot password notice, Google, guest mode, confirm-password check.
- Consistency pass: pill buttons and 16dp fields app-wide, Profile redesigned with avatar header and grouped settings card, sign-out confirmation.
