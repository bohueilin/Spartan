# Spartan — Codebase Audit

**Document:** `docs/Spartan_Codebase_Audit.md`
**Status:** As-found audit of the existing repository, written to ground the Spartan build plan in real code.
**Sibling docs:** `docs/Spartan_PRD.md` · `docs/Spartan_Architecture.md` · `docs/Spartan_Implementation_Plan.md`
**Audited tree:** `app/src/**` at repo root `/Users/bohueilin/Documents/GitHub/Spartan`

> Canonical vocabulary: see docs/Spartan_Decisions.md (authoritative).

> Scope note: this audit describes the code **as it exists today**. Every version, class, and file path
> below was read directly from the repo. Where the target Spartan design differs from what is present,
> that difference is called out in the Gap Analysis (Section 8), not silently assumed. No production
> readiness, medical-device status, or compliance certification is claimed anywhere in this document.

---

## 1. Summary

The repository is a **native Android / Kotlin application** currently branded **"Vital Compass"** with the
application ID and package namespace **`com.vitalcompass`**. It is a **local-first lab-metric tracker**: the
user logs lab and body metrics (fasting glucose, lipids, resting heart rate, BMI, etc.), the app classifies
each value against clinical reference ranges and personal targets, generates safety-guarded insight cards,
produces a deterministic weekly fitness plan, records workout completion, computes a weekly review, and
fires local reminders — **entirely on-device, with no network, no accounts, and no cloud**.

Spartan is the evolution of this codebase into a **WHOOP-style daily coaching app**: pull recovery / sleep /
strain signals from WHOOP, read the user's Google Calendar, and coach a personalized **daily activity plan**
with a check-in loop, completion tracking, and smart reminders. The strategic decision is explicitly
**reuse-not-rewrite**: the domain-engine architecture, Room persistence, Hilt DI graph, Compose/Navigation
shell, the privacy posture, and especially the **`SafetyEngine`** are directly reusable and become the
trust spine of Spartan. The evolution is additive (new WHOOP + Calendar clients, a coaching engine, a daily-plan model, an
OAuth/network layer) plus a rebrand from `com.vitalcompass` → **`com.spartan`**.

The as-found baseline is **small, coherent, and green**: ~2,451 lines of production Kotlin across 26 source
files, 24 passing unit tests, and a debug APK that has previously built. It is a genuinely good foundation
to build on rather than fight.

---

## 2. Current app stack (exact versions)

All versions were read from `build.gradle.kts` (root), `app/build.gradle.kts`, and
`gradle/wrapper/gradle-wrapper.properties`. There is **no version catalog** (`gradle/libs.versions.toml`
does not exist); dependency coordinates are declared inline in `app/build.gradle.kts`.

| Concern | Choice | Exact version | Source file |
| --- | --- | --- | --- |
| Language | Kotlin | **2.0.21** | root `build.gradle.kts` |
| Android Gradle Plugin | AGP | **8.7.3** | root `build.gradle.kts` |
| Gradle wrapper | Gradle | **8.10.2** (`-bin`) | `gradle/wrapper/gradle-wrapper.properties` |
| KSP | Kotlin Symbol Processing | **2.0.21-1.0.27** | root `build.gradle.kts` |
| Compose compiler | `kotlin.plugin.compose` | **2.0.21** | root `build.gradle.kts` |
| UI toolkit | Jetpack Compose (BOM) | **2024.12.01** | `app/build.gradle.kts` |
| Design system | Material 3 (`material3`, via BOM) | BOM-managed | `app/build.gradle.kts` |
| Persistence | Room (`room-ktx`, `room-compiler`) | **2.6.1** | `app/build.gradle.kts` |
| Preferences | DataStore (`datastore-preferences`) | **1.1.1** | `app/build.gradle.kts` |
| DI | Hilt (`hilt-android`, `hilt-compiler`) | **2.52** | root + `app/build.gradle.kts` |
| Navigation | Navigation Compose | **2.8.5** | `app/build.gradle.kts` |
| Background work | WorkManager (`work-runtime-ktx`) | **2.10.0** | `app/build.gradle.kts` |
| Hilt ↔ Compose | `hilt-navigation-compose` | **1.2.0** | `app/build.gradle.kts` |
| Activity | `activity-compose` | **1.9.3** | `app/build.gradle.kts` |
| Lifecycle | `lifecycle-runtime-compose` / `-viewmodel-compose` | **2.8.7** | `app/build.gradle.kts` |
| Core | `core-ktx` | **1.15.0** | `app/build.gradle.kts` |
| Test | JUnit4 / `kotlinx-coroutines-test` | **4.13.2 / 1.9.0** | `app/build.gradle.kts` |

