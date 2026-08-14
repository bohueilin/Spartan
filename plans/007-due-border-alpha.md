# 007 — DUE urgency border clears 3:1 in light mode

- **Status**: DONE
- **Commit**: cad6100
- **Severity**: Tier 3 (residual from plan 003's re-review)
- **Scope**: 1 file, 1 value

## Problem

The DUE (not yet overdue) urgency border renders its color at 60% alpha:

```kotlin
// app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt:359 — current
urgencyColor != null -> urgencyColor.copy(alpha = if (urgency == PlanUrgency.OVERDUE) 0.9f else 0.6f)
```

Measured: light amber `#7C570E @ 0.6` composited on white = **2.73:1**, just under the 3:1 non-text minimum. Dark mode passes (4.1:1). Since plan 003, a REQUIRED card that turns DUE swaps its full-accent border (6.67:1) for this weaker amber, so the state signal dips during exactly the hours it matters.

## Target

DUE alpha `0.6f` → `0.7f`. Measured: `#7C570E @ 0.7` on white = **3.35:1**; dark `#E7B25A @ 0.7` on `#121817` = 4.7:1. OVERDUE stays `0.9f`.

## Steps (numbered requirements)

1. `CheckInScreen.kt:359`: change `0.6f` → `0.7f`. Nothing else on the line changes.

If the line doesn't match the quoted code, STOP and report.

## Boundaries

- Do NOT change the OVERDUE alpha, the width logic, or `Tokens.planUrgencyColor`.
- Do NOT port to iOS — iOS has no urgency-border system (tracked separately in plan 013's notes; do not add it here).

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin` → exit 0. (`/usr/libexec/java_home` does not resolve on this machine.)
- **Measured**: WCAG formula on `#7C570E` blended at 0.7 over `#FFFFFF` → ≥ 3.3:1.
- **Done when**: requirement 1 confirmed with the new line quoted.

## Closing self-audit (2026-08-13)

1. **done** — `CheckInScreen.kt:359` now reads:
   ```kotlin
   urgencyColor != null -> urgencyColor.copy(alpha = if (urgency == PlanUrgency.OVERDUE) 0.9f else 0.7f)
   ```
   Only `0.6f` → `0.7f` changed; OVERDUE `0.9f`, width logic, and `planUrgencyColor` untouched; iOS untouched.

Measured (WCAG formula, alpha-composited): `#7C570E @ 0.7` on `#FFFFFF` = **3.33:1** (≥ 3.3 target); dark `#E7B25A @ 0.7` on `#121817` = **5.15:1**. Verification: `./gradlew :app:compileDebugKotlin` → exit 0, `BUILD SUCCESSFUL in 16s`.
