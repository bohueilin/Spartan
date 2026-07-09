# Spartan — Product Requirements Document (PRD)

| Field | Value |
| --- | --- |
| Product | **Spartan** (Android) |
| Application ID / package | `com.spartan` (rebrand-in-progress from `com.vitalcompass`) |
| Document | `docs/Spartan_PRD.md` |
| Status | Draft for Phase 1 (MVP) |
| Owner | Product |
| Platform | Native Android — Kotlin, Jetpack Compose, Material 3 |
| Min / target / compile SDK | 26 / 35 / 35 (JDK 17) |
| Regulatory framing | Consumer **wellness** app. Not a medical device. Not a HIPAA covered entity. No diagnosis, no treatment, no compliance certification claimed. |

> Canonical vocabulary: see docs/Spartan_Decisions.md (authoritative).

**Sibling documents (read together, keep terminology identical):**
[`docs/Spartan_Architecture.md`](Spartan_Architecture.md) ·
[`docs/Spartan_Implementation_Plan.md`](Spartan_Implementation_Plan.md) ·
[`docs/Spartan_Codebase_Audit.md`](Spartan_Codebase_Audit.md)

> **Provenance note.** The shipping foundation was built as "Vital Compass" (a local-first metric-tracking MVP; package `com.vitalcompass`). Spartan is a rebrand + pivot: it keeps the retained metric-tracking substrate and the safety/coaching engines, and adds a WHOOP-driven **daily coaching check-in** plus **Google Calendar** scheduling. Class names in this PRD that already exist in the tree are cited by path; names introduced for Spartan are marked **(new)** and are the canonical names the sibling docs must reuse.

---

## 1. Product summary

Spartan turns a user's **WHOOP recovery data** into a short, honest, do-able **daily plan** and helps them fit each activity into the real gaps in their **Google Calendar**. Every morning the user opens Spartan, sees their recovery-adjusted activities as check-in cards (each with an estimated time and a plain-language "why it matters"), and taps to mark them done, snooze, skip, or reschedule. Spartan nudges — never nags — with a small number of well-timed local notifications, and confirms progress over time using the retained metric-tracking engine inherited from the foundation.

Spartan is **local-first and consent-gated**. Nothing leaves the device unless the user explicitly connects a source (WHOOP) or a calendar (Google) and grants the specific, least-privilege scope for that action. The recommendation logic is **rules-based and transparent** in Phase 1 (every activity can be traced to the rule that produced it) and is architected to be swappable for an AI layer later without changing the safety envelope.

The product voice is calm, premium, and supportive. It uses **wellness framing**, never diagnoses, never prescribes medication or supplement doses, and routes concerning patterns to a clinician. These guardrails are enforced in code by the reused `SafetyEngine` blocked-phrase filter (`app/src/main/java/com/spartan/domain/engine/SafetyEngine.kt`).

---

## 2. Target users / personas

### 2.1 Marcus — primary persona (the reason the product exists)

| Attribute | Detail |
| --- | --- |
| Who | 42, product/eng manager, married, two kids, time-poor and calendar-driven. |
| Devices | Android phone; wears a **WHOOP** 24/7; lives in Google Calendar. |
| Health context (mock seed profile) | Fasting glucose **108 mg/dL**, TG/HDL **3.26**, HDL-C **41**, Vitamin D 25-OH **23 ng/mL**, resting HR **68 bpm**, weight **81.16 kg**, BMI **25.9**, BP **102/67**; ApoB / Lp(a) / CAC pending. *(This is the seeded mock profile in `HealthRepository.seedIfEmpty()`.)* |
| Goal | "Tell me the 2–3 things I should actually do today given how recovered I am, and slot them into my day so I don't have to think about it." |
| Pain | Generic weekly plans ignore how he slept and recovered; he skips workouts because there was "no time"; he distrusts apps that overclaim or shame him. |
| Success for Marcus | He opens Spartan, does what it says most days, and sees his resting HR trend and consistency improve without feeling managed. |

Marcus is the design center. When a decision is ambiguous, optimize for Marcus: **fewer, higher-confidence activities**, honest framing, and one-tap actions.

### 2.2 The Optimizer — secondary persona

A quantified-self user who already reads their WHOOP recovery, HRV, and strain daily. They want Spartan to (a) respect the numbers they trust, (b) expose *why* a recommendation was made, and (c) let them push a little harder on green days and back off on red days. They value transparency of the rule map (§16) and the ability to see the retained metric detail.

### 2.3 The Coach — future persona (out of MVP scope)

A trainer or health coach who oversees one or more athletes, reviews adherence, and adjusts plans. Multi-user, roles, and any coach↔athlete data sharing are **explicitly future scope** (§8) and are not built or designed in MVP beyond ensuring the data model does not foreclose them.

---

## 3. Problem statement

People who wear a recovery tracker get a *score* but not an *answer*. WHOOP tells Marcus his recovery is 41% today; it does not tell him to swap his planned lifting session for a Zone 2 walk and move it to the 30-minute gap he has at 5:15 pm. The translation from **signal → today's plan → a slot in a real day** is left entirely to the user, and that friction is exactly where adherence dies. Existing fitness apps either (a) ignore recovery and push fixed plans, (b) show data without an action, or (c) overclaim ("you're overtrained", quasi-medical advice) in ways that erode trust and cross safety lines. Nothing closes the loop from recovery data to a scheduled, safe, honest daily action with a check-in.

---

## 4. Product vision

Spartan is the **honest daily coach that reads your recovery and respects your calendar.** In the near term it is a transparent, rules-based Android app: recovery in, a small safe plan out, scheduled into your gaps, checked off in one tap. Over time the recommendation layer becomes adaptive (AI-assisted) while the same safety envelope holds, and the same trust spine — consent-gated data, least-privilege scopes, on-device by default, deletable at any time — lets Spartan expand to more signals and, eventually, a coach relationship, without ever becoming a data broker or a diagnosis engine.

---

## 5. Goals

