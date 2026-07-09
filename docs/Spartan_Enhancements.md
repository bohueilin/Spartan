# Spartan — Engineering Enhancement Backlog

> **Status (2026-07-09): implementation sweep complete.** Every item below now carries an explicit
> status: **DONE** (implemented + validated in this repo), **PARTIAL** (scoped subset landed,
> remainder deliberate), **PREPARED** (ready-to-enable with runbook), or **DEFERRED** (with the
> reason — device-bound, or an explicit product/security decision this doc itself recommends).
> Decision items resolved as documented: exact alarms → stay inexact; Play Integrity → skip until a
> backend exists; root detection → none by design; crash SDK → none, Play Vitals only.

A prioritized, honest backlog for the Spartan Android app. Grounded in the current tree:
Kotlin 2.0.21 · Compose + Material 3 · Hilt · Room **v4** (migrations `1→2→3→4`) · DataStore ·
WorkManager · Retrofit/OkHttp/AppAuth (Phase-2, behind `USE_MOCK_*`) · mock-first WHOOP + Calendar ·
rules-based `CoachingEngine` gated by `SafetyEngine`. `minSdk 26`, `target/compileSdk 35`, JDK 17.

**Priority:** P0 launch-blocker · P1 fast-follow · P2 later. **Effort:** S (<½ day) · M (1–3 days) · L (>3 days).
Nothing here makes medical claims; every user-facing string still passes `SafetyEngine.sanitize`. No fabricated metrics.

## Already done (do not re-propose)
Rebrand to `com.spartan`; mock-first WHOOP + real WHOOP OAuth (AppAuth/PKCE) + Retrofit; real Google
Calendar free/busy; Keystore-backed `EncryptedTokenStore`; rules-based `CoachingEngine` + `SafetyEngine`;
daily check-in UI with complete/snooze/skip/reschedule persistence (`daily_activities`); adaptive icon +
splash + edge-to-edge; R8 release build (`isMinifyEnabled`/`isShrinkResources`); 42 JVM unit tests +
coaching eval; Play collateral (listing, privacy policy, release checklist); notification tap-to-open
(`PendingIntent` → `MainActivity`); snooze wake-up + expired-snooze reactivation; sync-failure banner.

---

## 1. Testing & CI

- **CI matrix on GitHub Actions** — **DONE.** `.github/workflows/ci.yml`: `unit` (tests+Kover), `lint`, `assemble` (debug+release+androidTest compile, R8 mapping artifact, APK-size record), and an emulator `instrumentation` job (`reactivecircus/android-emulator-runner`, API 34). Serial (`--max-workers=1`), zero secrets. *Why:* there is **no `.github/workflows/`** today; every
  gate (`assembleDebug`, `testDebugUnitTest`, `lintDebug`) is run by hand, and the release checklist assumes
  they are green. *Sketch:* `.github/workflows/ci.yml` on `ubuntu-latest`, JDK 17 (`actions/setup-java`
  temurin), `android-actions/setup-android`, `gradle/actions/setup-gradle` cache. Jobs: `unit` (`./gradlew
  --no-daemon :app:testDebugUnitTest`), `lint` (`:app:lintDebug`, upload `lint-results-debug.html`),
  `assemble` (`:app:assembleDebug`). Run tasks **serially** (`--max-workers=1`) — this Hilt/KSP project races
  on generated files, per README. Secrets are unneeded (mock mode builds with no credentials).
