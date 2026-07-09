# Spartan — Security & Privacy Checklist (HIPAA-ready posture)

Spartan handles sensitive health, fitness, and calendar-derived data. It is **not** HIPAA-certified
and is **not** a medical device. This checklist tracks the engineering posture so that a
HIPAA-regulated deployment (e.g. a coach/clinic operating as a covered entity or business associate)
would not inherit obvious compliance debt. See also [Spartan_Architecture.md](Spartan_Architecture.md) §9–10
and [Spartan_PRD.md](Spartan_PRD.md) (HIPAA/privacy/security & safety sections).

Legend: ✅ implemented · 🟡 partial / scaffolded · ⬜ planned (documented, not built)

## Data classification & minimization
- ✅ WHOOP metrics, coaching plans, calendar-derived availability, and check-ins are treated as sensitive PHI-like data.
- ✅ Calendar reads are **free/busy only** — never event titles or contents (`calendar.freebusy`).
- ✅ WHOOP scopes are least-privilege (`read:*` only; `read:body_measurement` omitted from the MVP).
- ✅ Local-first storage: all app data lives on-device in Room + DataStore; there is no cloud backend in the MVP.

## Storage & secrets
- ✅ Tokens are never stored in Room, never logged. `SecureTokenStore` abstraction; `InMemoryTokenStore` for mock, `EncryptedTokenStore` when a real integration is enabled.
- ✅ `EncryptedTokenStore` (Android Keystore–backed `EncryptedSharedPreferences`, AES-256-GCM) implemented and bound via DI when `USE_MOCK_* = false`.
- ✅ Real OAuth via AppAuth (authorization-code + PKCE) for WHOOP and Google; tokens flow only through `SecureTokenStore`; bearer added per-request by an OkHttp interceptor (never in URLs/logs).
- ✅ No secrets/keys in the repo. `.env` / `local.properties` / `*.keystore` are gitignored; only `.env.example` (placeholders) is tracked.
- ✅ Secrets flow via `local.properties`/env → `BuildConfig` at build time; the app builds with none set (mock mode).
- ✅ Android backup & device-transfer exclude the database, shared prefs, and DataStore (`data_extraction_rules.xml`); `allowBackup=false`.

## Transport
- ✅ Cleartext traffic disabled app-wide (`usesCleartextTraffic="false"` + `network_security_config.xml` `cleartextTrafficPermitted=false`).
- ✅ `INTERNET` permission present for Phase-2 authenticated syncs over TLS only.
- ✅ Access tokens auto-refresh on 401 via an OkHttp `Authenticator` (single retry), so expiry never leaks a request or loops.
- 🟡 Certificate pinning: prepared with a rotation runbook ([CERT_PINNING_RUNBOOK.md](CERT_PINNING_RUNBOOK.md));
  deliberately enabled only alongside the first real-integration release (a stale pin bricks sync;
  the sample-data 1.0 has no live traffic to pin).
- ✅ StrictMode (thread + VM policies) in debug builds; never in release.
- ✅ Dependabot CVE/version watch (`.github/dependabot.yml`); `security-crypto` pinned note included.

## Consent & user control
- ✅ Explicit consent screen before connecting WHOOP or Calendar, with plain-language scope explanations (`ConnectionsScreen`).
- ✅ Consent + connection state persisted in the `integration_connections` table (single source of truth).
- ✅ Disconnect any integration at any time (clears the connection; Phase 2 also clears tokens).
- ✅ Full local data deletion (`deleteAllLocalData()` clears every table incl. activities & connections + preferences).
- ✅ Local export preview (user-directed sharing only).

## Logging, analytics, telemetry
- ✅ No PHI in logs. No analytics/telemetry/advertising/crash-reporting SDKs in the MVP.
- ⬜ Phase 2: if analytics is added, it must be opt-in, carry **no** PHI (event names + non-identifying counters only), and go to a BAA-covered processor.
- ⬜ Push payloads (if cloud push is added) must contain no PHI (title/body generic; details fetched locally).

## Safety (non-diagnostic)
- ✅ `SafetyEngine` blocks medical over-claiming; every generated coaching string is sanitized.
- ✅ Wellness/fitness framing only; no diagnosis, no treat/cure/prevent claims.
- ✅ Concerning vitals emit a non-diagnostic `CLINICIAN_REFERRAL` activity recommending a qualified clinician.

## Access control & audit
- ✅ Append-only, non-PHI audit trail (`audit_events` table): consent grants/revocations, sync runs,
  plan generation, and data deletion are recorded as actions + timestamps only. Cleared on
  delete-all (the right to erase includes the trail), leaving a single fresh `ALL_DATA_DELETED` marker.
- ✅ Feature flags gate experimental data flows (`USE_MOCK_*`, `USE_HEALTH_CONNECT` — off by default).
- ⬜ Role-based access control for coach/client workflows (needed only when multi-user lands; BAA/DPA review first).

## Business Associate Agreement (BAA) surface
The device-local MVP minimizes BAA needs. Introducing any of these processors triggers a BAA/DPA review for a HIPAA-regulated deployment:

| Vendor / service | Role | BAA consideration |
|---|---|---|
| WHOOP (Developer API) | Health data source | Confirm data-sharing/enterprise terms; user OAuth consent governs access. |
| Google (Calendar / OAuth / any GCP) | Availability + identity | BAA available under Google Workspace/Cloud terms; keep scope to free/busy. |
| Firebase Cloud Messaging (if added) | Push delivery | No PHI in payloads; FCM is not a PHI processor if payloads are generic. |
| Crash/analytics vendor (if added) | Diagnostics | Avoid, or ensure BAA + strictly no PHI. |
| Cloud backend/hosting (if added) | Sync/coach data | Requires BAA (e.g. AWS/GCP/Azure BAA) + encryption at rest + access controls. |

## Pre-release gate (each release)
- [ ] `git grep` finds no secrets/tokens; `.env`/`local.properties` untracked.
- [ ] No new PHI written to logs, analytics, or crash tooling.
- [ ] New integration scopes are least-privilege and explained in `ConnectionsScreen`.
- [ ] Delete + disconnect flows still clear all new data stores.
- [ ] `assembleDebug`, `testDebugUnitTest`, `lintDebug` green.
