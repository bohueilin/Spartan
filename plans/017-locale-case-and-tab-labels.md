# 017 — Locale-safe case operations; tab labels into resources

- **Status**: DONE
- **Commit**: 0a20c32
- **Severity**: Tier 5
- **Scope**: ~6 files, mechanical

## Problem

1. Plan 013 fixed one locale-unspecified `.uppercase()`, but the class is systemic — 7 more sites where default-locale case mapping breaks under locales like Turkish (dotted/dotless i):
   - `MetricExplainerSection.kt:31` — `explainer.title.lowercase()` (user-visible)
   - `CheckInScreen.kt:653` — `bestTimeOfDay.name.lowercase().replaceFirstChar { it.uppercase() }` (user-visible)
   - `Screens.kt:917` — `type.name.replace('_',' ').lowercase().replaceFirstChar { it.uppercase() }` (user-visible)
   - `CoachScreen.kt:274, 407, 408` — `.lowercase()` on string resources (user-visible)
   - `MainViewModel.kt:417` — `sexAtBirth?.uppercase()` used to parse a stored value into an enum — this one is a **correctness** bug class, not just display: under a Turkish default locale, `"male".uppercase()` → `"MALE"` is safe but `"i"`-containing values are not; enum parsing must be locale-invariant.
2. The five bottom-nav tab labels are hardcoded English (`SpartanRoot.kt:60-64` — `"Today"`, `"Metrics"`, `"Coach"`, `"Review"`, `"Settings"`), the most-seen strings in the app and invisible to translators.

## Target

- Enum/data parsing uses `Locale.ROOT`; user-visible display casing uses `Locale.getDefault()` — explicitly, at every site.
- Tab labels come from `strings.xml` (`tab_today`, `tab_metrics`, `tab_coach`, `tab_review`, `tab_settings`), copy byte-identical.

## Conventions to follow

- Exemplar from plan 013: `plan_title_caps`-style pre-cased resources are preferred where the cased form is fixed; `lowercase(Locale.getDefault())` where the input is dynamic.
- `Tab` is a private data class fed at top level (`SpartanRoot.kt:57-64`) — it holds `label: String` built before composition; converting to `@StringRes` ints resolved at the `NavigationBarItem` call site is the standard move.

## Steps (numbered requirements)

1. `MainViewModel.kt:417`: `uppercase()` → `uppercase(java.util.Locale.ROOT)` (parsing, locale-invariant).
2. `CheckInScreen.kt:653` and `Screens.kt:917`: enum-name prettifiers get `lowercase(Locale.ROOT)` (enum names are ASCII constants) and `titlecase(Locale.getDefault())` in `replaceFirstChar`.
3. `MetricExplainerSection.kt:31`: `lowercase()` → `lowercase(java.util.Locale.getDefault())`.
4. `CoachScreen.kt:274, 407, 408`: same explicit default-locale form — or, where the lowercased resource is fixed copy, add a pre-cased string resource instead (state which you chose per site in the self-audit).
5. `SpartanRoot.kt:57-64`: `Tab.label: String` → `labelRes: Int` (`R.string.tab_*`); resolve with `stringResource` at the `NavigationBarItem` (`:98` area). Add the five `tab_*` strings to `strings.xml`, byte-identical copy.

If code at a cited line doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT reword any copy.
- Do NOT touch the `MetricExplainers` long-form domain copy (what-it-is/what-moves-it paragraphs) — moving that corpus into resources is a deliberate i18n project, deferred and tracked in the round-3 review notes, not this plan.
- Do NOT touch iOS.

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin test` → exit 0; `grep -rn "\.lowercase()\|\.uppercase()" app/src/main/java/com/spartan/ui app/src/main/java/com/spartan/ui/screens/MainViewModel.kt` → every remaining hit carries an explicit `Locale` argument.
- **Feel check**: tab labels render identically; Coach/Metric Detail copy unchanged.
- **Done when**: all 5 requirements confirmed; the grep in Mechanical is clean.

## Closing self-audit (2026-08-13)

Cited lines had drifted a few lines from `0a20c32` (plans 014/016 edited the same files earlier in round 3); code at every site matched the plan's quoted content exactly before editing.

1. **done** — `MainViewModel.kt:419` (cited `:417`): `sexAtBirth?.uppercase(java.util.Locale.ROOT)` with a locale-invariance comment; enum parsing no longer varies with device locale.
2. **done** — `CheckInScreen.kt:641` (cited `:653`): `bestTimeOfDay.name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.getDefault()) }` (`java.util.Locale` imported); `Screens.kt:926` (cited `:917`): same pattern on `workoutTypeLabel`.
3. **done** — `MetricExplainerSection.kt:31`: `explainer.title.lowercase(java.util.Locale.getDefault())`.
4. **done** — per-site choices, all pre-cased resources (the cased forms are fixed copy — the plan's stated preference): `CoachScreen.kt:276` (cited `:274`) → `coach_windows_section_lower` = "high-pressure windows" (`strings.xml:77`); `CoachScreen.kt:409-410` (cited `:407-408`) → `coach_sex_female_lower` = "female" / `coach_sex_male_lower` = "male" (`strings.xml:97, :99`). Copy byte-identical to the previous runtime lowercase in en.
5. **done** — `SpartanRoot.kt:59` `Tab(..., labelRes: Int, ...)`; tabs list at `:62-66` uses `R.string.tab_*`; resolved via `stringResource(tab.labelRes)` at the `NavigationBarItem` (`:109-110`, both the icon contentDescription and the label). Five `tab_*` strings at `strings.xml:270-274`, copy byte-identical ("Today", "Metrics", "Coach", "Review", "Settings"). Imports `androidx.compose.ui.res.stringResource` + `com.spartan.R` added.

Boundaries respected: no copy reworded; `MetricExplainers` long-form corpus untouched; iOS untouched. Verification: `./gradlew :app:compileDebugKotlin :app:test` → exit 0, `BUILD SUCCESSFUL in 40s`; `grep -rn "\.lowercase()\|\.uppercase()" app/src/main/java/com/spartan/ui` → **0 bare hits** (every case operation now carries an explicit `Locale` or moved into a pre-cased resource).

Observation for re-review (out of scope, not changed): `bracketNoun`'s `SexAtBirth.UNSPECIFIED -> "adult"` branch (`CoachScreen.kt:411`) is still a hardcoded literal — the plan cited only the two `.lowercase()` lines; flagging it as a residual for a future strings pass.
