# Agent Instructions

## Product Context

**Spartan** is an Android-first personal health & fitness coaching app for WHOOP users (evolved from
the local-first *Vital Compass* tracker; same engine/persistence/safety substrate). It reads WHOOP
recovery/sleep/strain and generates a rules-based daily activity plan, schedules into calendar gaps,
and reminds the user. It handles sensitive health data, so privacy, safety, explainability, and user
control are hard requirements. Canonical decisions: [docs/Spartan_Decisions.md](docs/Spartan_Decisions.md).

## Privacy Model

- Local-first storage: on-device Room + DataStore; no cloud backend in the MVP.
- Network is allowed ONLY for authenticated, user-consented integration syncs (WHOOP, Google
  Calendar) over TLS. Default build uses mock/stub data with no network (`USE_MOCK_*` flags true).
- Do not add analytics, telemetry, advertising SDKs, or any cloud backend/login without explicit scope.
- Tokens live only in `SecureTokenStore` — never in Room, logs, analytics, or crash reports.
- Never commit secrets; only `.env.example` (placeholders) is tracked.
- Treat consent, disconnect, export, and delete controls as product-critical privacy features.
- Do not weaken local backup/data-transfer exclusions for health data without explicit approval.

## Health Safety Rules

The app must never say:

- "You have diabetes"
- "Your pancreas is overloaded"
- "You need medication"
- "Take X supplement dose"
- "You need statins"
- "Ignore your doctor"
- "Exercise through pain"

The app may say:

- "This value is above the normal clinical range."
- "This pattern is worth tracking and discussing with a clinician."
- "One value is not a diagnosis."
- "Here are safe behavior actions to try this week."
- "Confirm improvement using trends and repeat measurements."

Use supportive, non-shaming, non-alarmist language. Do not diagnose, prescribe medication, prescribe supplement dosing, override clinician guidance, or encourage training through pain.

## Clinical References vs Personal Targets

Always separate clinical reference ranges from personal optimization targets.

Examples:

- Fasting glucose 100-125 mg/dL may be described as above the normal clinical range, but the app must not diagnose diabetes or prediabetes.
- Vitamin D 25-OH at 23 ng/mL should not be called deficient by default. Treat it as above basic adequacy in many references but potentially below a user's personal target.
- TG/HDL ratio is a metabolic risk signal, not a diagnosis.
- BP 102/67 is normal.
- Resting heart rate 68 bpm is normal for many adults but may be above a user's personal fitness target.
- BMI 25.9 is in the adult overweight category, but emphasize trends, waist-to-height ratio, and sustainable behavior.

## Phase Boundaries

Phase 0:

- Repo inspection.
- Native Android project scaffolding if needed.
- Build configuration.
- Local-first privacy posture.
- Basic project instructions.

Phase 1:

- Local MVP foundation.
- Room/DataStore persistence.
- MVVM/Compose/Navigation foundation.
- Domain engines for metrics, insights, plans, reminders, review, and safety.
- Seed demo data.
- Required MVP screens at functional foundation level.
- Unit tests for core calculations and safety behavior.

Do not implement Phase 2 or future-phase work unless explicitly requested. Future phases include cloud sync, account/login, wearable integrations, external health APIs, advanced charts, production export/share flows, recurring reminder sophistication, and broader personalization.

## Build And Test Expectations

Before reporting stabilization complete, run when tooling is available:

```bash
./gradlew --no-daemon :app:assembleDebug
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:lintDebug
```

If local environment variables are needed, document them in the final response. If a command cannot run, report the exact blocker and do not imply it passed.

## Scope Control

- Do not add new product features during validation/stabilization.
- Fix compile, test, lint, documentation, and project setup issues minimally.
- Do not modify app source code when the requested step is documentation-only.
- Preserve existing user or generated changes unless explicitly asked to revert them.
