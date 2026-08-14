# 017 — Locale-safe case operations; tab labels into resources

- **Status**: TODO
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
