# Spartan — Implementation Plan

> Canonical vocabulary: see [`docs/Spartan_Decisions.md`](Spartan_Decisions.md) (authoritative).
>
> **Status:** Planning / build blueprint. This document is the execution plan that turns the as-found
> `com.vitalcompass` scaffold into **Spartan** (`com.spartan`) with a WHOOP-driven coaching layer,
> a daily check-in loop, and Google Calendar availability.
>
> **Read alongside:** [`Spartan_PRD.md`](Spartan_PRD.md) (what & why),
> [`Spartan_Architecture.md`](Spartan_Architecture.md) (layers & data flow),
> [`Spartan_Codebase_Audit.md`](Spartan_Codebase_Audit.md) (what actually exists today).
> Terminology, engine/adapter names, metric list, OAuth scopes, and phase numbering are held identical
> across all four documents, per `Spartan_Decisions.md`.

---

## 0. Ground truth (as-found)

The current tree is a single WIP commit (`42b2de7 WIP scaffold Vital Compass Android project from Codex`).
Everything below is verified against the real files, not assumed.

| Fact | Value (as-found) | Source file |
| --- | --- | --- |
| App name | Vital Compass | `AndroidManifest.xml` (`android:label`), `Screens.kt:81` |
| `namespace` / `applicationId` | `com.vitalcompass` | `app/build.gradle.kts:10,14` |
| `rootProject.name` | `VitalCompass` | `settings.gradle.kts` |
| Module | `:app` (single module) | `settings.gradle.kts` |
| Gradle | 8.10.2 | `gradle/wrapper/gradle-wrapper.properties` |
| AGP / Kotlin / KSP / Hilt | 8.7.3 / 2.0.21 / 2.0.21-1.0.27 / 2.52 | root `build.gradle.kts` |
| compileSdk / minSdk / targetSdk / JDK | 35 / 26 / 35 / 17 | `app/build.gradle.kts` |
| Compose BOM | 2024.12.01 | `app/build.gradle.kts:36` |
| Room / WorkManager / DataStore / Nav | 2.6.1 / 2.10.0 / 1.1.1 / 2.8.5 | `app/build.gradle.kts` |
| Network permission | **none** — only `POST_NOTIFICATIONS` | `AndroidManifest.xml:2` |
| Room DB | `AppDatabase` **version 3**, migrations `1→2`, `2→3`, 7 entities, `"vital_compass.db"` | `data/local/AppDatabase.kt`, `di/AppModule.kt:28` |
| DataStore | `"vital_compass_preferences"` | `data/local/PreferencesStore.kt:10` |
| Domain engines | `MetricEngine`, `InsightEngine`, `PlanEngine`, `ReminderEngine`, `ReviewEngine`, `SafetyEngine` — all deterministic, offline | `domain/engine/*` |
| `MetricType` values | 19 (`FASTING_GLUCOSE`…`CUSTOM`) | `domain/model/MetricType.kt` |
| Worker | `ReminderWorker` is a plain `CoroutineWorker`, no Hilt injection, default `WorkerFactory` | `data/reminder/ReminderWorker.kt` |
| Unit tests | 4 files in `src/test`; **no** `src/androidTest` | `app/src/test/**` |
| Resource strings | hardcoded in Compose; **no** `res/values/strings.xml` | `res/values/` (only `styles.xml`) |
| Brand token occurrences | `vitalcompass` ×169, `VitalCompass` ×12, `Vital Compass` ×12, `vital_compass` ×6 across 36 files | repo grep |

**Privacy inflection point.** `AGENTS.md` and `PRD.md` forbid network calls, external health APIs, secrets,
and the `INTERNET` permission *"unless the user explicitly requests that scope."* The Spartan brief **is**
that explicit request: WHOOP and Google Calendar are first-party integrations. This plan therefore keeps
**Phase 1 fully offline (mock)** and introduces the first real network egress only in **Phase 2**, gated by
consent and the security checklist. Every sample dataset in Phase 1 is labeled **MOCK**. No credentials,
real or fake, appear in the repo.

---

## 1. Phased implementation roadmap

Phase numbering matches `Spartan_Decisions.md` (D14) and, through it, the PRD and Architecture docs.

### Phase 0 — Rebrand + docs

**Goals.** Rename `com.vitalcompass` → `com.spartan` and `Vital Compass` → `Spartan` with zero behavior
change; land the four planning docs. Nothing new is built.

**Deliverables.**
- Full package/identifier rename (see §2, the "Renamed" rows) — directory move, `package`/`import`
  rewrites, class renames (`VitalCompassApp`→`SpartanApp`, `VitalCompassRoot`→`SpartanRoot`,
  `VitalCompassTheme`→`SpartanTheme`), `Theme.VitalCompass`→`Theme.Spartan`.
- `namespace`, `applicationId`, `rootProject.name`, `android:label`, `android:name` updated.
- `docs/Spartan_PRD.md`, `docs/Spartan_Architecture.md`, `docs/Spartan_Implementation_Plan.md` (this file),
  `docs/Spartan_Codebase_Audit.md`.
- No net-new dependencies, no schema change (DB stays v3).