**SDK / toolchain (from `app/build.gradle.kts`):** `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`,
`sourceCompatibility`/`targetCompatibility = JavaVersion.VERSION_17`, Kotlin `jvmTarget = JVM_17`.
Application ID / namespace = `com.vitalcompass`, `versionCode = 1`, `versionName = "0.1.0"`.

---

## 3. Current folder structure (annotated)

```
Spartan/
├── build.gradle.kts                 # root plugin versions (AGP, Kotlin, KSP, Hilt, Compose)
├── settings.gradle.kts              # rootProject.name = "VitalCompass"; include(":app")
├── gradle.properties                # AndroidX on; daemon off; ksp.incremental=false; in-process compiler
├── local.properties                 # sdk.dir=/Users/bohueilin/android-sdk  (machine-specific, gitignored)
├── gradle/wrapper/…                 # Gradle 8.10.2
├── PRD.md · AGENTS.md · README.md   # legacy Vital Compass product/agent/setup docs
├── docs/                            # Spartan doc set (this audit + PRD/Architecture/Impl-Plan siblings)
└── app/
    ├── build.gradle.kts             # module deps (inline; no version catalog)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml  # POST_NOTIFICATIONS only; allowBackup=false; single Activity
        │   ├── java/com/vitalcompass/
        │   │   ├── MainActivity.kt          # @AndroidEntryPoint; sets Compose content; perms + share intent
        │   │   ├── VitalCompassApp.kt        # @HiltAndroidApp; creates notification channel on startup
        │   │   ├── di/
        │   │   │   └── AppModule.kt          # Hilt SingletonComponent: DB, DAO, prefs, WorkManager, 6 engines
        │   │   ├── data/
        │   │   │   ├── local/
        │   │   │   │   ├── AppDatabase.kt    # Room DB v3, 7 entities, MIGRATION_1_2 + MIGRATION_2_3
        │   │   │   │   ├── Dao.kt            # HealthDao — observe/insert/update/delete queries
        │   │   │   │   ├── Entities.kt       # 7 @Entity data classes + ReminderFrequency enum
        │   │   │   │   ├── Converters.kt     # enum↔String TypeConverters (MetricType/WorkoutType/Freq)
        │   │   │   │   └── PreferencesStore.kt  # DataStore: onboarding/notif-denied/demo-seed flags
        │   │   │   ├── repository/
        │   │   │   │   └── HealthRepository.kt  # single repo; entity↔domain mapping; seedIfEmpty()
        │   │   │   ├── reminder/
        │   │   │   │   ├── ReminderScheduler.kt # WorkManager periodic scheduling + permission gate
        │   │   │   │   └── ReminderWorker.kt    # CoroutineWorker: builds/posts the notification
        │   │   │   └── export/
        │   │   │       └── LocalExportFormatter.kt  # in-memory CSV-ish text snapshot builder
        │   │   ├── domain/
        │   │   │   ├── model/
        │   │   │   │   ├── MetricType.kt     # 19-value metric enum (label/unit/lowerIsBetter)
        │   │   │   │   └── HealthModels.kt   # domain data classes + status/confidence enums
        │   │   │   └── engine/
        │   │   │       ├── SafetyEngine.kt   # blocked-phrase validator (the trust spine)
        │   │   │       ├── MetricEngine.kt   # validation, BMI/TG-HDL/WHtR math, clinical classification
        │   │   │       ├── InsightEngine.kt  # safe insight-card generation
        │   │   │       ├── PlanEngine.kt     # deterministic weekly plan + adherence
        │   │   │       ├── ReviewEngine.kt   # weekly review summary
        │   │   │       └── ReminderEngine.kt # reminder dedup + schedulability rules
        │   │   └── ui/
        │   │       ├── MainActivity uses →
        │   │       ├── navigation/VitalCompassRoot.kt  # Scaffold + bottom tabs + NavHost (13 routes)
        │   │       ├── screens/
        │   │       │   ├── MainViewModel.kt  # @HiltViewModel; combines flows → MainUiState
        │   │       │   └── Screens.kt        # all 11 screen composables + shared UI pieces
        │   │       └── theme/Theme.kt        # Material3 light/dark ColorScheme
        │   └── res/
        │       ├── drawable/ic_notification.xml     # vector; also (mis)used as app icon
        │       ├── values/styles.xml                # Theme.VitalCompass (Material.Light.NoActionBar)
        │       └── xml/data_extraction_rules.xml    # excludes DB/prefs/datastore from backup & transfer
        └── test/java/com/vitalcompass/
            ├── domain/MetricEngineTest.kt           # 10 tests
            ├── domain/InsightAndSafetyTest.kt       #  3 tests
            ├── domain/PlanReviewReminderTest.kt     # 10 tests
            └── data/LocalExportFormatterTest.kt     #  1 test
```

