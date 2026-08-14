# 013 — Icon fixes, dead accent token, hardcoded-string sweep

- **Status**: TODO
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
