# Response to the independent audit (2026-09-25)

An external, static-only audit (Muse AI) of commit `6f5c2d0` reported 0 P0, 2 P1, 10 P2 and 5 P3
findings plus an AI-era design consultation. Every claim below was re-checked against the tree
before acting; this round then built, tested and ran the result on a physical Pixel.

## Verdicts

| # | Finding | Verdict | What changed |
|---|---|---|---|
| F-001 | WHOOP sync reads one page, never follows `next_token` | **Agree** | `RealWhoopClient` follows `nextToken` for recovery/sleep/cycle, stops on a blank or repeated token, caps at 10 pages and logs (counts only) if the cap is hit. Naps were already excluded by the mapper; now tested |
| F-002 | OAuth/token code has zero tests | **Agree** | Tests for the token store, auth-request construction (scopes, redirect, PKCE S256), the 401 refresh authenticator and bearer interceptor, disconnect/revoke request shape; an instrumented `EncryptedTokenStore` round-trip |
| F-003 | README test counts stale | **Agree** | Numbers re-measured on this commit (see README), with the commands that reproduce them |
| F-004 | "SafetyEngine … can never make a medical claim" overstates it | **Agree** | README now calls it a phrase-level regression guard over templated, rules-based copy |
| F-005 | Projection ranges uncited | **Agree** | No citations invented. Engine KDoc, README and the in-app disclaimer (both platforms, still byte-identical) now call them illustrative estimates instead of "ranges seen in general research" |
| F-006 | CoachingGym framing could imply efficacy | **Agree** | README + `COACH_GYM.md` say it is invariant/regression testing, not outcome evidence |
| F-007 | Disconnect never revokes the server-side grant | **Agree** | Disconnect and delete-all delete local tokens first, then best-effort `DELETE /v2/user/access` (WHOOP) and `POST /revoke` (Google, token in the form body). No request when no token exists, so the default build stays network-silent |
| F-008 | Reflections are write-only | **Agree** | Weekly Review shows sleep patterns behind the user's own reflections ("Your 3 tough days all followed nights under 6 hours of sleep …"); needs ≥ 2 matching days and one counterexample silences it |
| F-009 | No iOS CI | **Agree** | `ios` job runs `swift run SpartanChecks` on macos-latest |
| F-010 | Coverage reported, not enforced | **Agree** | `koverVerifyDomain` gates `domain.engine/eval/model` at ≥ 95 % lines in CI |
| F-011 | Real-API mode never fetches workouts | **Partly** | The premise that pain/RPE rules depend on WHOOP workouts is wrong — they come from the user's debriefs. The only gap is exercise minutes in live-API mode; documented as a deliberate source split rather than implemented |
| F-012 | Docs drift (Room version, stale TODO) | **Agree** | Stale `TODO(Phase 2)` removed; current-state docs aligned |
| F-013 | Bottom bar mis-highlights | **Agree** | The selected tab is now the nearest tab on the back stack, so Connections and metric detail light the tab they were opened from |
| F-014 | Two logging seams can double-count | **Agree** | Non-destructive: both seams show "You already logged … today. Saving adds another session." The Today check only fires when a log isn't explained by another checked-off activity, so two planned mobility sessions don't trigger it |
| F-015 | Alpha dependencies | **Agree** | Health Connect 1.1.0 (requires compileSdk 36; targetSdk stays 35) and security-crypto 1.1.0 (deprecated upstream; suppressed in its one file) |
| F-016 | iOS shell never compiled | **Agree, deferred** | Status wording aligned with `ios/README.md`; compiling the shell is its own task |
| F-017 | UI-layer maintainability | **Agree, deferred** | Design-system work (Typography, TopAppBar, ViewModel split) is tracked in `DESIGN_REVIEW_2026-09.md` |
| AI | Keep generative AI out of core coaching | **Agree** | No change: `RecommendationSource` stays the only seam, with SafetyEngine outside it |

## Found during this round, not in the audit

- **Live OAuth sign-in is not wired.** Nothing in the UI calls `authorizationIntent()` /
  `handleAuthResponse()`; the Connect button records a status only. README and architecture docs
  now say so. The token, refresh and revoke code is tested for when it is wired.
- **WHOOP revoke usually meets an expired access token.** WHOOP access tokens are short-lived, so a
  best-effort revoke often gets a 401. The privacy policy points users to WHOOP account settings as
  the fallback; refresh-then-revoke is future work alongside wiring sign-in.
- **Disconnect during an in-flight sync** could let the 401 authenticator refresh and re-store
  tokens after they were cleared. Only reachable once sign-in is wired; noted for that work.
- The reflection card appears inside the weekly review, which exists only after a first logged
  workout (unchanged empty-state rule).
- **Kover's report also runs the release unit tests**, where `DebugLog` is a no-op; a new test
  asserting a debug log line would have failed CI's coverage step. The log assertion is now
  debug-only.

Two small UI fixes came from running the result on a phone rather than from the audit:

- The exercise debrief sheet opened half-expanded, so **Skip** and the note below it sat under the
  fold (worse once the "already logged" notice was added). It now opens fully expanded.
- With the correct tab now highlighted, **re-tapping the active tab** returns to that tab's root
  screen instead of leaving the user on the pushed screen.

## Verification (2026-09-25, Pixel 10 Pro XL, Android 17)

| Check | Result |
|---|---|
| `:app:assembleDebug`, `:app:assembleRelease` (R8 full mode) | green; release APK ~4.0 MB unsigned (README's older 3.2 MB was stale) |
| `:app:testDebugUnitTest` + `:app:testReleaseUnitTest` | 207 + 207 tests, 0 failures (171 before this round) |
| `:app:lintDebug` | 0 errors (warnings 69 → 65) |
| `:app:koverVerifyDomain` | green; line coverage `data.whoop` 68.6 → 76.5 %, `data.calendar` 26.1 → 45.8 %, `data.security` 0 → 16.2 % (the Keystore store is covered on-device instead) |
| `:app:connectedDebugAndroidTest` on the Pixel | 5/5 (incl. the new `EncryptedTokenStore` round-trip). One earlier run timed out waiting for the first frame with the screen dozing; two re-runs with the screen awake were 5/5 |
| `swift run SpartanChecks` | 89 tests, 30,895 assertions, 0 failures |
| On-device walkthrough | Tab highlight from Settings → Connections, Today → metric detail/Connections, Metrics → detail → add; duplicate-log notice absent on first debrief, shown on re-check and on Coach → Log Mobility, absent for Strength; reflection card rendered from seeded reflections ("Your 3 strong days all followed nights of 7 or more hours of sleep …") with a lone tough day correctly silent; Delete local data returns to onboarding; no crashes in logcat |
