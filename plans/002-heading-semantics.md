# 002 — Add heading semantics for screen readers

- **Status**: TODO
- **Commit**: 91c0816
- **Severity**: Tier 1 (exclusionary)
- **Scope**: ~6 files, one-line edits at each site

## Problem

TalkBack/VoiceOver heading navigation is impossible: zero `semantics { heading() }` in the Android app and zero `.accessibilityAddTraits(.isHeader)` in the iOS app. Every screen title (`headlineLarge` + SemiBold) and every all-caps section label is plain text to assistive tech.

Android title sites: `app/src/main/java/com/spartan/ui/screens/Screens.kt:188, 246, 359, 402, 448, 492, 535, 569, 604`; `CoachScreen.kt:84`; `ConnectionsScreen.kt:58`. Section labels: `SectionLabel` (`CheckInScreen.kt:622`) and `CoachSectionLabel` (`CoachScreen.kt:186`).

iOS: `SectionLabel` (`ios/SpartanApp/Sources/CheckInView.swift:~570`, the kerning 1.4 caps label). `NavigationStack` titles in Connections/Settings already carry header semantics from the system — leave them.

## Target

Every screen title and section label announces as a heading. TalkBack "Headings" navigation jumps: screen title → TODAY'S PLAN → … on Today; title → each Coach section; etc.

## Conventions to follow

- Android modifiers chain in place; imports `androidx.compose.ui.semantics.heading` and `androidx.compose.ui.semantics.semantics`.
- Reuse the existing shared composables — `SectionLabel`/`CoachSectionLabel` fix all their call sites at once.

## Steps (numbered requirements)

1. `SectionLabel` (`CheckInScreen.kt:622`): add `Modifier.semantics { heading() }` to its `Text`.
2. `CoachSectionLabel` (`CoachScreen.kt:186`): same.
3. Each of the 9 `Screens.kt` screen-title `Text`s listed above: append `.semantics { heading() }` to their modifier (add `Modifier.semantics { heading() }` where no modifier exists).
4. `CoachScreen.kt:84` and `ConnectionsScreen.kt:58` titles: same.
5. iOS `SectionLabel` in `CheckInView.swift`: add `.accessibilityAddTraits(.isHeader)`.
6. While there (same file, same pattern): give `SampleDataChip` a spoken label — Android `SampleDataChip.kt`: `contentDescription = stringResource(R.string.sample_data_a11y)` (add string "Sample data"); iOS chip (`CheckInView.swift:458-470`): `.accessibilityLabel("Sample data")` — all-caps "SAMPLE DATA" can be spelled out letter-by-letter by screen readers.

If a cited line's code doesn't match, STOP and report the requirement number instead of improvising.

## Boundaries

- Do NOT restructure layouts or change any visual property.
- Do NOT add headings to card titles inside activity cards (they are list items, not headings).

## Verification

- **Mechanical**: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin` passes.
- **Feel check**: TalkBack on device/emulator → set reading control to "Headings" → swipe down cycles through title + section labels on Today and Coach.
- **Done when**: all 6 requirements confirmed with file:line evidence in a closing self-audit.
