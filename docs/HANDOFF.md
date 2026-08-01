# Spartan — Project Handoff

**Purpose of this document:** everything a new conversation (or a new engineer) needs to pick this
project up: what Spartan is, exactly where it stands, and the recommended next moves in priority
order. Written 2026-07-10 at commit `1ce85c1` (master, pushed, clean tree).

Repo: https://github.com/bohueilin/Spartan (private) · Owner: bohueilin

---

## 1. What Spartan is

**Vision:** increase human longevity through personalized daily habit building — small actions your
own physiology chooses for you, kept through consistency (and, on the roadmap, community).

**Product:** a local-first personal health coach for WHOOP users, on Android (Kotlin/Compose) and
iOS (Swift/SwiftUI). Every morning it turns recovery, sleep, strain, HRV, and resting heart rate
into a short plan of 2–4 concrete activities — each with why it matters, step-by-step instructions,
a duration, a curated follow-along video, and one-tap done/snooze/skip/reschedule. It explains every
metric in plain language, projects honest 8-week improvement ranges at your actual consistency,
nudges proactively (7:15 digest, 19:00 catch-up) without nagging, and adapts next week's plan from
your effort/pain debriefs.

**Non-negotiables baked into the architecture:**
- **Local-first privacy.** No servers, no accounts, no analytics/ads SDKs. Data in Room/DataStore,
  excluded from backups; OAuth tokens in Keystore-encrypted storage; full in-app deletion; a
  non-PHI audit trail. Notifications hide content on the lock screen.
- **Never medical.** A `SafetyEngine` (blocked-phrase sanitizer) machine-checks every sentence of
  generated coaching copy on both platforms. Concerning vitals suppress intensity and produce a
  "talk to a clinician" nudge — never a diagnosis. Projections are capped ranges, never promises.
- **Honest data provenance.** Ships in clearly-labeled SAMPLE DATA mode; **WHOOP CSV import** brings
  real data with zero credentials; live WHOOP/Google OAuth is fully built but flag-gated
  (`USE_MOCK_* = true`) until production app registration.
- **Transparent coaching.** A rules engine (`CoachingEngine` + `RuleBasedRecommendationSource`),
  not a black box; a pluggable `RecommendationSource` seam exists for a future AI coach, and the
  **CoachingGym** (600+ gold scenarios, weighted reward: readiness 0.35 / safety hard-gate 0.25 /
  quality 0.40) is the eval/RL harness any AI source must beat.

**Architecture (identical shape both platforms):**
UI → ViewModel/ObservableObject → domain engines (`CoachingEngine`, `SafetyEngine`,
`ProjectionEngine`, `MetricExplainers`, `GoalEngine`, `StressPatterns`, `ReferenceRanges`) →
repository → adapters (`WhoopClient`: mock/CSV/real · `CalendarClient`: stub/real ·
`SecureTokenStore`: in-memory/encrypted). Canonical names/enums/schemas live in
[Spartan_Decisions.md](Spartan_Decisions.md) — **it is the tie-breaker; keep it that way.**

---

## 2. Where it is now (verified 2026-07-10)

### Quality gates — all green, re-verified today
| Gate | Result |
|---|---|
| Android unit + Robolectric | **161 tests / 0 failures** (23 classes) |
| iOS SpartanKit checks | **89 tests / 30,895 assertions / 0 failures** (`swift run SpartanChecks`) |
| Android lint | 0 errors |
| R8 release build | green, ~3.2 MB APK |
| Instrumentation (Room migration 3→4→5, Compose smoke + a11y) | compile-validated locally; **runs in the CI emulator job — has never executed on real hardware** |
| CI | `.github/workflows/ci.yml`: unit+Kover, lint, assemble debug/release (+mapping artifact), emulator job · Dependabot on |

### Shipped feature inventory (Android — all on `master`, all tested)
Daily check-in (readiness ring, greeting, haptic check-off, pull-to-refresh, consistency strip,
day-complete moment, time-of-day ordering) · rules-based daily plan with safety gating · metric
explainers (9 WHOOP metrics) · 8-week projection card ("Where this can take you") · exercise
debrief → adaptive deload · **WHOOP CSV import** · **curated age/needs-aware video library** with
"Train this metric" · **Coach hub** (goals with safety counter-offers, stress windows, age/sex
reference ranges) · calendar free/busy scheduling (stub default, real client built) · notifications:
morning digest, evening nudge, activity reminders with **Done/Snooze action buttons**, quiet hours,
lock-screen privacy · home-screen widget · deep links (`spartan://today|connections`, warm-tap fixed)
· in-app review prompt (rate-limited, positive-moment-only) · consent/disconnect/delete flows ·
Spartan helmet adaptive icon + splash + edge-to-edge · 150+ externalized strings · debug-only
Diagnostics screen · audit-events trail · daily 04:00 refresh worker + update re-arm receiver.

### iOS status — the one big asymmetry
`ios/SpartanKit` (all domain engines, mock/CSV data sources, scheduling) is **fully built and
test-verified on macOS**. `ios/SpartanApp` (SwiftUI shell at feature parity, XcodeGen project) is
**source-complete but has never been compiled** — this machine has only Command Line Tools, no
Xcode. Expect a modest compile-fix pass, not a rewrite.

### Field status
Installed and verified **live on the owner's Pixel 10 Pro XL** (screenshots confirmed the full
check-in composing correctly). One device-only bug was found that way (onboarding transparent
window background) and is fixed in `1ce85c1`. **The 10-item release smoke list in
RELEASE_CHECKLIST §6.3 has not been formally executed.**

