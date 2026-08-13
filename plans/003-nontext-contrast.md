# 003 — Fix non-text contrast: required-card signal, outlines, ring track, skeleton

- **Status**: TODO
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
