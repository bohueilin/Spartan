# Spartan for iOS

The iOS port of Spartan, structured for maximum verifiability:

- **`SpartanKit/`** — a pure-Swift package holding the entire domain core: models, the rules-based
  `CoachingEngine` gated by `SafetyEngine` (same rule ids, thresholds, and coaching copy as
  Android), the labeled sample-data `MockWhoopClient`, `AvailabilityService`, and the stub calendar
  client. **Builds and unit-tests on macOS** (`swift build && swift test`) — including the ported
  coaching **eval harness** (500+ generated plans checked against safety/correctness invariants) —
  so the logic that decides what a user is told to do is verified even without Xcode.
- **`SpartanApp/`** — the SwiftUI app layer (check-in hero screen at visual parity with Android,
  consent/connections, onboarding, settings), local JSON persistence with seed-once semantics,
  and quiet-hours-aware local notifications. Generated into an Xcode project with
  [XcodeGen](https://github.com/yonaskolb/XcodeGen):
  ```bash
  brew install xcodegen
  cd ios/SpartanApp && xcodegen generate && open SpartanApp.xcodeproj
  ```
- **`docs/`** — App Store listing copy, App Privacy (nutrition label) answers, and the iOS release
  checklist (signing, TestFlight, App Review notes for health apps).

## Honest build status

| Layer | Status |
|---|---|
| SpartanKit (domain, data, tests) | ✅ `swift build` + `swift test` green on this machine |
| SpartanApp (SwiftUI) | 🟡 source-complete; needs one `xcodegen + xcodebuild` pass on a machine with Xcode + iOS SDK (this machine has only Command Line Tools) |

Same privacy posture as Android: local-first, no analytics/ads/trackers, sample data clearly
labeled, wellness guidance only — not medical advice, not a medical device.
