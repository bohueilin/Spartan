# 006 — Recovery number renders still; only the ring animates

- **Status**: TODO
- **Commit**: 91c0816
- **Severity**: Tier 2 (integrity)
- **Scope**: 1 file, 1 line

## Problem

The Android recovery score counts up with the ring sweep, transiently displaying **incorrect recovery values** for 420ms:

```kotlin
// app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt:297 — current
val shown = recovery?.let { (it * revealAnim).toInt().toString() } ?: "--"
```

A recovery of 63 renders 0→17→41→63. A value the user must trust is animated to look alive; the code's own comment at `:285-286` already recognizes replaying it "would transiently show wrong recovery numbers." iOS got this right — its number is still (`CheckInView.swift:281`: `recovery.map(String.init) ?? "--"`) while the arc sweeps.

## Target

The number renders its final value on first frame; the arc sweep (420ms `tween(Motion.slow)`) remains the moment of ceremony. Cross-platform parity with iOS.

## Conventions to follow

- Match the iOS expression shape: value or "--", no interpolation.

## Steps (numbered requirements)

1. `CheckInScreen.kt:297`: change to `val shown = recovery?.toString() ?: "--"`.
2. Simplify the stale comment at `:285-286` if it now overstates (the `rememberSaveable` guard stays — it still prevents the sweep replaying).

## Boundaries

- Do NOT remove the ring sweep or its `rememberSaveable` guard.
- Do NOT touch iOS.

## Verification

- **Mechanical**: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin` passes.
- **Feel check**: launch → number shows final recovery immediately while the arc sweeps up to it; switching tabs and back does not replay.
- **Done when**: both requirements confirmed with file:line evidence.