| ID | Goal |
| --- | --- |
| G-1 | Convert WHOOP recovery into a **safe, recovery-appropriate daily plan** the user can complete. |
| G-2 | Make daily engagement **one-tap**: open → see 2–4 cards → done/snooze/skip/reschedule. |
| G-3 | Help the user **fit activities into real calendar gaps** (free/busy first; event creation only on opt-in). |
| G-4 | Keep everything **transparent** — every activity traces to a named rule; every claim passes the safety filter. |
| G-5 | Preserve **trust**: local-first, consent-gated, least-privilege scopes, full export + delete. |
| G-6 | Confirm **improvement over time** using the retained metric-tracking + review engines. |
| G-7 | Be **architecturally ready** for a real WHOOP/Calendar backend and an AI recommendation layer behind feature flags. |

## 6. Non-goals

| ID | Non-goal (for MVP) |
| --- | --- |
| NG-1 | No diagnosis, medical advice, medication/supplement dosing, or clinical decision support of any kind. |
| NG-2 | No claim of HIPAA compliance, FDA clearance, or any certification. Spartan is a wellness product. |
| NG-3 | No cloud account system, server-side user store, or cross-device sync in MVP. |
| NG-4 | No live WHOOP or live Google Calendar API calls in MVP — both run behind feature flags that default to **mock/stub** (`USE_MOCK_WHOOP` / `USE_MOCK_CALENDAR` = `true`). |
| NG-5 | No coach/multi-user, no social feed, no leaderboard, no marketplace. |
| NG-6 | No advertising SDK, no third-party analytics SDK, no telemetry SDK shipping in MVP. |
| NG-7 | No auto-editing of the user's calendar without explicit, per-action opt-in. |

---

## 7. MVP scope (Phase 1 — per brief §11)

The retained Vital Compass foundation already exists in the tree: Compose/Material 3, Room, DataStore, Hilt, Navigation Compose, WorkManager, local notifications, and the deterministic engines (`MetricEngine`, `InsightEngine`, `PlanEngine`, `ReviewEngine`, `ReminderEngine`, `SafetyEngine`) with passing unit tests. **Phase 0** mechanically rebrands this foundation from `com.vitalcompass` to `com.spartan` and updates the docs; **Phase 1** delivers the Spartan coaching MVP on top of it.

> **Phase 0 (rebrand + docs).** Mechanically rename `com.vitalcompass` → `com.spartan` (namespace, applicationId, app label "Spartan", DataStore/DB names, theme, backup-rule paths) and update the docs — see Implementation Plan. This precedes the Phase 1 MVP work below and is **not** part of Phase 1 scope.

**In scope for Phase 1 (MVP):**

1. **WHOOP client (mock)** — `WhoopClient` **(new)** interface with `MockWhoopClient` **(new)** returning labeled **mock** recovery/sleep/strain data as a `WhoopSnapshot` **(new)**. No network. Behind `USE_MOCK_WHOOP` (default `true` ⇒ mock).
2. **Coaching engine (rules-based)** — `CoachingEngine` **(new)** consumes a `ReadinessSnapshot` **(new)** (built from the WHOOP-layer `WhoopSnapshot` via `ReadinessSnapshot.from(...)`) and returns a `DailyPlan` **(new)** of `DailyActivity` **(new)** items via `buildPlan(readiness, options)`, using the transparent rule map (§16), reusing `PlanEngine` deload logic and `SafetyEngine` validation. A pluggable `RecommendationSource` **(new)** interface (default `RuleBasedRecommendationSource` **(new)**) keeps an AI source addable later without the MVP depending on it.
3. **Daily check-in UI** — activity cards with checkbox, estimated time, per-day total time, "why it matters", and a progress indicator; one-tap done/snooze/skip/reschedule with **completion persisted to Room** (§17).
4. **Calendar scheduling (free/busy first)** — `CalendarClient` **(new)** interface with a `StubCalendarClient` **(new)** that returns free/busy slots; "fit into a gap" via `AvailabilityService` **(new)**. **Opt-in** event creation is a stubbed, flag-gated write. Behind `USE_MOCK_CALENDAR` (default `true` ⇒ stub).
5. **Consent + privacy gating** — connection consent modeled in the `integration_connections` Room table (`IntegrationConnectionEntity` **(new)**, `ConnectionStatus`); WHOOP/calendar features are unreachable until the matching connection is `CONNECTED`; export + delete honored.
6. **Retained metric tracking** — existing metric entry, clinical-vs-target assessment, insight cards, and weekly review remain available (repurposed as the "progress/metrics" surface).
7. **Notifications** — scheduled daily check-in, optional pre-activity nudge, missed-activity follow-up, and completion confirmation, via the existing WorkManager reminder path, with quiet hours + anti-spam.
8. **Tests + validation** — unit tests for the new engine/mappers/rules plus retained engine tests; app builds; `.env.example` only (no secrets).

**MVP acceptance is defined in §24.**

## 8. Future scope

| Phase | Item |
| --- | --- |
| Phase 2 | Real WHOOP OAuth 2.0 + live sync (`RealWhoopClient` **(new)**, `USE_MOCK_WHOOP=false`). |
| Phase 2 | Real Google Calendar via OAuth (`GoogleCalendarClient` **(new)**, FreeBusy + opt-in Events write, `USE_MOCK_CALENDAR=false`). |
| Phase 3 | AI-assisted recommendation layer behind the same `CoachingEngine` / `RecommendationSource` contract and `SafetyEngine` envelope. |
| Phase 3 | Advanced trend charts; richer plan editing and personalization. |
| Phase 4 | Coach persona: multi-user, roles, consented coach↔athlete sharing. |
| Later | Optional encrypted cloud sync / account system (currently a non-goal). |

---

## 9. User journeys

### J-1 — First connect / consent to WHOOP
1. Marcus installs Spartan, completes lightweight onboarding (name, optional height), lands on **Today**.
2. Today shows an empty/mock state and a primary **"Connect WHOOP"** call to action.
3. Tapping it opens a **consent screen** that states, in plain language, exactly what Spartan will read (recovery, sleep, strain), that data stays on device, and that he can disconnect and delete anytime.
4. Marcus grants consent → the `WHOOP` row in `integration_connections` moves to `ConnectionStatus.CONNECTED` (persisted). In MVP (`USE_MOCK_WHOOP=true`) this binds to `MockWhoopClient`; the UI clearly labels the data as **mock**.
5. Today re-renders with a recovery summary and a generated `DailyPlan`.

