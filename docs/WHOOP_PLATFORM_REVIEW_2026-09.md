# WHOOP platform review (2026-09-07)

What changed on WHOOP's side since this integration was written, what we can actually use, and
what we deliberately cannot. Researched against WHOOP's live developer docs and press material.

## 1. The finding that mattered: we were on a dead API

**WHOOP API v1 is no longer supported.** New features ship to v2 only, and v1 webhooks have already
been removed. Our `RealWhoopClient` targeted `v1/recovery`, `v1/activity/sleep`, and `v1/cycle`.

**Fixed in this change.** The migration cost us six lines, because the two things that break most
integrations do not apply to us:

- The headline v2 break is record ids changing from `long` to `UUID` (sleep, workout). We never
  deserialize an id — `WhoopDtos.kt` models only `start`, `end`, `score`, `score_state`.
- v2 adds fields to existing payloads. Our reader sets `ignoreUnknownKeys = true`, so they parse
  harmlessly.

Note this path is still behind `USE_MOCK_WHOOP=true` and needs WHOOP developer credentials to run.
The shipping real-data path remains CSV import.

## 2. What v2 exposes that we still do not use

Available today under scopes we already request, and currently unmodelled:

| Data | Endpoint | Why it is interesting |
|---|---|---|
| `skin_temp_celsius`, `spo2_percentage` | `/v2/recovery` | WHOOP's own "something is off" signals |
| Sleep stages: light / slow-wave / REM | `/v2/activity/sleep` | Turns one sleep score into an explanation |
| `sleep_efficiency_percentage` | `/v2/activity/sleep` | Cheap, legible sleep-quality number |
| `sport_name`, `zone_durations`, `distance_meter` | `/v2/activity/workout` | We request `read:workout` but never call this endpoint |
| `height_meter`, `weight_kilogram`, `max_heart_rate` | `/v2/user/measurement/body` | Could prefill onboarding instead of asking |

Deliberately **not** added in this change: adding fields with no screen to show them and no rule to
consume them is dead data. Each should land with its consumer (see §5).

## 3. The 2026 WHOOP features we cannot integrate

WHOOP's 2026 releases are substantial — Healthspan / WHOOP Age, Blood Pressure Insights, FDA-cleared
ECG, Hormonal Symptom Insights, and Advanced Labs blood biomarkers (now including a women's health
panel, and available without a WHOOP wearable).

**None of them are exposed in the developer API.** There is no endpoint or field for Healthspan,
blood pressure, ECG, hormonal or menstrual insights, Advanced Labs biomarkers, journal entries, or
steps. The v2 scope list is unchanged: profile, body measurement, cycles, recovery, sleep, workout.

The one real seam: **Advanced Labs results are blood biomarkers, and Spartan already has a
first-class lab-metric system** (ApoB, Lp(a), CAC, fasting glucose) with clinical-range logic and
"pending" handling. A user can type their Advanced Labs results into the metrics they already have.
That is an additive, honest fit — no API access required.

## 4. Health Connect: the credential-free path we already built

WHOOP has two-way Android **Health Connect** sync, and this repo already contains a complete
`data/healthconnect/HealthConnectSource.kt` that maps Health Connect into the same `WhoopSnapshot`
vocabulary — so every coaching rule and screen is reused unchanged. It is flag-gated off
(`USE_HEALTH_CONNECT = false`).

This is the only path to real WHOOP-derived data that needs **no developer credentials and no OAuth**.

It is not free, and the gate is a product decision, not an engineering one:
- Manifest `android.permission.health.READ_*` declarations plus the permissions-rationale intent
  filter — declaring these triggers Google Play's Health apps review.
- A Play Console Health apps declaration.
- Health Connect has no recovery-score type, so readiness derives from HRV / RHR / sleep trends via
  the existing null-recovery fallback rules.

*Unverified:* WHOOP's support page listing exact Health Connect data types would not load. Confirm
which types WHOOP actually writes before committing to this path.

## 5. Ranked recommendations

1. **Sleep stages + efficiency, shown on the Sleep metric detail** (small). The app's single largest
   "rich data shown as a bare number" gap: we hold a sleep performance percentage and can explain it.
2. **Health Connect decision** (medium, needs your call on Play review). The highest-leverage
   unlock for real users on Android — the code is already written.
3. **Advanced Labs → existing lab metrics** (small). Document the mapping and add any missing
   biomarkers to `MetricType`; no integration work.
4. **Scope hygiene** (tiny). We request `read:profile` but never call a profile endpoint. For an app
   whose consent screen promises least privilege, drop it or use it.
5. **Workout endpoint on the API path** (small). We request `read:workout` and the CSV importer
   already parses workouts, but the API client never fetches them — the two real-data paths disagree.

## Sources

- [v1 to v2 Migration Guide](https://developer.whoop.com/docs/developing/v1-v2-migration/)
- [API Changelog](https://developer.whoop.com/docs/api-changelog/)
- [WHOOP API Docs](https://developer.whoop.com/api/)
- [2026 What's New at WHOOP](https://www.whoop.com/us/en/thelocker/2026-whats-new/)
- [WHOOP Advanced Labs women's health panel](https://www.whoop.com/us/en/press-center/whoop-broadens-whoop-advanced-labs-offering/)
- [WHOOP Health Connect integration](https://whoop.my.site.com/whoopsupport/s/article/Google-Health-Integration-For-Android)
