# Spartan — Health-Data Compliance Pack

**Audience:** store reviewers, future partners/counsel, and ourselves at each release.
**Posture in one sentence:** Spartan is a *local-first consumer wellness app* — it has no backend,
no accounts, no analytics, and never transmits health data; the developer never receives, stores,
or can access any user's data, so the strongest privacy control is architectural, not procedural.

Companion docs: [PRIVACY_POLICY.md](PRIVACY_POLICY.md) (user-facing) ·
[SECURITY_PRIVACY_CHECKLIST.md](SECURITY_PRIVACY_CHECKLIST.md) (engineering posture) ·
[PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md) §6 (Play Health-apps declaration + Data-safety form)
· [ios/docs/IOS_RELEASE_CHECKLIST.md](../ios/docs/IOS_RELEASE_CHECKLIST.md) (App Store privacy
labels). Every claim below is verified against code; file references are the proof points.

---

## 1. Data inventory (PII / PHI-like classification)

| Category | Examples | Source | Where it lives | Protection | Erasure path |
|---|---|---|---|---|---|
| Wearable physiology (**special category** / PHI-like) | Recovery %, HRV, resting HR, sleep stages/debt, respiratory rate, strain, energy | WHOOP CSV import or (flag-gated) WHOOP OAuth; sample data otherwise | Room `whoop_cycles`, `whoop_workouts`, `metric_entries` (app-private storage) | Android app sandbox + OS file-based encryption; **excluded from cloud backup & device transfer** (`data_extraction_rules.xml`, `allowBackup=false`) | Disconnect removes the raw source tables (`HealthRepository.clearImportedWhoopSource`); Settings → Privacy → *Delete local data* removes everything (`deleteAllLocalData`) |
| Behavior journal (health-adjacent) | caffeine / alcohol / late-meal flags | WHOOP CSV journal file | `whoop_cycles` columns | same as above | same as above |
| Manually logged biometrics | weight, BP, glucose, lipids | user entry | `metric_entries` | same as above | Delete local data |
| Profile (PII) | display name, height | onboarding | `user_profiles` | same as above | Delete local data |
| Plan & check-in state | activities, completions, RPE/pain debriefs | generated + user | `daily_activities`, `workout_sessions` | same as above | Delete local data |
| Preferences | reminders, onboarding flags | user | DataStore | excluded from backup | Delete local data (`PreferencesStore.clear`) |
| Consent & audit records (deliberately **non-PHI**) | "WHOOP_CONNECTED", timestamps, counts | app | `integration_connections`, `audit_events` | same as above | Delete local data (trail cleared; single fresh marker) |
| OAuth tokens (credentials; Phase-2 only) | WHOOP/Google refresh tokens | OAuth flow | Keystore-backed `EncryptedSharedPreferences` (`EncryptedTokenStore`) | AES-256-GCM, hardware-backed | Disconnect / Delete local data |

**Never stored anywhere:** analytics identifiers, advertising IDs, location, contacts, calendar
event contents (free/busy only, Phase 2), crash dumps, server-side anything.

## 2. HIPAA analysis (United States)

Spartan is distributed **direct-to-consumer**. HIPAA attaches to *covered entities* (providers,
plans, clearinghouses) and their *business associates* — not to consumer apps an individual
chooses for their own wellness data. Spartan is therefore **not a HIPAA-covered entity or
business associate**, and consumer-held WHOOP exports are not HIPAA PHI in the app.
We nonetheless maintain a **HIPAA-ready engineering posture**
([SECURITY_PRIVACY_CHECKLIST.md](SECURITY_PRIVACY_CHECKLIST.md)) so a future coach/clinic
deployment (which *would* create covered/BA relationships and require BAAs) inherits no debt.

**What does apply in the US instead:**
- **FTC Health Breach Notification Rule** (16 CFR 318, enforced against consumer health apps):
  applies to vendors of personal health records. Spartan's exposure is minimized structurally —
  there is no cloud copy to breach and no third-party disclosure; a "breach" would require device
  compromise outside our boundary. If any backend is ever added, HBNR notification duties activate
  and this section must be revisited **before** launch of that backend.
- **FTC Act §5** (deceptive claims): our privacy policy states only what the code does; the
  pre-release gate below re-verifies each claim.
- **State consumer-health laws** (e.g. Washington *My Health My Data*): "collection" is limited to
  on-device processing the consumer initiates; there is no sale or sharing of consumer health data.
  The in-app consent screen (`ConnectionsScreen`) satisfies the affirmative-consent pattern.

## 3. GDPR / EU & UK analysis

- **Controller analysis.** All processing of health data occurs **on the user's device under the
  user's control**; the developer receives nothing (no server, no telemetry — verifiable: the app
  makes zero network calls in its shipping default; the only egress is the user tapping a YouTube
  link, below). For the on-device data the user is the natural holder; to the extent the developer
  is a controller of anything, it is of **no personal data at all**. This is the maximal
  data-minimization position (Art 5(1)(c)).
- **Art 9 special-category data.** Wearable physiology is "data concerning health". Processing
  basis: **Art 9(2)(a) explicit consent**, implemented as an explicit, granular, informed action —
  the user must either run the OAuth consent or deliberately pick their own export files in the
  system picker; scope-by-scope plain-language explanation on the Connections screen; consent
  recorded locally (`integration_connections.consentGrantedAtMillis`).