### J-2 — Daily check-in
1. Morning: a scheduled local notification ("Your Spartan plan is ready") opens **Today**.
2. Marcus sees 2–4 activity cards, each with estimated minutes, a total-time-for-today figure, and a "why it matters" line.
3. He taps the checkbox on "Zone 2 walk — 25 min" → it animates to **Done**, progress updates ("1 of 3 · 25/60 min"), and the completion is written to Room immediately.
4. He **snoozes** "Mobility — 15 min" to later, and **skips** one card with an optional reason. State persists across app restarts.

### J-3 — Schedule an activity into a calendar gap
1. On a "Strength — 30 min" card Marcus taps **Schedule**.
2. Spartan reads **free/busy** (mock in MVP) and proposes the earliest fitting gap (e.g., 5:15–5:45 pm) that satisfies the activity duration and quiet-hours rules.
3. Marcus confirms. If `USE_MOCK_CALENDAR=true`, Spartan records the chosen slot locally and sets a pre-activity reminder. If live (`USE_MOCK_CALENDAR=false`) + he opted into event creation, Spartan creates a single calendar event with a clear title.
4. The card shows its scheduled time; a pre-activity nudge is armed.

### J-4 — Missed-activity follow-up
1. An activity's scheduled/target time passes with no action → it transitions to **Missed**.
2. A single, gentle missed-activity notification offers **Reschedule** or **Skip** (never a shaming tone; anti-spam caps apply — §15).
3. Reschedule re-runs the gap finder; Skip closes the loop for the day. No repeated nagging.

### J-5 — Disconnect + delete data
1. Settings → **Privacy** shows Connections (WHOOP, Calendar) and **Delete all local data**.
2. **Disconnect WHOOP** sets its `integration_connections` row to `ConnectionStatus.CONSENT_REVOKED`, stops sync, and (optionally) purges cached snapshots; the plan surface returns to the pre-connect state.
3. **Delete all local data** clears every Room table and DataStore key (reusing `HealthRepository.deleteAllLocalData()` + `PreferencesStore.clear()`), cancels all scheduled reminders (`ReminderScheduler.cancelAll()`), and returns the app to first-run.
4. An **Export** action produces a local, human-readable snapshot before deletion (Phase 1 exports text via `LocalExportFormatter`; file/share output is future scope).

---

## 10. Functional requirements

IDs are stable and referenced by the Architecture and Implementation Plan docs.

### WHOOP sync
| ID | Requirement |
| --- | --- |
| FR-1 | Spartan SHALL define a `WhoopClient` interface exposing `fetchRecentDays(days): List<WhoopSnapshot>` (oldest first, today last) and `isMock`, where `WhoopSnapshot` carries recovery %, HRV, resting HR, sleep performance %, sleep duration, sleep debt, day strain, respiratory rate, energy (each field nullable). See docs/Spartan_Decisions.md §7a. |
| FR-2 | In MVP, `MockWhoopClient` SHALL supply deterministic, clearly **mock**-labeled snapshots and SHALL make no network calls. |
| FR-3 | WHOOP data ingestion SHALL be reachable only after the `WHOOP` `integration_connections` row is `ConnectionStatus.CONNECTED`; otherwise the WHOOP surface is hidden/disabled. |
| FR-4 | Live WHOOP sync SHALL be gated behind `USE_MOCK_WHOOP` (default `true` ⇒ mock); MVP SHALL ship with `USE_MOCK_WHOOP=true` (no live sync). |
| FR-5 | A WHOOP snapshot older than a staleness threshold (default 36h) SHALL be flagged as **stale** in the UI and SHALL cause the plan to fall back to a conservative default (§23). |

### Coaching plan generation
| ID | Requirement |
| --- | --- |
| FR-6 | `CoachingEngine.buildPlan(readiness, options)` — where `readiness: ReadinessSnapshot` is built from a `WhoopSnapshot` via `ReadinessSnapshot.from(...)` — SHALL return a `DailyPlan` of `DailyActivity` items derived from the transparent rule map (§16). |
| FR-7 | Every `DailyActivity` SHALL carry: category, title, estimated minutes, intensity, a "why it matters" line, a safety/guidance note, and the **id of the rule** that produced it (traceability). |
| FR-8 | Plan generation SHALL reuse `PlanEngine` deload behavior: a pain flag, high RPE (≥8), or low adherence (<60%) in recent history SHALL reduce intensity/volume. |
| FR-9 | All generated copy (titles, why-it-matters, notes, focus) SHALL pass `SafetyEngine.sanitize(...)`; generation SHALL fail closed if any string is blocked. |
| FR-10 | The plan SHALL contain a bounded number of activities (default 2–4) to protect one-tap simplicity and Marcus's time budget. |

### Activity lifecycle (done / snooze / skip / reschedule)
| ID | Requirement |
| --- | --- |
| FR-11 | A `DailyActivity` SHALL have `status: ActivityStatus` ∈ {`PLANNED`, `DONE`, `SNOOZED`, `SKIPPED`, `RESCHEDULED`, `MISSED`}. |
| FR-12 | Marking **Done** SHALL persist a completion record to Room and update daily progress immediately and idempotently. |
| FR-13 | **Snooze** SHALL move the activity's target time forward by a user-choosable interval and re-arm any pre-activity nudge. |
| FR-14 | **Skip** SHALL close the activity for the day, optionally capturing a non-free-text or short reason, with no shaming copy. |
| FR-15 | **Reschedule** SHALL re-invoke `AvailabilityService.suggestSlot(...)` to find a new gap (§FR-19) and update the scheduled time. |
| FR-16 | Lifecycle state SHALL survive process death and app restart (persisted, not in-memory only). |

