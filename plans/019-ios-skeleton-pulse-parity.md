# 019 — Mirror the skeleton pulse on iOS

- **Status**: TODO
- **Commit**: 0a20c32
- **Severity**: Tier 5 (platform motion-parity; Android got the pulse in plan 009)
- **Scope**: 1 file (CheckInView.swift, LoadingPlan)

## Problem

Plan 009 gave Android's skeleton a working pulse (alpha 0.55↔0.9, `Motion.slow` per leg, reversing, static at 0.9 under reduced motion). iOS `LoadingPlan` (`ios/SpartanApp/Sources/CheckInView.swift:543-558`) remains static full-opacity blocks — the platforms' loading-state motion language now diverges on the one screen both share.

## Target

Same pulse, SwiftUI idiom:

```swift
private struct LoadingPlan: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dim = false
    // body unchanged except each skeleton fill gains:
    //   .opacity(reduceMotion ? 0.9 : (dim ? 0.55 : 0.9))
    // and the container gains:
    //   .onAppear { guard !reduceMotion else { return }
    //     withAnimation(.easeInOut(duration: SpartanMotion.slow).repeatForever(autoreverses: true)) { dim = true } }
}
```

Values verbatim from plan 009: 0.55 / 0.9, `SpartanMotion.slow` (0.42s) per leg, autoreversing; reduced motion → static at 0.9.

## Steps (numbered requirements)

1. Apply the Target to `LoadingPlan` in `CheckInView.swift:543-558`: all skeleton rectangles share the one `dim` state (one pulse, in phase — matching Android's single `rememberInfiniteTransition`).

If the view doesn't match, STOP and report.

## Boundaries

- Do NOT change block sizes, corner radius (10), or layout.
- Do NOT add shimmer gradients.
- Do NOT touch Android.

## Verification

- **Mechanical**: no Xcode here — self-review the diff; confirm `SpartanMotion.slow` is used, no new duration literal.
- **Feel check** (deferred to a Mac with Xcode): loading state breathes in ~0.84s cycles; with Reduce Motion on, blocks are static.
- **Done when**: requirement 1 confirmed with the modifier chain quoted.