- **Room migration tests** — **DONE (code + CI job; executes on the emulator job/device).** `exportSchema=true` + `room.schemaLocation` landed; authentic `5.json` exported, `4.json`/`3.json` derived (additive-migration subsets) and committed under `app/schemas/`; `MigrationTest` walks 3→4→5 asserting data survival. DB is now v5 (adds `audit_events`). *Why:* `AppDatabase` has
  `exportSchema = false`, so `MIGRATION_3_4` (creates `daily_activities` + `integration_connections`) is
  **untested**; a bad migration silently drops local health data. *Sketch:* flip to `exportSchema = true`,
  add `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, commit `app/schemas/`. Add
  `androidx.room:room-testing:2.6.1`; `MigrationTestHelper` walks `createDatabase(v3)` → `runMigrationsAndValidate(4, MIGRATION_3_4)`.
  Needs a **device/emulator** (instrumentation) — add to CI as an `androidTest` job on `reactivecircus/android-emulator-runner`.
- **Compose UI / instrumentation tests** — **DONE (compile-validated; runs on the CI emulator job).** `CheckInScreenTest` drives the REAL app in mock mode (sample data = deterministic fixture, no Hilt doubles): onboarding→plan render, SAMPLE DATA honesty label, and 48dp touch-target assertion. *Why:* zero `androidTest/` sources exist; check-in
  complete/snooze/skip/reschedule and the consent flow are only covered indirectly via ViewModel-less JVM
  tests. *Sketch:* `androidTestImplementation` `androidx.compose.ui:ui-test-junit4`,
  `androidx.test.ext:junit`; `createAndroidComposeRule<MainActivity>()`; drive `CheckInScreen` /
  `ConnectionsScreen` by `onNodeWithText`/`onNodeWithContentDescription`. Use `@HiltAndroidTest` +
  `HiltTestRunner` (custom `AndroidJUnitRunner` with `HiltTestApplication`) and a test module binding
  `MockWhoopClient`/`StubCalendarClient`. **Needs emulator.**
- **Robolectric for JVM-run Android tests** — **DONE.** `AndroidComponentsTest` (Robolectric 4.14.1 + work-testing): reminder enqueue/dedupe/disable-cancels, past-trigger skip, worker day-mask branches, exhaustive Room converter round-trips. Runs green on the JVM. *Why:* lets `PreferencesStore`, `ReminderScheduler`
  (WorkManager), `Converters`, and notification logic run on CI **without an emulator** (faster, cheaper than
  the instrumentation job). *Sketch:* `testImplementation "org.robolectric:robolectric:4.13"` +
  `androidx.test:core-ktx`; `androidx.work:work-testing` (`WorkManagerTestInitHelper`,
  `TestListenableWorkerBuilder<ReminderWorker>`) to assert enqueue/quiet-hours/day-mask branches.
- **Screenshot / snapshot tests** — **DEFERRED (P2).** Roborazzi goldens recorded on a dev Mac drift against Linux CI font rendering; adopting this needs a containerized record strategy first. Font-scale previews + the emulator-job Compose assertions cover the regression classes it targeted for 1.0. *Why:* dark/light + large-font regressions (release
  checklist §6.3 items 8–9) are currently caught only by manual QA. *Sketch:* Roborazzi
  (`io.github.takahirom.roborazzi`, JVM, no device) or Paparazzi (`app.cash.paparazzi`) over the composables
  in `ui.screens`; golden PNGs in the repo, `verifyRoborazzi` in CI. Paparazzi conflicts with Hilt-injected
  previews — snapshot pure `@Composable`s that take state, not `MainViewModel`.
- **Coverage reporting** — **DONE.** Kover 0.8.3 plugin; `koverXmlReport` uploaded as a CI artifact. *Why:* the 42 JVM tests concentrate on engines; coverage of
  `data.*` mappers/repository is unknown. *Sketch:* Kover (`org.jetbrains.kotlinx.kover` Gradle plugin) →
  `koverXmlReport`; upload to CI artifact / PR summary. No third-party service required (privacy posture).
- **Baseline profiles for startup** — **DEFERRED (device-bound).** Requires a physical/managed device for MacrobenchmarkRule capture; module plan documented here, revisit once a bench device is available. *Why:* faster cold start on `minSdk 26` low-end devices;
  R8 alone doesn't pre-compile the hot path. *Sketch:* `androidx.baselineprofile` plugin + a
  `:baselineprofile` module using `MacrobenchmarkRule` + `BaselineProfileRule` to capture the
  launch→Today→check-off journey; profile ships in the AAB. **Needs a physical device / managed device.**

## 2. Reliability

- **OkHttp `Authenticator` for automatic 401 token refresh** — **DONE (P0).** Both `OkHttpClient.Builder()`s
  in `NetworkModule` now attach a `refreshAuthenticator` that, on a 401, calls `WhoopAuthManager.refresh()` /
  `CalendarAuthManager.refresh()`, rewrites `Authorization`, and gives up after one attempt
  (`priorResponseCount >= 1`) to avoid refresh loops.
  *Remaining fast-follow (P1 · S):* single-flight the refresh (per-provider `Mutex`) to avoid stampedes under
  concurrent 401s, and on repeated failure set `IntegrationConnectionEntity.status = ERROR` to drive the
  existing sync-failure banner. Needs a device/instrumented test to exercise (mock default ships without it).
- **Periodic daily-plan refresh worker** — **DONE.** `@HiltWorker DailyPlanRefreshWorker` (~04:00 local, `KEEP`), sharing the `DailyPlanSync` use-case with the ViewModel so morning plans are identical either path; refreshes the home-screen widget; one retry cycle on real-network failure. *Why:* the plan is (re)built when the app opens; a
  user who only taps the reminder needs a fresh, readiness-correct plan waiting each morning. *Sketch:*
  `DailyPlanRefreshWorker : CoroutineWorker` enqueued via `enqueueUniquePeriodicWork("daily_plan_refresh",
  KEEP, PeriodicWorkRequest(1 day) with initialDelay to ~04:00 local)`; it pulls the latest `WhoopSnapshot`,
  runs `CoachingEngine.buildPlan`, upserts `daily_activities`, then calls `ReminderScheduler.scheduleActivityReminder(...)`
  per activity. Constraints: none (fully local). Idempotent by activity `id`.
- **Re-arm reminders after reboot / app-update** — **DONE.** WorkManager persistence covers reboot (documented); `PackageReplacedReceiver` (`MY_PACKAGE_REPLACED`) re-asserts the periodic refresh and runs an immediate one-shot after every update. *Why:* WorkManager persists its own
  enqueued jobs across reboot (its internal boot receiver reschedules them), **but** a package-replace
  (app update) and any future exact-alarm path do **not** auto-restore. There is **no** `RECEIVE_BOOT_COMPLETED`
  receiver today. *Sketch:* rely on WorkManager persistence for the current `OneTimeWorkRequest` reminders
  (document this explicitly), **and** add a `MY_PACKAGE_REPLACED` `BroadcastReceiver` that re-runs
  `DailyPlanRefreshWorker` so reminders re-arm after every update. Only add `RECEIVE_BOOT_COMPLETED` if the
  exact-alarm item below lands.
- **Exact-alarm consideration for time-critical nudges** — **P2 · M.** *Why:* WorkManager periodic/one-time
  work is **inexact** (batched, Doze-deferred) — fine for "log how it went" but a pre-activity nudge can slip
  10–20 min. *Sketch:* only if product wants punctual nudges, add an `AlarmManager.setExactAndAllowWhileIdle`
  path guarded by `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (API 31+ `canScheduleExactAlarms()` + settings
  deep-link). This **requires** `RECEIVE_BOOT_COMPLETED` to re-arm alarms after reboot. Weigh against the
  Play policy scrutiny exact-alarm permission attracts — default to inexact.