### Calendar scheduling
| ID | Requirement |
| --- | --- |
| FR-17 | `CalendarClient.freeBusy(startEpochMinute, endEpochMinute): List<TimeWindow>` SHALL return busy intervals (free/busy only, never event contents) for a window; MVP uses `StubCalendarClient`. |
| FR-18 | Calendar reads SHALL be reachable only after the `GOOGLE_CALENDAR` `integration_connections` row is `ConnectionStatus.CONNECTED`. |
| FR-19 | `AvailabilityService.suggestSlot(activityMinutes, constraints)` SHALL return the earliest fitting gap (a `TimeWindow`) ≥ the activity duration that respects quiet hours and day bounds, or `null` if no gap fits; `AvailabilityService.openWindows(constraints)` SHALL return the day's open `TimeWindow`s. |
| FR-20 | **Event creation is opt-in and per-action**: Spartan SHALL NOT write to the calendar unless the user has both granted the events scope and confirmed the specific event. Gated by `USE_MOCK_CALENDAR` (default `true` in MVP ⇒ stub, local scheduling only). |

### Notifications
| ID | Requirement |
| --- | --- |
| FR-21 | Spartan SHALL support four notification kinds: scheduled daily check-in, pre-activity nudge, missed-activity follow-up, and completion confirmation. |
| FR-22 | Notifications SHALL be scheduled via WorkManager (`ReminderScheduler` / `ReminderWorker`) and SHALL deduplicate by id (`ReminderEngine.deduplicate`). |
| FR-23 | If `POST_NOTIFICATIONS` (API 33+) is denied, Spartan SHALL degrade gracefully, persist the denied state, and never crash or loop (existing `notificationPermissionDenied` path). |
| FR-24 | Notification content SHALL pass `SafetyEngine.sanitize` and honor quiet hours + anti-spam caps (§15). |

### Consent
| ID | Requirement |
| --- | --- |
| FR-25 | Spartan SHALL persist per-provider connection consent in the `integration_connections` Room table (`IntegrationConnectionEntity`), keyed by `IntegrationProvider` ∈ {`WHOOP`, `GOOGLE_CALENDAR`} with a `status: ConnectionStatus` and the granted `scopes` (calendar read vs opt-in write are distinguished by scope), default `ConnectionStatus.NOT_CONNECTED`. Optional analytics consent is a separate, revocable preference flag. |
| FR-26 | Each connection SHALL be independently grantable and revocable, with plain-language scope descriptions shown before granting. |
| FR-27 | Revoking a connection SHALL set its `integration_connections` status to `ConnectionStatus.CONSENT_REVOKED`, immediately stop the corresponding data access, and offer to purge cached data for that source. |

### Deletion / disconnect
| ID | Requirement |
| --- | --- |
| FR-28 | **Delete all local data** SHALL clear all Room tables and DataStore keys, cancel all scheduled work, and return to first-run. |
| FR-29 | **Disconnect** for a single source SHALL revoke its consent and optionally purge only that source's cached data, leaving the rest intact. |
| FR-30 | An **Export** SHALL produce a local human-readable snapshot of the user's data before deletion (text in MVP; file/share is future scope). |

### Retained metrics tracking
| ID | Requirement |
| --- | --- |
| FR-31 | Spartan SHALL retain metric entry, `MetricEngine` clinical-vs-target assessment, `InsightEngine` cards, and `ReviewEngine` weekly summary as the "progress/metrics" surface. |
| FR-32 | Clinical reference ranges and personal optimization targets SHALL remain modeled and displayed **separately** (existing `ClinicalStatus` vs `TargetStatus`). |
| FR-33 | WHOOP-derived signals SHALL be representable as retained metrics — using the canonical `MetricType` additions `RECOVERY_SCORE`, `HRV_RMSSD`, `SLEEP_PERFORMANCE`, `SLEEP_DEBT`, `RESPIRATORY_RATE`, `DAY_STRAIN`, `ENERGY_KCAL` (no `SPO2`/`SKIN_TEMP`/`READINESS` in MVP) — so trends and review can incorporate them over time. |

---

## 11. Non-functional requirements

| ID | Category | Requirement |
| --- | --- | --- |
| NFR-1 | Performance / fast-open | Cold start to interactive **Today** ≤ 2 s on a mid-range device; plan render from a cached snapshot ≤ 300 ms. UI state is served from a `StateFlow` (`MainViewModel.uiState`) with `WhileSubscribed(5s)`. |
| NFR-2 | Reliability | Engine computations are pure/deterministic and unit-tested; a bad or partial snapshot never crashes the app (fail-safe defaults, §23). |
| NFR-3 | Security | No secrets in source or VCS; only `.env.example` is committed. Live tokens (Phase 2) live in per-app `.env.local` / secure storage, never in Room or logs. |
| NFR-4 | Privacy | Local-first by default; no network egress in MVP; all outbound integrations are consent-gated and least-privilege (§13, §14, §18). |
| NFR-5 | Accessibility | TalkBack labels on all interactive elements; dynamic type; contrast ≥ WCAG AA; touch targets ≥ 48dp; no color-only meaning (§22). |
| NFR-6 | Offline behavior | The app is fully usable offline with the last cached snapshot; scheduling and check-in work offline; live syncs queue/retry when connectivity returns (Phase 2). |
| NFR-7 | Battery | Background work uses WorkManager periodic scheduling (min 15-min granularity), no polling loops, no wake-lock abuse; notifications are event-driven. |
| NFR-8 | Maintainability | Domain engines depend only on domain models (no Android imports), enabling JVM unit tests without instrumentation. |
| NFR-9 | Compatibility | Supports Android 8.0 (API 26) through 15 (API 35); respects per-version permission models (e.g., runtime `POST_NOTIFICATIONS` on 33+). |

---

## 12. Data requirements

| ID | Data | Classification | Retention | Minimization |
| --- | --- | --- | --- | --- |
| DR-1 | WHOOP snapshots (recovery %, HRV, RHR, sleep perf/duration, strain, resp rate) | **Sensitive / PHI-like** health data | On device until user deletes/disconnects; stale cache pruned | Only fields the rule map consumes are stored; no raw WHOOP payload archived |
| DR-2 | Retained metrics (glucose, lipids, BP, weight, BMI, vitamin D, etc.) | **Sensitive / PHI-like** | Until deletion | User-entered; nullable "pending" allowed |
| DR-3 | Daily plans & activity lifecycle (status, times, completion) | Behavioral health data | Until deletion | Store status + timestamps, not free-form health narratives |
| DR-4 | Calendar free/busy | **Sensitive** (schedule) | Not persisted beyond the scheduling decision; only chosen slot retained | Read free/busy, not event titles/attendees/contents |
| DR-5 | Consent state & preferences | Config | Until deletion | Booleans/enums only |
| DR-6 | OAuth tokens (Phase 2 only) | **Secret** | Secure storage (EncryptedSharedPreferences / Keystore), never Room, never logs | Store refresh/access tokens only; never embed client secrets in the app |

