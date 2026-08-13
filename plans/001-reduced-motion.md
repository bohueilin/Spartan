# 001 — Honor reduced-motion on both platforms

- **Status**: DONE
- **Commit**: 91c0816
- **Severity**: Tier 1 (exclusionary)
- **Scope**: ~5 files (Android: 3, iOS: 1–2), small edits

## Problem

Users who disable animations still get full motion. Android has zero reduced-motion handling: no reference to `Settings.Global.ANIMATOR_DURATION_SCALE` or any reduce-motion flag exists under `app/src/main` (verified by grep). The 420ms readiness-ring sweep fires unconditionally:

```kotlin
// app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt:288 — current
val revealAnim by animateFloatAsState(if (revealed) 1f else 0f, tween(Motion.slow), label = "ringReveal")
```

iOS honors `accessibilityReduceMotion` on the ring (`CheckInView.swift:164`) and check spring (`:420`) but NOT on the progress bar (`:212`, `.easeInOut(duration: 0.22)`) or the card expand (`:258`, `withAnimation(.easeInOut(duration: 0.22))`) — the largest layout change in the app.

## Target

When the OS reports animations disabled: ring renders at final sweep instantly, progress bar snaps, card expand/collapse snaps, list item placement animations are skipped. Opacity-only fades (tab crossfade `SpartanRoot.kt:120-123`, check color fades) may remain — reduced motion keeps opacity/color, drops movement.

## Conventions to follow

- Android motion durations live in `object Motion` (`app/src/main/java/com/spartan/ui/theme/Tokens.kt:30-34`); add the helper next to it or in `ui/theme/`.
- iOS already has the exact gating pattern to copy: `CheckInView.swift:164` — `.animation(reduceMotion ? nil : .easeOut(duration: 0.42), value: revealed)` with `@Environment(\.accessibilityReduceMotion)` at `:152`.

## Steps (numbered requirements)

1. **Android helper.** Add to `app/src/main/java/com/spartan/ui/theme/Tokens.kt`:
   ```kotlin
   @Composable
   fun rememberReducedMotion(): Boolean {
       val context = LocalContext.current
       return remember {
           Settings.Global.getFloat(context.contentResolver,
               Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
       }
   }
   ```
2. **Ring sweep** (`CheckInScreen.kt:288`): when reduced, use `snap()` instead of `tween(Motion.slow)`:
   `animateFloatAsState(if (revealed) 1f else 0f, if (reducedMotion) snap() else tween(Motion.slow), ...)`.
3. **Plan progress bar** (`CheckInScreen.kt:309`): same pattern — `snap()` when reduced, else `tween(Motion.medium)`.
4. **Card expand** (`CheckInScreen.kt:364`): apply `animateContentSize(tween(Motion.medium))` only when not reduced (conditional `Modifier.then(...)`).
5. **List item animations** (`CheckInScreen.kt:218-222`): pass `null` for `placementSpec` when reduced; keep `fadeIn`/`fadeOut`.
6. **iOS progress bar** (`CheckInView.swift:212`): `.animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: ...)` — add `@Environment(\.accessibilityReduceMotion)` to the containing view if absent.
7. **iOS card expand** (`CheckInView.swift:258`): wrap in `if reduceMotion { expanded.toggle() } else { withAnimation(.easeInOut(duration: 0.22)) { expanded.toggle() } }`.

No escape hatches: do not skip a numbered step silently; if code at a cited line doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT touch the check-off spring (`CheckInScreen.kt:466-470`) — it already reads as feedback; Android may keep it (haptic remains regardless).
- Do NOT change durations/easings for the non-reduced path.
- Do NOT add dependencies.

## Verification

- **Mechanical**: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin` passes (see memory note: Homebrew JDK 17; don't pipe-mask exit codes). iOS: `swift build` for SpartanKit unaffected; SwiftUI files remain source-consistent (no compile available on this machine — self-review the diff).
- **Feel check**: `adb shell settings put global animator_duration_scale 0`, relaunch → ring appears fully swept instantly with final number; expanding a card snaps; restore with `... 1.0`.
- **Done when**: all 7 numbered requirements confirmed with file:line evidence in a closing self-audit.

## Closing self-audit (2026-08-12)

1. **done** — `rememberReducedMotion()` added at `app/src/main/java/com/spartan/ui/theme/Tokens.kt:44` (body verbatim from the plan; imports `android.provider.Settings`, `androidx.compose.runtime.remember`, `androidx.compose.ui.platform.LocalContext` added).
2. **done** — ring sweep now `if (reducedMotion) snap() else tween(Motion.slow)` at `CheckInScreen.kt:293` (reads helper at `:292`; `androidx.compose.animation.core.snap` imported).
3. **done** — plan progress bar now `if (reducedMotion) snap() else tween(Motion.medium)` at `CheckInScreen.kt:315` (helper read at `:314`).
4. **done** — card expand at `CheckInScreen.kt:371`: `.then(if (reducedMotion) Modifier else Modifier.animateContentSize(tween(Motion.medium)))` (helper read in `ActivityCard` at `:347`).
5. **done** — `animatedItem(reducedMotion: Boolean)` at `CheckInScreen.kt:222-226` passes `placementSpec = if (reducedMotion) null else tween(Motion.medium)`; fadeIn/fadeOut kept. All 5 call sites updated (`:179, :182, :184, :186, :197`), reading the helper once at `:152`.
6. **done** — iOS progress bar `CheckInView.swift:213`: `.animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: done)`; `@Environment(\.accessibilityReduceMotion)` added to `PlanProgress` at `:192`.
7. **done** — iOS card expand `CheckInView.swift:261-266`: `if reduceMotion { expanded.toggle() } else { withAnimation(.easeInOut(duration: 0.22)) { expanded.toggle() } }`; environment added to `ActivityCard` at `:231`.

Boundaries respected: check-off spring untouched (`CheckInScreen.kt:473-477`, `CheckInView.swift:428` unchanged); non-reduced durations/easings unchanged; no new dependencies. Verification: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin` → exit 0, `BUILD SUCCESSFUL in 18s`. iOS: no SpartanKit sources touched; SwiftUI diff self-reviewed against `:164` gating pattern (no iOS SDK on this machine).
