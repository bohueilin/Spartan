# 013 — Icon fixes, dead accent token, hardcoded-string sweep

- **Status**: DONE
- **Commit**: cad6100
- **Severity**: Tier 5
- **Scope**: ~8 files, mostly mechanical

## Problem

1. **Wrong glyphs**: the Review tab uses `Icons.Outlined.Flag` (`SpartanRoot.kt:63`) — a goal/finish-line metaphor for a trends screen; debug Diagnostics reuses the Edit pencil (`Screens.kt:557`).
2. **Dead brand token**: `SpartanAccent = Color(0xFF15C9B0)` (`Theme.kt:18`, exported unused at `:48`) — nothing references it; light mode's real accent is `0xFF0B685C`. Dead code that misleads the next designer about what the brand color is.
3. **Hardcoded English strings** bypass `strings.xml` (locale-broken, invisible to translators): `TrajectoryCard.kt:29` "Where this can take you"; `MetricExplainerSection.kt:28-37` (five section headers); `ExerciseDebriefSheet.kt` (seven strings: "Nice work. How did it go?", "Minutes", "Effort %d of 10", "Pain or concerning symptoms", "Save session", "Skip", coaching note); `Screens.kt:~890-905` diagnostic status/target labels ("Clinical normal", "Meets personal target", …); `NextActivityWidget.kt:86, 105, 112-114`. Plus a locale-unsafe `.uppercase()` at `CoachScreen.kt:151`.

## Target

- Review tab icon → `Icons.Outlined.Insights`; Diagnostics → `Icons.Outlined.BugReport` (both in `material-icons-extended`, already a dependency at `app/build.gradle.kts:111`).
- `SpartanAccent`/`SpartanAccentColor` deleted (or, if any doc references it as the brand color, updated to `0xFF0B685C`).
- Every user-visible hardcoded string moved to `strings.xml` and referenced via `stringResource`/`getString`; `.uppercase()` → `.uppercase(java.util.Locale.getDefault())` or better, bake the caps into the string resource as other labels do.

## Steps (numbered requirements)

1. `SpartanRoot.kt:63`: `Icons.Outlined.Flag` → `Icons.Outlined.Insights`.
2. `Screens.kt:557`: `Icons.Outlined.Edit` (Diagnostics row) → `Icons.Outlined.BugReport`.
3. `Theme.kt:18` and `:47-48`: delete `SpartanAccent` and `SpartanAccentColor`; run a repo grep for `SpartanAccentColor` first — if any source file uses it, STOP and report.
4. `TrajectoryCard.kt:29`: string → `strings.xml` (`trajectory_title`).
5. `MetricExplainerSection.kt:28-37`: five headers → `strings.xml` (`explainer_*`).
6. `ExerciseDebriefSheet.kt`: all seven user-visible strings → `strings.xml` (`debrief_*`); "Effort %d of 10" becomes a positional format string.
7. `Screens.kt` diagnostic `statusLabel`/`targetLabel` values (search for `"Clinical normal"`) → `strings.xml`. These render in UI badges; map enum → resource id, not enum → literal.
8. `NextActivityWidget.kt:86, 105, 112-114`: strings → `strings.xml` (Glance supports `context.getString`).
9. `CoachScreen.kt:151`: remove `.uppercase()`; add an explicitly-caps string resource (`plan_title_caps`) instead, matching how `checkin_sample_data` ("SAMPLE DATA") already does it.

If code at a cited line doesn't match, STOP and report the requirement number. No silent skips: every string moved is listed in the self-audit with old literal → new resource name.

## Boundaries

