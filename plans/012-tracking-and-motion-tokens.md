# 012 — Unify wordmark tracking; give iOS motion tokens

- **Status**: TODO
- **Commit**: cad6100
- **Severity**: Tier 5 (cohesion)
- **Scope**: 4 files (CheckInScreen.kt, Screens.kt, OnboardingView.swift, CheckInView.swift, SpartanApp.swift)

## Problem

1. The SPARTAN wordmark wears two different trackings for the same word: `letterSpacing = 3.sp` on Today (`CheckInScreen.kt:237`) vs `4.sp` on onboarding (`Screens.kt:121`); iOS mirrors the same split (`CheckInView.swift:104` kerning 3 vs `OnboardingView.swift:26` kerning 4).
2. iOS re-types motion durations as literals at every call site (0.42 at `CheckInView.swift:166`, 0.22 at `:217` and `:268`, 0.14 at `:432` and in plan 010's press style) while Android has `Motion.fast/medium/slow` tokens.

## Target

- Wordmark tracking = **3** everywhere (Today's value wins: it's the daily surface; onboarding conforms to it).
- iOS gains `enum SpartanMotion { static let fast = 0.14; static let medium = 0.22; static let slow = 0.42 }` in `SpartanApp.swift` next to `SpartanSpacing`, and every duration literal is replaced with the token. Values unchanged — this is a naming move, not a retiming.

## Steps (numbered requirements)

1. `Screens.kt:121`: `letterSpacing = 4.sp` → `3.sp`.
2. `OnboardingView.swift:26`: `.kerning(4)` → `.kerning(3)`.
3. `SpartanApp.swift`: add
   ```swift
   enum SpartanMotion {
       static let fast: Double = 0.14   // state fades, press feedback
       static let medium: Double = 0.22 // progress, card expand
       static let slow: Double = 0.42   // readiness ring reveal
   }
   ```
4. Replace every animation duration literal in `CheckInView.swift` with the matching token (`0.42`→`.slow` at :166, `0.22`→`.medium` at :217 and :268, `0.14`→`.fast` at :432), and in `SpartanPressStyle` if plan 010 has landed.
5. Sweep: `grep -n "duration: 0\." ios/SpartanApp/Sources/*.swift` → zero hits outside `SpartanMotion`'s definition comments.

If a cited line doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT change any duration or easing value — identical rendered motion before/after.
- Do NOT touch the section-label trackings (1.4 / 0.8) — size-specific tracking is correct; only the wordmark was split.
- Do NOT introduce a custom font or type scale (that is a separate, deliberate direction decision — out of scope).

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin` → exit 0; iOS diff self-reviewed; requirement-5 grep clean.
- **Feel check**: onboarding wordmark now matches Today's; no motion feels different.
- **Done when**: all 5 requirements confirmed with file:line evidence.