Production Kotlin ≈ **2,451 LOC** across 26 files; unit tests ≈ **371 LOC** across 4 files.

---

## 4. Existing screens / components / services

### 4.1 Domain engines (the six)

| Engine | File | Responsibility |
| --- | --- | --- |
| **SafetyEngine** | `domain/engine/SafetyEngine.kt` | 6 regex **blocked-phrase patterns** ("you have diabetes", "your pancreas is overloaded", "you need medication/statin", "take … supplement dose", "ignore your doctor", "exercise through pain"). `validateCopy()` normalizes then checks; `sanitize()` throws `IllegalArgumentException` if copy is unsafe. Every other engine routes its user-facing copy through it. |
| **MetricEngine** | `domain/engine/MetricEngine.kt` | `bmi()`, `tgHdlRatio()`, `waistToHeightRatio()` math; per-type `validate()` range checks; `classifyClinical()` against a `clinicalRanges` map (10 types have ranges); `compareTarget()` for personal targets; `assess()` builds a `MetricAssessment` with safety-sanitized clinical + target messages. |
| **InsightEngine** | `domain/engine/InsightEngine.kt` | Maps assessments → `InsightCard`s for glucose, TG/HDL, vitamin D, RHR, BMI, and BP; each card carries explanation, why-it-matters, safe actions, clinician triggers, confidence. Every card's full text is re-validated through SafetyEngine before return. |
| **PlanEngine** | `domain/engine/PlanEngine.kt` | `defaultPlan()` builds a deterministic 8-slot week (Zone 2 / Strength / Mobility / Recovery / Review); adapts volume down on pain flag, high RPE (≥8), or adherence < 60%; `adherencePercent()` from planned vs. completed minutes. All copy safety-checked. |
| **ReviewEngine** | `domain/engine/ReviewEngine.kt` | `summarize()` computes a 7-day window: adherence, Zone 2 minutes, strength sessions, latest + 7-day-avg weight/RHR, latest paired BP, latest fasting glucose, and improvement / attention / next-focus lists. Depends on PlanEngine + SafetyEngine. |
| **ReminderEngine** | `domain/engine/ReminderEngine.kt` | Pure rules: `deduplicate()` (no duplicate reminder IDs; disabled requests removed) and `canSchedule()` (permission granted + enabled + valid hour/minute). |

### 4.2 Data layer

| Component | File | Responsibility |
| --- | --- | --- |
| **AppDatabase** | `data/local/AppDatabase.kt` | Room `@Database` version **3**, 7 entities, `exportSchema = false`, `Converters` registered, migrations 1→2 and 2→3. |
| **HealthDao** | `data/local/Dao.kt` | Flow-based observers + insert/update/delete for profile, metrics, targets, workouts, plan overrides, reminders; plus per-table `delete*()` used by wipe, and `metricCount()`. |
| **Entities** | `data/local/Entities.kt` | `UserProfileEntity`, `MetricEntryEntity`, `TargetEntity`, `WorkoutSessionEntity`, `PlanWorkoutOverrideEntity`, `ReminderEntity`, `WeeklyReviewEntity` + `ReminderFrequency` enum. |
| **Converters** | `data/local/Converters.kt` | `MetricType`/`WorkoutType`/`ReminderFrequency` ⇄ String. |
| **PreferencesStore** | `data/local/PreferencesStore.kt` | DataStore (`vital_compass_preferences`): `onboardingComplete`, `notificationPermissionDenied`, `demoSeedCompleted`; `clear()` for wipe. |
| **HealthRepository** | `data/repository/HealthRepository.kt` | The single repository. Exposes entity Flows + `metricReadings()`/`targetValues()`/`workoutLogs()` domain mappers; `addMetric`/`updateMetric`/`addWorkout`/`savePlanOverride`/`upsertReminder`; `deleteAllLocalData()`; `exportTextSnapshot()`; **`seedIfEmpty()`** (mock demo data). |
| **LocalExportFormatter** | `data/export/LocalExportFormatter.kt` | Object that renders profile/metrics/targets/workouts/overrides/reminders into a sectioned, RFC-4180-quoted CSV-style **text** snapshot (no file I/O; the string is shared via intent). |

