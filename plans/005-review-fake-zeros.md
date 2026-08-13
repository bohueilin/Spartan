# 005 — Review tab: never render confident zeros for absent data

- **Status**: TODO
- **Commit**: 91c0816
- **Severity**: Tier 2 (trust)
- **Scope**: 2 files (Screens.kt, strings.xml)

## Problem

An unloaded or absent weekly review renders as a real result:

```kotlin
// app/src/main/java/com/spartan/ui/screens/Screens.kt:496-497 — current
SummaryCard(stringResource(R.string.review_adherence), stringResource(R.string.review_percent_value, review?.adherencePercent ?: 0), Modifier.weight(1f))
SummaryCard(stringResource(R.string.review_strength), "${review?.strengthSessions ?: 0}", Modifier.weight(1f))
```

`?: 0` shows **"0%" adherence and "0" strength sessions in headlineSmall/SemiBold** — indistinguishable from a genuinely bad week. A number the user is asked to trust is fabricated by a null-coalesce. (The Review tab also has no loading state; the zeroed layout doubles as its loading render.)

## Target

`review == null` → the summary grid and trend cards are replaced by a designed empty state: what this is, why it's empty, and when it fills. No numeric placeholder anywhere.

## Conventions to follow

- Empty-state exemplar: `EmptyPlan` (`CheckInScreen.kt:610-618`) — one calm sentence inside a standard card. Copy that structure.
- Card style: `OutlinedCard` with `Radius.card` (`Tokens.kt:27`), padding `Spacing.lg`.
- Strings in `res/values/strings.xml`; wellness voice, no shame (see `AGENTS.md` safety rules).

## Steps (numbered requirements)

1. In the Review screen composable (`Screens.kt:488-518`), branch on `review == null`.
2. Null branch: render one `OutlinedCard` (`Radius.card`, `Spacing.lg` padding) containing a new string `review_empty_body`: "Your weekly review appears after your first full week of activity. Check back Sunday." — `bodyMedium`, `onSurfaceVariant`.
3. Non-null branch: existing content unchanged, but remove both `?: 0` fallbacks (safe now — branch guarantees non-null).
4. Keep the `SampleDataChip` (`Screens.kt:493`) visible in both branches.

If code at a cited line doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT add a spinner or skeleton here (a broader loading-state pass is a separate, lower-tier item).
- Do NOT change `TrajectoryCard` / `TrendCard` internals — they already early-return on empty data.
- Do NOT touch other tabs.

## Verification

- **Mechanical**: `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :app:compileDebugKotlin` passes.
- **Feel check**: fresh install (or cleared data) → Review tab shows the empty-state card, no "0%" anywhere; after a week of mock data → summary grid as before.
- **Done when**: all 4 requirements confirmed; grep for `?: 0` in the Review composable returns nothing.
