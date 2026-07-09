> **Superseded.** This is the original *Vital Compass* PRD, kept for historical provenance.
> The current product is **Spartan** — see [docs/Spartan_PRD.md](docs/Spartan_PRD.md) and the
> canonical [docs/Spartan_Decisions.md](docs/Spartan_Decisions.md). Spartan reuses this app's
> engines/persistence/safety substrate and pivots to WHOOP-driven daily coaching.

# Vital Compass PRD (original — superseded by Spartan)

## Product Mission

Vital Compass is a local-first Android personal health intelligence app that helps a user track health signals, understand what metrics mean, create a safe weekly fitness plan, receive local reminders, confirm improvement over time, and control local data through export and deletion.

The product should feel calm, premium, native to Android, accessible, and supportive. It must avoid shame, panic, diagnosis, or prescriptive medical advice.

## Seed Demo Health Data

Initial demo data:

- Fasting glucose: 108 mg/dL
- Triglycerides: 134 mg/dL
- HDL: 41 mg/dL
- TG/HDL ratio: 3.26
- Vitamin D 25-OH: 23 ng/mL
- Resting heart rate: 68 bpm
- Weight: 81.16 kg
- BMI: 25.9
- BP: 102/67
- ApoB: pending
- Lp(a): pending
- CAC: pending scheduling

## Personal Diagnostic Record Requirement

The MVP should support a local diagnostic data log for structured personal health records, not only numeric lifestyle metrics. These records remain local-first and user-controlled.

May 2026 personal records to support:

| Diagnostic test | Date logged | Result as entered | Clinical/reference status or personal target |
| --- | --- | --- | --- |
| Lipoprotein(a) | March 27, 2026 | 17 | Personal target: below 30, unit/source-specific |
| CT calcium scoring | May 19, 2026 | 0, MESA 0th percentile | Personal target: zero calcium score |
| 12-lead EKG | May 2026 | Normal | Reported clear/normal result |
| Chest X-ray, 2 views | May 26, 2026 | Normal / clear | Reported clear/normal result |
| Fasting glucose | March 2026 baseline | 108 mg/dL | Above the normal clinical range; one value is not a diagnosis |
| Triglyceride/HDL ratio | March 2026 baseline | 3.26 | Elevated metabolic risk signal, not a diagnosis |

Product behavior:

- Support both numeric diagnostic records and qualitative diagnostic records such as "normal", "clear", "pending", or "scheduled".
- Store date logged, source/result label, value or text result, unit when available, clinical/reference status, personal target, and user notes.
- Separate source-reported findings from app interpretation.
- Group diagnostic records into understandable themes such as cardiovascular risk markers, metabolic markers, imaging, and symptom/context notes.
- Treat Lp(a), CAC, EKG, and chest X-ray records as useful context, while avoiding claims that future cardiovascular or pulmonary risk is eliminated.
- Keep ApoB as a pending cardiovascular marker that may add long-term lipid particle context when available.
- Allow the user to track stress/anxiety context and symptoms alongside metabolic markers without claiming a single cause for fasting glucose changes.
- Encourage repeat measurements, trend review, and clinician discussion for persistent or concerning patterns.
- Never tell the user to start medication, request specific labs as a directive, ignore clinician guidance, or treat anxiety/stress care as a guaranteed metabolic intervention.

## Required Screens

MVP screens:

1. Onboarding
2. Today dashboard
3. Metrics list
4. Metric detail
5. Add/edit metric
6. Weekly fitness plan
7. Workout completion
8. Weekly review
9. Reminder settings
10. Privacy/export/delete settings

Core bottom tabs:

- Today
- Metrics
- Plan
- Review
- Settings

## Data Model

Local Room entities:

- UserProfile
- MetricEntry
- Target
- WorkoutSession
- Reminder
- WeeklyReview, if needed

Metric types:

- FASTING_GLUCOSE
- TRIGLYCERIDES
- HDL_C
- TG_HDL_RATIO
- VITAMIN_D_25OH
- RESTING_HEART_RATE
- SYSTOLIC_BP
- DIASTOLIC_BP
- WEIGHT
- WAIST_CIRCUMFERENCE
- BMI
- WAIST_TO_HEIGHT_RATIO
- APOB
- LPA
- CAC
- EKG_12_LEAD
- CHEST_XRAY
- DIAGNOSTIC_IMAGING
- DIAGNOSTIC_NOTE
- SLEEP_DURATION
- EXERCISE_MINUTES
- STRENGTH_SESSIONS
- CUSTOM

## Domain Engines

MetricEngine:

- Validate values.
- Compute BMI, TG/HDL, and waist-to-height ratio.
- Compare current values to clinical ranges and personal targets separately.

InsightEngine:

- Generate safe insight cards.
- Include explanation, why it matters, safe actions, clinician discussion triggers, and confidence.

PlanEngine:

- Generate weekly fitness plans.
- Include Zone 2, strength, mobility, recovery, and review.
- Adapt based on adherence, RPE, and pain flag.
- Avoid unsafe progression.

ReminderEngine:

- Handle local notification scheduling.
- Avoid duplicate reminders.
- Support permission denied state.

SafetyEngine:

- Prevent diagnostic overclaiming.
- Block unsafe health advice.
- Validate generated copy against blocked phrases.

## Safety Boundaries

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

Clinical reference ranges must remain separate from personal optimization targets. The app should explain uncertainty and encourage repeat measurements and clinician discussion when appropriate.

## Phase Plan

Phase 0:

- Inspect repository.
- Create native Android Kotlin project if empty.
- Configure Android build, Compose, Material 3, Room, DataStore, MVVM, Navigation Compose, Hilt, WorkManager, and local notifications foundation.
- Document build/test expectations and privacy constraints.

Phase 1:

- Implement local MVP foundation.
- Add local Room entities and repository.
- Seed demo data.
- Add required MVP screens.
- Add deterministic domain engines.
- Add unit tests for calculations, classification, safe insight generation, plan generation, adherence, and reminder deduplication.
- Validate build, unit tests, and lint.

Future phases only when explicitly requested:

- Cloud sync or account systems.
- Wearable or external health API integrations.
- Advanced trend charts.
- Production file export/share flows.
- More sophisticated recurring reminder scheduling.
- Broader personalization and plan editing.

## Acceptance Criteria

- App builds successfully with Gradle.
- Unit tests pass.
- Lint passes or any remaining warnings are documented.
- No cloud backend, remote storage, login or account systems, analytics SDKs, telemetry SDKs, advertising SDKs, network calls, external health APIs, secrets, or API keys are present.
- Seed demo health data appears locally.
- Required MVP screens are reachable.
- Clinical references and personal targets are displayed or modeled separately.
- Health copy avoids blocked phrases and unsafe advice.
- Diagnostic records can represent numeric and qualitative results while keeping source-reported findings separate from app interpretation.
- Weekly review calculates adherence, Zone 2 minutes, strength sessions, latest and 7-day averages where data exists, latest BP, latest fasting glucose, improvements, attention items, and next focus.
- Local data can be deleted.
- Export behavior exists at MVP level or is clearly documented as incomplete.

## Test Plan

Unit tests:

- BMI calculation.
- TG/HDL calculation.
- Waist-to-height ratio calculation.
- Metric validation and clinical classification.
- Personal target comparison.
- Safe insight generation.
- Blocked medical overclaiming.
- Weekly plan generation.
- Adherence calculation.
- Weekly review summary.
- Reminder duplicate prevention.

Build validation:

- `./gradlew --no-daemon :app:assembleDebug`
- `./gradlew --no-daemon :app:testDebugUnitTest`
- `./gradlew --no-daemon :app:lintDebug`

Run Gradle validation tasks serially for this Hilt/KSP project. Starting build, test, and lint in parallel can race on generated KSP files.

Manual validation:

- Launch app on emulator.
- Complete onboarding.
- Confirm seeded metrics are visible.
- Open metric detail.
- Add a metric entry.
- View weekly plan.
- Log a workout.
- Review weekly summary.
- Configure reminders and permission denied state.
- Export preview and delete local data.
