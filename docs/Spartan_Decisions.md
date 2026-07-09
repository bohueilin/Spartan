# Spartan — Canonical Decisions & Domain Vocabulary

> **This document is the tie-breaker.** Where any Spartan doc or the code disagrees on a name,
> schema, enum, flag, or scope, **this document wins**. The PRD, Architecture, Implementation
> Plan, and Codebase Audit MUST use the exact names and shapes below, verbatim. The code under
> `com.spartan` is built to this vocabulary.

Status: authoritative as of the Spartan rebrand/pivot from the as-found **Vital Compass** (`com.vitalcompass`) local-first metric tracker to **Spartan**, an Android-first WHOOP coaching app. Mock WHOOP + stub Calendar are the default data sources; real integrations are behind flags.

---

## 1. Decision log (resolved)

| # | Question | Decision |
|---|----------|----------|
| D1 | App name / package | **Spartan** / **com.spartan** (was `com.vitalcompass`). Full mechanical rename. |
| D2 | Coaching engine | **`CoachingEngine`** with method **`buildPlan(readiness: ReadinessSnapshot, options): DailyPlan`**. A pluggable **`RecommendationSource`** interface (default **`RuleBasedRecommendationSource`**) lets an AI source be added later without the MVP depending on it. There is **no** `RecommendationEngine`. |
| D3 | Daily output shape | A **`DailyPlan`** containing a **bounded `List<DailyActivity>` (default 2–4)**. Not a single-session recommendation. |
| D4 | Engine input model | **`ReadinessSnapshot`** (wearable-agnostic domain model) built from a **`WhoopSnapshot`** (WHOOP-layer normalized data) via `ReadinessSnapshot.from(...)`. There is **no** `CoachingInput`/`WhoopRecovery` input type. |
| D5 | Activity status | **`ActivityStatus { PLANNED, DONE, SNOOZED, SKIPPED, RESCHEDULED, MISSED }`** (6 values; supports the required snooze/skip/reschedule loop). Uses `DONE` (not `COMPLETED`) and `PLANNED` (not `PENDING`). Persisted. |
| D6 | WHOOP adapter names | **`WhoopClient`** (interface) · **`MockWhoopClient`** (default) · **`RealWhoopClient`** (Phase-2 stub) · **`WhoopMapper`** · **`WhoopAuthManager`**. (Client + Mock/Real, not Adapter.) |
| D7 | Calendar adapter names | **`CalendarClient`** (interface) · **`StubCalendarClient`** (default) · **`GoogleCalendarClient`** (Phase-2 stub) · **`CalendarAuthManager`** · **`AvailabilityService`**. |
| D8 | Scheduling contract | **`AvailabilityService.openWindows(constraints): List<TimeWindow>`** and **`AvailabilityService.suggestSlot(activityMinutes, constraints): TimeWindow?`** (earliest fitting gap or null). No `ActivityScheduler`, no `availableMinutes(): Int`. |
| D9 | Feature flags | **`BuildConfig.USE_MOCK_WHOOP`** and **`BuildConfig.USE_MOCK_CALENDAR`**, **default `true`** (mock/stub). `.env`/tooling keys: `SPARTAN_USE_MOCK_WHOOP` / `SPARTAN_USE_MOCK_CALENDAR`. One polarity everywhere: *true ⇒ mock*. |
| D10 | v3→v4 Room migration | Add tables **`daily_activities`** (`DailyActivityEntity`) and **`integration_connections`** (`IntegrationConnectionEntity`). WHOOP readings are stored as ordinary **`metric_entries`** rows (reusing the existing pipeline). **No** `whoop_daily` table, **no** `daily_check_ins` table, **no** `metric_entries.source` column. |
| D11 | Consent representation | Single source: the **`integration_connections`** Room table (`IntegrationConnectionEntity`). Drives connect/disconnect UI and is cleared on data deletion. Not a DataStore `ConsentState`, not loose booleans. |
| D12 | Calendar read scope | **`https://www.googleapis.com/auth/calendar.freebusy`** only for reads (least privilege; never event contents). **`calendar.events`** is strictly opt-in for writes. No `calendar.readonly`, no `openid`/`email`. |
| D13 | Secure token store | Interface **`SecureTokenStore { fun save(key, value); fun load(key): String?; fun clear(key) }`**. Phase-1 default binding **`InMemoryTokenStore`** (no real tokens exist with mock data). Phase-2 **`EncryptedTokenStore`** (Keystore-backed EncryptedSharedPreferences). |
| D14 | Phases | **Phase 0** = rebrand + docs. **Phase 1** = MVP foundation (mock WHOOP + coaching engine + daily check-in + completion persistence + calendar stub + notification-ready + security/consent scaffolding). **Phase 2** = real WHOOP OAuth + real Calendar free/busy + encrypted tokens. **Phase 3+** = scale (iOS, coach dashboard, AI layer, more wearables, flags, audit logs). Identical numbering in all docs. |
| D15 | Redirect URIs | **`com.spartan.oauth://whoop`** and **`com.spartan.oauth://google`** (match `.env.example` verbatim). |
| D16 | MetricType additions | The exact 7-member set in §4 below. No `SPO2`, `SKIN_TEMP`, or `READINESS` MetricType in the MVP (documented as future). Strain member is **`DAY_STRAIN`**. |