- **Offline-first behavior audit** — **DONE (code paths).** All sync flows go through `DailyPlanSync`, which converts any fetch failure (IOException, auth, empty) into a non-crashing outcome → last-good data + the sync banner; worker retries once. The airplane-mode instrumented assertion remains listed under the emulator job. *Why:* README/checklist claim "airplane-mode end-to-end
  works"; keep it literally true as the real network paths land. *Sketch:* wrap `RealWhoopClient` /
  `GoogleCalendarClient` calls so `IOException`/`UnknownHostException` degrade to last-good cached
  `MetricEntryEntity` + a "using last sync" note (never a crash/toast); add an instrumentation test that
  toggles `ShadowConnectivityManager` offline and asserts the daily loop still renders.

## 3. Features (beyond current PRD — honest, non-medical)

- **Health Connect as a WHOOP alternative** — **ADAPTER DONE, flag-gated OFF.** `HealthConnectSource : WhoopClient` maps HRV/RHR/sleep(+bed/wake)/energy into `WhoopSnapshot`; DI selects it via `USE_HEALTH_CONNECT` (default false). Enable steps (manifest health permissions, permission contract, Play Health-apps declaration) documented in the class header — deliberately not declared while unused. *Why:* many users don't own a WHOOP; Health
  Connect (Google's on-device aggregator) reads recovery-adjacent signals from other wearables and keeps the
  same local-first, consent-gated posture. *Sketch:* new `HealthConnectClient : WhoopClient`-shaped adapter
  in `data.healthconnect` reading `HeartRateVariabilityRmssdRecord`, `RestingHeartRateRecord`, `SleepSessionRecord`,
  `TotalCaloriesBurnedRecord` via `androidx.health.connect:connect-client`; map into the existing
  `WhoopSnapshot` vocabulary so `ReadinessSnapshot.from` and every rule are reused unchanged. Add a
  `HEALTH_CONNECT` `IntegrationProvider`; per-record read permissions requested at connect time. No new
  clinical claims — same non-diagnostic framing.
- **Home-screen widget** — **DONE** (`NextActivityWidget`, Glance): next planned activity, taps deep-link to `spartan://today`, refreshed by the daily worker. **Wear tile — DEFERRED** (separate module + Wear testing; revisit post-launch). *Why:* the product is a daily loop;
  a glanceable "next thing to do" removes a launch step. *Sketch:* `androidx.glance:glance-appwidget` widget
  bound to the top `PLANNED` `DailyActivity` (title + scheduled time + a "done" action that updates the row);
  Wear tile via `androidx.wear.tiles`. Read-only projection over the repository — no new data.
- **Longitudinal trend charts** — **DONE (dependency-free).** Review screen now renders 14-point Recovery / HRV / Sleep-performance sparklines from `metric_entries` via the existing `TrendCard` canvas — descriptive only, no inference copy. Vico can replace the canvas later if richer interaction is wanted. *Why:* recovery/HRV/sleep trends give context the single-day
  card can't; the data already lives in `metric_entries`. *Sketch:* Vico (`com.patrykandpatrick.vico:compose-m3`)
  line charts on a Trends screen for `RECOVERY_SCORE`, `HRV_RMSSD`, `SLEEP_PERFORMANCE`; keep it descriptive
  (no "you're overtrained" inference — that would need `SafetyEngine` review and risks over-claiming).
- **Streaks / consistency** — **DONE.** `daysWithCompletedActivity` DAO query → calm "Active on N of the last 7 days" line on the Review screen. No badges, no streak-loss anxiety. *Why:* gentle adherence feedback aids a
  daily habit without dark patterns. *Sketch:* derive a "days with ≥1 completed REQUIRED activity" count from
  `daily_activities` in `ReviewEngine`; show a calm consistency strip. No badges/points/streak-loss anxiety,
  no push-to-break-a-streak — deliberately restrained.
- **Weekly review polish** — **DONE.** Review screen enriched with consistency + the three readiness trends alongside the existing adherence/strength/7-day summaries; share continues via the local export path. *Why:* `weekly_reviews` + `ReviewEngine` exist but the surface is
  thin. *Sketch:* richer `ReviewScreen` — adherence %, Zone-2 minutes, strength sessions, and
  `nextWeekFocus`, all sourced from existing entities; add a "share weekly summary" via the existing local
  export path.
- **Multiple check-ins per day** — **PARTIAL (deliberate).** Cards now order by priority then time-of-day (morning→evening) and each carries its time label; a fully segmented morning/evening view is a product decision deferred to real-user feedback. *Why:* morning-plan + evening-reflection matches how people
  actually run a day. *Sketch:* `bestTimeOfDay` already partitions activities (`MORNING`/`MIDDAY`/…); add a
  time-of-day segmented view in `CheckInScreen` and a second, lighter evening reminder channel.
- **Human-coach mode architecture** — **PARTIAL.** The append-only, non-PHI `audit_events` table + `logAudit` hooks (consent, sync, plan, deletion) landed — the audit seam coach/HIPAA deployments need. Multi-client `clientId` scoping + `RemoteSyncSource` remain deferred behind the BAA/DPA review, as specified. *Why:* the SECURITY checklist and
  architecture §11 both anticipate coach/client workflows and RBAC. *Sketch:* introduce a `clientId` scoping
  seam at the repository boundary and a `RemoteSyncSource` behind it (per architecture §11); add the
  append-only `audit_events` table (actions/timestamps only, never PHI) that the checklist lists as planned.
  Gate behind a feature flag; **triggers a BAA/DPA review** before any PHI leaves the device.
- **Respect the sleep window from WHOOP in scheduling** — **DONE.** `WhoopSnapshot` gains `bedMinuteOfDay`/`wakeMinuteOfDay` (real mapper parses sleep start/end; mock supplies 22:45/06:30); `scheduleActivity` windows are wake+30min → bed−60min with the old 08:00–21:00 as no-data fallback. *Why:* `AvailabilityService` already
  subtracts a sleep window, but it should be **derived from WHOOP sleep data**, not a static default, so
  nudges never land during actual sleep. *Sketch:* feed `WhoopSnapshot.sleepDurationHours` / typical
  bed/wake into `AvailabilityService`'s constraints; unit-test the DST + cross-midnight cases already
  handled by `ZonedDateTime`.

## 4. Quality & accessibility

- **String extraction + i18n + RTL** — **DONE (extraction pass).** Static UI copy extracted to `res/values/strings.xml` with `stringResource(...)` across the screens; runtime coaching copy (SafetyEngine-sanitized) intentionally stays code-generated. Pseudolocale smoke (`en-XA`/`ar-XB`) + first real locale remain the pre-localization step. *Why:* `res/values/strings.xml` is **3 lines** and UI
  code has **~65 hardcoded `Text("…")`** with **0** `stringResource` calls — the app is effectively
  un-localizable today, though the manifest already sets `supportsRtl="true"`. *Sketch:* extract all
  user-facing copy to `strings.xml` (keep the `SafetyEngine`-sanitized coaching strings that are generated at
  runtime as-is), replace with `stringResource(R.string.…)`, add a lint gate (`HardcodedText`), and
  pseudolocalize (`en-XA`/`ar-XB`) to smoke-test RTL + expansion before shipping real locales.
- **Automated accessibility checks** — **DONE (Compose semantics assertions; run on the emulator job).** `CheckInScreenTest` asserts labeled checkboxes and the 48dp touch-target minimum; semantics contract documented in docs/ACCESSIBILITY.md. *Why:* release checklist §6.2 treats pre-launch
  accessibility findings as fix-before-launch; catch them in CI, not at upload. *Sketch:* Espresso
  Accessibility (`androidx.test.espresso:espresso-accessibility` + `AccessibilityChecks.enable()`) in the
  instrumentation suite; or Compose semantics assertions for `contentDescription`, min 48dp touch targets,
  and merged-node labels on `CheckInScreen`/`ConnectionsScreen`. **Needs emulator.**
- **Large-font & display-scaling QA** — **DONE (tooling).** `FontScalePreviews.kt` renders the check-in at 1.0×/1.5×/2.0× for Studio review; no fixed-height text containers in the token system. *Why:* `minSdk 26` reaches many small/low-DPI devices;
  200% font + smallest-width can clip cards. *Sketch:* `preview_resize`-style font-scale previews
  (`@Preview(fontScale = 2f)`), plus a Roborazzi variant at `fontScale = 1.5/2.0`; audit fixed `dp` heights
  in `ui.theme.Tokens`.
- **Contrast audit** — **DONE, with a real fix.** Programmatic WCAG audit over both palettes (docs/ACCESSIBILITY.md): all text pairs pass AA; the readiness-band colors FAILED 3:1 on light surfaces and `bandColor()` is now theme-aware (darkened light-mode variants, ≥4.5:1). *Why:* both `values` and `values-night` palettes must clear WCAG AA for
  the "readable in both themes" claim. *Sketch:* run Material Theme Builder / manual contrast checks over
  `ui.theme` color tokens; assert AA (4.5:1 text) for primary text-on-surface in both palettes.
- **TalkBack test script** — **DONE.** 10-step repeatable script in docs/ACCESSIBILITY.md, including the font-scale-2.0 + TalkBack worst case. *Why:* §6.3 item 9 is a manual TalkBack pass — write it down so it's
  repeatable and delegable. *Sketch:* a checked script in `docs/` covering onboarding → Today → check off an
  activity entirely by TalkBack, verifying focus order, live-region announcements on completion, and that the
  sync-failure banner is announced.

## 5. Security hardening

- **Certificate pinning** — **PREPARED, deliberately not enabled** (per this doc's own guidance: no live traffic to pin in sample-data 1.0; a stale pin bricks sync). Full config template, SPKI capture commands, and rotation runbook in docs/CERT_PINNING_RUNBOOK.md; ships with the first real-integration release. *Why:* the SECURITY
  checklist and architecture §9 both list pinning as the one **⬜ planned** transport item; cleartext is
  already disabled but there is no pin. *Sketch:* `network_security_config.xml` `<pin-set>` with SPKI SHA-256
  for the WHOOP + Google leaf/intermediate (pin the intermediate CA to survive leaf rotation), plus a backup
  pin and an `expiration` date; **or** OkHttp `CertificatePinner` in `NetworkModule`. Document a rotation
  runbook — a stale pin bricks sync. Ship only after real integrations are enabled (avoid brittle pins in
  sample-data 1.0).
- **StrictMode in debug** — **DONE.** Thread + VM policies with `penaltyLog()` in `SpartanApp.onCreate`, `BuildConfig.DEBUG`-guarded. *Why:* catch accidental disk/network on the main thread and
  unclosed resources early. *Sketch:* in `SpartanApp.onCreate()`, guard with `if (BuildConfig.DEBUG)` →
  `StrictMode.setThreadPolicy(detectAll().penaltyLog())` + `setVmPolicy(detectLeakedClosableObjects()…)`.
  Debug-only; never in release.
- **Dependency vulnerability scanning** — **DONE.** `.github/dependabot.yml` (gradle weekly, grouped androidx/kotlin; actions monthly). `security-crypto` alpha pin note included; OWASP dependency-check evaluated and skipped (NVD API-key + runtime cost outweigh benefit with Dependabot active). *Why:* no automated CVE watch; `security-crypto` is on
  `1.1.0-alpha06` (an alpha in the crypto path). *Sketch:* enable Dependabot (`.github/dependabot.yml`,
  gradle ecosystem) for PR bumps, and add OWASP `dependency-check-gradle` (or `gradle-versions-plugin`) as a
  non-blocking CI job. Pin `security-crypto` to a stable release when one lands.
- **Play Integrity API — consideration only** — **P2 · S.** *Why:* worth a documented decision, not a
  default. *Sketch:* for a **local-first** app with no backend, Integrity/attestation adds little (nothing to
  protect server-side) and pulls in Google Play Services. **Recommendation: skip** until coach-mode/backend
  sync exists; revisit then to attest the client to the sync server.
- **Tamper / root awareness tradeoffs** — **P2 · S.** *Why:* be explicit rather than silently ignoring it.
  *Sketch:* document that Spartan does **not** do root detection — data is on-device, Keystore-backed, and a
  rooted-device block would harm legitimate power users for little gain. Reconsider only for a
  HIPAA-regulated deployment where a covered entity requires it.
- **ProGuard / R8 full mode** — **DONE.** `android.enableR8.fullMode=true` explicit in gradle.properties; keep rules verified by green `assembleRelease` (2.4 MB APK); CI archives `mapping.txt` per build. *Why:* R8 is on but "full mode" is not explicitly enabled and
  release minify is untested against Hilt/Room/AppAuth/kotlinx.serialization (release checklist §2.2 flags
  this). *Sketch:* set `android.enableR8.fullMode=true` in `gradle.properties`, verify keep rules in
  `proguard-rules.pro` for `@Serializable` classes (WHOOP/Calendar DTOs), AppAuth, and Room, then
  device-smoke the release APK. Keep `mapping.txt` per the checklist.

## 6. Performance & size

- **R8 full mode + keep-rule verification** — **DONE** (see §5; release builds green under full mode, size recorded in CI). *Why:* biggest size/perf lever already half-in
  place. *Sketch:* as in §5 (full mode) — measure APK/AAB delta before/after; ensure serialization DTOs
  aren't stripped.
- **Baseline profiles** — **P2 · M.** *Why:* cold-start on low-end `minSdk 26` devices. *Sketch:* see §1
  (baseline profile module). **Needs device.**
- **Startup tracing** — **P2 · S.** *Why:* find real cold-start cost before optimizing. *Sketch:*
  `androidx.tracing:tracing-perfetto` + Macrobenchmark `startupCompilationMode`; also audit `MainActivity`
  (`installSplashScreen` + `viewModel.seed()` in `LaunchedEffect`) so seeding doesn't block first frame.
- **APK/AAB size budget** — **DONE (budget met + monitored).** Release APK ≈2.4 MB (vector-only assets, R8 full mode strips unused `material-icons-extended` glyphs); CI records sizes every build against the informal ≤8 MB bar. *Why:* keep the download small; `material-icons-extended` alone is
  large. *Sketch:* set an informal budget (e.g. ≤8 MB AAB), replace `material-icons-extended` with only the
  glyphs used, and check the AAB with `bundletool build-apks --mode=default` per-density split sizes in CI.
- **Per-density asset audit** — **DONE.** All launcher/splash/notification assets are vectors; no raster densities ship; AAB density splits handle the rest. *Why:* adaptive icon + splash may ship unused raster densities.
  *Sketch:* audit `res/mipmap-*`/`drawable-*`; prefer vector (`VectorDrawable`) for the splash/icon
  foreground; let AAB density splits do the rest.

## 7. Observability without PHI

- **Play Vitals as the primary signal** — **DONE (process documented).** Cadence (daily×2 weeks→weekly), Google bad-behavior thresholds (crash >1.09%, ANR >0.47%), and per-versionCode `mapping.txt` archiving live in docs/RELEASE_CHECKLIST.md §7 + the CI mapping artifact. *Why:* by design there is **no** crash/analytics
  SDK, so Android Vitals is the only crash/ANR surface (release checklist §7). *Sketch:* document the daily
  check cadence for the first two weeks + thresholds (crash > 1.09%, ANR > 0.47%); archive `mapping.txt`
  with every `versionCode` to deobfuscate — no code change, purely operational.
- **Opt-in, privacy-preserving crash reporting (evaluate)** — **P2 · M.** *Why:* Vitals gives slower, less
  detailed signal than a crash SDK; an opt-in, no-PHI alternative could help without breaking the posture.
  *Sketch:* evaluate self-hosted **Sentry** or **ACRA** with a self-hosted endpoint, **strictly opt-in**,
  scrubbing all PHI (metric values, plan text, tokens) and sending only stack + device class + app version.
  Any such processor **needs a BAA** for a HIPAA-regulated deployment — default remains "none".
- **Debugging without analytics** — **DONE.** `DebugLog` in-memory ring buffer (200 events, never PHI, never persisted, no-op in release) + debug-only Diagnostics screen in Settings. *Why:* engineers still need to reason about issues with no
  telemetry. *Sketch:* a debug-only, on-device, ring-buffered event log (WorkManager runs, sync
  outcomes) viewable via a hidden debug screen — **never** PHI, **never** persisted to backup, stripped from
  release by `BuildConfig.DEBUG`.

## 8. Store growth

- **In-app review API** — **DONE.** `review-ktx`; prompt only on finishing the whole day's plan, ≥7 days after first open, ≤1 per 90 days, never in an error state; Play decides actual display. *Why:* prompt happy users at the right moment for organic ratings.
  *Sketch:* `com.google.android.play:review-ktx` `ReviewManager`; trigger after a positive signal (e.g. a
  multi-day consistency streak or a completed weekly review), rate-limited, never after an error state.
- **Deep links** — **DONE.** `spartan://today` + `spartan://connections` (manifest VIEW/BROWSABLE filters + `navDeepLink` routes, distinct from the AppAuth `spartan://oauth/*` hosts); reminder notifications and the widget now open the check-in route directly. *Why:* the manifest exposes only a `LAUNCHER` intent-filter; the notification
  `PendingIntent` opens `MainActivity` but there's no route-level deep linking (e.g. straight to a specific
  activity's check-in, or to Connections). *Sketch:* add `navDeepLink` destinations in `SpartanRoot`
  (`spartan://today`, `spartan://activity/{id}`, `spartan://connections`) and matching manifest
  `intent-filter`s; point the reminder `PendingIntent` at the per-activity route. Note the `spartan://oauth/*`
  scheme is already claimed by AppAuth — keep OAuth paths distinct.
- **Localized store listings** — **P2 · S.** *Why:* pairs with §4 i18n once real locales exist. *Sketch:*
  translate `PLAY_STORE_LISTING.md` per locale in Play Console **after** in-app strings are localized and
  re-checked against `SafetyEngine` constraints (no medical claims in any language).
- **A/B store-listing experiments** — **P2 · S.** *Why:* data-driven listing optimization. *Sketch:* Play
  Console **Store Listing Experiments** on icon/short-description/screenshots. Keep every variant within the
  same no-medical-claims, "sample data" honesty bar as the release checklist demands — do not test hype copy.

---

### Suggested near-term order
1. **P0:** CI workflow, Room migration tests (`exportSchema=true` + `MigrationTestHelper`), OkHttp
   `Authenticator` (done), Play Vitals runbook.
2. **P1:** boot/app-update reminder re-arm, daily-plan refresh worker, string extraction/i18n, a11y checks,
   cert pinning, R8 full-mode + keep rules, dependency scanning, in-app review + deep links.
3. **P2:** Health Connect, widget/Wear tile, trend charts, baseline profiles, size/asset audit, coach-mode
   architecture, crash-reporting evaluation, localized/A-B listings.

Device/emulator-dependent (cannot run on a headless JVM CI runner alone): Room migration tests, Compose
instrumentation + Espresso a11y, baseline profiles, startup tracing, on-device release-APK smoke tests.
Robolectric, Roborazzi/Paparazzi, Kover, and lint run JVM-only.
