# 004 — Complete sample-data provenance on every surface

- **Status**: DONE
- **Commit**: 91c0816
- **Severity**: Tier 2 (trust)
- **Scope**: ~7 files across both platforms

## Problem

The honest-provenance system (SAMPLE DATA chips, "Use sample data" buttons, refusing the CONNECTED chip for stubs) is Spartan's most distinctive product decision — and it is incomplete. `SampleDataChip.kt:18-21` claims coverage of "EVERY surface"; it covers five of eight:

1. **Android Plan tab** (`Screens.kt:400`), **Metric Detail** (`Screens.kt:241`) and the **Privacy export** (`Screens.kt:601` area) show fabricated values with no provenance marker. Metric Detail renders a full sparkline of mock data unlabeled.
2. **iOS ring VoiceOver label** (`CheckInView.swift:181-185`) says "Recovery 63 percent, Balanced" with no sample-data mention — a screen-reader user never encounters the chip's information.
3. **"Find a time"** schedules against fabricated busy blocks from the stub calendar and fires a real local notification; the resulting "Scheduled for 3:15 PM" status (`CheckInView.swift:316`; Android equivalent in `CheckInScreen.kt` status text) carries no sample marker. A real-feeling artifact from fake availability.
4. `MainViewModel.kt:779` hardcodes `accountLabel = "Sample data"` in English outside `strings.xml`.

## Target

Every surface that renders WHOOP-derived numbers shows `SampleDataChip` when `whoopIsMock`; screen-reader output carries the same provenance as visual output; anything derived from the stub calendar says so.

## Conventions to follow

- Android exemplar: Metrics title row `Screens.kt:186-191` — title + `if (state.whoopIsMock) SampleDataChip()` in a Row. Copy this pattern exactly.
- Strings go in `app/src/main/res/values/strings.xml`; iOS copy is duplicated verbatim per existing port convention (`CheckInView.swift:5-6` header comment).
- iOS chip: `CheckInView.swift:458-470`.

## Steps (numbered requirements)

1. Plan screen (`Screens.kt:400ff`): add `if (state.whoopIsMock) SampleDataChip()` beside the title, copying the Metrics row pattern.
2. Metric Detail (`Screens.kt:241ff`): same, beside the metric title.
3. Privacy export: locate the export text builder (start at `Screens.kt:601` and the ViewModel it calls). Prepend a line "Includes sample (mock) data — not real WHOOP measurements." to exported text when `whoopIsMock`. If the builder cannot be found from those entry points, STOP and report this requirement as blocked.
4. `MainViewModel.kt:779`: move `"Sample data"` into `strings.xml` (reuse the existing chip string if identical) and resolve via resources.
5. iOS ring accessibility label (`CheckInView.swift:181-185`): when the app is on mock data, append ", sample data" — pass `isMock` into `ReadinessRing` (or the header) from `viewModel.whoopIsMock`.
6. Android ring a11y string (`CheckInScreen.kt:272-277`): same appendix when `whoopIsMock`, via a new plural-safe string resource.
7. "Find a time" scheduled status: when the calendar client is the stub (`calendarIsStub` on Android `MainViewModel.kt:110`; iOS `StubCalendarClient` always), the status line reads "Scheduled for 3:15 PM (sample calendar)" — add the suffix string on both platforms (`CheckInView.swift:305-324` statusText; Android status text equivalent in `CheckInScreen.kt`).
8. Update the `SampleDataChip.kt:18-21` doc comment to list the now-true coverage.

If code at a cited line doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT redesign the chip (size/color changes belong to a separate finding).
- Do NOT gate any feature behind real data — sample mode must remain fully usable, just labeled.
- Do NOT alter any other user-visible copy.

## Verification

- **Mechanical**: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin test` passes.
- **Feel check**: default (mock) build — Plan and Metric Detail show the chip; export text carries the sample line; TalkBack on the ring announces sample provenance; a found time slot says "(sample calendar)".
- **Done when**: all 8 requirements confirmed with file:line evidence; chip renders on all surfaces listed in the updated doc comment.

## Closing self-audit (2026-08-12)

Line numbers are post-edit (files shifted after plans 001–003; all cited code matched at commit 91c0816 before editing).

1. **done** — Plan screen title wrapped in the Metrics row pattern with `if (state.whoopIsMock) SampleDataChip()` at `Screens.kt:404-407`.
2. **done** — Metric Detail title row gained the chip at `Screens.kt:248` (between title and edit button, matching Metrics).
3. **done** — export builder located: `LocalExportFormatter.format` feeds `health.exportText`, which enters UI state at a single point; when mock, `privacy_export_sample_line` ("Includes sample (mock) data — not real WHOOP measurements.") is prepended at `MainViewModel.kt:391-395` — covers both the Privacy preview (`Screens.kt` `state.exportText`) and the Share action (same field).
4. **done** — `MainViewModel.kt:786`: `"Sample data"` literal replaced with `appContext.getString(R.string.sample_data_a11y)` (reused the identical chip a11y string added by plan 002). Same-pattern literal at `:810` (calendar `accountLabel`, identical text in the same file) converted identically — noted as an extension beyond the cited line, same requirement intent, zero copy change.
5. **done** — iOS `ReadinessRing` now takes `isMock` (`CheckInView.swift:147`), passed from `viewModel.whoopIsMock` at `:114`; a11y label appends ", sample data" when mock at `:188`.
6. **done** — Android ring a11y: base string wrapped via new resource `checkin_recovery_a11y_sample` ("%1$s, sample data", `strings.xml:34`) at `CheckInScreen.kt:278-285`, gated on `state.whoopIsMock`.
7. **done** — "Find a time" status: Android `StatusLine` takes `calendarIsStub` (wired `state.calendarIsStub` → `ActivityCard:341` → `StatusLine:516`), rescheduled case uses `checkin_scheduled_for_sample` ("Scheduled for %1$s (sample calendar)", `strings.xml:41`) at `:523`; iOS statusText appends "(sample calendar)" at `CheckInView.swift:330` — unconditional because this build's calendar client is always `StubCalendarClient` (`CheckInViewModel.swift:190`), per the plan.
8. **done** — `SampleDataChip.kt:17-25` doc comment updated to the now-true coverage list plus the text-provenance surfaces (export line, ring a11y, sample-calendar status).

Boundaries respected: chip visuals untouched; nothing gated behind real data (labels only); no other user-visible copy altered. Verification: `./gradlew :app:compileDebugKotlin test` (Homebrew JDK 17) → exit 0, `BUILD SUCCESSFUL in 39s` (debug+release unit tests). iOS diff self-reviewed; no SpartanKit sources touched.
