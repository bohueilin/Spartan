# Spartan

**Spartan** is an Android-first personal health and fitness coaching app for **WHOOP** users. It
reads your recovery, sleep, strain, HRV, resting heart rate, and respiratory rate, then generates a
simple **daily plan** of concrete activities — each with why it matters, step-by-step instructions,
an estimate, and a completion check-in. It schedules activities into open calendar windows, reminds
you at the right time, and treats your health data with a HIPAA-ready posture.

Spartan is disciplined, calm, and premium — a daily operating system for health improvement, not a
dashboard to decode.

> Spartan offers **wellness and fitness guidance, not medical advice**, and is not a medical device.
> It does not diagnose. For any health concern, contact a qualified clinician.

## Status

- Rebranded and evolved from the local-first Android tracker formerly named *Vital Compass*.
- **Mock-first**: ships with a clearly-labeled sample WHOOP client and a stub Google Calendar client,
  so the whole experience runs with **no credentials and no network**. Real OAuth integrations are
  behind feature flags (Phase 2).
- Rules-based coaching engine (transparent, testable) with a seam for a future AI recommendation source.

## Stack

Native Android · Kotlin 2.0.21 · Jetpack Compose + Material 3 · MVVM · Hilt · Room (v4) · DataStore ·
WorkManager · Navigation Compose · KSP. `minSdk 26`, `target/compileSdk 35`, JDK 17.

## Architecture at a glance

```
Compose UI → MainViewModel → domain engines (CoachingEngine, MetricEngine, SafetyEngine, …)
                                   ↓
                     HealthRepository ── Room (local, encrypted-backup-excluded)
                                   ├── WhoopClient      (MockWhoopClient | RealWhoopClient)
                                   ├── CalendarClient   (StubCalendarClient | GoogleCalendarClient)
                                   └── SecureTokenStore  (InMemory | Encrypted, Phase 2)
```

Design and rationale:
[docs/Spartan_PRD.md](docs/Spartan_PRD.md) ·
[docs/Spartan_Architecture.md](docs/Spartan_Architecture.md) ·
[docs/Spartan_Implementation_Plan.md](docs/Spartan_Implementation_Plan.md) ·
[docs/Spartan_Codebase_Audit.md](docs/Spartan_Codebase_Audit.md) ·
[docs/Spartan_Decisions.md](docs/Spartan_Decisions.md) (canonical vocabulary) ·
[docs/SECURITY_PRIVACY_CHECKLIST.md](docs/SECURITY_PRIVACY_CHECKLIST.md).

Launch collateral:
[docs/PLAY_STORE_LISTING.md](docs/PLAY_STORE_LISTING.md) ·
[docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md) ·
[docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) ·
[docs/ACCESSIBILITY.md](docs/ACCESSIBILITY.md) ·
[docs/CERT_PINNING_RUNBOOK.md](docs/CERT_PINNING_RUNBOOK.md) ·
[docs/Spartan_Enhancements.md](docs/Spartan_Enhancements.md) (backlog with statuses).

CI: `.github/workflows/ci.yml` runs unit tests + Kover coverage, lint, debug/release assembly
(with the R8 `mapping.txt` artifact), and an emulator job for instrumentation tests (Room
migrations, Compose smoke + accessibility). Dependabot watches dependency CVEs. Mock mode means
CI needs zero secrets.

## Build & test

Requires JDK 17 and the Android SDK (platform 35). On this machine:

```bash
export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/android-sdk"

./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:lintDebug
./gradlew --no-daemon :app:assembleRelease   # minified (R8) release build; signing per docs/RELEASE_CHECKLIST.md
```

Run Gradle tasks **serially** for this Hilt/KSP project — parallel build/test/lint can race on
generated files. The app builds and runs with **no** configuration: mock data is the default.

## Configuration (optional, for real integrations)

Copy `.env.example` and mirror the values you need into `local.properties` (both gitignored). They
become `BuildConfig` fields. Feature flags default to mock:

```properties
SPARTAN_USE_MOCK_WHOOP=true      # false => real WHOOP OAuth (needs WHOOP_CLIENT_ID/SECRET)
SPARTAN_USE_MOCK_CALENDAR=true   # false => real Google Calendar (needs GOOGLE_OAUTH_CLIENT_ID)
WHOOP_CLIENT_ID=...
GOOGLE_OAUTH_CLIENT_ID=...
```

**Never commit secrets.** Only `.env.example` (placeholders) is tracked.

## Privacy

All app data is stored on-device (Room + DataStore), excluded from cloud backup and device transfer.
Tokens are never stored in Room or logged. No analytics/telemetry/ads SDKs. Consent is required
before connecting WHOOP or Calendar; you can disconnect or delete all data at any time. See the
[security & privacy checklist](docs/SECURITY_PRIVACY_CHECKLIST.md).
