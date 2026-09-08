# Overall design review (2026-09-07)

A product/UX review of the whole app, separate from the defect audit in `AUDIT_2026-08-25.md`.
Every structural claim below was verified against the tree before it was written down.

## Verified structural findings

| Finding | Evidence |
|---|---|
| **`PlanScreen` was dead code** — the old Plan tab was folded into Coach and the screen was left behind, referenced only by a stale KDoc | fixed in this change |
| **`trendNotes` is computed, stored, and never rendered** on either platform. The engine writes real sentences ("HRV is trending below your recent baseline") and no pixel shows them | `CoachingModels.kt:95-117`; fixed in this change |
| **Two logging paths both write `WorkoutLog`** — `logExerciseDebrief` (Today check-off) and `completeWorkout` (Coach → Log → `WorkoutCompletionScreen`). A user can log the same session twice and inflate their own adherence | `MainViewModel.kt:820, 829` |
| **No `TopAppBar` and no back affordance anywhere** — zero hits across the whole `ui/` package, over eight non-tab destinations. System back is the only exit | `ui/` |
| **No typography system.** `typography = MaterialTheme.typography` (stock M3), compensated by **71 ad-hoc `FontWeight.` overrides** across the screens. No `Typography.kt` exists | `Theme.kt:51` |
| **The bottom bar misreports location** — `parentTabRoute` maps `connections` → `today`, and the readiness card opens `detail/RECOVERY_SCORE` which maps to `metrics`, so tapping a card on Today selects the Metrics tab | `SpartanRoot.kt:300-301` |

## The loop's real defect: extraction without payback

The morning leg is genuinely strong — digest → ring reveal → 2–4 cards → tactile check-off → day
complete. The evening and weekly legs are **write-only**:

- The reflection sheet's only consumer is `alreadyAnsweredToday`. `ReviewEngine.summarize` does not
  take reflections as a parameter. The user reports how their day felt, nightly, and the app never
  mentions it again.
- `nextWeekFocus` is a string the user reads; nothing consumes it. The daily engine takes exactly
  one bit from the past (`options.painFlag`).
- `daily_activities` rows persist per date, but the UI only ever reads today. Yesterday is
  unreachable; seven days collapse to seven booleans.

**What the app asks:** name, height, age, notification permission, CSV import, per-activity
check-off, a 3-field debrief per exercise, a nightly mood plus free-text note, goal setup, pressure
windows, demographics, and manual entry for 19 lab metrics.
**What it gives back:** one number, 2–4 cards, one of five fixed headlines, a video link, seven
dots, and an 8-week projection range. Nothing typed with thumbs ever returns in recognizable form.

## Ranked opportunities

1. **A history surface.** Every row already exists in Room (`daily_activities` by `dateEpochDay`,
   workout logs, reflections) and none of it is browsable. Cheapest possible fix for the loop's
   biggest hole — a habit app that cannot show you last Tuesday is a daily widget.
2. **"Why this plan today."** Partly shipped now (trend notes under the ring). Finish it: expose the
   deltas that fired each rule and put `ruleId` provenance on the expanded card. This is the gap
   between the README's "not a black box" and the UI, and the honest answer to "why not just use
   the WHOOP app" — WHOOP gives the score, Spartan should give the derivation.
3. **Reflection payback.** Pass reflections into `ReviewEngine.summarize`. Rules-based correlation
   is enough: "your three Tough days all followed nights under 6h." One parameter, a few rules.
4. **Sleep as a first-class object.** The app ingests performance, duration and debt, and prescribes
   sleep-hygiene activities, but there is no bedtime target and no "go to bed at 22:40 to clear 1.4h
   of debt." The highest-leverage lever is currently the thinnest surface, and it is the natural
   anchor for the empty evening half of the loop.
5. **Plan negotiation.** Snooze / skip / find-a-time are all subtractive. There is no "give me
   something else" or "I only have 20 minutes", though `estimatedMinutes` + `ActivityCategory` +
   `prioritizeAndCap` already have what a swap needs.

## Design-system fixes, in order

1. Write a real `Typography.kt` and delete the 71 weight overrides.
2. Extract `SpartanCard` and `ScreenHeader` — `OutlinedCard(shape = RoundedCornerShape(Radius.card))`
   is hand-written ~24 times and five screens repeat the same header row.
3. Add a `TopAppBar` with back to the eight non-tab destinations; fix `parentTabRoute`.
4. Finish `Spacing` adoption in `Screens.kt` (raw dp throughout, unlike the newer files).

Also unresolved at system level: no tinted-surface token (state alphas are hand-picked at
0.10/0.12/0.14/0.18/0.32 in five places), no spring tokens though springs are used, and effectively
no preview coverage — which is why the dark-mode onboarding contrast bug could only be caught on a
physical device.

## Structural question for the owner

The reviewer's recommendation is **three tabs: Today · Body · You** — "Body" absorbing Metrics +
Review + trends (the same question at two time scales), "You" absorbing Coach's configure-once
content plus Connections, Reminders and Privacy. Coach today is five unrelated things in a scroll,
three of which are configure-once; a tab visited twice a year is a settings page. This is a real
restructure, not a cleanup — it needs your decision before anyone starts.