**Exit criteria.**
- `grep -rn "vitalcompass\|VitalCompass\|Vital Compass" app/src` returns **0** (channel ids / db name handled per §2 notes).
- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` all green (all 4 existing test files still pass unchanged in behavior after import rewrite).
- App launches, onboarding → 5 tabs reachable, seed demo data visible.

### Phase 1 — MVP foundation (Spartan coaching, mock-backed)

**Goals.** Stand up the Spartan value loop end-to-end **without any network**: a WHOOP **mock** adapter
feeds a new **`CoachingEngine`**, which produces a **`DailyPlan`** (a bounded `List<DailyActivity>`, default
2–4); a **daily check-in** screen records each activity's **`ActivityStatus`** to Room; a **calendar stub**
supplies availability; consent + security scaffolding and `.env.example` exist but no real credentials are wired.

**Deliverables.**
- `MetricType` extended with the 7 WHOOP metrics (§2); `MetricEngine.validate` branches added (exhaustive `when`).
- `data/whoop/*`: `WhoopClient` interface, `@Serializable` DTOs, `WhoopMapper`, **`MockWhoopClient`**
  (loads `assets/mock/whoop_sample.json`, a labeled multi-day sample) as the DI default.
- `domain/model/CoachingModels.kt` + `domain/engine/CoachingEngine.kt` (with the `RecommendationSource` interface
  and default `RuleBasedRecommendationSource`) (+ `CoachingEngineTest`).
- `data/calendar/*`: `CalendarClient` interface, `AvailabilityService`, **`StubCalendarClient`** (mock busy blocks) as the DI default.
- `data/security/SecureTokenStore.kt` interface + `InMemoryTokenStore` (no persistence, safe default).
- Room: `DailyActivityEntity` + `IntegrationConnectionEntity`, DAO methods, `MIGRATION_3_4`, `AppDatabase` → **v4**.
- `HealthRepository` + `MainViewModel` additions; `ui/screens/DailyCheckInScreen.kt`; Today reworked around the `DailyPlan`; consent screen; navigation wiring.
- `ReminderScheduler`/`ReminderWorker` extended with a daily check-in reminder id.
- `res/values/strings.xml` (extract `app_name` = "Spartan"); `Theme.Spartan`.
- `.env.example` + `docs/SECURITY_PRIVACY_CHECKLIST.md`.
- `BuildConfig` flag `USE_MOCK_WHOOP = true`, `USE_MOCK_CALENDAR = true`.

**Exit criteria.**
- New unit tests pass: `CoachingEngine` (per-scenario), `WhoopMapper`, `AvailabilityService`, plus the existing suite.
- One instrumentation test proves a daily activity's `ActivityStatus` change (e.g. `PLANNED` → `DONE`) **persists** across process restart.
- App still makes **zero network calls** (verify: no `INTERNET` permission yet; no Retrofit on classpath yet).
- Today shows a `DailyPlan` sourced from `MockWhoopClient`; updating an activity's status updates adherence/review.
- All generated coaching copy passes `SafetyEngine.sanitize` (enforced in code + covered by test).

### Phase 2 — Real WHOOP OAuth + real Calendar free/busy + encrypted tokens

**Goals.** Replace mock adapters with live ones behind the same interfaces; introduce the first real network
egress under consent; persist tokens with `EncryptedSharedPreferences`.

**Deliverables.**
- `INTERNET` permission + `res/xml/network_security_config.xml` (cleartext disabled, host-scoped).
- Dependencies: Retrofit + OkHttp logging, `androidx.security:security-crypto`, `net.openid:appauth` (§3).
- `WhoopApi` (Retrofit) + `RealWhoopClient` (real `WhoopClient`) + `WhoopAuthManager` (auth-code + PKCE, `offline` scope for refresh).
- `GoogleCalendarClient` (real `CalendarClient`) hitting Calendar **free/busy**.
- `EncryptedTokenStore` (real `SecureTokenStore`), `MasterKey` AES256-GCM.
- `WhoopSyncService` (`@HiltWorker`, needs `hilt-work` + custom `WorkerFactory`) for periodic pull.
- Consent screens become functional connect/disconnect; `BuildConfig.USE_MOCK_* = false` for real builds; credentials flow from `local.properties` → `BuildConfig` (§4).

**Exit criteria.**
- With real WHOOP/Google client ids in `local.properties`, OAuth completes, tokens land encrypted, real recovery + free/busy render.
- With `USE_MOCK_* = true` (default in CI/dev without creds) the app behaves exactly as Phase 1.
- Token store never logs secrets; `network_security_config` blocks cleartext; `SECURITY_PRIVACY_CHECKLIST.md` items ticked.

### Phase 3+ — Platform expansion

**Goals (not scheduled here; gated).** iOS client, coach-facing dashboard, an AI coaching layer over the
deterministic `CoachingEngine`, remote feature flags, and audit logging of consent/sync events. Each is
opt-in and must preserve the safety spine (`SafetyEngine`) and the mock-default posture.

**Exit criteria.** Defined per sub-project when scheduled; out of scope for this plan beyond the interface
seams (`WhoopClient`, `CalendarClient`, `SecureTokenStore`, `CoachingEngine`) that make them possible.

---

## 2. File-by-file change plan

`New` = create, `Modified` = edit in place, `Renamed` = moved as part of the `com.vitalcompass`→`com.spartan`
rename (Phase 0) and possibly further edited.

### 2.1 Build, manifest, settings

| File | Action | Change |
| --- | --- | --- |
| `settings.gradle.kts` | Modified | `rootProject.name = "Spartan"`. |
| `build.gradle.kts` (root) | Modified | Add plugin `id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false` (P1). |
| `app/build.gradle.kts` | Modified | `namespace`/`applicationId` → `com.spartan` (P0). Apply `kotlin-plugin.serialization` (P1). `buildFeatures { buildConfig = true }`. Add `BuildConfig` fields `USE_MOCK_WHOOP`, `USE_MOCK_CALENDAR` (P1) and `WHOOP_CLIENT_ID/SECRET/REDIRECT_URI`, `GOOGLE_OAUTH_CLIENT_ID/REDIRECT_URI` read from `local.properties` (P2, §4). Add deps per §3, tagged P1 vs P2. Add `manifestPlaceholders` for the appauth redirect scheme (P2). |
| `app/src/main/AndroidManifest.xml` | Modified | `android:label="@string/app_name"`, `android:name=".SpartanApp"`, `android:theme="@style/Theme.Spartan"` (P0). Add `<uses-permission android:name="android.permission.INTERNET" />` and `android:networkSecurityConfig="@xml/network_security_config"` (P2). Add appauth `RedirectUriReceiverActivity` intent-filter (P2). |
| `gradle/wrapper/…`, `gradlew` | unchanged | Wrapper 8.10.2 stays. |
| `local.properties` | Modified (gitignored) | Dev-only: add real WHOOP/Google client ids for P2. **Never committed** (already in `.gitignore`). |
| `.env.example` | New | Documented, non-secret template of the five keys (§4). |

### 2.2 Package rename set (Phase 0 — all `Renamed`)

Move `app/src/main/java/com/vitalcompass/**` → `app/src/main/java/com/spartan/**` and
`app/src/test/java/com/vitalcompass/**` → `app/src/test/java/com/spartan/**`; rewrite every `package` and
`import` from `com.vitalcompass` to `com.spartan`. Class/string renames called out per file.

| File (new path under `com/spartan/`) | Action | Rename notes |
| --- | --- | --- |
| `VitalCompassApp.kt` → `SpartanApp.kt` | Renamed | Class `VitalCompassApp`→`SpartanApp`; matches `android:name`. |
| `MainActivity.kt` | Renamed | `VitalCompassTheme`→`SpartanTheme`, `VitalCompassRoot`→`SpartanRoot`, export subject string "Vital Compass local export"→"Spartan local export". |
| `ui/navigation/VitalCompassRoot.kt` → `SpartanRoot.kt` | Renamed | Fun `VitalCompassRoot`→`SpartanRoot`. |
| `ui/theme/Theme.kt` | Renamed | Fun `VitalCompassTheme`→`SpartanTheme`. |
| `ui/screens/Screens.kt` | Renamed | Onboarding title `"Vital Compass"`→`"Spartan"` (line 81). |
| `ui/screens/MainViewModel.kt` | Renamed | package/imports only. |
| `di/AppModule.kt` | Renamed | DB filename `"vital_compass.db"`→`"spartan.db"` **or keep**; see note ▼. |
| `data/local/AppDatabase.kt`, `Converters.kt`, `Dao.kt`, `Entities.kt` | Renamed | package/imports. |
| `data/local/PreferencesStore.kt` | Renamed | DataStore name `"vital_compass_preferences"`→`"spartan_preferences"`; must match `data_extraction_rules.xml`. |
| `data/repository/HealthRepository.kt` | Renamed | Export header strings `"Vital Compass export…"`→`"Spartan export…"`. |
| `data/reminder/ReminderScheduler.kt` | Renamed | `REMINDER_TAG "vital_compass_reminders"`→`"spartan_reminders"`. |
| `data/reminder/ReminderWorker.kt` | Renamed | `import com.vitalcompass.R`→`com.spartan.R`; `CHANNEL_ID`, channel name, default title/body strings → Spartan. |
| `data/export/LocalExportFormatter.kt` | Renamed | Header `"Vital Compass Local Export"`→`"Spartan Local Export"`. |
| `domain/engine/*.kt` (6 files) | Renamed | package/imports only; **`SafetyEngine` copy rules unchanged**. |
| `domain/model/HealthModels.kt` | Renamed | package/imports only. |
| `app/src/test/java/com/spartan/**` (4 test files) | Renamed | package/imports; assertion strings referencing brand updated to match new copy. |
| `res/values/styles.xml` | Modified | `Theme.VitalCompass`→`Theme.Spartan`. |
| `res/xml/data_extraction_rules.xml` | Modified | `datastore/vital_compass_preferences.preferences_pb`→`spartan_preferences.preferences_pb`. |

> **DB/DataStore rename note.** Renaming `spartan.db` / `spartan_preferences` drops any *existing* local data
> for a dev who upgrades in place. Because Spartan is pre-release (`versionCode 1`, no distributed installs),
> this is acceptable and cleaner than a rename-migration; document "uninstall/reinstall on dev devices."
> The safe fallback (keep the physical filenames, rename only user-visible strings) is the applicationId-only
> rollback in §9.

### 2.3 New — domain & coaching

| File | Action | What it holds |
| --- | --- | --- |
| `domain/model/MetricType.kt` | Modified | Append the 7 WHOOP metrics (per Decisions §4): `RECOVERY_SCORE` ("Recovery","%"), `HRV_RMSSD` ("HRV","ms"), `SLEEP_PERFORMANCE` ("Sleep performance","%"), `SLEEP_DEBT` ("Sleep debt","h", lowerIsBetter), `RESPIRATORY_RATE` ("Respiratory rate","rpm"), `DAY_STRAIN` ("Day strain",""), `ENERGY_KCAL` ("Energy","kcal"). Reuse existing `RESTING_HEART_RATE`, `SLEEP_DURATION`. No `SPO2`, `SKIN_TEMP`, or `READINESS` MetricType in the MVP (documented as future). |
| `domain/engine/MetricEngine.kt` | Modified | Add `validate` ranges for the 7 new types (the `when(type)` is **exhaustive** — build breaks until branches exist): RECOVERY_SCORE 0–100, HRV_RMSSD 5–300, SLEEP_PERFORMANCE 0–100, SLEEP_DEBT 0–24, RESPIRATORY_RATE 5–40, DAY_STRAIN 0–21, ENERGY_KCAL 0–10000. Intentionally **do not** add them to `clinicalRanges`, so `classifyClinical` returns `UNKNOWN` and no clinical over-claim is emitted for wearable metrics (a safety property, see PRD safety boundaries). |
| `domain/model/CoachingModels.kt` | New | Per Decisions §3–4: `enum ReadinessBand { PRIMED, BALANCED, EASY, REST }` (from recovery score: `>=67 → PRIMED`, `50–66 → BALANCED`, `34–49 → EASY`, `<=33 → REST`; null recovery → `BALANCED` + `isStale`); `data class WhoopSnapshot(dateEpochDay, recoveryScore, hrvMs, restingHeartRate, sleepPerformance, sleepDurationHours, sleepDebtHours, respiratoryRate, dayStrain, energyKcal, isMock)`; `data class ReadinessSnapshot(recoveryScore, hrvMs, hrvVsBaseline, restingHeartRate, rhrVsBaseline, sleepPerformance, sleepDebtHours, dayStrainPrior, respiratoryRate, band: ReadinessBand, trendNotes, isStale, isMock)` with `ReadinessSnapshot.from(today, history)`; `data class DailyActivity(id, title, category: ActivityCategory, priority: ActivityPriority, whyItMatters, relatedMetric: MetricType?, instructions, estimatedMinutes, intensity: Intensity, bestTimeOfDay: TimeOfDay, status: ActivityStatus, ruleId, scheduledEpochMinute, completedAtMillis, snoozedUntilMillis, safetyNote)`; `data class DailyPlan(dateEpochDay, headline, band: ReadinessBand, activities: List<DailyActivity>, totalEstimatedMinutes, safetyBanner, isMock)`; enums `ActivityCategory`, `ActivityPriority`, `ActivityStatus`, `Intensity`, `TimeOfDay` (Decisions §4). |
| `domain/engine/CoachingEngine.kt` | New | Pure, deterministic. `buildPlan(readiness: ReadinessSnapshot, options): DailyPlan` — emits a bounded `List<DailyActivity>` (default 2–4). Delegates to a pluggable `RecommendationSource` interface (default `RuleBasedRecommendationSource`, driven by `CoachingRule`s) so an AI source can be added later without the MVP depending on it. Rules: pain-recent or **REST** band → `RECOVERY`/`MOBILITY` activities, low minutes, gentle copy; **EASY** → `ZONE2` moderate, minutes capped; **PRIMED** + ample availability → `STRENGTH`/progression, still "leave reps in reserve"; **BALANCED** → mixed default; per-activity `estimatedMinutes` is clamped to the availability that `AvailabilityService` reports; `readiness.isStale` (null recovery) → `PlanEngine`-backed fallback plan tagged `STALE_DATA_FALLBACK`, "connect WHOOP for personalized guidance". Every output string passed through injected `SafetyEngine.sanitize` (constructor default `SafetyEngine()`, mirroring the other engines). |

### 2.4 New — WHOOP adapter (`data/whoop/`)

| File | Action | What it holds | Phase |
| --- | --- | --- | --- |
| `WhoopClient.kt` | New | `interface WhoopClient { suspend fun isAuthorized(): Boolean; suspend fun latestSnapshot(): WhoopSnapshot?; suspend fun snapshotHistory(days: Int): List<WhoopSnapshot> }`. | P1 |
| `WhoopDtos.kt` | New | `@Serializable` DTOs mirroring WHOOP v2 REST (`RecoveryDto`, `CycleDto`, `SleepDto`, `ScoreDto`) — the same types parse the mock JSON and the real API. | P1 |
| `WhoopMapper.kt` | New | DTO → `WhoopSnapshot` and → `metric_entries` rows (`MetricReading`: recovery %, HRV ms, RHR, sleep performance, day strain, respiratory rate, energy). Null-safe; skips unscored cycles; orders by date. Pure/testable. | P1 |
| `MockWhoopClient.kt` | New | **Default** `WhoopClient`. Loads `assets/mock/whoop_sample.json` — a **labeled multi-day MOCK** dataset (~14 days of recovery/strain/sleep) — via `WhoopMapper`. `isAuthorized()` returns `true` so the loop runs offline. Every `WhoopSnapshot` carries `isMock = true`. | P1 |
| `assets/mock/whoop_sample.json` | New | Static sample. First object carries `"_note": "MOCK SAMPLE DATA — not real WHOOP data"`. No PII, no tokens. | P1 |
| `WhoopApi.kt` | New | Retrofit interface (`GET /v2/recovery`, `/v2/cycle`, `/v2/activity/sleep`). TODO stubs until P2. | P2 |
| `RealWhoopClient.kt` | New | Real `WhoopClient` (Decisions §D6) over Retrofit + `SecureTokenStore`; refreshes with `offline`-scoped refresh token. | P2 |
| `WhoopAuthManager.kt` | New | appauth auth-code + PKCE against WHOOP OAuth; scopes in §5. | P2 |

### 2.5 New — Calendar adapter (`data/calendar/`)

| File | Action | What it holds | Phase |
| --- | --- | --- | --- |
| `CalendarClient.kt` | New | As built (Decisions §7a): `interface CalendarClient { val isStub: Boolean; suspend fun freeBusy(startEpochMinute: Long, endEpochMinute: Long): List<TimeWindow>; suspend fun createEvent(title, startEpochMinute, durationMinutes): Result<String> }`. Busy blocks and free windows both use the canonical `TimeWindow` — no separate `BusyBlock` type. | P1 |
| `AvailabilityService.kt` | New | Pure (Decisions §7a/§D8): `openWindows(dayStartEpochMinute, dayEndEpochMinute, busy, minWindowMinutes): List<TimeWindow>` — the free gaps after subtracting (merged, clamped) busy blocks — and `suggestSlot(activityMinutes, dayStartEpochMinute, dayEndEpochMinute, busy): TimeWindow?` — the earliest gap that fits, or `null`. Testable without Android. | P1 |
| `StubCalendarClient.kt` | New | **Default** `CalendarClient` (`isStub = true`). Returns **MOCK** busy `TimeWindow`s (e.g., 09:00–10:00, 14:00–15:30). | P1 |
| `GoogleCalendarClient.kt` | New | Real `CalendarClient` hitting Calendar **free/busy**; TODO until P2. | P2 |

### 2.6 New — Security (`data/security/`)

| File | Action | What it holds | Phase |
| --- | --- | --- | --- |
| `SecureTokenStore.kt` | New | `interface SecureTokenStore { fun save(key: String, value: String); fun load(key: String): String?; fun clear(key: String) }` (Decisions §D13). Token payloads (access/refresh/expiry/scopes) are serialized into the stored `value`. | P1 |
| `InMemoryTokenStore.kt` | New | **Default** — holds nothing across restart, persists no secret; safe when running mock-only. | P1 |
| `EncryptedTokenStore.kt` | New | `EncryptedSharedPreferences` (`MasterKey` `AES256_GCM`); never logs values. | P2 |

### 2.7 Modified — persistence

| File | Action | Change |
| --- | --- | --- |
| `data/local/Entities.kt` | Modified | Add `DailyActivityEntity(@PrimaryKey id: String, dateEpochDay, title, category, priority, whyItMatters, relatedMetric: String?, instructions (newline-joined), estimatedMinutes, intensity, bestTimeOfDay, status, ruleId, scheduledEpochMinute: Long?, completedAtMillis: Long?, snoozedUntilMillis: Long?, safetyNote: String?, updatedAtMillis)` and `IntegrationConnectionEntity(@PrimaryKey provider: String, status, consentGrantedAtMillis: Long?, scopes, lastSyncMillis: Long?, accountLabel: String?)` (Decisions §5). WHOOP readings are **not** given their own table — they persist as ordinary `metric_entries` rows via `WhoopMapper`. |
| `data/local/Dao.kt` | Modified | Add `observeDailyActivities(date)/upsertDailyActivity()/setActivityStatus(id, status)`, `observeConnections()/upsertConnection()/connection(provider)`, and `deleteDailyActivities()/deleteConnections()` (extend `deleteAllLocalData` coverage). |
| `data/local/AppDatabase.kt` | Modified | Register the 2 new entities, bump `version = 4`, add `MIGRATION_3_4` (Decisions §5): `CREATE TABLE daily_activities (...)` + its `index_daily_activities_dateEpochDay`, `CREATE TABLE integration_connections (...)`. **No** `whoop_daily` table and **no** `metric_entries.source` column — WHOOP data reuses the existing `metric_entries` pipeline. Additive only. |
| `data/local/Converters.kt` | Modified | Add converters for the new enums stored as TEXT: `ActivityCategory`, `ActivityPriority`, `ActivityStatus`, `Intensity`, `TimeOfDay`, `IntegrationProvider`, `ConnectionStatus` (existing `WorkoutType`/`MetricType` converters unchanged). |
| `di/AppModule.kt` | Modified | `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`. |

### 2.8 Modified — repository, VM, UI, DI, reminders

| File | Action | Change |
| --- | --- | --- |
| `data/repository/HealthRepository.kt` | Modified | Expose `dailyActivities`/`connections` flows; `saveDailyActivity(...)`, `setActivityStatus(id, status)`, `upsertConnection(...)`, `latestWhoopSnapshot()`; extend `deleteAllLocalData()` to clear `daily_activities` + `integration_connections`. WHOOP readings persist as ordinary `metric_entries` rows via `WhoopMapper` (no `source` param — no source column exists). |
| `ui/screens/MainViewModel.kt` | Modified | Inject `CoachingEngine`, `WhoopClient`, `CalendarClient`, `AvailabilityService`. Build a `ReadinessSnapshot` via `ReadinessSnapshot.from(today, history)` from the latest `WhoopSnapshot` + short history, then call `coachingEngine.buildPlan(readiness, options)`; expose `dailyPlan: DailyPlan` in `MainUiState`; add `setActivityStatus(...)` (drives the snooze/skip/reschedule/done loop), `refreshWhoop()`, and connect/disconnect that upserts `IntegrationConnectionEntity`. Fold activity completions into adherence/review. |
| `ui/screens/DailyCheckInScreen.kt` | New | Shows today's `DailyPlan` (the bounded `List<DailyActivity>`); user sets each activity's `ActivityStatus` (`DONE` / `SNOOZED` / `SKIPPED` / `RESCHEDULED`); **persists** via `setActivityStatus` (Room). |
| `ui/screens/Screens.kt` | Modified | `TodayScreen` reworked to lead with the `DailyPlan` card (today's activities) + a "Check in" CTA; keep metrics summary below. |
| `ui/screens/ConsentScreen.kt` | New | Plain-language WHOOP + Calendar data-use consent with connect/disconnect; state is the `integration_connections` Room table (`IntegrationConnectionEntity`), not DataStore. Copy passes `SafetyEngine`. |
| `data/local/PreferencesStore.kt` | unchanged | Consent is **not** stored here. Per Decisions §D11 it lives solely in the `integration_connections` Room table (`IntegrationConnectionEntity`) — not as DataStore booleans, not a `ConsentState`. |
| `ui/navigation/SpartanRoot.kt` | Modified | Add `checkin` route (Today CTA), `consent` route (from onboarding + Settings). Insert consent step in onboarding gate. |
| `di/IntegrationModule.kt` | New | `@Module @InstallIn(SingletonComponent::class)`. `provideWhoopClient` → `MockWhoopClient` when `BuildConfig.USE_MOCK_WHOOP` else `RealWhoopClient`; `provideCalendarClient` → `StubCalendarClient` else `GoogleCalendarClient`; `provideSecureTokenStore` → `InMemoryTokenStore` (P1) / `EncryptedTokenStore` (P2); `provideCoachingEngine` (with `RuleBasedRecommendationSource`), `provideAvailabilityService`. |
| `data/reminder/ReminderScheduler.kt` | Modified | Add a stable daily check-in reminder id constant so re-scheduling dedupes (works with existing `ReminderEngine.deduplicate`). |
| `data/reminder/ReminderWorker.kt` | Modified (P1) → possibly `@HiltWorker` (P2) | P1: default check-in title/body strings. P2: a separate `WhoopSyncService` (`@HiltWorker`) needs `hilt-work` + a `HiltWorkerFactory` set on `SpartanApp` via `Configuration.Provider`. |
| `SpartanApp.kt` | Modified (P2) | Implement `Configuration.Provider` to supply `HiltWorkerFactory` once `WhoopSyncService` exists. |

### 2.9 New — resources & docs

| File | Action | Change |
| --- | --- | --- |
| `res/values/strings.xml` | New | `app_name` = "Spartan"; migrate other user-facing strings opportunistically. |
| `res/xml/network_security_config.xml` | New (P2) | `cleartextTrafficPermitted="false"`; host-scope to WHOOP + Google API domains. |
| `docs/SECURITY_PRIVACY_CHECKLIST.md` | New | Consent-before-connect, token encryption, no-secrets-in-repo, host-scoped egress, deletion clears tokens + cached WHOOP, mock-default, logging redaction. |

---

## 3. Dependencies to add

Existing versions (Compose BOM 2024.12.01, Room 2.6.1, Hilt 2.52, Nav 2.8.5, WorkManager 2.10.0) are kept.
New libraries, with rationale and phase:

| Dependency | Version | Rationale | Phase |
| --- | --- | --- | --- |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.7.3` | Parse the bundled **mock** WHOOP JSON with the *same* `@Serializable` DTOs the real API will use, so `WhoopMapper` is exercised identically in mock and live. Add plugin `org.jetbrains.kotlin.plugin.serialization:2.0.21`. | **P1** |
| `com.squareup.retrofit2:retrofit` | `2.11.0` | WHOOP v2 REST client. | **P2** |
| `com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter` | `1.0.0` | Bridge Retrofit ↔ kotlinx-serialization (no second JSON lib). | **P2** |
| `com.squareup.okhttp3:logging-interceptor` | `4.12.0` | Debug-only request logging (**redacts** `Authorization`). | **P2** |
| `androidx.security:security-crypto` | `1.1.0-alpha06` | `EncryptedSharedPreferences` + `MasterKey` for `EncryptedTokenStore`. (Alpha is the only channel exposing the `MasterKey` API; call out in the checklist.) | **P2** |
| `net.openid:appauth` | `0.11.1` | OAuth2 **auth-code + PKCE** for WHOOP (and, optionally, Google). Standards-based; avoids embedding a secret in the app. **Documented alternative:** WHOOP confidential-client code exchange via a thin backend if a public-client refuses PKCE — deferred, backend not in scope. | **P2** |
| `androidx.hilt:hilt-work` + `ksp androidx.hilt:hilt-compiler` | `1.2.0` | Inject `HealthRepository`/clients into `WhoopSyncService` (`@HiltWorker`). Only needed once a background sync worker exists. | **P2** |
| `com.google.android.gms:play-services-auth` | `21.2.0` | *Optional* Google sign-in path if appauth is not used for Google; free/busy still via Calendar REST. Prefer appauth to keep one OAuth path. | **P2 (optional)** |

Everything in Phase 1 stays offline: only `kotlinx-serialization-json` is added, and the app has **no**
`INTERNET` permission and **no** HTTP client on the classpath until Phase 2.

---

## 4. Environment variables (`.env.example`)

`.env.example` is a **non-secret template** committed to document the required keys. Real values live only in
`local.properties` (already git-ignored) and never in git or `BuildConfig` output that ships literals to VCS.

```dotenv
# .env.example — Spartan integration credentials (Phase 2). COPY VALUES INTO local.properties, DO NOT COMMIT SECRETS.
WHOOP_CLIENT_ID=your-whoop-client-id
WHOOP_CLIENT_SECRET=your-whoop-client-secret
WHOOP_REDIRECT_URI=com.spartan.oauth://whoop
GOOGLE_OAUTH_CLIENT_ID=your-google-oauth-client-id.apps.googleusercontent.com
GOOGLE_OAUTH_REDIRECT_URI=com.spartan.oauth://google
# Mock/stub data sources are the default (true ⇒ mock). Flip to false for live integrations.
SPARTAN_USE_MOCK_WHOOP=true
SPARTAN_USE_MOCK_CALENDAR=true
```

**Flow: `local.properties` → `BuildConfig` (git-ignored).**

```kotlin
// app/build.gradle.kts
import java.util.Properties
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String) = (localProps.getProperty(key) ?: System.getenv(key) ?: "")

android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        buildConfigField("boolean", "USE_MOCK_WHOOP", "true")     // flip to "false" for live builds
        buildConfigField("boolean", "USE_MOCK_CALENDAR", "true")
        buildConfigField("String", "WHOOP_CLIENT_ID", "\"${secret("WHOOP_CLIENT_ID")}\"")
        buildConfigField("String", "WHOOP_REDIRECT_URI", "\"${secret("WHOOP_REDIRECT_URI")}\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"${secret("GOOGLE_OAUTH_CLIENT_ID")}\"")
        buildConfigField("String", "GOOGLE_OAUTH_REDIRECT_URI", "\"${secret("GOOGLE_OAUTH_REDIRECT_URI")}\"")
        // WHOOP_CLIENT_SECRET is NOT emitted to BuildConfig; a public PKCE client does not ship a secret.
    }
}
```

Notes: `local.properties` is already in `.gitignore`; missing keys resolve to `""` so a clean checkout still
builds (mock-only). `WHOOP_CLIENT_SECRET` is intentionally **not** compiled into the APK — PKCE avoids
shipping it; a confidential exchange would live behind a backend (Phase 3+).

---

## 5. API credential assumptions

- **No real WHOOP or Google credentials exist in this repo, and none are invented.** The default build
  (`USE_MOCK_WHOOP = true`, `USE_MOCK_CALENDAR = true`) runs entirely on mock adapters and needs no keys.
- **Interfaces + stubs + TODOs.** `WhoopClient`, `CalendarClient`, and `SecureTokenStore` are defined in
  Phase 1 with mock/in-memory implementations. Real implementations (`RealWhoopClient`, `GoogleCalendarClient`,
  `EncryptedTokenStore`, `WhoopAuthManager`) are added in Phase 2 with explicit `// TODO(phase2)` markers where
  a credential or live endpoint is required.
- **Assumed OAuth scopes** (per Decisions §6, documented so the sibling docs match):
  - **WHOOP:** `read:recovery`, `read:sleep`, `read:workout`, `read:cycles`, `read:profile`, plus `offline`
    (refresh tokens). `read:body_measurement` is added **only if** body metrics are surfaced — omitted from the MVP default.
  - **Google Calendar:** `https://www.googleapis.com/auth/calendar.freebusy` (free/busy read only — least
    privilege; never event contents). `calendar.events` is strictly opt-in for writes.
- **How to flip to real.** (1) Register a WHOOP developer app + Google OAuth client with redirects
  `com.spartan.oauth://whoop` (WHOOP) and `com.spartan.oauth://google` (Google); (2) put ids in `local.properties` (§4);
  (3) set `USE_MOCK_WHOOP`/`USE_MOCK_CALENDAR` to `false`; (4) rebuild. DI (`IntegrationModule`) then binds the
  real clients — no call-site changes, because everything depends on the interface.

---

## 6. Mock data strategy

Mock is the **injected default**, not a test-only fixture, so the whole Phase-1 experience is demoable offline.

- **`MockWhoopClient`** is bound by `IntegrationModule` whenever `BuildConfig.USE_MOCK_WHOOP` is true. It reads
  `assets/mock/whoop_sample.json` — a **labeled** multi-day dataset (~14 days of recovery score, HRV, RHR,
  sleep performance, day strain) — through the real `WhoopMapper`, so the mapper runs the same path it will for
  live data. Every produced `WhoopSnapshot` carries `isMock = true`, which flows into the `DailyPlan.isMock` flag
  and the export and (subtly) into the UI so mock data is never mistaken for real WHOOP data.
- **`StubCalendarClient`** returns fixed **MOCK** busy blocks (e.g., a morning meeting and an afternoon block),
  feeding `AvailabilityService.openWindows()`/`suggestSlot()` so the `CoachingEngine` sizes and schedules each
  `DailyActivity` to realistic free time.
- **`InMemoryTokenStore`** is the default `SecureTokenStore`: no secret is persisted while mock-only.
- **Swap mechanism.** Flipping `USE_MOCK_WHOOP`/`USE_MOCK_CALENDAR` to `false` (or, for manual QA, a debug
  Settings toggle backed by the same flag) rebinds to the real clients via DI. No engine, ViewModel, or screen
  references a concrete client — only the interfaces — so the swap is a one-line binding change.
- **Labeling discipline.** The JSON's first element includes `"_note": "MOCK SAMPLE DATA — not real WHOOP data"`;
  the checklist and PRD both state Phase 1 ships no real health data.

---

## 7. Test strategy

All new logic is deterministic and Android-free where possible, so it runs under `:app:testDebugUnitTest`
alongside the existing 4 suites (`MetricEngineTest`, `InsightAndSafetyTest`, `PlanReviewReminderTest`,
`LocalExportFormatterTest`).

### 7.1 Unit tests (`src/test`)

| Target | Cases |
| --- | --- |
| `MetricEngine` (additions) | `validate` accepts in-range and rejects out-of-range values for the 7 new WHOOP metrics; new types classify as `UNKNOWN` clinically (no over-claim) — guards the exhaustive `when`. |
| `WhoopMapper` | DTO→`WhoopSnapshot` maps recovery/HRV/RHR/sleep/strain; skips unscored cycles; null score → null recovery; multi-day list ordered by date; parses the bundled mock JSON without loss; also emits `metric_entries` rows. |
| `CoachingEngine` (per scenario) | REST band → `RECOVERY`/`MOBILITY` activities, gentle copy; EASY → `ZONE2`, minutes capped; PRIMED + ample availability → `STRENGTH`/progression with "reps in reserve"; **availability clamp** (short free window shrinks each `DailyActivity.estimatedMinutes` and its `suggestSlot` placement); recent **pain** overrides the band to a recovery plan; `readiness.isStale` (null recovery) → `PlanEngine`-backed fallback tagged `STALE_DATA_FALLBACK`; `buildPlan` returns a bounded 2–4 `DailyActivity` list; **every** returned string passes `SafetyEngine.sanitize` (assert no blocked phrase — reuses the safety spine covered by `InsightAndSafetyTest`). |
| `AvailabilityService` | `openWindows`: full window when no busy blocks; subtracts a single block; merges overlapping blocks; fully-booked → empty list; block outside window ignored. `suggestSlot`: earliest gap that fits `activityMinutes`, else `null`. |
| `SafetyEngine` (new copy coverage) | Assert all `CoachingEngine`/`ConsentScreen`/check-in strings are accepted; add any new blocked-phrase cases the coaching copy could tempt (e.g., no "push through pain"). |
| `ReminderEngine` (dedupe) | The stable daily check-in reminder id dedupes on re-save (extends existing `reminderEnginePreventsDuplicateIds`). |
| `LocalExportFormatter` | Extend to assert daily-activity rows and WHOOP `metric_entries` rows serialize and CSV-escape correctly. |

### 7.2 Instrumentation tests (`src/androidTest` — new dir)

| Target | Cases |
| --- | --- |
| `DailyCheckInPersistenceTest` | In-memory Room (or app DB): set a `DailyActivity`'s `ActivityStatus` (e.g. `PLANNED` → `DONE`), reopen the DAO/DB, assert the row **persists** with the updated status + `completedAtMillis` — proves Phase 1 completion persistence. |
| `MigrationTest` (recommended) | `MigrationTestHelper` v3→v4 keeps existing rows and adds the new `daily_activities` + `integration_connections` tables (no dropped/rewritten data, no `metric_entries.source` column). |

### 7.3 Gradle validation commands

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"

./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:connectedDebugAndroidTest   # instrumentation; needs an emulator/device
```

---

## 8. Build validation steps

Run **serially** — this is a Hilt/KSP project and `gradle.properties` sets `ksp.incremental=false`;
parallel `assemble`/`test`/`lint` can race on generated KSP files (documented in `README.md`/`AGENTS.md`).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"   # add proxy flags only if the network requires them

./gradlew --no-daemon :app:assembleDebug        # 1. compiles (default = mock, no creds needed)
./gradlew --no-daemon :app:testDebugUnitTest    # 2. unit suite (existing + new)
./gradlew --no-daemon :app:lintDebug            # 3. lint
```

**Expected:** all three green on a clean checkout with **no** credentials, because the default build is
mock-only. Instrumentation (`connectedDebugAndroidTest`) is run separately when an emulator is available.
Per-phase gate: Phase 0 must be green *before* Phase 1 files land; Phase 1 green with `USE_MOCK_* = true`;
Phase 2 adds a live smoke test with real ids in `local.properties`.

---

## 9. Rollback strategy

The Origin `~/hackathons` originals-are-the-rollback rule does **not** apply here — this is a standalone
single-commit repo. Rollback safety comes from four properties:

1. **Single-commit history / clean git.** The tree is one WIP commit. Any phase is reverted with
   `git revert <sha>` or `git reset --hard <sha>` back to a known-green point; land each phase as its own commit
   so `assembleDebug`/`testDebugUnitTest`/`lintDebug` bisect cleanly.
2. **Feature-flagged real integrations.** `USE_MOCK_WHOOP` / `USE_MOCK_CALENDAR` default to `true`. If a Phase 2
   live client misbehaves, flip the flag (no code revert) to fall straight back to the offline mock experience.
3. **Mock default is safe.** Because DI binds mock/in-memory implementations by default and the app has no
   `INTERNET` permission until Phase 2, a broken real client cannot leak data or block the core loop — the app
   still runs fully offline.
4. **Additive DB migration.** `MIGRATION_3_4` only adds two tables (`daily_activities`, `integration_connections`);
   it never drops or rewrites existing data (WHOOP readings reuse `metric_entries`), so a forward migration is
   low-risk and an app downgrade simply ignores the new tables.

**Package-rename fallback (the key Phase 0 escape hatch).** The full `com.vitalcompass`→`com.spartan` directory
move is the riskiest single step (169 `vitalcompass` tokens across 36 files, plus `R` and generated Hilt/Room
classes). If the full rename breaks the build and can't be fixed quickly, fall back to **applicationId-only
rebranding**:

- Keep the source package `com.vitalcompass` (and its directory) untouched — no `package`/`import`/`R` churn.
- Change only the user-visible identity: `applicationId = "com.spartan"` and `android:label`/`app_name = "Spartan"`
  in `build.gradle.kts` and the manifest/strings.
- Optionally keep `namespace = "com.vitalcompass"` so `R`/`BuildConfig` and all imports stay valid.

This ships as **Spartan** to users and stores/installs under `com.spartan` while deferring the internal package
rename to a later, isolated commit — decoupling the brand cutover from the source refactor. It is the documented,
lower-risk path if the flagship rename cannot be stabilized in the Phase 0 window.

---

## 10. Consistency check with PRD & Architecture

- **Safety spine preserved.** `SafetyEngine` is unchanged; `CoachingEngine`, consent, and check-in copy all
  route through `sanitize`. WHOOP metrics are deliberately excluded from clinical ranges → `UNKNOWN` status,
  so no wearable number triggers a clinical over-claim (aligns with PRD "Safety Boundaries").
- **Local-first, then explicit scope.** Phase 1 keeps the PRD's zero-network posture. Phase 2's network egress
  is the user's explicitly requested scope, consent-gated, host-scoped, and covered by
  `SECURITY_PRIVACY_CHECKLIST.md`.
- **Deletion still total.** `deleteAllLocalData()` is extended to clear `daily_activities`, `integration_connections`,
  the WHOOP `metric_entries` rows, and (P2) encrypted tokens, honoring the PRD's export/delete privacy guarantees.
- **Naming is shared.** App = **Spartan**, package = **com.spartan**; adapters = `WhoopClient`/`MockWhoopClient`/`RealWhoopClient`,
  `CalendarClient`/`StubCalendarClient`/`GoogleCalendarClient`, `SecureTokenStore`; engine = `CoachingEngine.buildPlan(readiness: ReadinessSnapshot, options): DailyPlan`
  with `DailyActivity`/`ActivityStatus`; phases 0–3+ — all taken verbatim from `Spartan_Decisions.md` (the authoritative
  tie-breaker) and therefore identical across `Spartan_PRD.md`, `Spartan_Architecture.md`, and `Spartan_Codebase_Audit.md`.
