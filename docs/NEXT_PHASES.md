# Spartan — Next Phases (research-driven)

The enhancement plan driven by the comprehensive design/retention research in
[research/FITNESS_APP_DESIGN_2026.md](research/FITNESS_APP_DESIGN_2026.md) and
[research/HABIT_RETENTION_UX_2026.md](research/HABIT_RETENTION_UX_2026.md), executed against the
prompts in [UX_ROADMAP_PROMPTS.md](UX_ROADMAP_PROMPTS.md).

## Phase R1 — SHIPPED (the overnight build)

Everything below was chosen because the research ranked it highest evidence-per-effort, and is
implemented + build-validated:

| Feature | Research basis |
|---|---|
| **Notification action buttons** (Done / Snooze 1 hour on activity reminders, persisting through the real repository — Robolectric-tested) | Highest-evidence engagement lever in industry benchmarks (NN/g; Airship/Braze action-button data); works the plan without an app launch |
| **Consistency strip** (7 calm dots, oldest→today; no chain, no loss state) | Silverman & Barasch (JCR 2023): broken-streak salience depresses engagement; Lally 2010: single misses don't break habit formation; Gentler Streak (Apple Watch App of the Year) as the consistency-without-streaks proof |
| **Check-off haptics + spring physics** | The single most-cited "feel" differentiator across award-winning daily-loop apps |
| **Readiness ring sweep + count-up on reveal** | The morning-ritual moment; Apple Fitness rings evidence that the metric's *arrival* is the emotional beat |
| **Pull-to-refresh** on the check-in | Baseline expectation in every daily-loop app studied |
| **Greeting + day-complete moment** (calm, no confetti) | Self-efficacy framing over reward framing (SDT literature); an ending, not a firework |
| **Time-of-day flow** (cards ordered morning→evening within priority) | Morning-routine anchoring evidence |
| **Warm deep-link fix** (`onNewIntent` → NavController) | Notification taps must never no-op — trust in the loop |

## Phase R2 — next build night (prompts ready in UX_ROADMAP_PROMPTS.md)

1. **Post-value notification permission ask** — request POST_NOTIFICATIONS right after the first
   plan renders (not at launch): the research's #2 evidence-per-hour item; today the prompt only
   appears from Reminder settings.
2. **Evening reflection sheet** — the 19:00 nudge deep-links into a two-tap "how was today" that
   feeds `ReviewEngine` (already has the fields).
3. **Interactive trend charts with projection overlay** — Vico charts where the "Where this can
   take you" band renders *on* the recovery trend; scrubbing reads values aloud (TalkBack).
4. **Plan editor (swap an activity)** — same-category alternatives, honoring the safety rules;
   autonomy-supportive per SDT findings.
5. **Copy pass on notifications** — apply the research's three principles (data-leading,
   autonomy-supportive, gain-framed); rewrites for our exact strings are in the retention doc.

## Phase R3 — platform depth (device-dependent)

iOS haptics vocabulary (Core Haptics), Live Activities + Dynamic Island for in-progress
activities, WidgetKit + watchOS per [APPLE_DESIGN_AWARD_RUNWAY.md](APPLE_DESIGN_AWARD_RUNWAY.md);
Android: Wear OS tile, App Shortcuts/Assistant, baseline profiles once a bench device exists.

## Deliberately rejected (research-backed)

- **Streak counters with loss states** — the strongest *negative* evidence in the whole research
  pass; our consistency strip stays lossless.
- **Confetti/celebration animations** — every studied award-winner in the calm-health category
  avoids them; Spartan's completion moment stays quiet.
- **Daily-guilt notification copy** — churn-correlated; the evening nudge stays optional-feeling
  ("Still time for an easy win", never "Don't lose your progress").