### Known-untrue-until-done (honest gaps)
1. Store presence requires human/account steps — Play Console + signing keystore + hosted privacy
   policy + real support email (placeholder `support@spartan.app` is in policy/listing/checklist) +
   store art. All scripted in [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).
2. Live OAuth needs production app registration (WHOOP developer app; Google sensitive-scope review
   for `calendar.freebusy` — has lead time). `WHOOP_CLIENT_SECRET` currently flows via BuildConfig —
   must move to PKCE-only/backend before real WHOOP ships (flagged in checklist).
3. Cert pinning is prepared-not-enabled by design ([CERT_PINNING_RUNBOOK.md](CERT_PINNING_RUNBOOK.md)).
4. Video library links are curated statically — verify each video URL resolves before shipping the
   build that surfaces them.
5. Instrumentation tests + baseline profiles need an emulator/device runner (CI job exists; never
   run on GitHub yet — first CI run happens on the next push to a repo with Actions enabled).

---

## 3. Next evaluations & recommendations (priority order)

### P0 — Prove it on hardware (hours, no code)
1. **Run the 10-item smoke list** (RELEASE_CHECKLIST §6.3) on the Pixel with a **signed release
   build** — R8 output has never run on-device. Add: CSV import with a real WHOOP export, one video
   link end-to-end, notification action buttons, widget, TalkBack pass
   ([ACCESSIBILITY.md](ACCESSIBILITY.md) script).
2. **Confirm CI goes green on GitHub** (push any trivial change; check the emulator job actually
   passes the migration + Compose tests in the cloud).

### P1 — Ship Android (days; mostly account work)
3. Execute RELEASE_CHECKLIST §§2–5: keystore → `bundleRelease` → Play Console → hosted privacy
   policy + real support mailbox → store art (poster prompt in `docs/assets/POSTER_PROMPT.md`,
   screenshots plan in listing §7) → internal track → staged rollout. Ship 1.0 in **sample-data +
   CSV-import mode** — it is honest and complete without OAuth.

### P1 — Ignite iOS (one Xcode session + follow-ups)
4. On a Mac with Xcode: `brew install xcodegen && cd ios/SpartanApp && xcodegen generate`, fix
   compile drift, run `swift test` under Xcode, TestFlight. Checklist:
   [ios/docs/IOS_RELEASE_CHECKLIST.md](../ios/docs/IOS_RELEASE_CHECKLIST.md).

### P2 — Live integrations (when accounts exist)
5. Register production WHOOP + Google OAuth apps; move WHOOP secret out of BuildConfig; flip
   `USE_MOCK_*`; enable cert pinning per the runbook; device-test 401-refresh, disconnect-clears-
   tokens, and airplane-mode degradation.

### P2 — Product depth (next build cycles)
6. **Retention mechanics with evidence:** the research docs (`docs/research/`, if the interrupted
   research sweep is re-run — see Open threads) + [NEXT_PHASES.md](NEXT_PHASES.md) +
   [UX_ROADMAP_PROMPTS.md](UX_ROADMAP_PROMPTS.md) Tier 2/3 (rich digest notification, plan editor
   with activity swaps, interactive trend charts with projection overlay, personal baselines).
7. **Community phase** per [COACH_DESIGN.md](COACH_DESIGN.md) — but note: any social/cloud feature
   ends the "no servers" era and triggers the BAA/DPA + compliance review pre-planned in
   [COMPLIANCE.md](COMPLIANCE.md). Decide deliberately.
8. **AI coach experiments** only through the `RecommendationSource` seam, graded by CoachingGym
   ([COACH_GYM.md](COACH_GYM.md)) — never ship a source that scores below the rules baseline.

### P3 — Craft horizon
9. Apple-Design-Award-level polish per [APPLE_DESIGN_AWARD_RUNWAY.md](APPLE_DESIGN_AWARD_RUNWAY.md):
   haptics vocabulary, Live Activities/Dynamic Island, watch surfaces, ProMotion polish,
   Inclusivity excellence. Realism note preserved there: awards follow shipped, loved products.

---

## 4. Resuming in a new conversation

**Point the next session at:** this file first, then [Spartan_Decisions.md](Spartan_Decisions.md)
(canonical vocabulary — never contradict it), [LAUNCH_READINESS.md](LAUNCH_READINESS.md) (audit
verdict), and [Spartan_Enhancements.md](Spartan_Enhancements.md) (every backlog item with status).

**Build/verify commands (this machine):**
```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/android-sdk"
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
cd ios/SpartanKit && swift run SpartanChecks        # iOS domain suite (no Xcode needed)
# Device: ~/android-sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Run Gradle tasks **serially** (Hilt/KSP races when parallelized). Everything builds with zero
credentials; `.env.example` documents the optional real-integration config.

**Standing guardrails for any future work:** every user-facing coaching string passes SafetyEngine;
mock/sample data stays visibly labeled; projections stay capped ranges; no analytics/tracking SDKs;
`Spartan_Decisions.md` wins naming disputes; keep Android and iOS engines behaviorally identical
(the mirrored test suites are the contract); commit + push at every stable checkpoint.

**Open threads:** (a) the fitness-app design-research sweep was interrupted mid-run — if
`docs/research/` is missing or thin, re-run it (resume: `Workflow({scriptPath, resumeFromRunId:
"wf_1e043c4b-9ea"})` from the prior session, or just relaunch fresh); (b) `docs/NEXT_PHASES.md`
exists from the overnight cycle — reconcile it against research once re-run; (c) the GitHub poster
image slot (`docs/assets/spartan-github.png`) — regenerate anytime with `docs/assets/POSTER_PROMPT.md`.