- Do NOT reword any copy — byte-identical user-visible text, only its storage moves.
- Do NOT touch iOS (its copy-duplication convention is deliberate; a localization pass there is a future decision).
- Do NOT restyle the nav bar or Settings rows.

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin test` → exit 0; `grep -rn '"Clinical normal"\|"Where this can take you"\|"Nice work' app/src/main/java` → 0 hits.
- **Feel check**: Review tab icon reads as trends; every moved string renders identically; Coach "PLAN" section label unchanged visually.
- **Done when**: all 9 requirements confirmed; the self-audit lists the full literal→resource mapping.

## Closing self-audit (2026-08-13)

1. **done** — `SpartanRoot.kt:63`: Review tab `Icons.Outlined.Flag` → `Icons.Outlined.Insights` (import at `:18`).
2. **done** — Diagnostics row `Screens.kt:579`: `Icons.Outlined.Edit` → `Icons.Outlined.BugReport` (import added; cited `:557` at cad6100, shifted by plans 008/011).
3. **done** — repo grep for `SpartanAccentColor` found zero users outside `Theme.kt` (STOP not triggered); `SpartanAccent` (`Theme.kt:18`) and `SpartanAccentColor` (`:47-48`) deleted; a comment now records that light mode's accent is `0xFF0B685C`. `SpartanAccentDark` kept — it is live (`DarkColors.primary`).
4. **done** — `TrajectoryCard.kt:31`: title → `R.string.trajectory_title`.
5. **done** — `MetricExplainerSection.kt:29-39`: five headers → `explainer_title` ("Understanding %1$s"), `explainer_what_it_is`, `explainer_what_moves_it`, `explainer_good_pattern`, `explainer_how_used`.
6. **done** — `ExerciseDebriefSheet.kt`: all seven strings resourced (mapping below); "Effort %d of 10" is the positional `debrief_effort` ("Effort %1$d of 10").
7. **done** — `Screens.kt:926-940`: `statusLabel`/`targetLabel` (enum → literal) replaced by `statusLabelRes`/`targetLabelRes` (enum → resource id), resolved via `stringResource` at the `StatusChips` call sites (`:741`, `:743`).
8. **done** — `NextActivityWidget.kt`: `"SPARTAN"` → `context.getString(R.string.common_brand)` (`:87`); `"~… min"` → `widget_minutes` (`:106`); the three empty-state strings → `widget_no_plan` / `widget_all_done` / `widget_none_left` (`:112-114`). Glance `context.getString` throughout.
9. **done** — `CoachScreen.kt:157`: `.uppercase()` removed; label now `stringResource(R.string.plan_title_caps)` ("WEEKLY PLAN" — the caps baked into the resource, matching `checkin_sample_data`).

**Full literal → resource mapping** (byte-identical text; apostrophe escaped per XML):

| Old literal | Resource |
|---|---|
| "Where this can take you" | `trajectory_title` |
| "Understanding ${…}" | `explainer_title` ("Understanding %1$s") |
| "What it is" | `explainer_what_it_is` |
| "What moves it" | `explainer_what_moves_it` |
| "What a good pattern looks like" | `explainer_good_pattern` |
| "How Spartan uses it" | `explainer_how_used` |
| "Nice work. How did it go?" | `debrief_title` |
| "Minutes" | `debrief_minutes` |
| "Effort ${…} of 10" | `debrief_effort` ("Effort %1$d of 10") |
| "Pain or concerning symptoms" | reused existing `workout_pain_label` (identical, per the reuse-if-identical convention) |
| "Save session" | `debrief_save` |
| "Skip" | `debrief_skip` |
| "Your effort and pain answers shape next week's plan. …" | `debrief_note` |
| "Below range" | `status_below_range` |
| "Clinical normal" | `status_clinical_normal` |
| "Above range" | `status_above_range` |
| "Pending" (both enums) | `status_pending` (new — existing `common_pending` is lowercase "pending", not byte-identical) |
| "Reference hidden" | `status_reference_hidden` |
| "Meets personal target" | `target_meets` |
| "Above personal target" | `target_above` |
| "Below personal target" | `target_below` |
| "No target" | `target_none` |
| "SPARTAN" (widget) | reused existing `common_brand` |
| "~${…} min" (widget) | `widget_minutes` ("~%1$d min") |
| "No plan yet" | `widget_no_plan` |
| "All done for today" | `widget_all_done` |
| "No activities left today" | `widget_none_left` |
| `plan_title.uppercase()` | `plan_title_caps` ("WEEKLY PLAN") |

Boundaries respected: zero copy rewording (storage moved only); iOS untouched; nav bar/Settings rows unstyled. Verification: `./gradlew :app:compileDebugKotlin :app:test` → exit 0, `BUILD SUCCESSFUL in 40s` (all unit tests pass); the plan's grep (`"Clinical normal"|"Where this can take you"|"Nice work`) → 0 hits (one doc comment updated to cite the resource instead of quoting the title); `uppercase` grep in CoachScreen.kt → 0 hits; `SpartanAccent`-exact grep → only `SpartanAccentDark` (live token) remains.