**Data classification principle.** WHOOP and self-entered metrics are treated as **PHI-like sensitive personal health data** even though Spartan is not a HIPAA covered entity. They are handled with covered-entity-grade *hygiene* (on-device, encrypted-at-rest via platform, excluded from cloud backup, deletable) without any *claim* of HIPAA compliance.

---

## 13. WHOOP integration requirements

| ID | Requirement |
| --- | --- |
| WR-1 | Spartan models WHOOP data behind the `WhoopClient` interface so mock and live implementations are interchangeable. |
| WR-2 | **MVP uses `MockWhoopClient`** producing deterministic, explicitly **mock** snapshots; `USE_MOCK_WHOOP` defaults `true` (mock). |
| WR-3 | Live integration (Phase 2) uses WHOOP OAuth 2.0 (authorization-code + PKCE) requesting only the **least-privilege read scopes** below, plus `offline` for token refresh. |
| WR-4 | Requested WHOOP scopes (MVP default): `read:recovery`, `read:sleep`, `read:workout`, `read:cycles`, `read:profile`, and `offline` for refresh tokens. `read:body_measurement` is **omitted from the MVP default** and requested only if body metrics are later surfaced. No write scopes are requested. |
| WR-5 | WHOOP `client_id`/`client_secret` are **never** committed and never embedded in the APK; only `.env.example` documents the variable names. |
| WR-6 | A mapper (`WhoopMapper` **(new)**) converts the raw WHOOP shape into the internal `WhoopSnapshot`; the mapper is unit-tested against fixture JSON (labeled mock). |
| WR-7 | Missing/partial WHOOP fields are represented as nullable and handled by the coaching engine's fallback rules (§23), never as zero. |

## 14. Google Calendar requirements

| ID | Requirement |
| --- | --- |
| CR-1 | **Free/busy first.** Spartan's default and only MVP calendar read is free/busy (busy intervals), never event contents. |
| CR-2 | **Least privilege.** Phase 2 live reads use `https://www.googleapis.com/auth/calendar.freebusy` only (never event contents); Spartan does not request `calendar.readonly`, `openid`/`email`, or full-calendar management scopes. |
| CR-3 | **Opt-in event creation.** Writing an event requires (a) an explicit, separate opt-in (recorded as the `calendar.events` scope on the `GOOGLE_CALENDAR` `integration_connections` row) and (b) per-event confirmation. Only then is the `calendar.events` write scope requested. |
| CR-4 | Event writes create a single, clearly-titled Spartan event and never modify or delete the user's existing events. |
| CR-5 | Calendar features are gated by `USE_MOCK_CALENDAR` (default `true` ⇒ `StubCalendarClient`) and by the `GOOGLE_CALENDAR` `integration_connections` status. |
| CR-6 | Spartan stores only the chosen scheduling slot, not the surrounding calendar data. |

---

## 15. Notification requirements

| ID | Requirement |
| --- | --- |
| NR-1 | **Scheduled**: a daily check-in notification at a user-set time (default via `ReminderEntity` DAILY frequency). |
| NR-2 | **Pre-activity**: an optional nudge shortly before a scheduled activity's start. |
| NR-3 | **Missed**: at most one gentle follow-up when an activity transitions to `MISSED`, offering Reschedule/Skip. |
| NR-4 | **Confirm**: an optional lightweight confirmation acknowledging a completed activity or a finished day. |
| NR-5 | **Quiet hours**: no notifications during a user-configured quiet window; scheduling respects the window when placing activities. |
| NR-6 | **Anti-spam**: a hard daily cap on total notifications and per-kind caps; deduplicate by id; never re-notify a resolved activity. |
| NR-7 | **Preferences**: each kind is independently toggleable; disabling a kind cancels its scheduled work. |
| NR-8 | All notification copy passes `SafetyEngine.sanitize` and uses supportive, non-shaming language. |
| NR-9 | Denied `POST_NOTIFICATIONS` is handled per FR-23 (graceful degrade, persisted state). |

---

## 16. Coaching engine requirements

The coaching engine is **rules-based, transparent, testable, and extensible to AI**. Each rule has a stable id and emits activities whose provenance the UI can display. (Requirement IDs keep the `RE-` prefix for stability.)

| ID | Requirement |
| --- | --- |
| RE-1 | `CoachingEngine.buildPlan(readiness, options)` is a pure function of a `ReadinessSnapshot` (built from a `WhoopSnapshot`), a free/busy summary, and recent history; no I/O, no Android imports; fully JVM-unit-testable. |
| RE-2 | The rule map (brief §7; realized below) maps a `ReadinessBand` + modifiers to a bounded activity set. |
| RE-3 | Every emitted `DailyActivity` records the canonical **rule id** (`DailyActivity.ruleId`, drawn from the taxonomy in the rule map below / Decisions §7) that produced it (traceability / "why this?"). |
| RE-4 | The engine reuses `PlanEngine` deload logic and validates all copy through `SafetyEngine`. |
| RE-5 | The engine is swappable via the `RecommendationSource` interface (default `RuleBasedRecommendationSource`): an AI `RecommendationSource` (Phase 3) must satisfy the same interface and pass through the same `SafetyEngine` gate and rule-provenance contract. |
| RE-6 | Fallback rules cover missing/stale WHOOP data (§23). |

**Rule map (Phase 1 — realized from brief §7).** Recovery bands are the canonical `ReadinessBand` (`PRIMED` ≥ 67, `BALANCED` 50–66, `EASY` 34–49, `REST` ≤ 33; null recovery → `BALANCED` + stale); modifiers layer on top. Rule ids are the canonical taxonomy from Decisions §7 (carried on each `DailyActivity.ruleId`).