---

## 2. Engines, adapters, auth (canonical names)

| Concern | Type(s) | Package |
|---|---|---|
| Coaching (rules) | `CoachingEngine`, `RecommendationSource` (iface), `RuleBasedRecommendationSource` (default), `CoachingRule` | `com.spartan.domain.engine` |
| Safety (reused) | `SafetyEngine` (blocked-phrase sanitizer — reused unchanged) | `com.spartan.domain.engine` |
| Metrics (reused/extended) | `MetricEngine`, `InsightEngine`, `PlanEngine`, `ReviewEngine`, `ReminderEngine` | `com.spartan.domain.engine` |
| WHOOP | `WhoopClient`, `MockWhoopClient`, `RealWhoopClient`(stub), `WhoopMapper`, `WhoopAuthManager`, `WhoopSyncService` | `com.spartan.data.whoop` |
| Calendar | `CalendarClient`, `StubCalendarClient`, `GoogleCalendarClient`(stub), `CalendarAuthManager`, `AvailabilityService` | `com.spartan.data.calendar` |
| Security | `SecureTokenStore` (iface), `InMemoryTokenStore` (default), `EncryptedTokenStore` (Phase 2) | `com.spartan.data.security` |
| Notifications (reused/extended) | `ReminderScheduler`, `ReminderWorker` | `com.spartan.data.reminder` |

## 3. Domain models (canonical fields)

- **`WhoopSnapshot`** — WHOOP-layer normalized data for one day + short history: `dateEpochDay: Long`, `recoveryScore: Int?`, `hrvMs: Double?`, `restingHeartRate: Double?`, `sleepPerformance: Int?`, `sleepDurationHours: Double?`, `sleepDebtHours: Double?`, `respiratoryRate: Double?`, `dayStrain: Double?`, `energyKcal: Double?`, `isMock: Boolean`.
- **`ReadinessSnapshot`** — engine input (wearable-agnostic): `recoveryScore: Int?`, `hrvMs: Double?`, `hrvVsBaseline: Double?`, `restingHeartRate: Double?`, `rhrVsBaseline: Double?`, `sleepPerformance: Int?`, `sleepDebtHours: Double?`, `dayStrainPrior: Double?`, `respiratoryRate: Double?`, `band: ReadinessBand`, `trendNotes: List<String>`, `isStale: Boolean`, `isMock: Boolean`. Built by `ReadinessSnapshot.from(today, history)`.
- **`DailyActivity`** — `id: String`, `title: String`, `category: ActivityCategory`, `priority: ActivityPriority`, `whyItMatters: String`, `relatedMetric: MetricType?`, `instructions: List<String>`, `estimatedMinutes: Int`, `intensity: Intensity`, `bestTimeOfDay: TimeOfDay`, `status: ActivityStatus`, `ruleId: String`, `scheduledEpochMinute: Long?`, `completedAtMillis: Long?`, `snoozedUntilMillis: Long?`, `safetyNote: String?`.
- **`DailyPlan`** — `dateEpochDay: Long`, `headline: String`, `band: ReadinessBand`, `activities: List<DailyActivity>`, `totalEstimatedMinutes: Int`, `safetyBanner: String?`, `isMock: Boolean`.
- **`TimeWindow`** — `startEpochMinute: Long`, `endEpochMinute: Long`.

