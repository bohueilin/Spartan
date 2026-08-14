# 011 — One card radius

- **Status**: DONE
- **Commit**: cad6100
- **Severity**: Tier 5 (token drift with Tier 3 consequences)
- **Scope**: ~4 files (Screens.kt, ConnectionsScreen.kt, and the two iOS Connections/Settings views)

## Problem

The token says cards are 18dp (`Tokens.kt:28` `Radius.card`), and Today/Coach honor it — but most other cards hardcode 16dp, and Plan's cards fall through to the M3 default 12dp. Same component, three radii:

- `Screens.kt:212, 559, 690, 750, 786, 820` — `RoundedCornerShape(16.dp)`
- `Screens.kt:266, 304 (shape absent), 419, 623` — `OutlinedCard` with no `shape` → M3 default 12dp
- `ConnectionsScreen.kt:132, 172` (as of the original review) — `16.dp`
- iOS: `ConnectionsView.swift:222, 227, 273, 278` and `SettingsAboutView.swift:49, 54, 70, 75` — `cornerRadius: 16`; `ConnectedChip` at `ConnectionsView.swift:337` — `6` while `SampleSourceChip` uses the chip token 8.

## Target

Every card uses `Radius.card` (18dp) / `SpartanRadius.card`; every chip uses `Radius.chip` (8dp) / `SpartanRadius.chip`. Zero numeric radius literals for cards or chips on either platform.

## Steps (numbered requirements)

1. Android: at each `RoundedCornerShape(16.dp)` card site in `Screens.kt` and `ConnectionsScreen.kt`, replace with `RoundedCornerShape(Radius.card)` (import `com.spartan.ui.theme.Radius` where missing).
2. Android: every `OutlinedCard(`/`Card(` without a `shape` argument in `Screens.kt` gets `shape = RoundedCornerShape(Radius.card)`. Find them all: `grep -n "OutlinedCard(\|Card(" Screens.kt` and check each for a shape param — list every touched line in the self-audit.
3. iOS: replace `cornerRadius: 16` with `SpartanRadius.card` at the eight cited sites.
4. iOS: `ConnectedChip` `cornerRadius: 6` → `SpartanRadius.chip`.
5. Sweep check: `grep -rn "RoundedCornerShape(1[0-9]\.dp\|cornerRadius: 1[0-9][^.]" app/src/main/java/com/spartan/ui ios/SpartanApp/Sources` — remaining hits must each be justified in the self-audit (the 10dp skeleton and 9dp check glyph are deliberate component shapes, not cards: leave them).

If a cited site doesn't match, STOP and report the requirement number.

## Boundaries

- Do NOT change the token values themselves (chip 8 / card 18).
- Do NOT touch the widget (`NextActivityWidget.kt` already uses 18dp).
- Do NOT alter the check glyph 9dp, skeleton 10dp, or progress-clip 5dp — not cards.

## Verification

- **Mechanical**: `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:compileDebugKotlin` → exit 0; iOS diff self-reviewed.
- **Feel check**: Settings, Connections, Plan, Review cards visually match Today's corner rounding.
- **Done when**: the requirement-5 grep output is clean or fully justified, listed in the self-audit.

## Closing self-audit (2026-08-13)

Line numbers are current-tree (Screens.kt shifted ~+15 vs the cad6100 cites after plans 008/010; each cited site was matched by content).

1. **done** — all six `RoundedCornerShape(16.dp)` sites in `Screens.kt` → `Radius.card`: `:220` (WhoopImportBanner), `:580` (Settings about), `:711` (SummaryCard), `:771` (TrendCard), `:807` (SettingsCard), `:841` (ReminderEditor); both `ConnectionsScreen.kt` sites (`:135`, `:175`; `Radius` import added).
2. **done** — all four shape-absent card sites in `Screens.kt` got `shape = RoundedCornerShape(Radius.card)`: `:274` (Metric Detail history entry), `:312-319` (TrainThisMetric video card), `:434` (WeeklyPlanSection workout card), `:644` (Privacy export card). Full `grep -n "OutlinedCard(\| Card("` re-run confirms every card in the file now passes an explicit `Radius.card` shape.
3. **done** — all eight iOS `cornerRadius: 16` → `SpartanRadius.card`: `ConnectionsView.swift:231, :235` (IntegrationCard), `:282, :286` (WhoopImportResultCard); `SettingsAboutView.swift:49, :53, :70, :74`.
4. **done** — iOS `ConnectedChip` `cornerRadius: 6` → `SpartanRadius.chip` (`ConnectionsView.swift:348`).
5. **done** — sweep grep found four hits beyond the cited list; per this requirement's own rule (remaining hits justified or eliminated — a 16dp `OutlinedCard` is a card, not a deliberate component shape) they were fixed, not excused: `TrajectoryCard.kt:28` 16dp → `Radius.card`; `MetricExplainerSection.kt:27` 16dp → `Radius.card`; Android CONNECTED chip `ConnectionsScreen.kt:144` 6dp → `Radius.chip` (the exact Android twin of requirement 4's iOS chip — cross-platform parity); `StatusBadge` `Screens.kt:749` 8dp literal → `Radius.chip` (same value, now tokenized). Final sweep output — every remaining hit is a plan-sanctioned component shape: skeleton 10dp (`SkeletonComponents.kt:42`; iOS `CheckInView.swift:546, :550`), check glyph 9dp/9 (`CheckInScreen.kt:507`; `CheckInView.swift:422, :424`), progress clip 5dp (`CheckInScreen.kt:330`).

Boundaries respected: token values unchanged (chip 8 / card 18); `NextActivityWidget.kt` untouched; glyph/skeleton/progress shapes untouched. Note: the ConnectedChip changes (6 → 8, both platforms) and the two extra 16dp cards are the only visually observable deltas beyond the cited list — all are the Target's own definition of correct. Verification: `./gradlew :app:compileDebugKotlin` → exit 0, `BUILD SUCCESSFUL in 16s`; iOS diff self-reviewed; SpartanKit untouched.