- **Withdrawal (Art 7(3)):** *Disconnect* stops all processing of the source and removes the raw
  imported tables (`HealthRepository.clearImportedWhoopSource`); the app falls back to labeled
  sample data. Already-normalized readings deliberately remain as the user's own history —
  withdrawal of consent stops processing, it does not destroy the user's data behind their back —
  and one tap in Privacy erases everything. Withdrawal is as easy as the grant, on the same screen.
- **Data-subject rights, mapped to shipped features:**

  | Right | In-app mechanism |
  |---|---|
  | Access (Art 15) | Metrics/Review screens show all stored data; local export (Settings → Privacy) |
  | Rectification (Art 16) | Edit metric entries in-app |
  | Erasure (Art 17) | Settings → Privacy → Delete local data — clears every Room table (incl. `whoop_cycles`/`whoop_workouts`), DataStore, tokens, the audit trail itself, WorkManager's job database (incl. reminder text), shown notifications, the home-screen widget, and disarms the background workers that would repopulate the DB |
  | Portability (Art 20) | Local export; the user also retains their original WHOOP CSVs — data arrives portable and stays portable |
  | Restriction / objection (Arts 18/21) | Disconnect (stops processing new data), sample mode |
- **Storage limitation:** data persists only until the user deletes it — appropriate for a
  self-tracking tool whose purpose is longitudinal trends the user owns.
- **International transfers:** none. No data leaves the device.
- **DPIA:** not triggered (no large-scale processing, no systematic monitoring by a controller;
  WP29 criteria unmet). This document stands as the lightweight record of that assessment.
- **EU MDR / FDA boundary:** Spartan is general wellness — fitness/recovery guidance with no
  diagnosis or treatment claims. The `SafetyEngine` machine-blocks medical claims in every
  generated string; concerning readings produce a non-diagnostic "see a qualified clinician"
  nudge. This keeps Spartan outside MDR Annex/FDA device definitions (general-wellness policy).

## 4. Threat model (leakage vectors → mitigations)

| Vector | Exposure | Mitigation (shipped) |
|---|---|---|
| Cloud backup / device transfer | Health DB copied off-device | `allowBackup=false` + full exclusion rules for DB/prefs/DataStore (both cloud & d2d); iOS import store is `FileProtectionType.complete` + `isExcludedFromBackup` |
| Device loss/theft | Reading app-private files | OS file-based encryption + app sandbox; lock-screen is the user's control. App-level DB encryption (SQLCipher) documented as roadmap, deliberately not day-one |
| Logs / crash dumps | Values in logcat or crash SDKs | No analytics/crash SDKs; `DebugLog` is debug-only, in-memory, counts-only; release logging stripped by R8 |
| Notifications on lock screen | Activity titles visible | `VISIBILITY_PRIVATE` + generic public version ("You have a reminder") on Android; iOS bodies carry no health rationale and redacted previews show a neutral placeholder |
| App switcher / screenshots | Recents thumbnail of health screens | Judged acceptable for a consumer wellness app (users screenshot their own data); revisit with a FLAG_SECURE toggle if users ask |
| Share/export | User-initiated share of export text | Only via explicit share-sheet action; nothing automatic |
| Deep links (`spartan://`) | Injection / data exfil | Links carry **no data in either direction** — they only route to today/connections; OAuth redirect is PKCE-protected |
| Widget | Health data on home screen | Widget shows next activity title only, never metric values |
| Tapping a follow-along video | YouTube learns the video you opened | Explicit user tap opens the external app; the URL contains a video id, never health values. Documented in the privacy policy |
| Supply chain | Dependency CVEs | Dependabot watch; pinned versions; R8; no dynamic code loading |

## 5. Residual risks & decisions (owned, not hidden)

1. **No app-level database encryption** — relies on OS file-based encryption + sandbox. Roadmap:
   SQLCipher/EncryptedFile evaluation before any multi-user or backup feature.
2. **`security-crypto` is an alpha artifact** (tokens only, Phase-2 path) — pinned + Dependabot;
   swap to stable when released.
3. **Certificate pinning** deliberately deferred until the first live-API release (a stale pin
   bricks sync; there is no live traffic in the shipping default). Runbook ready.
4. **Screenshots/recents not blocked** — deliberate UX choice, documented above.

## 6. Store-form consistency

- **Google Play:** Data-safety form = *no data collected, no data shared* (accurate because
  nothing leaves the device); Health-apps declaration = health & wellness, positioning statement in
  [PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md) §6.
- **Apple:** privacy nutrition label = **Data Not Collected**; `PrivacyInfo.xcprivacy` declares no
  tracking, no collected data types, and required-reason API usage only; health data never written
  to iCloud (5.1.3) — import store excluded from backup.
- Both listings avoid medical claims; copy tone is enforced by the same SafetyEngine wording rules
  the app itself uses.

## 7. Pre-release compliance gate (run every release)

- [ ] Privacy policy claims re-verified against code (grep new network calls: the default build
      must make none).
- [ ] New tables/files added since last release are covered by: backup-exclusion rules, delete-all,
      disconnect (if source-scoped), and the export.
- [ ] Data-safety / nutrition-label answers still accurate (any new SDK = re-answer).
- [ ] SafetyEngine passes on all new user-facing strings; no medical claims in store copy.
- [ ] `git grep` finds no secrets; keystores/local.properties untracked.
- [ ] Threat-model table reviewed against the diff (new vectors?).
