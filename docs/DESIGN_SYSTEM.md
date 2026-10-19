# Spartan — Design System (Android)

The canonical reference for how Spartan's Android UI is built. Code is the source of truth:
`ui/theme/` (tokens, colors, type) and `ui/components/Components.kt` (the shared building blocks).
Brand palette and marketing assets live in [VISUAL_AND_VIDEO_GUIDE.md](VISUAL_AND_VIDEO_GUIDE.md).

The direction follows the patterns of best-in-class consumer apps (Airbnb is the explicit
reference): a white canvas, soft-shadow cards, a bold type scale with large page titles, one
primary action per page, and nothing on screen that the user can't act on or doesn't need.

## 1. Product principle: every number leads to an action

A number on its own is trivia. Every signal Spartan shows answers three questions, in order:

1. **Where am I?** The latest value, read against *your own normal*: the middle half (25th–75th
   percentile) of your prior days, not a population average. Shown as a shaded band on the chart
   and as a plain status: *Higher than usual*, *Lower than usual*, *In your normal range*, or
   *Still learning your normal* (until there are 5 prior days).
2. **What does it mean?** One safe, plain-language sentence (`SignalEngine.meaning`). It never
   diagnoses and follows the AGENTS.md health-safety rules.
3. **What do I do?** A concrete action from `ActionPlaybook` with an **Add to today** button. The
   added activity lands in today's plan, the row flips to *In today's plan*, and a snackbar
   offers **Undo**.

Domain code: `domain/engine/Signals.kt` (`SignalEngine`, `ActionPlaybook`, `SleepPlanner`).
User-added activities carry `ruleId = "USER_ADDED"` so they never stop the daily plan from being
generated and survive a plan regeneration.

## 2. Information architecture

Four tabs. The bottom bar shows only on tab roots; pushed pages are immersive.

| Tab | Job | Key surfaces |
|---|---|---|
| **Today** | What to do now | Readiness ring + "What's driving it" (HRV, resting HR, sleep, strain vs. your normal) · Today's plan · Tonight (bedtime target from sleep debt) · Consistency |
| **Insights** | Make sense of the data | One card per daily signal: value, status, sparkline with your normal band, meaning, and a "Try" action · Lab results |
| **Progress** | Is it working? | Goal · this week's stats · next week's focus · trends · reflections · training week |
| **You** | Settings and trust | Profile · plan settings (goal, weekly plan, high-pressure windows, healthy ranges) · Connections · Reminders · Privacy |

Pushed pages: metric detail, add/edit metric, workout log, connections, reminders, privacy,
training week, pressure windows, healthy ranges, diagnostics (debug only).

## 3. Tokens

| Token | Value | Notes |
|---|---|---|
| Spacing | 4 / 8 / 12 / 16 / 20 / 24, section 32 | `Spacing.xs…xxl`, `Spacing.section`; page gutter is 24 (`PageGutter`) |
| Radius | chip 8 · tile 12 · card 20 | Buttons and pills are fully round (`CircleShape`) |
| Motion | 140 / 220 / 420 ms | Fast (check-off), medium (state change), slow (reveal). All respect reduced motion. |
| Canvas | light `#FFFFFF` · dark `#0A0F0E` | Mirrored in iOS `spartanBackground` |

Every Material 3 color role is defined for light and dark in `Theme.kt`, so no baseline purple
can leak through a default component.

## 4. Type

Inter (variable, OFL; license in `docs/licenses/Inter-OFL.txt`), defined in `Typography.kt`.

| Role | Size / line / weight | Used for |
|---|---|---|
| displaySmall | 34 / 40 / 800 | Hero values (metric detail), onboarding headline |
| headlineLarge | 30 / 36 / 700 | Tab page titles, Today greeting |
| headlineMedium | 26 / 32 / 700 | Pushed-page titles |
| headlineSmall | 22 / 28 / 700 | Sheet titles |
| titleLarge → titleSmall | 20 / 17 / 15, 600 | Section headers, card titles, row titles |
| bodyLarge → bodySmall | 16 / 15 / 13 | Copy |
| labelLarge / Medium / Small | 15 / 13 / 11 | Buttons, pills, eyebrows (labelSmall is +0.04em, uppercase use only) |

Rule: screens use the scale and never override `fontWeight`. (The Glance widget is the one
exception; it can't use Compose typography.)

## 5. Components (`ui/components/Components.kt`)

| Component | Use it for |
|---|---|
| `SpartanCard` | The one card. Light: soft wide shadow, no heavy border. Dark: lifted surface + hairline. Optional `onClick` with an `onClickLabel`. |
| `ScreenTitle` | Tab root title + optional subtitle and trailing slot (e.g. `SampleDataChip`). |
| `SectionHeader` | Section title with an optional text action. |
| `StatusPill` + `Tone` | Calm status: POSITIVE, NEUTRAL, CAUTION, ALERT, INFO. Replaces red borders and shouty caps. |
| `IconTile` | Rounded 44dp icon tile tinted by category or status. |
| `ListRow` | Settings-style row: icon tile, title, subtitle, chevron. |
| `PrimaryButton` / `SecondaryButton` | 52dp minimum, fully round, grows with font scale. One primary per page. |
| `DetailScaffold` / `DetailPage` | Every pushed page: quiet back arrow, large title in the content, optional sticky bottom action bar (the "Reserve" pattern). |
| `Sparkline` | Trend with gradient fill, end dot, and an optional shaded "your normal" band. |

Also shared: `SampleDataChip` (provenance; required on every surface showing sample data) and
`SafetyBanner` (calm info banner with a polite live region).

## 6. Interaction patterns

- **Activity sheet over hidden menus.** Tapping a plan card opens one sheet with everything about
  that activity (why, what it improves, numbered steps, safety note, follow-along video) and every
  action: **Mark as done** (primary), Find a time, Snooze 1 hour, Skip today. No overflow menus.
- **Reversible by default.** Check-off, skip, snooze and add all confirm with a snackbar and Undo.
- **Status, not alarm.** Overdue and due are pills, not red card borders. Required is an INFO pill.
- **Adaptive copy.** The Tonight card changes to "Lights out soon" when the target has passed
  instead of showing a time already gone.
- **Insets are padded once.** The root Scaffold consumes the system-bar insets it pads for, so a
  pushed page's own Scaffold doesn't pad them a second time.

## 7. Accessibility contract

- 48dp minimum touch targets (the plan check is a 28dp visual in a 48dp target).
- Cards and tiles that open something carry an `onClickLabel` naming the destination ("Open HRV details").
- Status text wraps instead of truncating at large font scales. It's the part that matters.
- Headings carry `heading()` semantics. Charts are hidden from TalkBack (`clearAndSetSemantics {}`)
  because the value, status, and normal range beside them say the same thing as text.
- Verified at font scale 1.3 and in dark mode on the emulator.

See also [ACCESSIBILITY.md](ACCESSIBILITY.md).