## 4. Enums (canonical members)

- `ActivityCategory { RECOVERY, MOVEMENT, ZONE2, STRENGTH, MOBILITY, BREATHWORK, SLEEP, HYDRATION, NUTRITION, MINDSET }`
- `ActivityPriority { REQUIRED, RECOMMENDED, OPTIONAL }`
- `ActivityStatus { PLANNED, DONE, SNOOZED, SKIPPED, RESCHEDULED, MISSED }`
- `Intensity { REST, EASY, MODERATE, HARD }`
- `TimeOfDay { MORNING, MIDDAY, AFTERNOON, EVENING, ANYTIME }`
- `ReadinessBand { PRIMED, BALANCED, EASY, REST }` — from recovery score: `>=67 → PRIMED`, `50–66 → BALANCED`, `34–49 → EASY`, `<=33 → REST` (null recovery → `BALANCED` + `isStale`).
- `IntegrationProvider { WHOOP, GOOGLE_CALENDAR }`
- `ConnectionStatus { NOT_CONNECTED, CONNECTED, CONSENT_REVOKED, ERROR }`

**MetricType additions (7 new; existing 19 unchanged; `SLEEP_DURATION` + `RESTING_HEART_RATE` reused):**

| Member | label | unit | lowerIsBetter |
|---|---|---|---|
| `RECOVERY_SCORE` | "Recovery" | "%" | false |
| `HRV_RMSSD` | "HRV" | "ms" | false |
| `SLEEP_PERFORMANCE` | "Sleep performance" | "%" | false |
| `SLEEP_DEBT` | "Sleep debt" | "h" | true |
| `RESPIRATORY_RATE` | "Respiratory rate" | "rpm" | false |
| `DAY_STRAIN` | "Day strain" | "" | false |
| `ENERGY_KCAL` | "Energy" | "kcal" | false |

> `MetricEngine.validate(...)` uses an exhaustive `when(type)` — each new member gets a range branch (e.g. RECOVERY_SCORE 0–100, HRV_RMSSD 5–300, SLEEP_PERFORMANCE 0–100, SLEEP_DEBT 0–24, RESPIRATORY_RATE 5–40, DAY_STRAIN 0–21, ENERGY_KCAL 0–10000).

## 5. Room entities & v4 migration (canonical)

`AppDatabase` version **3 → 4**, `MIGRATION_3_4`:

```sql
CREATE TABLE IF NOT EXISTS daily_activities (
  id TEXT NOT NULL PRIMARY KEY,           -- "<dateEpochDay>:<slug>"
  dateEpochDay INTEGER NOT NULL,
  title TEXT NOT NULL, category TEXT NOT NULL, priority TEXT NOT NULL,
  whyItMatters TEXT NOT NULL, relatedMetric TEXT,
  instructions TEXT NOT NULL,              -- newline-joined
  estimatedMinutes INTEGER NOT NULL, intensity TEXT NOT NULL, bestTimeOfDay TEXT NOT NULL,
  status TEXT NOT NULL, ruleId TEXT NOT NULL,
  scheduledEpochMinute INTEGER, completedAtMillis INTEGER, snoozedUntilMillis INTEGER,
  safetyNote TEXT, updatedAtMillis INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS index_daily_activities_dateEpochDay ON daily_activities(dateEpochDay);
CREATE TABLE IF NOT EXISTS integration_connections (
  provider TEXT NOT NULL PRIMARY KEY,      -- WHOOP | GOOGLE_CALENDAR
  status TEXT NOT NULL, consentGrantedAtMillis INTEGER,
  scopes TEXT NOT NULL, lastSyncMillis INTEGER, accountLabel TEXT
);
```