| Rule id | Condition | Emitted plan (bounded) |
| --- | --- | --- |
| `GOOD_RECOVERY_GREENLIGHT` | `PRIMED` band (Recovery ≥ 67%) | Full day: Strength (moderate) + Zone 2; permit a small progression if adherence ≥ 85%. |
| — (baseline; no modifier rule fires) | `BALANCED` / `EASY` band (34% ≤ Recovery ≤ 66%) | Moderate day: Zone 2 + Mobility; hold intensity steady (the default plan when neither greenlight nor low-recovery fires). |
| `LOW_RECOVERY` | `REST` band (Recovery ≤ 33%) | Recovery day: easy walk / Mobility / Recovery; **no** high-intensity; emphasize sleep. |
| `POOR_SLEEP` | Sleep performance < 70% (any band) | Add an earlier-bedtime / short-nap recommendation; cap strain. |
| `HIGH_STRAIN_LOW_RECOVERY` | Yesterday day strain high **and** today recovery not `PRIMED` | Deload: reduce volume/intensity (reuses `PlanEngine` needsDeload). |
| `PAIN_DELOAD` | Pain reported in recent history | Gentle, pain-free activities only; safety note; no progression. |
| `STALE_DATA_FALLBACK` | No WHOOP data / snapshot stale (> 36h) | Conservative default plan (Zone 2 + Mobility), labeled as not recovery-personalized. |

The remaining Decisions §7 rules — `LOW_HRV_TREND`, `ELEVATED_RHR_TREND`, `MISSED_GOAL`, `HYDRATION_BASELINE`, and `CLINICIAN_REFERRAL` (concerning vitals → recommend seeing a qualified clinician, never a diagnosis) — layer on as trend/baseline/safety overlays and complete the `DailyActivity.ruleId` provenance taxonomy.

These deterministic rules mirror the existing `PlanEngine.defaultPlan(...)` behavior (`zone2Minutes`/`strengthMinutes` reduced when `painReported || highRpeReported || adherence < 60`), extended with recovery bands.

---

## 17. Daily check-in UX requirements

| ID | Requirement |
| --- | --- |
| CX-1 | **Today** presents the day's activities as **cards**; a card is the atomic unit of interaction. |
| CX-2 | Each card shows a **checkbox** (or equivalent one-tap Done affordance), the activity title, and **estimated time** in minutes. |
| CX-3 | The screen shows a **total time** for the day (sum of estimated minutes) and time completed. |
| CX-4 | Each card exposes a **"why it matters"** line in plain, non-alarmist language. |
| CX-5 | A **progress indicator** shows completion (e.g., "2 of 3 · 40/60 min") and updates instantly on action. |
| CX-6 | **Minimal friction**: Done is a single tap; snooze/skip/reschedule are one tap away (e.g., overflow); no mandatory dialogs to complete an activity. |
| CX-7 | Completed cards give clear, calm visual + non-color confirmation (icon/state change, not color alone). |
| CX-8 | Empty/first-run and stale-data states are explicit and actionable (Connect WHOOP / data is stale). |

---

## 18. HIPAA / privacy / security requirements (brief §10)

Spartan is **not** a HIPAA covered entity or business associate and makes **no** compliance certification claim. It nonetheless adopts covered-entity-grade hygiene for PHI-like data.

| ID | Requirement |
| --- | --- |
| SEC-1 | **Local-first.** In MVP no personal data leaves the device; there is no backend, remote store, or analytics SDK. |
| SEC-2 | **Consent-gated egress.** Any future outbound data flow (WHOOP/Calendar) requires explicit, revocable, least-privilege consent (§FR-25–27). |
| SEC-3 | **No secrets in VCS.** Only `.env.example` is committed; `.env*` real files are git-ignored; no API keys or client secrets in the APK. |
| SEC-4 | **Encryption at rest.** Sensitive data relies on platform encryption; OAuth tokens (Phase 2) use EncryptedSharedPreferences / Keystore, never Room, never logs. |
| SEC-5 | **Backup exclusion.** Health DB + preferences are excluded from cloud backup and device transfer (`data_extraction_rules.xml`, `allowBackup=false`, `fullBackupContent=false`). Do not weaken without explicit approval. |
| SEC-6 | **Data subject controls.** Full local **export** and **delete** are product-critical and always available (§FR-28–30). |
| SEC-7 | **Minimization.** Store only fields the product uses; never archive raw third-party payloads or calendar contents (§DR-1, DR-4). |
| SEC-8 | **No PHI in telemetry.** If analytics is ever enabled (opt-in), events carry no health values or identifiers (§21). |
| SEC-9 | **Transparency.** In-app privacy copy states what is read, where it is stored, and how to delete it, in plain language. |

---

## 19. Safety boundaries

Spartan is a wellness coach, not a clinician. Safety is enforced in code, not just in copy, by the reused `SafetyEngine`.

| ID | Requirement |
| --- | --- |
| SB-1 | **No diagnosis / no medical claims.** Spartan must never assert a disease, a medication need, a supplement dose, or that a value "means" a diagnosis. |
| SB-2 | **Blocked phrases enforced.** All generated/notification copy passes `SafetyEngine.validateCopy`, which blocks: "you have diabetes", "your pancreas is overloaded", "you need medication/statin(s)", "take X supplement dose", "ignore your doctor", "exercise through pain". Generation fails closed on a match. |
| SB-3 | **Wellness framing.** Approved framings only, e.g. "This value is above the normal clinical range", "This pattern is worth tracking and discussing with a clinician", "One value is not a diagnosis", "Here are safe behavior actions to try", "Confirm improvement using trends and repeat measurements". |
| SB-4 | **Clinician-referral triggers.** Insight/recommendation output includes clinician-discussion triggers (reusing `InsightEngine` `clinicianTriggers`) for repeated out-of-range values, new/severe symptoms, pain, palpitations, chest pain, or fainting. |
| SB-5 | **No training through pain.** A pain flag forces gentle, pain-free activities and a stop-if-pain note; the engine never progresses load under a pain flag. |
| SB-6 | **Clinical ranges vs personal targets stay separate** (never conflated in copy or UI). |

---

## 20. Success metrics

