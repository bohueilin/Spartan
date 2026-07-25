# Spartan Coach — Personalized Training Hub design

**Product vision:** *Increase human longevity by providing personalized habit building and an
engaging community.* Spartan's job is not a prettier dashboard — it is turning each person's own
physiology into small, durable daily habits, and (later) letting people carry those habits
together. Everything below is data-driven, grounded in published evidence, and honest about
uncertainty. Wellness guidance, never medical advice.

## 1. The three pillars

| Pillar | What it does | Where it lives |
|---|---|---|
| **Know your numbers** | Educates the top-5 metrics against reference bands for the user's age and sex, next to their own baseline | Coach tab → "Your healthy ranges" (`ReferenceRanges`) |
| **Train toward a goal** | Captures a personal goal (weight, sleep/recovery, stress), validates it against safe evidence-based rates, tracks progress, and bends the daily plan toward it | Coach tab → Goal card (`GoalEngine`) |
| **Defuse stress before it lands** | Finds weekday patterns in the user's own data and lets them declare recurring high-pressure windows; schedules a 5-minute breathwork before each one | Coach tab → Stress insights (`StressPatterns`) |

## 2. Personalization buckets: age + sex, deliberately not race

Reference bands are keyed to **age bracket and sex at birth** (both optional, on-device only).
We deliberately **do not collect or key anything to race**:

- There are no credible published reference ranges for wearable metrics (HRV, RHR, sleep
  architecture) keyed to race; the demographic variance that exists is dwarfed by individual
  variance and confounded by social, not biological, factors.
- Clinical medicine is actively removing race-based corrections (eGFR 2021, pulmonary function
  2023) because they misdirected care. A wellness app must not re-import that mistake.
- Spartan's strongest personalization is the user's **own baseline** — 45 days of their real
  data beats any population table. Buckets only frame education; baselines drive coaching.

## 3. Evidence base for the reference bands (`ReferenceRanges.kt`)

All copy is wellness-framed ("typical range in published cohorts"), passes `SafetyEngine`, and
links the user back to their own trend. Sources encoded as code comments next to each band:

| Metric | Basis |
|---|---|
| Resting HR | AHA normal-adult span (60–100 bpm) with fitness-forward bands from large cohort percentiles by age/sex; women typically a few bpm higher |
| HRV (RMSSD) | Age-declining medians from wearable-cohort publications (≈3–5%/decade decline); framed as "median for your age bracket", individual baseline emphasized |
| Sleep duration | AASM/NSF consensus: 7–9 h for adults 18–64 |
| Sleep consistency | Sleep-regularity literature: keeping bed/wake within ±30–45 min is associated with better recovery than an equal amount of irregular sleep |
| Recovery score | WHOOP-proprietary; educated as a distribution to read (green/yellow/red mix over weeks), not a clinical range |

## 4. Goals (`GoalEngine.kt`) — SMART with hard safety caps

Three launch goal types, each with an evidence-based rate cap; a goal that exceeds its cap is
never refused rudely — Spartan proposes the nearest safe version:

| Goal | Example | Safety cap (source) | Progress signal |
|---|---|---|---|
| `WEIGHT_LOSS` | "Lose 10 lb in 4 weeks" | ≤ 2 lb/week (CDC gradual-loss guidance) → counter-offer "10 lb in 6 weeks" | 7-day-averaged WEIGHT readings vs baseline |
| `SLEEP_RECOVERY` | "Improve sleep recovery 10% in 3 weeks" | ≤ 15% per 3 weeks vs 14-day baseline | trailing-7-day sleep-performance / recovery mean vs baseline |
| `STRESS_RESILIENCE` | "Reduce stress around my Tue/Thu 11 AM meetings" | n/a (habit goal) | breathwork adherence + HRV trend vs baseline |

Each active goal emits **plan modifiers** the existing engines consume: weight goals add Zone-2
volume and protect 2 weekly strength sessions; sleep goals emphasize the wind-down and earlier
caffeine cutoff; stress goals insert pre-window breathwork. The daily plan stays recovery-gated —
a goal never overrides a REST day (goals adjust *emphasis*, `SafetyEngine` still owns the floor).

## 5. Stress patterns (`StressPatterns.kt`) — honest about the data we have

The WHOOP CSV export has **no intraday stress stream**, so Spartan does not pretend to see one:

1. **Weekday effect (computed):** mean recovery/HRV following each weekday vs the personal mean,
   with minimum-sample gating (n ≥ 3 weeks) and an effect-size floor before anything is shown —
   e.g. "Recoveries after Tuesdays run 9 points below your average."
2. **Declared pressure windows (user input):** day-of-week + time window (e.g. Tue/Thu
   11:00–12:00). Spartan schedules a 5-minute guided breathwork reminder 5 minutes before each
   window and adds it to that day's plan.
3. **Calendar (future):** with free/busy consent, meeting-dense recurring blocks can suggest
   windows automatically. The seam exists (`CalendarClient`); off by default.

## 6. Community — roadmap, privacy-first

The longevity vision needs an engaging community; the local-first architecture is the feature,
not the obstacle. Phased and strictly opt-in:

1. **Share cards (no backend):** export a rendered week/goal summary image the user shares
   anywhere. Nothing leaves the device except what the user posts.
2. **Squads (backend, opt-in):** small accountability groups sharing *derived* signals only
   (streaks, adherence %, goal milestones) — never raw physiology. Requires the compliance work
   in [COMPLIANCE.md](COMPLIANCE.md) to be revisited (HBNR, GDPR transfers) before build.
3. **Challenges:** time-boxed habit challenges built on the same derived-signal rule.

## 7. What ships in this increment (Android)

- `ReferenceRanges`, `GoalEngine`, `StressPatterns` engines + unit tests
- Room v7: `goals`, `pressure_windows` tables; profile gains an in-app sex/age editor
- Plan tab becomes **Coach**: goal card + setup sheet, stress insights, healthy-ranges education,
  weekly plan retained below
- Pre-window breathwork reminders; goal-aligned daily-plan emphasis
- iOS/SpartanKit port: follow-up increment (domain engines are pure and port cleanly)
