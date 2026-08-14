# 018 — Restore OutlinedCardButton's full tap target

- **Status**: TODO
- **Commit**: 0a20c32
- **Severity**: Tier 3 (touch target; regression risk introduced by plan 010)
- **Scope**: 1 file, ~2 lines

## Problem

Plan 010 gave `OutlinedCardButton` (`ios/SpartanApp/Sources/ConnectionsView.swift:316-337` — Disconnect / Import WHOOP export / Dismiss) the custom `SpartanPressStyle` and moved its chrome inside the label. But its chrome is a `strokeBorder` overlay with **no opaque fill** and no `.contentShape`: with a custom `ButtonStyle`, transparent regions are not hit-testable, so the tap target likely shrinks from the 48pt full-width pill to just the text glyphs and the 1pt border ring. The codebase already guards this exact pattern elsewhere — `SpartanCheck` adds `.contentShape(Rectangle())` at `CheckInView.swift:436`. The filled buttons (Connect, Begin) are safe (opaque fill inside the label).

## Target

The whole 48pt pill is tappable again:

```swift
// inside OutlinedCardButton's label chain, after the frame/overlay modifiers
.contentShape(RoundedRectangle(cornerRadius: SpartanRadius.card))
```

## Steps (numbered requirements)

1. `ConnectionsView.swift`, `OutlinedCardButton` body: add `.contentShape(RoundedRectangle(cornerRadius: SpartanRadius.card))` after the `.overlay(...strokeBorder...)` modifier (and after the `frame(minHeight: 48)` so the shape covers the full frame).

If the composable doesn't match this description, STOP and report.

## Boundaries

- Do NOT change the button's visual chrome, colors, or `SpartanPressStyle`.
- Do NOT touch the filled buttons or Android.

## Verification

- **Mechanical**: no Xcode here — self-review the diff; the pattern must match `CheckInView.swift:436`'s guard.
- **Feel check** (deferred to a Mac with Xcode): tap the middle of a Disconnect/Import pill away from the text — it must register.
- **Done when**: requirement 1 confirmed with the new modifier quoted in context.