### 4.3 Reminder service

| Component | File | Responsibility |
| --- | --- | --- |
| **ReminderScheduler** | `data/reminder/ReminderScheduler.kt` | `@Singleton`; enqueues a `PeriodicWorkRequest` (1-day period, initial delay to next HH:mm) via WorkManager `enqueueUniquePeriodicWork(UPDATE)`; gates on `hasNotificationPermission()` (auto-true below API 33); `cancel`/`cancelAll` by unique name / tag. |
| **ReminderWorker** | `data/reminder/ReminderWorker.kt` | `CoroutineWorker`; ensures the notification channel, honors a `daysOfWeekMask` (skips days not enabled), builds and posts the notification, swallows `SecurityException`. |

### 4.4 ViewModel + navigation + UI

| Component | File | Responsibility |
| --- | --- | --- |
| **MainViewModel** | `ui/screens/MainViewModel.kt` | Single `@HiltViewModel`. `combine`s profile/metrics/targets/workouts/reminders (+ plan overrides) into a `HealthBundle`, then folds in onboarding/notification prefs to emit `MainUiState` (latest-per-type readings, assessments, insights, weekly plan with overrides applied, review, export text). Exposes `seed`, `completeOnboarding`, `addMetric`, `updateMetric`, `completeWorkout`, `savePlanMinutes`, `saveReminder`, `deleteAllLocalData`. |
| **VitalCompassRoot** | `ui/navigation/VitalCompassRoot.kt` | Gates onboarding, then a `Scaffold` with a 5-item `NavigationBar` (**Today / Metrics / Plan / Review / Settings**) and a `NavHost` of **13 routes** (5 tabs + `detail/{type}`, `addMetric`, `editMetric/{id}`, `complete/{type}/{minutes}`, `reminders`, `privacy`). |
| **Theme** | `ui/theme/Theme.kt` | `VitalCompassTheme` — Material 3 light/dark `ColorScheme` (teal primary), no dynamic color. |

**The ~11 screen composables** (all in `ui/screens/Screens.kt`):

| # | Composable | Route | Responsibility |
| --- | --- | --- | --- |
| 1 | `OnboardingScreen` | (pre-nav gate) | Capture name + optional height; completes onboarding. |
| 2 | `TodayScreen` | `today` | Dashboard: summary tiles, per-metric rows, insight cards. |
| 3 | `MetricsScreen` | `metrics` | Full metric list; add / drill-in. |
| 4 | `MetricDetailScreen` | `detail/{type}` | Single-metric history, trend card, clinical/target status. |
| 5 | `AddMetricScreen` | `addMetric` / `editMetric/{id}` | Add **and** edit (same composable, `initialReading` param). |
| 6 | `PlanScreen` | `plan` | Weekly plan; edit per-slot minutes; launch completion. |
| 7 | `WorkoutCompletionScreen` | `complete/{type}/{minutes}` | Log completed minutes, RPE, pain flag. |
| 8 | `ReviewScreen` | `review` | Weekly review summary. |
| 9 | `SettingsScreen` | `settings` | Entry points to reminders / privacy. |
| 10 | `ReminderSettingsScreen` | `reminders` | Reminder editor (time, frequency, enable) + permission request. |
| 11 | `PrivacyScreen` | `privacy` | Export preview, share intent, delete-with-confirmation dialog. |

Shared UI helpers in the same file: `ScreenColumn`, `SummaryCard`, `MetricRow`, `StatusChips`,
`StatusBadge`, `InsightCardView`, `TrendCard`, `SettingsCard`, `ReminderEditor`.

### 4.5 Metric catalog (from `domain/model/MetricType.kt`)

19 enum values, each with `label`, `unit`, `lowerIsBetter`:
`FASTING_GLUCOSE`, `TRIGLYCERIDES`, `HDL_C`, `TG_HDL_RATIO`, `VITAMIN_D_25OH`, `RESTING_HEART_RATE`,
`SYSTOLIC_BP`, `DIASTOLIC_BP`, `WEIGHT`, `WAIST_CIRCUMFERENCE`, `BMI`, `WAIST_TO_HEIGHT_RATIO`, `APOB`,
`LPA`, `CAC`, `SLEEP_DURATION`, `EXERCISE_MINUTES`, `STRENGTH_SESSIONS`, `CUSTOM`.

