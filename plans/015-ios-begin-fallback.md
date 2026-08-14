# 015 — Re-enable Begin: the name is optional by design

- **Status**: TODO
- **Commit**: 0a20c32
- **Severity**: Tier 4 (product-behavior divergence introduced by plan 010)
- **Scope**: 1 file, 1 line

## Problem

Plan 010 requirement 4 disabled iOS onboarding's Begin on an empty name:

```swift
// ios/SpartanApp/Sources/OnboardingView.swift:62 — current
.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
```

The re-review confirms the name is **optional by design, cross-platform**: Android's Begin stays enabled and falls back (`MainViewModel.kt:629` — `displayName = name.ifBlank { "You" }`), iOS has the identical fallback (`CheckInViewModel.swift:452` — `name.isEmpty ? "You" : name`), and the Today greeting deliberately suppresses the fallback name (`CheckInScreen.kt:672` — `takeIf { it.isNotBlank() && it != "You" }`). The disable makes the iOS fallback unreachable, diverges from Android, and gates onboarding's only primary action on input the product never required — with no inline explanation of why the button is dead.

## Target

Begin enabled always, matching Android. Empty name → "You" fallback, exactly as both view models already implement. The `.disabled` on the Connections import button (plan 010 requirement 5) is correct and stays.

## Steps (numbered requirements)

1. `OnboardingView.swift:62`: delete the `.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)` line. Keep `SpartanPressStyle` and everything else from plan 010.

If the line doesn't match, STOP and report.

## Boundaries

- Do NOT touch the import button's disabled state or `SpartanPressStyle`.
- Do NOT add name validation to Android — parity is achieved by removal, not addition.

## Verification

- **Mechanical**: no Xcode on this machine — self-review the one-line diff; SpartanKit untouched.
- **Feel check** (deferred to a Mac with Xcode): Begin tappable with all fields empty; completing onboarding without a name shows no "You," greeting oddity on Today (greeting simply omits the name).
- **Done when**: requirement 1 confirmed; `grep -c "disabled(name" OnboardingView.swift` returns 0.
