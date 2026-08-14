# 009 — Fix the dead skeleton pulse (and its light-mode invisibility)

- **Status**: TODO
- **Commit**: cad6100
- **Severity**: Tier 4
- **Scope**: 1 file (the skeleton composable — CheckInScreen.kt, or SkeletonComponents.kt if plan 008 ran first)

## Problem

The skeleton "pulse" animates from its own target, so it never moves, and the 600ms duration is off-token:

```kotlin
// app/src/main/java/com/spartan/ui/screens/CheckInScreen.kt:628-629 — current
val alpha by animateFloatAsState(0.9f, tween(600), label = "sk")
Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f * alpha)))
```

Net effect: a static block at `surfaceVariant @ 45%` — on light (`#E6ECEB @ 45%` over `#F6F8F8`) that is ≈ **1.02:1**, essentially invisible. iOS fixed its equivalent in plan 003; Android's survived because the broken math hid it.

## Target

A working, gentle pulse between 55% and 90% alpha of full `surfaceVariant`, `Motion.slow` (420ms) per leg, reversing — and static at 90% under reduced motion:

```kotlin
val infinite = rememberInfiniteTransition(label = "sk")
val alpha by infinite.animateFloat(
    initialValue = 0.55f, targetValue = 0.9f,
    animationSpec = infiniteRepeatable(tween(Motion.slow), RepeatMode.Reverse),
    label = "skAlpha",
)
val shown = if (rememberReducedMotion()) 0.9f else alpha
Box(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shown)))
```

Light-mode worst case `#E6ECEB @ 55%` on `#F6F8F8` is still subtle but visible; at 90% it reads clearly. This is the one permitted constant motion — it indicates a genuine ongoing process and exists only while loading.

## Conventions to follow

- `Motion.slow` from `com.spartan.ui.theme.Tokens`; `rememberReducedMotion()` from the same file (added by plan 001).
- Imports: `androidx.compose.animation.core.rememberInfiniteTransition`, `infiniteRepeatable`, `RepeatMode`, `animateFloat`.

## Steps (numbered requirements)

1. Replace the `Skeleton` body with the Target code above, wherever the composable now lives (CheckInScreen.kt:626-630 at this commit; SkeletonComponents.kt if plan 008 moved it).

## Boundaries

- Do NOT change block sizes, corner radius (10dp), or the skeleton layout.
- Do NOT add shimmer gradients — an alpha pulse only.

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin` → exit 0.
- **Feel check**: cold start in light mode → skeleton blocks clearly visible and breathing (~0.84s full cycle); `adb shell settings put global animator_duration_scale 0` → blocks static at the brighter value.
- **Done when**: requirement 1 confirmed; no `tween(600)` remains in the file (grep).
