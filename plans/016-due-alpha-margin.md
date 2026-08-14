# 016 — DUE border margin: 0.7 → 0.75

- **Status**: DONE
- **Commit**: 0a20c32
- **Severity**: Tier 6 (margin hardening; current value passes)
- **Scope**: 1 file, 1 value

## Problem

Plan 007's `0.7f` passes, but thinly on its worst abutment: the card border meets the `#F6F8F8` screen background as well as the white card. Measured (WCAG, alpha-composited): `#7C570E @ 0.7` = 3.35:1 vs `#FFFFFF` but **3.14:1 vs `#F6F8F8`**. Any future lightening of `easyLight` tips it under 3:1.

## Target

`0.75f`: **3.70:1** vs white, **3.47:1** vs `#F6F8F8`; dark `#E7B25A @ 0.75` on `#121817` ≈ 5.5:1. Comfortable margin on both abutments, visually near-identical.

## Steps (numbered requirements)

1. `app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt:359` (the `urgencyColor.copy(alpha = ...)` line): `0.7f` → `0.75f`. OVERDUE `0.9f` unchanged.
2. Add one comment on the same line block: `// DUE alpha ≥ 0.75: keeps ≥3:1 against both #FFFFFF cards and the #F6F8F8 background — re-measure if easyLight changes.`

If the line doesn't match, STOP and report.

## Boundaries

- Do NOT change OVERDUE alpha, width logic, `Tokens.planUrgencyColor`, or iOS.

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin` → exit 0.
- **Measured**: `#7C570E` blended at 0.75 over `#FFFFFF`, ratio vs `#F6F8F8` ≥ 3.4:1.
- **Done when**: both requirements confirmed with the new lines quoted.

## Closing self-audit (2026-08-13)

1. **done** — `CheckInScreen.kt:361` (was `:359`; the guard comment above shifted it) now reads:
   ```kotlin
   urgencyColor != null -> urgencyColor.copy(alpha = if (urgency == PlanUrgency.OVERDUE) 0.9f else 0.75f)
   ```
   OVERDUE `0.9f` unchanged.
2. **done** — guard comment added directly above, `CheckInScreen.kt:360`:
   ```kotlin
   // DUE alpha ≥ 0.75: keeps ≥3:1 against both #FFFFFF cards and the #F6F8F8 background — re-measure if easyLight changes.
   ```

Boundaries respected: OVERDUE alpha, width logic, `Tokens.planUrgencyColor`, and iOS untouched. Measured (WCAG, alpha-composited): `#7C570E @ 0.75` = **3.70:1** vs `#FFFFFF` and **3.56:1** vs `#F6F8F8` (≥3.4 target); dark `#E7B25A @ 0.75` on `#121817` = **5.74:1**. Verification: `./gradlew :app:compileDebugKotlin` → exit 0, `BUILD SUCCESSFUL in 17s`.