> Note: the legacy `PRD.md` lists additional diagnostic types (`EKG_12_LEAD`, `CHEST_XRAY`,
> `DIAGNOSTIC_IMAGING`, `DIAGNOSTIC_NOTE`) that are **not** present in the shipped enum. The code is the
> source of truth for this audit; `docs/Spartan_PRD.md` reconciles the intended metric set for Spartan.

---

## 5. Build / run commands & baseline status

**Documented toolchain (from `README.md`) on this machine:**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
# Android SDK: local.properties → sdk.dir=/Users/bohueilin/android-sdk

./gradlew --no-daemon :app:assembleDebug        # build
./gradlew --no-daemon :app:testDebugUnitTest    # unit tests
./gradlew --no-daemon :app:lintDebug            # lint
```

`AGENTS.md`/`PRD.md` require these tasks be run **serially** for this Hilt/KSP project — parallel build +
test + lint can race on generated KSP files (`gradle.properties` sets `ksp.incremental=false` and an
in-process compiler for the same reason).

**Baseline status: GREEN (with one environment caveat).** The most recent captured run of the unit-test
task is present in the tree at `app/build/test-results/testDebugUnitTest/`, timestamped **2026-07-09 00:51**,
and reports **24 tests / 0 failures / 0 errors / 0 skipped**:

| Suite | Tests | Failures |
| --- | --- | --- |
| `MetricEngineTest` | 10 | 0 |
| `PlanReviewReminderTest` | 10 | 0 |
| `InsightAndSafetyTest` | 3 | 0 |
| `LocalExportFormatterTest` | 1 | 0 |
| **Total** | **24** | **0** |

A prior `app-debug.apk` (~18 MB) also exists under `app/build/outputs/apk/debug/`, confirming the module
assembles. In practice this baseline builds + tests in roughly **~20 s** warm.

**Caveat (honest):** at audit time the JDK referenced by `README.md`
(`/Applications/Android Studio.app/Contents/jbr/…`) is **not currently resolvable in this shell** — the
Android Studio bundled JBR path returns no `java` binary and `/usr/libexec/java_home` finds no runtime, so
`./gradlew` could not be re-executed live during this audit. The GREEN status above is therefore asserted
from the freshly captured test-results/APK, not from a live re-run. Restoring a JDK 17+ (reinstalling
Android Studio's JBR or pointing `JAVA_HOME` at any JDK 17) is a prerequisite before Spartan build work; see
`docs/Spartan_Implementation_Plan.md`.

---

## 6. Dependencies & notable configuration

**Runtime dependencies** (`app/build.gradle.kts`): Compose BOM 2024.12.01 (`material3`,
`material-icons-extended`, `ui`, `ui-tooling-preview`), `activity-compose`, `core-ktx`,
`datastore-preferences`, `hilt-navigation-compose`, `lifecycle-runtime-compose` +
`-viewmodel-compose`, `navigation-compose`, `room-ktx`, `work-runtime-ktx`, `hilt-android`.
**KSP processors:** `room-compiler`, `hilt-android-compiler`. **Debug-only:** `ui-tooling`,
`ui-test-manifest`. **Test:** `junit:4.13.2`, `kotlinx-coroutines-test:1.9.0`.

**Notable configuration:**

- **No-network posture.** `AndroidManifest.xml` declares exactly one permission — `POST_NOTIFICATIONS`.
  There is **no `INTERNET` permission**, no HTTP client (no OkHttp/Retrofit/Ktor), and a source-wide grep
  finds no `http(s)://`, no OAuth, no token storage. The app cannot make a network call today. `PrivacyScreen`
  states this to the user verbatim.
- **Backup / transfer exclusions.** `app/allowBackup="false"` and `fullBackupContent="false"`, plus
  `res/xml/data_extraction_rules.xml` **excludes** the Room `database`, `sharedpref`, and the DataStore
  `datastore/vital_compass_preferences.preferences_pb` file from both `cloud-backup` and `device-transfer`
  (`disableIfNoEncryptionCapabilities="true"`). Health data does not leave the device via Android backup.
- **Room migrations v1→v3.** `MIGRATION_1_2` adds `frequency` / `daysOfWeekMask` / `updatedAtMillis` columns
  to `reminders`; `MIGRATION_2_3` creates the `plan_workout_overrides` table. Both are registered in
  `AppModule.provideDatabase`. `exportSchema = false` (no schema JSON is checked in).
