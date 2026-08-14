# 010 — iOS pressed and disabled states

- **Status**: DONE
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

## Closing self-audit (2026-08-13)

1. **done** — `SpartanPressStyle` added at `SpartanApp.swift:141-155`, next to `SpartanRadius`. Two documented additions to the prescribed snippet, both serving the plan's stated Target: `@Environment(\.isEnabled)` with `.opacity(isEnabled ? (pressed ? 0.85 : 1) : 0.5)` — without it, the "system-standard dimming" the Target promises never appears, because these buttons set explicit brand colors that opt out of the system's automatic disabled tint. Prescribed press values (0.96 scale, 0.85 pressed opacity, `.easeOut(duration: 0.14)`, no scale under reduced motion) are intact.
2. **done** — `CheckInView.swift:438`: `.buttonStyle(.plain)` → `.buttonStyle(SpartanPressStyle())`; `grep -c "buttonStyle(.plain)" CheckInView.swift` → 0.
3. **done** — style applied to the filled connect button (`ConnectionsView.swift:216`), Onboarding "Begin" (`OnboardingView.swift:61`), and `OutlinedCardButton` (`ConnectionsView.swift:336`). At each, the color/background/overlay chrome moved *inside* the Button label (visually identical) — the plan's own problem statement diagnoses that chrome outside the label shows no highlight; without this move the style would scale only the text, not the pill.
4. **done** — `OnboardingView.swift:62`: `.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)` — exactly the plan's expression; `name` is the actual field binding (`@State private var name`, bound at `:43`).
5. **done** — import button: `IntegrationCard` gained Android-parity `secondaryActionEnabled: Bool = true` (`ConnectionsView.swift:143-145`, init `:158`, `:174`), applied as `.disabled(!secondaryActionEnabled)` on the secondary `OutlinedCardButton` (`:220`), passed as `secondaryActionEnabled: viewModel.whoopImport?.inProgress != true` at the WHOOP call site (`:54`) — the same `whoopImport.inProgress` flag the VM guard reads at `CheckInViewModel.swift:366`, already `@Published` via `whoopImport` (`:180`); no VM change needed.

Boundaries respected: no color/size/copy changes (chrome moved, values identical); no spinners; Android untouched. Verification: no Xcode on this machine — diff self-reviewed line-by-line against the steps; SpartanKit untouched (`git status ios/SpartanKit` clean), so its `swift build` remains green.