| ID | Metric | Definition | MVP target (directional) |
| --- | --- | --- | --- |
| SM-1 | **Activation** | % of new users who connect WHOOP (mock or live) | ≥ 60% of onboarded users |
| SM-2 | **D1 plan completion rate** | % of day-1 activities marked Done | ≥ 50% |
| SM-3 | **D7 plan completion rate** | % of activities Done on day 7 among retained users | ≥ 45% |
| SM-4 | **Activities completed / day** | Mean Done activities per active user-day | ≥ 1.5 |
| SM-5 | **Retention** | D7 / D30 active users | D7 ≥ 35%, D30 ≥ 20% |
| SM-6 | **Scheduling adoption** | % of activities scheduled into a calendar gap | ≥ 30% |

Targets are directional planning goals for a beta, **not** guarantees or claims of achieved outcomes.

## 21. Analytics / telemetry plan

Privacy-safe, **opt-in**, and **carrying no PHI**. Analytics is **off by default and local-only in MVP** (no third-party analytics SDK ships). The taxonomy below defines what *would* be recorded if the user opts in; each event's properties are restricted to non-health, non-identifying values.

| Event | Properties (PHI-free) |
| --- | --- |
| `app_open` | source (launcher/notification) |
| `whoop_connect_started` / `_succeeded` / `_failed` | failure_reason_code only (no health values) |
| `plan_generated` | activity_count, rule_ids[] (canonical rule provenance per §16 / Decisions §7, not scores) |
| `checkin_activity_done` / `_snoozed` / `_skipped` | activity_type, day_index (no biometric values) |
| `activity_scheduled` | had_gap (bool) |
| `notification_shown` / `_opened` | kind |
| `consent_granted` / `_revoked` | scope_key |
| `data_exported` / `data_deleted` | — |

Prohibited in any event: recovery %, HRV, glucose or any metric value, timestamps precise enough to re-identify, calendar contents, free text. Analytics consent is an independent, revocable preference flag (separate from the `integration_connections` connection consents).

---

## 22. Accessibility requirements

| ID | Requirement |
| --- | --- |
| A11Y-1 | Every interactive element has a TalkBack content description; card actions are individually focusable and announced with state ("Zone 2 walk, 25 minutes, not done"). |
| A11Y-2 | Dynamic type is respected; layouts reflow without truncation up to large font scales. |
| A11Y-3 | Text/background contrast meets WCAG **AA** in light and dark themes. |
| A11Y-4 | Touch targets are ≥ **48dp**. |
| A11Y-5 | **No color-only meaning** — status (done/missed/recovery band) is conveyed with icon/text/shape in addition to color. |
| A11Y-6 | Focus order is logical; the primary daily action is reachable without excessive traversal. |

---

## 23. Edge cases

| ID | Case | Expected behavior |
| --- | --- | --- |
| EC-1 | No WHOOP data (never connected / no snapshot) | Show connect prompt; if a plan is needed, use `STALE_DATA_FALLBACK` conservative default, labeled not-personalized. |
| EC-2 | Stale WHOOP data (> 36h) | Flag stale in UI; fall back to conservative default; invite re-sync. |
| EC-3 | Partial metrics (some snapshot fields null) | Use available fields; nullable handling per WR-7; never treat null as 0. |
| EC-4 | Notification permission denied | Degrade gracefully, persist denied state, surface in-app reminders only (FR-23). |
| EC-5 | Calendar permission denied / not connected | Hide scheduling; allow local-only target times; no crash. |
| EC-6 | Offline | Full app usable from cache; scheduling + check-in work; live sync queued for later (Phase 2). |
| EC-7 | Timezone / travel | Times computed in the device's current zone; scheduled activities re-evaluate against the active zone. |
| EC-8 | DST transition | Local-time scheduling uses `java.time` (`LocalDateTime`/`Duration`) so pre-activity delays remain correct across the shift. |
| EC-9 | Empty calendar (all free) | Scheduler places activities at sensible default times within day bounds and quiet hours. |
| EC-10 | All-day-busy (no gaps) | Scheduler returns "no gap found"; UI offers manual time or Skip; no silent failure. |

---

## 24. Acceptance criteria (map to MVP scope, §7)

| ID | Criterion | Trace |
| --- | --- | --- |
| AC-1 | App **builds**: `./gradlew --no-daemon :app:assembleDebug` succeeds. | §7.1 |
| AC-2 | **Unit tests pass**: `./gradlew --no-daemon :app:testDebugUnitTest` green (retained engine tests + new rule/mapper/scheduler tests). | §27 |
| AC-3 | **Lint passes** (or remaining warnings documented): `./gradlew --no-daemon :app:lintDebug`. | NFR-* |
| AC-4 | A **mock WHOOP** snapshot produces a rendered `DailyPlan` on Today, clearly labeled mock. | FR-2, FR-6 |
| AC-5 | Marking an activity **Done persists** to Room and survives app restart. | FR-12, FR-16 |
| AC-6 | **Progress** indicator reflects completed count and minutes in real time. | CX-5 |
| AC-7 | **Consent gating** present: WHOOP/calendar surfaces are unreachable until consent is granted. | FR-3, FR-18, FR-25 |
| AC-8 | **No secrets** in the repo; only `.env.example` committed; no network calls in MVP. | SEC-1, SEC-3 |
| AC-9 | Health/coaching copy avoids **blocked phrases** and passes `SafetyEngine`. | SB-2 |
| AC-10 | **Delete all local data** clears Room + DataStore and cancels scheduled work. | FR-28 |
| AC-11 | Feature flags `USE_MOCK_WHOOP` / `USE_MOCK_CALENDAR` default **`true`** (mock/stub); no live API calls occur in MVP. | FR-4, FR-20, CR-5 |