- **DI graph.** One `@Module` (`AppModule`, `SingletonComponent`) provides the DB, DAO, `PreferencesStore`,
  `WorkManager`, and all six engines (respecting their dependency edges: Insight/Plan ← Safety, Review ← Plan).
  `HealthRepository`, `ReminderScheduler`, `MainViewModel` are constructor-injected.
- **Seed / demo data (explicitly MOCK).** `HealthRepository.seedIfEmpty()` writes a fixed **mock** dataset
  (fasting glucose 108, TG 134, HDL 41, TG/HDL 3.26, vitamin D 23, RHR 68, weight 81.16, BMI 25.9, BP
  102/67, ApoB/Lp(a)/CAC pending) plus three personal targets. Every seeded row is labeled `"Seed demo data"`
  / `"Pending"`. This is sample data for demo/testing, **not** a real user record.

---

## 7. What already works (honest)

- Clean, buildable **native Android/Kotlin + Compose + Material 3** app; onboarding → 5-tab shell.
- **Room persistence** (7 entities, migrations v1→v3) with reactive `Flow` observers throughout.
- **DataStore** flags for onboarding, notification-denied, and demo-seed state.
- **Six deterministic domain engines** with clear seams and a genuinely enforced **SafetyEngine** guardrail
  that every user-facing string passes through (`sanitize()` throws on blocked copy).
- **Metric logging** with per-type validation, clinical-range classification, and **separate** personal-target
  comparison; derived math (BMI, TG/HDL, waist-to-height).
- **Insight cards** (glucose, TG/HDL, vitamin D, RHR, BMI, BP) that are non-diagnostic by construction.
- **Weekly plan** generation that adapts to pain / high RPE / low adherence, with per-slot minute overrides
  persisted; **workout completion** logging (minutes, RPE, pain).
- **Weekly review** over a 7-day window (adherence, Zone 2 minutes, strength sessions, trends).
- **Local reminders** via WorkManager with a permission gate, day-of-week masking, and dedup rules;
  Android 13+ `POST_NOTIFICATIONS` runtime request wired in `MainActivity`.
- **Privacy controls that work:** in-app export **preview**, user-directed share intent, and a
  confirm-then-**delete-all-local-data** flow that also cancels scheduled reminders and clears prefs.
- **24 passing unit tests** covering calculations, classification, safe-insight generation, blocked
  overclaiming, plan generation, adherence, weekly review, and reminder dedup.

---

## 8. Gap analysis — what is missing for the Spartan vision

Spartan = WHOOP-integrated **daily coaching** app. The table maps each Spartan requirement to what exists
today. "Present?" is judged against the as-found code, not intent.

