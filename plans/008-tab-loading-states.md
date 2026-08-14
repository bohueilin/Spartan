# 008 — Loading states for Metrics, Coach, Plan, Review

- **Status**: DONE
- **Commit**: cad6100
- **Severity**: Tier 3
- **Scope**: 3 files (Screens.kt, CoachScreen.kt, CheckInScreen.kt — to export the skeleton)

## Problem

Only Today has a loading state. Metrics, Coach, Plan, and Review render zeroed/empty layouts while data loads — content pops in with no reserved space. (Review's worst symptom, the confident "0%", was fixed by plan 005; this plan covers the remaining flash-of-empty on all four tabs.)

## Target

Each of the four tabs shows the same skeleton language as Today (rounded 10dp blocks in `surfaceVariant`) while its data is loading, and never shows a skeleton on sync failure. Reuse — do not reinvent.

## Conventions to follow

- Exemplar: `LoadingPlan` / `SkeletonRow` / `Skeleton` in `app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt:613-631` and its guard at `:150-152` (`loading = ... && !state.syncFailed`).
- Loading predicate: prefer an explicit `state.isLoaded`-style flag if `MainUiState` has one; otherwise derive per-tab from "primary collection empty AND not syncFailed", exactly like Today does. Do NOT invent a new state-machine.

## Steps (numbered requirements)

1. Move `Skeleton` and `SkeletonRow` from `CheckInScreen.kt` (private, `:622-631`) to a new shared file `app/src/main/java/com/spartan/ui/screens/SkeletonComponents.kt` (internal), updating CheckInScreen's references. No visual change.
2. Metrics (`Screens.kt`, `MetricsScreen`): when `state.metrics` (the list backing `MetricRow`s) is empty and data is still loading, render `SkeletonRow(0.4f)` + three 76dp `Skeleton` blocks in place of the rows. If loading cannot be distinguished from genuinely-zero metrics with existing state, STOP and report this requirement.
3. Coach (`CoachScreen.kt`): same pattern in place of the card stack.
4. Plan (`Screens.kt`, Plan screen): same pattern in place of `WeeklyPlanSection`.
5. Review (`Screens.kt`, `ReviewScreen`): gate the plan-005 empty-state card so it renders only when loading has finished; while loading, show the skeleton instead. The empty card must never flash before data arrives.

If the code found doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT add spinners — skeletons only, matching Today.
- Do NOT animate beyond what plan 009 adds to the shared `Skeleton` composable.
- Do NOT change `MainViewModel` data flow beyond (at most) one boolean loading flag if none exists — and if you add one, wire it from the existing load pipeline, not a new coroutine.

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin test` → exit 0.
- **Feel check**: cold start → switch to each tab immediately: skeletons, then content, no zero-value flash, no layout jump on resolve; with sync failure simulated, no infinite skeleton.
- **Done when**: all 5 requirements confirmed with file:line evidence.

## Closing self-audit (2026-08-13)

**Loading predicate note (requirement 2's STOP clause, resolved with existing state):** `MainUiState` has no `metrics` field — the list backing `MetricRow` is `state.assessments` (the plan's own parenthetical). Loading IS distinguishable from genuinely-zero data without any new flag: the main tabs render only when `onboardingComplete` (`SpartanRoot.kt:74`), onboarding always writes a profile, and `deleteAllLocalData()` clears preferences (back to onboarding) — so `state.profile == null` occurs exactly while the health bundle's first emission is in flight. Each tab conjoins that with `!state.syncFailed` (and its primary collection where the plan named one), Today-style. Zero `MainViewModel` changes (boundary: "at most one boolean flag" — none needed).

1. **done** — `Skeleton` + `SkeletonRow` moved verbatim (no visual change) from CheckInScreen.kt to new [SkeletonComponents.kt:22-29](../app/src/main/java/com/spartan/ui/screens/SkeletonComponents.kt) as `internal`; CheckInScreen's `LoadingPlan` (`CheckInScreen.kt:613-618`) still compiles against them unchanged (same package, no import edits needed).
2. **done** — Metrics: `Screens.kt:197-199` — `if (state.assessments.isEmpty() && state.profile == null && !state.syncFailed)` renders `SkeletonRow(0.4f)` + `items(3) { Skeleton(...76.dp) }`; banner/insights/rows in the else branch.
3. **done** — Coach: `CoachScreen.kt:96-98` — same skeleton block replaces the card stack (goal notice through weekly-plan section moved into the else branch, re-indented; trailing spacer outside).
4. **done** — Plan: `Screens.kt:417-422` — `state.weeklyPlan == null && !state.syncFailed` (the engine always yields a plan once the bundle emits) → skeleton, else `WeeklyPlanSection`.
5. **done** — Review: `Screens.kt:518-520` — skeleton branch precedes the plan-005 `review == null` empty card, which now renders only after loading finishes; `SampleDataChip` stays in the title row above all branches.

Boundaries respected: no spinners; skeleton body untouched (plan 009's pulse comes next); no `MainViewModel` data-flow changes. Verification: `./gradlew :app:compileDebugKotlin :app:test` → exit 0, `BUILD SUCCESSFUL in 36s` (all unit tests pass).
