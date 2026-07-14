# Spartan — Launch Readiness Report (2026-07-09; re-verified 2026-07-13 for 1.1.0)

Verdict from a 4-dimension multi-agent audit (security/privacy, store policy, code correctness,
docs truthfulness) plus fresh build evidence, covering both platforms.

## 1.1.0 delta (2026-07-13)

Two features landed after the original audit, both re-verified with the same gauntlet
(unit tests · lint · debug + androidTest + R8 release builds all green):

1. **WHOOP CSV import** — the user's real data with zero credentials (Connections → Import WHOOP
   export). Fully local; adversarially reviewed (25-agent pass, 2 major + 11 minor findings fixed);
   e2e-verified on an emulator with a real 45-day export. Privacy policy updated for the new data
   categories (incl. journal flags). This resolves the old "sample-data-only 1.0" listing caveat:
   the listing can now truthfully say real WHOOP data works via CSV import.
2. **Guided training videos** — every generated training activity links a specific follow-along
   YouTube video (large channels only; every URL verified live via oEmbed), and each trainable
   metric has a "Train this metric" section that flags out-of-range readings and hands the user
   the sessions that target that number. Opening a video is an explicit user tap that leaves the
   app; Spartan itself still makes no network calls. Clinician-first metrics (ApoB, Lp(a), CAC)
   and pain/clinician activities deliberately get no video.

Version bumped to 1.1.0 (versionCode 2).

## Verdict

**Android: READY TO SUBMIT once the 6 human/account steps below are done. It cannot be *live* on
Google Play tomorrow** — new apps and health-category apps go through Play review (typically days
for a first submission). Submitting tomorrow is realistic; live-on-store is Google's clock.

**iOS: core is built and validated; the app shell needs one Xcode pass.** The entire coaching
domain (same rules, thresholds, copy, and safety engine) is ported to `ios/SpartanKit` and
**verified on this machine — 27 tests / 30,413 assertions / 0 failures**, including the 756-plan
eval sweep. The SwiftUI app layer (~1,500 lines: check-in hero, connections/consent, onboarding,
settings, JSON persistence, quiet-hours notifications) is source-complete but **compile-unverified**
(no Xcode on this machine — Command Line Tools only). Same App Store reality: Apple review is
typically 24–48h+; live tomorrow is not plausible for a first submission.

## Audit evidence (Android)

- Fresh full gauntlet green after audit fixes: 73 unit/Robolectric tests · 0 lint errors ·
  debug + androidTest + R8 release (~3.2 MB) all build.
- Verified clean: no secrets ever committed (history checked); minimal permissions; cleartext
  disabled; backup exclusions match policy; zero PHI-capable log statements; tokens never in
  Room/URLs; exported components all justified; blocked-phrase safety engine enforced over all
  generated coaching copy.

## Fixed during this audit (were real findings)

1. **[BLOCKER→fixed]** In-app privacy text falsely claimed "no network calls or API keys included"
   while the binary declares INTERNET + ships the OAuth/REST stack → rewritten truthfully.
2. **[RISK→fixed]** Disconnect/delete didn't clear OAuth tokens (policy said it did) →
   `disconnectWhoop/Calendar` and `deleteAllLocalData` now call the auth managers' `disconnect()`.
3. **[RISK→fixed]** AppAuth claimed the whole `spartan://` scheme, colliding with
   `spartan://today|connections` deep links → OAuth moved to its own `com.spartan.oauth` scheme
   (repo-wide: BuildConfig, .env.example, docs).
4. **[RISK→fixed]** Play listing implied live WHOOP sign-in works in 1.0 → reworded ("coming in an
   update"); privacy policy "per-source purge" overclaim reworded; unused `logging-interceptor`
   removed from release deps.

## Remaining HUMAN steps for Android submission (no code required)

1. Google Play developer account + create the app (fee if new account).
2. Generate upload keystore + local signing config + `bundleRelease` (scripted: RELEASE_CHECKLIST §2–3).
3. Host PRIVACY_POLICY.md at a public URL and **replace the `support@spartan.app` placeholder**
   with a monitored mailbox (it appears in the policy, listing, and checklist).
4. Produce store assets: 512px icon PNG, 1024×500 feature graphic, 6 screenshots (plan in listing §7).
5. Play Console forms: content rating, health-apps declaration, data-safety (answers pre-written).
6. **Physical-device smoke test of the signed release build** (10-item list, §6.3) — R8 output has
   never run on real hardware; this is the one test this machine cannot perform.

## Remaining steps for iOS submission

1. On a Mac with Xcode: `brew install xcodegen && cd ios/SpartanApp && xcodegen generate` → build,
   fix any compile drift in the SwiftUI layer (domain layer is already test-verified), run
   `swift test` on SpartanKit (XCTest works under full Xcode).
2. Apple Developer Program account, bundle id, automatic signing, archive → TestFlight.
3. App Store Connect: listing (written: `ios/docs/APP_STORE_LISTING.md`), privacy nutrition labels
   (written: `APP_STORE_PRIVACY_LABELS.md`), review notes for Guideline 5.1.3 (health) explaining
   sample-data mode. Checklist: `ios/docs/IOS_RELEASE_CHECKLIST.md`.

## Accepted risks (documented, not blocking a sample-data 1.0)

- Warm-app deep-link tap may not renavigate (cold start works; Today is the start tab) — verify in
  the device smoke test; fix queued.
- Snooze that lands inside quiet hours (22:00–07:00) is intentionally silent; the activity still
  reappears as PLANNED. Documented behavior.
- `WHOOP_CLIENT_SECRET` flows through BuildConfig — blank in the mock build; must move to a
  backend/PKCE-only flow before real WHOOP ships (flagged in checklist).
- Instrumentation tests are compile-verified locally; they execute in the CI emulator job/device.
- ViewModel captures "today" at construction; multi-day background sessions refresh via the 04:00
  worker. Edge accepted for 1.0.