| # | Spartan requirement | Present today? | Gap |
| --- | --- | --- | --- |
| 1 | **WHOOP integration** (recovery / sleep / strain / cycles / workout signals) | ❌ No | No client, no data model for WHOOP signals. Needs a `WhoopClient` seam with a **mock** default (`MockWhoopClient`; see Section 10) + new domain metrics (recovery %, HRV, sleep performance, day strain). |
| 2 | **Google Calendar** (schedule-aware coaching) | ❌ No | No Calendar client, no OAuth, no event model. Needs a read-only `CalendarClient` seam (stub default, `StubCalendarClient`) plus an `AvailabilityService` to place activities around real commitments. |
| 3 | **Coaching / daily-activity engine** | ⚠️ Partial | `PlanEngine` produces a fixed **weekly** plan; there is no per-day coaching that fuses WHOOP recovery + calendar + history. Needs the new `CoachingEngine` producing a **daily** `DailyPlan` (per `docs/Spartan_Architecture.md`), reusing PlanEngine's adaptation logic and SafetyEngine. |
| 4 | **Daily check-in UX** | ❌ No | No morning/evening check-in screen or flow. Needs a check-in composable + state (mood/energy/soreness/intent) feeding the coaching engine. |
| 5 | **Activity completion persistence** | ⚠️ Partial | `WorkoutSessionEntity` + `completeWorkout()` persist *workout* completion, but there is no per-**daily-activity** completion record keyed to a day/plan slot. Needs a `DailyActivityEntity` (`daily_activities` table) + Room migration (v3→v4, `MIGRATION_3_4`). |
| 6 | **Activity reminders + quiet hours** | ⚠️ Partial | Reminders exist (WorkManager, frequency, day mask) but there are **no quiet hours** (grep: none) and reminders are not tied to specific daily activities. Needs quiet-hours modeling + per-activity reminder wiring. |
| 7 | **Network / OAuth layer** | ❌ No | No `INTERNET` permission, no HTTP client, no OAuth. Needs an HTTP stack, an auth/OAuth module (PKCE), and the `INTERNET` permission — a deliberate, guarded departure from the current no-network posture. |
| 8 | **Secure token storage** | ❌ No | No token store at all. Needs encrypted storage (e.g., EncryptedSharedPreferences / DataStore + Keystore-backed key) for WHOOP/Google refresh + access tokens. |
| 9 | **Consent flows** | ⚠️ Partial | Privacy copy + delete/export exist, but there is no explicit consent gate for connecting external accounts or sending health data off-device. Needs per-integration consent screens before any network egress. |
| 10 | **HIPAA-ready network posture** | ❌ No | Not applicable to the current on-device app; once network is added it must be engineered toward a HIPAA-*ready* posture (TLS pinning, minimal-scope tokens, encryption at rest, audit-ready logging, no third-party analytics). **This is an engineering target, not a certification** — Spartan makes no HIPAA-compliance or medical-device claims. |
| 11 | **`.env` handling** | ❌ No | No secrets management. Needs a **`.env.example`** template (WHOOP + Google client IDs/secrets/redirect) surfaced via `BuildConfig`/Gradle, with real `.env`/`.local` files git-ignored and never committed. |
| 12 | **Branding (Vital Compass → Spartan)** | ❌ No | Namespace `com.vitalcompass`, `rootProject.name = "VitalCompass"`, `Theme.VitalCompass`, `label="Vital Compass"`, DataStore/DB file names, and a placeholder notification vector reused as the launcher icon. Needs a full rebrand to **`com.spartan`** / **Spartan** plus a real app icon. |

**Summary:** the biggest additive surfaces are (a) the network/OAuth/token stack and its consent + secure-
storage prerequisites, (b) the WHOOP + Calendar clients and their domain models, and (c) the daily coaching
engine + check-in + daily-completion loop. Everything reuses the existing repository, DI, Compose shell, and
SafetyEngine. Full detail lives in `docs/Spartan_PRD.md` and `docs/Spartan_Implementation_Plan.md`.

---

## 9. Key technical risks

| Risk | Why it matters | Mitigation posture |
| --- | --- | --- |
| **Hilt / KSP build fragility** | Hilt 2.52 + Room over KSP 2.0.21-1.0.27 generate interdependent code; the project already forces `ksp.incremental=false`, in-process compiler, and **serial** build/test/lint to avoid races. New `@Module`s / injected adapters can reintroduce annotation-processing races or DI cycles. | Add DI in small increments; run tasks serially; keep engines/adapters constructor-injected with mock defaults so the graph stays resolvable. |
| **DB migration** | Spartan's v3→v4 migration adds the `daily_activities` and `integration_connections` tables (WHOOP readings reuse the existing `metric_entries` rows; tokens are never stored in Room). A missing/incorrect `Migration` is a runtime crash on upgrade, and `exportSchema=false` means no checked-in schema to diff against. | One migration per change, tested; consider enabling `exportSchema` for Spartan to gain schema history. |
| **Adding network to a formerly local-only app** | The entire trust story ("no network calls") is currently enforced by *absence*. Introducing `INTERNET` + HTTP silently widens the attack/egress surface and can leak health data if unguarded. | Gate all egress behind explicit consent; keep WHOOP/Calendar data cached locally; no analytics/telemetry; keep the privacy copy truthful. |
| **Secure token storage** | OAuth refresh tokens are long-lived credentials to a user's health + calendar data. Plaintext storage or a leaked token is a serious incident. | Keystore-backed encryption at rest; short-lived access tokens; scoped/minimal permissions; wipe tokens on "delete local data." |
| **WHOOP API access / credentials** | Depends on approved WHOOP developer credentials, specific OAuth scopes, and rate limits outside our control; access can lag development. | Build against a **mock `WhoopClient`** (`MockWhoopClient`) by default so the app is fully buildable/demoable without live credentials; swap to the live client (`RealWhoopClient`) behind the same seam. |
| **Notification permission on Android 13+** | `POST_NOTIFICATIONS` is a runtime permission (API 33+); denial must degrade gracefully. Current code handles the workout-reminder case; new activity reminders must not assume grant. | Reuse the existing permission gate (`ReminderScheduler.hasNotificationPermission`) and denied-state persistence for all Spartan reminders. |
| **Scope creep** | Spartan adds many surfaces at once (WHOOP, Calendar, coaching, check-in, OAuth, consent, rebrand). Doing them big-bang risks a long red build. | Strict incremental, buildable-after-each-change sequencing with client seams + mock/stub defaults (Section 10); follow the phased order in `docs/Spartan_Implementation_Plan.md`. |

