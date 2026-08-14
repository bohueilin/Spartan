# 010 — iOS pressed and disabled states

- **Status**: TODO
- **Commit**: cad6100
- **Severity**: Tier 4
- **Scope**: 4 files (CheckInView.swift, ConnectionsView.swift, OnboardingView.swift, SpartanApp.swift)

## Problem

1. The most-tapped control in the app has no visual press feedback: `SpartanCheck` uses `.buttonStyle(.plain)` (`ios/SpartanApp/Sources/CheckInView.swift:438`), which strips even the default press opacity. Only the haptic confirms the press.
2. Filled accent buttons (`ConnectionsView.swift:199-208`, `OnboardingView.swift` "Begin") put `.background` outside the label, so they show no highlight either.
3. `.disabled(` appears zero times in the app: "Begin" is enabled with empty fields; "Import" stays tappable during an in-flight import (silently guarded in the VM at `CheckInViewModel.swift:366`) — dead-feeling taps.

## Target

- One shared `ButtonStyle` giving press feedback per the standard: `scaleEffect(configuration.isPressed ? 0.96 : 1)` with `.animation(.easeOut(duration: 0.14), value: configuration.isPressed)`, and no scale under reduced motion (opacity 0.85 fallback when pressed).
- Buttons disable when their action can't run, with system-standard dimming.

## Conventions to follow

- Tokens live in `SpartanApp.swift` — add the style there, next to `SpartanRadius`.
- Duration 0.14 = the existing fast token value used at `CheckInView.swift:432`.

## Steps (numbered requirements)

1. In `SpartanApp.swift`, add:
   ```swift
   struct SpartanPressStyle: ButtonStyle {
       @Environment(\.accessibilityReduceMotion) private var reduceMotion
       func makeBody(configuration: Configuration) -> some View {
           configuration.label
               .scaleEffect(reduceMotion ? 1 : (configuration.isPressed ? 0.96 : 1))
               .opacity(configuration.isPressed ? 0.85 : 1)
               .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
       }
   }
   ```
2. `CheckInView.swift:438`: `.buttonStyle(.plain)` → `.buttonStyle(SpartanPressStyle())` on `SpartanCheck`.
3. Apply `SpartanPressStyle()` to the filled accent buttons in `ConnectionsView.swift` (`:199` connect/import) and `OnboardingView.swift` ("Begin"), and to `OutlinedCardButton` (`ConnectionsView.swift:316`).
4. Onboarding "Begin": `.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)` — match the actual field bindings in `OnboardingView.swift`; if validation semantics are unclear from the bindings, disable on all-fields-empty only, and note it.
5. Connections import button: `.disabled(viewModel.whoopImportInProgress)` — use the existing in-flight flag the VM guard at `CheckInViewModel.swift:366` reads; expose it as `@Published` if it isn't already.

If code at a cited line doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT change button colors, sizes, or copy.
- Do NOT add a loading spinner to buttons (out of scope).
- Do NOT touch Android — its Material ripple already provides press feedback.

## Verification

- **Mechanical**: no Xcode on this machine — self-review the diff line-by-line against these steps; `swift build` for SpartanKit must stay green if touched (it should not be).
- **Feel check** (deferred to a Mac with Xcode): press-and-hold the check → scales to 0.96 and returns; Begin dimmed until a name is entered; Import dims while importing.
- **Done when**: all 5 requirements confirmed with file:line evidence; `grep -c "buttonStyle(.plain)" CheckInView.swift` returns 0.