Run Gradle validation tasks **serially** for this Hilt/KSP project (parallel runs can race on generated KSP files), using JDK 17 (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`).

---

## 25. Launch plan

1. **Foundation freeze** — confirm the retained Vital Compass engines/tests are green on `com.spartan` after rebrand.
2. **MVP feature-complete** — mock WHOOP → plan → check-in → local scheduling → consent/delete, all flag-gated.
3. **Safety & privacy review** — verify `SafetyEngine` coverage, backup exclusions, no secrets, consent copy.
4. **Accessibility pass** — TalkBack, dynamic type, contrast, 48dp, no color-only meaning.
5. **Beta build** — internal → closed beta (see §26), with live integrations still flagged off unless a beta cohort is explicitly opted into Phase 2.
6. **Store readiness** — Play Data safety form reflecting local-first posture; privacy policy; sensitive-scope justifications prepared for the eventual WHOOP/Google review (Phase 2).

## 26. Rollout plan

| Stage | Audience | Integrations | Gate |
| --- | --- | --- | --- |
| Internal dogfood | Team | Mock WHOOP + mock calendar | `USE_MOCK_*=true` |
| Closed beta | Invited users (Marcus-like) | Mock by default; opt-in live WHOOP for a small cohort | Live WHOOP behind flag + consent; calendar still mock |
| Phased GA | Gradually widened % | Live WHOOP (Phase 2), calendar free/busy read; event-create opt-in | Each integration behind its feature flag + per-source consent |

Feature flags are the rollout lever: real integrations ship dark and are enabled per cohort; a flag flip never bypasses consent or the safety envelope.

## 27. Test plan

**Unit (JVM, no instrumentation) — engines / mappers / rules:**
- `CoachingEngine` rule map: each rule id fires on its band/modifier; bounded activity count; provenance recorded on `DailyActivity.ruleId`; fallback on stale/missing data.
- `WhoopMapper`: fixture JSON (mock) → `WhoopSnapshot`; nulls preserved; no crash on partial data. `ReadinessSnapshot.from(...)`: `WhoopSnapshot` → `ReadinessSnapshot` band mapping.
- `AvailabilityService`: `suggestSlot(...)` earliest-fitting gap; quiet-hours respected; `null`/"no gap" on all-day-busy; `openWindows(...)` and empty-calendar defaults.
- Retained: `MetricEngineTest`, `InsightAndSafetyTest`, `PlanReviewReminderTest`, `LocalExportFormatterTest` remain green (BMI/TG-HDL/waist ratios, classification, safe insight copy, plan distribution + deload, adherence cap, weekly review windows, reminder dedup/validation, export).
- `SafetyEngine`: blocked-phrase coverage extended to any new copy sources.

**Instrumentation (Compose) — check-in:**
- Today renders N cards from a mock plan; total time correct.
- Tapping Done updates progress and persists across a simulated restart (Room).
- Snooze/skip/reschedule transition state and persist.
- Consent-gated surfaces hidden until consent granted.

**Manual matrix:**

| Scenario | Check |
| --- | --- |
| First run → connect WHOOP (mock) | Consent screen accurate; plan renders labeled mock |
| Green / yellow / red recovery | Correct rule fires; correct activity mix |
| Stale / missing snapshot | Fallback plan + stale flag |
| Schedule into gap / all-day-busy | Slot found / "no gap" handled |
| Notifications denied | Graceful degrade, persisted |
| Quiet hours + anti-spam | No notifications in window; caps respected |
| Delete all data | Returns to first-run; work cancelled |
| TalkBack + large font + dark mode | A11Y-1..6 hold |

---

## 28. Risk register

| ID | Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- | --- |
| R-1 | WHOOP/Google API review rejects sensitive scopes | Medium | High | Request least-privilege scopes only (WR-4, CR-2); free/busy first; prepare scope justifications; keep MVP mock so launch doesn't depend on approval. |
| R-2 | A recommendation reads as medical advice | Low | High | `SafetyEngine` fail-closed on all copy (SB-2); wellness framing; clinician-referral triggers; legal/safety review pre-beta. |
| R-3 | Rebrand `com.vitalcompass`→`com.spartan` breaks build/tests or backup-rule paths | Medium | Medium | Scripted rename; update DataStore/DB names + `data_extraction_rules.xml` paths; run serial Gradle validation (AC-1..3). |
| R-4 | Secret leakage (tokens/keys) | Low | High | `.env.example` only; git-ignore `.env*`; tokens in Keystore, never Room/logs (SEC-3, SEC-4). |
| R-5 | Notification fatigue / spam | Medium | Medium | Hard caps, quiet hours, dedup, one-shot missed follow-up (NR-5..7). |
| R-6 | Scheduler picks a bad slot (DST/timezone/travel) | Medium | Medium | `java.time` local-time math; re-evaluate on zone change; unit tests for DST/empty/all-busy (EC-7..10). |
| R-7 | Partial/stale WHOOP data yields a misleading plan | Medium | Medium | Nullable handling (WR-7); stale flag + conservative fallback (EC-1..3). |
| R-8 | Users don't trust an AI/coach that can't explain itself | Medium | Medium | Rule provenance per activity (RE-3); "why it matters" copy; transparent rule map. |
| R-9 | Scope creep into medical/coach features pre-MVP | Medium | Medium | Enforce non-goals (§6) and phase boundaries; future work stays flagged/deferred. |
| R-10 | Local-only data lost on uninstall/device change (no cloud) | Medium | Low | Export before delete (FR-30); document local-first tradeoff; cloud sync remains a future non-goal. |

---

## 29. Open questions

| ID | Question |
| --- | --- |
| OQ-1 | Exact recovery-band thresholds — adopt WHOOP's 33/66 cutoffs verbatim or tune for Spartan's activity mix? |
| OQ-2 | Canonical daily activity count — fix at 3, or vary 2–4 by recovery band and available calendar time? |
| OQ-3 | Which WHOOP signals become **retained metrics** (trend/review) vs ephemeral inputs (recovery only)? |
| OQ-4 | Snooze semantics — fixed intervals, "later today", or gap-aware auto-reschedule? |
| OQ-5 | Should completed activities feed `PlanEngine` adherence/RPE directly, or capture a lightweight post-activity RPE prompt? |
| OQ-6 | Quiet-hours default window and whether it also blocks scheduling (not just notifications). |
| OQ-7 | Phase 2 storage for OAuth tokens — EncryptedSharedPreferences vs Keystore-wrapped Room field. |
| OQ-8 | Google Play Data safety + sensitive-scope verification timeline for the WHOOP/Calendar go-live. |
| OQ-9 | Do we surface a coarse recovery band in opt-in analytics, or exclude it entirely as PHI-adjacent? |
| OQ-10 | Coach persona data model — reserve fields now, or defer entirely to Phase 4? |

---

*Spartan is a consumer wellness product. It does not diagnose, treat, or prevent any disease, is not a medical device, and claims no regulatory certification. All health data in this document sourced from the seeded profile is **mock** sample data.*