---

## 10. Recommended implementation approach

**Principle: reuse-not-rewrite, incremental, green after every step.** Keep the app buildable and the 24
existing tests passing at each change; add tests alongside each new engine/client. The sequence follows the
phased plan in `docs/Spartan_Implementation_Plan.md`: **step 1 is Phase 0** (rebrand + docs), **steps 2–6 are
Phase 1** (the mock-backed MVP foundation), and **step 7 is Phase 2** (real WHOOP OAuth + real Calendar
free/busy + encrypted tokens).

1. **Rebrand first, behavior-frozen (Phase 0).** Rename `com.vitalcompass` → **`com.spartan`**, `rootProject.name`,
   theme, `android:label`, DataStore/DB file names, and add a real app icon — as a mechanical, test-green
   change before any feature work. (Gap #12.)
2. **Client seams with mock/stub defaults.** Introduce `WhoopClient` and `CalendarClient` as **interfaces**
   provided through `AppModule`, with the **mock/stub implementations as the default binding** — `MockWhoopClient`
   and `StubCalendarClient` (clearly labeled mock data, in the spirit of the existing `seedIfEmpty()`). The app
   stays fully buildable and demoable with **no network and no credentials**; the live clients (`RealWhoopClient`,
   `GoogleCalendarClient`) are drop-in replacements behind the same interface. (Gaps #1, #2, #7.)
3. **Reuse `SafetyEngine` unchanged** as the trust spine. Every new coaching string — daily plan copy,
   check-in prompts, WHOOP-derived nudges — must route through `sanitize()`, exactly as the existing engines
   do. This is the single most valuable asset carried forward.
4. **`CoachingEngine` on top of `PlanEngine`.** Add the `CoachingEngine` (per `docs/Spartan_Architecture.md`)
   that consumes WHOOP recovery (a `ReadinessSnapshot`) + calendar availability (via `AvailabilityService`) +
   history and emits a **daily** `DailyPlan`, reusing PlanEngine's pain/RPE/adherence adaptation and producing
   safety-checked output. Persist a new `DailyActivityEntity` (`daily_activities`) with the v3→v4 Room migration
   (`MIGRATION_3_4`). (Gaps #3, #5.)
5. **Daily check-in + completion UX** as new composables inside the existing Compose/Navigation shell and the
   single `MainViewModel`/repository pattern — no architectural change. (Gaps #4, #5.)
6. **Reminders: extend, don't replace.** Add quiet-hours modeling and per-activity reminders on top of the
   existing `ReminderScheduler`/`ReminderWorker`/`ReminderEngine`, reusing the Android 13+ permission gate.
   (Gap #6.)
7. **Network/OAuth/secure storage as a guarded, late phase (Phase 2).** Only when the mock-backed app is complete: add
   `INTERNET`, an HTTP stack, OAuth (PKCE), **encrypted** token storage, and **per-integration consent gates**
   before any egress. Engineer toward a **HIPAA-ready** posture (TLS pinning, minimal scopes, encryption at
   rest, no analytics) — explicitly a target, **not** a certification or medical-device claim. Add
   **`.env.example`** and keep real secrets git-ignored. (Gaps #7, #8, #9, #10, #11.)
8. **Preserve the privacy contract.** Keep backup/transfer exclusions; extend "delete local data" to wipe
   cached WHOOP/Calendar data and tokens; keep the on-screen privacy copy truthful as network is introduced.

**Cross-references.** Product requirements and the Spartan metric/scope set: `docs/Spartan_PRD.md`.
Target module boundaries, adapter/engine names, and OAuth scopes: `docs/Spartan_Architecture.md`.
Phase-by-phase, buildable sequencing (including the JDK-restore prerequisite from Section 5):
`docs/Spartan_Implementation_Plan.md`.

---

*End of `docs/Spartan_Codebase_Audit.md`. This audit reflects the repository as read on 2026-07-09; it
asserts no production readiness and no compliance certification, and labels all seed/demo data as mock.*