WHOOP metrics persist as `metric_entries` rows via `WhoopMapper` (no new column). Tokens are **never** in Room.

## 6. Feature flags, scopes, redirect URIs

- Flags: `BuildConfig.USE_MOCK_WHOOP` (default `true`), `BuildConfig.USE_MOCK_CALENDAR` (default `true`). `.env`: `SPARTAN_USE_MOCK_WHOOP`, `SPARTAN_USE_MOCK_CALENDAR`.
- WHOOP scopes: `read:recovery read:sleep read:workout read:cycles read:profile offline` (+`read:body_measurement` only if body metrics surfaced — omitted from MVP default).
- Calendar scopes: `calendar.freebusy` (read) · `calendar.events` (opt-in write).
- Redirect URIs: `com.spartan.oauth://whoop`, `com.spartan.oauth://google`.

## 7. Rule IDs (provenance carried on each `DailyActivity.ruleId`)

`LOW_RECOVERY`, `POOR_SLEEP`, `HIGH_STRAIN_LOW_RECOVERY`, `LOW_HRV_TREND`, `ELEVATED_RHR_TREND`, `MISSED_GOAL`, `GOOD_RECOVERY_GREENLIGHT`, `PAIN_DELOAD`, `HYDRATION_BASELINE`, `STALE_DATA_FALLBACK`, `CLINICIAN_REFERRAL`.

## 7a. As-built API signatures (authoritative — matches shipped `com.spartan` code)

These pin the exact seams as implemented. Where any design doc's method signature differs, THIS wins.

```kotlin
// data.whoop — the client returns NORMALIZED snapshots (mapper lives at/below the client),
// so everything above the boundary is wearable-agnostic.
interface WhoopClient {
    suspend fun fetchRecentDays(days: Int = 7): List<WhoopSnapshot>  // oldest first, today last
    val isMock: Boolean
}
object WhoopMapper { fun toReadings(s: WhoopSnapshot): List<MetricReading>; fun toReadings(l: List<WhoopSnapshot>): List<MetricReading> }

// data.calendar — reads are free/busy only (opaque busy TimeWindows, never event contents).
interface CalendarClient {
    val isStub: Boolean
    suspend fun freeBusy(startEpochMinute: Long, endEpochMinute: Long): List<TimeWindow>  // busy blocks
    suspend fun createEvent(title: String, startEpochMinute: Long, durationMinutes: Int): Result<String>
}
class AvailabilityService {
    fun openWindows(dayStartEpochMinute: Long, dayEndEpochMinute: Long, busy: List<TimeWindow>, minWindowMinutes: Int = 5): List<TimeWindow>
    fun suggestSlot(activityMinutes: Int, dayStartEpochMinute: Long, dayEndEpochMinute: Long, busy: List<TimeWindow>): TimeWindow?
}

// data.security
interface SecureTokenStore { fun save(key: String, value: String); fun load(key: String): String?; fun clear(key: String); fun clearAll() }

// domain.engine
class CoachingEngine(source: RecommendationSource = RuleBasedRecommendationSource(), safety: SafetyEngine = SafetyEngine()) {
    fun buildPlan(readiness: ReadinessSnapshot, options: CoachingOptions = CoachingOptions()): DailyPlan
}
```

Note: the Architecture doc's earlier "WhoopClient returns raw DTOs / busyBlocks(date,zone)" phrasing is superseded by the signatures above (mapper is at the client boundary; the calendar read method is `freeBusy`).

## 8. Safety (reused `SafetyEngine`)

All generated coaching copy passes `SafetyEngine.sanitize(...)`. No diagnosis / no medical claims / wellness framing. Concerning vitals (very low HRV, very high RHR, respiratory-rate spike) emit a `CLINICIAN_REFERRAL` activity that recommends seeing a qualified clinician — never a diagnosis. Existing blocked phrases remain enforced.
