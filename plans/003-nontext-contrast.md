# 003 — Fix non-text contrast: required-card signal, outlines, ring track, skeleton

- **Status**: DONE
- **Commit**: 91c0816
- **Severity**: Tier 1 (required-signal invisible) + Tier 3 (light-mode states near-blank)
- **Scope**: 4 files (Theme.kt, CheckInScreen.kt, SpartanApp.swift, CheckInView.swift)

## Problem

Borders are this design's ONLY depth and state cue (no shadows anywhere, by design), yet the border colors fail non-text contrast:

1. **Required-card border** — the sole visual signal that an activity is REQUIRED — is accent at 28% alpha:
   ```swift
   // ios/SpartanApp/Sources/CheckInView.swift:284 — current
   activity.priority == .required ? Color.spartanAccent.opacity(0.28) : Color.spartanOutline
   ```
   ```kotlin
   // app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt:348 — current (same pattern)
   ```
   Light mode: `#0B685C @ 28%` over `#FFFFFF` ≈ **1.4:1** — a state indicator below the 3:1 minimum, effectively invisible.
2. **Card outline hairlines**: light `#C3CFCD` on `#FFFFFF` = **1.60:1**; dark `#293630` on `#121817` = **1.42:1** (`Theme.kt:31, 44`; `SpartanApp.swift:84`). Dark-mode cards nearly dissolve into the background.
3. **iOS ring track**: `spartanSurfaceVariant #E6ECEB` on `#FFFFFF` = **1.09:1** in light mode — a low recovery score reads as a broken ring, not a partial one. (Android track `CheckInScreen.kt:293` has the same issue on white cards.)
4. **iOS skeleton**: `spartanSurfaceVariant.opacity(0.5)` (`CheckInView.swift:532`) on `#F6F8F8` ≈ **1.05:1** — the loading state is essentially blank in light mode.

## Target

| Token/site | Current | Target | Ratio |
|---|---|---|---|
| outline (light) | `#C3CFCD` | `#9BADAA` | 2.35:1 on #FFF (visible hairline; calm) |
| outline (dark) | `#293630` | `#3D4F48` | 2.06:1 on #121817 |
| required border | accent @ 28%, 1dp/pt | accent @ 100%, 1.5dp/pt | 6.67:1 light / 10.87:1 dark |
| ring track (both platforms) | surfaceVariant | outline token (post-change) | ≥2:1 |
| iOS skeleton fill | surfaceVariant @ 50% | surfaceVariant @ 100% | parity with Android |

Hairlines stay under 3:1 deliberately — they are ambient depth, not state; the state-bearing border (required) is what must clear 3:1, and does at full accent.

## Conventions to follow

- Android colors live in `app/src/main/java/com/spartan/ui/theme/Theme.kt` (light `:21-32`, dark `:34-45`); iOS mirrors them 1:1 in `ios/SpartanApp/Sources/SpartanApp.swift:72-90` via `Color.spartanDynamic(light:dark:)`. **Every hex changed on one platform must change identically on the other.**
- Do not add new tokens; adjust existing ones.

## Steps (numbered requirements)

1. `Theme.kt:31`: outline `0xFFC3CFCD` → `0xFF9BADAA`.
2. `Theme.kt:44`: outline `0xFF293630` → `0xFF3D4F48`.
3. `SpartanApp.swift:84`: `spartanOutline` light `0xC3CFCD` → `0x9BADAA`, dark `0x293630` → `0x3D4F48`.
4. `CheckInScreen.kt:348`: REQUIRED border color `primary.copy(alpha = 0.28f)` → `MaterialTheme.colorScheme.primary` (full), and its width from 1dp → `1.5.dp` (keep the 2dp overdue escalation at `:351` as-is; if overdue also applies to required, 2dp wins).
5. `CheckInView.swift:284`: `Color.spartanAccent.opacity(0.28)` → `Color.spartanAccent`, `lineWidth: 1` → `1.5` for the required branch only.
6. Ring track: Android `CheckInScreen.kt:293` and iOS `CheckInView.swift` track `Circle().stroke(...)` — change track color from surfaceVariant to the outline token.
7. `CheckInView.swift:532`: remove `.opacity(0.5)` from the skeleton fill.

If code at a cited line doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT change surface, background, or any text color.
- Do NOT add shadows/elevation — flat-with-borders is the design language; this plan strengthens it, not replaces it.
- Do NOT touch the time-of-day urgency border colors (`Tokens.kt:76-83`) — they pass (≥5:1 as text; stronger as borders).

## Verification

- **Mechanical**: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin` passes.
- **Measured**: recompute ratios (WebAIM contrast checker or the python snippet in the review): `#9BADAA`/`#FFFFFF` = 2.35, `#3D4F48`/`#121817` = 2.06, `#0B685C`/`#FFFFFF` = 6.67.
- **Feel check**: light mode — a REQUIRED card is instantly distinguishable at arm's length; dark mode — card edges legible; light mode loading — skeleton blocks visible.
- **Done when**: all 7 requirements confirmed with file:line evidence; both platforms' hex values match exactly.

## Closing self-audit (2026-08-12)

Line numbers are post-edit (CheckInScreen.kt/CheckInView.swift shifted after plans 001–002; all cited code matched at commit 91c0816 before editing).

1. **done** — `Theme.kt:31`: `outline = Color(0xFF9BADAA)`.
2. **done** — `Theme.kt:44`: `outline = Color(0xFF3D4F48)`.
3. **done** — `SpartanApp.swift:84`: `spartanDynamic(light: 0x9BADAA, dark: 0x3D4F48)` — hex-identical to Android (parity grep in verification below).
4. **done** — `CheckInScreen.kt:357`: REQUIRED branch now full `MaterialTheme.colorScheme.primary`; width via when-chain at `:362-366`: OVERDUE → 2dp (escalation kept, wins over required), REQUIRED → 1.5dp, else 1dp.
5. **done** — `CheckInView.swift:292-293`: required branch `Color.spartanAccent` (full) with `lineWidth: activity.priority == .required ? 1.5 : 1` — required branch only.
6. **done** — ring tracks: Android `CheckInScreen.kt:277` `val track = MaterialTheme.colorScheme.outline` (the plan's `:293` cite pointed at the drawArc consuming this value; the color is defined here); iOS `CheckInView.swift:159` `Circle().stroke(Color.spartanOutline, ...)`.
7. **done** — iOS skeleton `LoadingPlan`: `.opacity(0.5)` removed from both skeleton fills (`CheckInView.swift:541, :545`) — the plan cited the first (`:532` at 91c0816); the three 76pt blocks share the same fill and the feel check ("skeleton blocks visible") requires both, so both were changed.

Boundaries respected: no surface/background/text color changed; no shadows added; `Tokens.kt` urgency colors untouched. Verification: measured ratios (WCAG formula, python) — `9BADAA`/`FFFFFF` = **2.35**, `3D4F48`/`121817` = **2.06**, `0B685C`/`FFFFFF` = **6.67**, `3FE0C8`/`121817` = **10.87** — all match the target table. `./gradlew :app:compileDebugKotlin` (Homebrew JDK 17) → exit 0, `BUILD SUCCESSFUL in 16s`. iOS diff self-reviewed; no SpartanKit sources touched.
