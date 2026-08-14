# 014 — Sync-failure states for Metrics, Coach, Plan, Review

- **Status**: DONE
- **Commit**: 0a20c32
- **Severity**: Tier 3
- **Scope**: 4 files (SkeletonComponents.kt or a new shared file, CheckInScreen.kt, Screens.kt, CoachScreen.kt)

## Problem

Plan 008 gave the four secondary tabs loading skeletons gated on `!state.syncFailed` — correct (no infinite skeleton) — but left the failure branch unstyled:

- Metrics with `syncFailed` and no data renders only its title (`Screens.kt:198` skips the skeleton, and there are no rows to show).
- Review's failure state is indistinguishable from its designed empty state: `review == null` + `syncFailed` shows "Your weekly review appears after your first full week of activity. Check back Sunday." — which is false when the real cause is a failed sync.
- Coach (`CoachScreen.kt:96`) and Plan (`Screens.kt:420`) likewise render bare titles on failure with no data.

Today already solves this: `SafetyBanner` (`CheckInScreen.kt:588`) with `checkin_sync_failed` ("Couldn't refresh WHOOP data. Showing your most recent sync.").

## Target

When `state.syncFailed` and a tab has no data to show, it renders the same `SafetyBanner` + failure copy Today uses, instead of a bare title or a misleading empty state. When it has stale data, existing content renders (no banner needed beyond Today's — don't multiply banners).

## Conventions to follow

- Exemplar: Today's usage — `SafetyBanner(stringResource(R.string.checkin_sync_failed), ...)` in `CheckInScreen.kt`.
- Shared components live in `SkeletonComponents.kt` (created by plan 008) — same `internal` pattern.
- Strings in `res/values/strings.xml`; reuse `checkin_sync_failed` — same copy is true on all tabs.

## Steps (numbered requirements)

1. Move `SafetyBanner` from `CheckInScreen.kt:588` (private) to `SkeletonComponents.kt` as `internal`, no visual change; CheckInScreen call sites stay valid (same package).
2. Metrics (`Screens.kt:198` area): add a branch — `state.syncFailed && state.assessments.isEmpty()` → `SafetyBanner(stringResource(R.string.checkin_sync_failed))` in place of the row list.
3. Coach (`CoachScreen.kt:96` area): same branch where the profile/data is absent and `syncFailed`.
4. Plan (`Screens.kt:420` area): same — `state.weeklyPlan == null && state.syncFailed` → banner instead of nothing.
5. Review (`Screens.kt:520` area): when `state.syncFailed && review == null`, render the banner INSTEAD of the plan-005 "Check back Sunday" card — the empty card renders only when the sync state is healthy.
6. While in `SkeletonComponents.kt`: extract the four copy-pasted skeleton stacks (`SkeletonRow(0.4f)` + three 76dp blocks, currently duplicated at `CheckInScreen.kt:616-617`, `CoachScreen.kt:98-99`, and the Metrics/Plan/Review branches in `Screens.kt`) into one `internal @Composable fun TabLoadingSkeleton()` and call it from all sites. Keep the 10dp corner radius — it is a sanctioned component shape (plan 011 boundary); add the comment `// 10dp: deliberate skeleton shape, not a card — see plan 011 boundaries.` at its definition.

If code at a cited line doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT add retry buttons (refresh flows are out of scope; Today's pull-to-refresh remains the retry path).
- Do NOT show the banner when a tab has data — stale data renders as-is.
- Do NOT touch iOS (it has only the Today surface; its banner already exists).
- Do NOT reword `checkin_sync_failed`.

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin test` → exit 0.
- **Feel check**: simulate sync failure with empty data → each of the four tabs shows the banner, never a bare title, never "Check back Sunday".
- **Done when**: all 6 requirements confirmed with file:line evidence; the skeleton stack exists in exactly one place (grep for `height(76.dp)` → 1 defining hit).

## Closing self-audit (2026-08-13)

1. **done** — `SafetyBanner` moved verbatim (no visual change) from CheckInScreen.kt to `SkeletonComponents.kt:64-68` as `internal` (imports `Surface`, `Text`, `padding`, `Radius`, `Spacing` added there); CheckInScreen's two call sites (`:183`, `:185`) compile unchanged, same package.
2. **done** — Metrics `Screens.kt:201-202`: `else if (state.syncFailed && state.assessments.isEmpty()) { item { SafetyBanner(stringResource(R.string.checkin_sync_failed)) } }` between the skeleton branch and the row list.
3. **done** — Coach `CoachScreen.kt:99-100`: `else if (state.syncFailed && state.profile == null)` → banner item (profile-null is Coach's "no data" proxy, per plan 008's predicate).
4. **done** — Plan `Screens.kt:425-426`: `else if (state.weeklyPlan == null && state.syncFailed)` → banner instead of nothing.
5. **done** — Review `Screens.kt:528-529`: `else if (state.syncFailed && review == null)` → banner, ordered BEFORE the plan-005 `review == null` empty card, so "Check back Sunday" renders only when the sync state is healthy.
6. **done** — `TabLoadingSkeleton()` at `SkeletonComponents.kt:53-59` is the single skeleton stack; all five sites call it (Today `CheckInScreen.kt:172` — the private `LoadingPlan` wrapper was deleted as part of the extraction; Metrics `Screens.kt:200`; Plan `:424`; Review `:527`; Coach `CoachScreen.kt:98`). The 10dp comment sits at the radius definition in `Skeleton` (`SkeletonComponents.kt:49`). Grep `height(76.dp)` → exactly 1 hit (`SkeletonComponents.kt:58`).

Boundaries respected: no retry buttons; banner renders only when the tab has no data (stale data renders as-is — every banner branch requires the empty/null condition); iOS untouched; `checkin_sync_failed` copy unchanged. Verification: `./gradlew :app:compileDebugKotlin :app:test` → exit 0, `BUILD SUCCESSFUL in 38s`.

Note for re-review: on Today, the loading item is keyed `"loading"` and now renders `TabLoadingSkeleton()` directly — behavior identical to the old `LoadingPlan` (same Column, same spacing); deleting the wrapper was the minimal way to honor "the skeleton stack exists in exactly one place".
