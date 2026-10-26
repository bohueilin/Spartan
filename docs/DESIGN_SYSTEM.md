# Spartan — Design System (Android)

The canonical reference for how Spartan's Android UI is built. Code is the source of truth:
`ui/theme/` (tokens, colors, type) and `ui/components/Components.kt` (the shared building blocks).
Brand palette and marketing assets live in [VISUAL_AND_VIDEO_GUIDE.md](VISUAL_AND_VIDEO_GUIDE.md).

The direction is Material 3 Expressive, restrained (`SPARTAN_WORLD_CLASS_SPEC.md`, 2026-10-25):
one hero per screen, one primary action per screen, one emphasised headline per screen, and
everything else quieter. A barely-tinted canvas, flat outlined cards (no stacked shadows), a
filled deep-teal hero, colour that means something (the recovery zone, always with its word and
number), and nothing on screen that the user can't act on or doesn't need.

## 1. Product principle: every number leads to an action

A number on its own is trivia. Every signal Spartan shows answers three questions, in order:

1. **Where am I?** The latest value, read against *your own normal*: the middle half (25th–75th
   percentile) of your prior days, not a population average. Shown as a shaded, labelled band on
   the chart ("Your usual 49–78%") and as a plain status: *In your usual range* (only when it is),
   *Above your usual*, *Below your usual*, or still learning (until there are 5 prior days).
2. **What does it mean?** One safe, plain-language sentence (`insightMeaningRes` in
   `InsightsScreen.kt`, shared by metric detail; Recovery's line also follows its zone). It never
   diagnoses and follows the AGENTS.md health-safety rules.
3. **What do I do?** At most one concrete action from `ActionPlaybook`, unique across the cards
   on screen (`ActionPlaybook.uniqueFirstActions`), with an **Add** button. The added activity
   lands in today's plan, the row flips to *In today's plan*, and a snackbar offers **Undo**.

Domain code: `domain/engine/Signals.kt` (`SignalEngine`, `ActionPlaybook`, `SleepPlanner`).
User-added activities carry `ruleId = "USER_ADDED"` so they never stop the daily plan from being
generated and survive a plan regeneration.

## 2. Information architecture

Four tabs. The bottom bar shows only on tab roots; pushed pages are immersive.

| Tab | Job | Key surfaces |
|---|---|---|
| **Today** | What to do now | Hero verdict (ring to the real score + zone word + number) · one primary action (start / next / set bedtime / add wind-down) · 3–5 checkable actions · Why today? · Tonight (always an action) · Last 7 days · the one in-context safety line |
| **Insights** | Make sense of the data | One card per daily signal, each in its own colour: value, status, chart with labelled usual band, day labels and latest point, one plain line, at most one unique "Try" · weekday patterns · lab results (no blood pressure) |
| **Progress** | Is it working? | Goal hero (ring vs. weekly target, or goal setup as the primary action) · week stats with definitions · forgiving consistency · projection range bands with the illustrative-estimate disclaimer · weekly review · trends · training week |
| **You** | Settings and trust | Profile header · Your plan (goal, weekly plan, plan settings, reminders, high-pressure windows) · Data and devices (connections, healthy ranges) · Display · Privacy · one About row (version, full disclaimer, licences) |

Pushed pages: metric detail, add/edit metric, workout log, connections, reminders, privacy,
training week, pressure windows, healthy ranges, about, diagnostics (debug only).

## 3. Tokens

| Token | Value | Notes |
|---|---|---|
| Spacing | 4 / 8 / 12 / 16 / 20 / 24, section 32 | `Spacing.xs…xxl`, `Spacing.section`; page gutter is 24 (`PageGutter`) |
| Radius | chip 8 · tile 12 · card 20 · hero 28 | Buttons and pills are fully round (`CircleShape`) |
| Touch target | 48dp (`MinTouchTarget`) | Every action row and icon button, even when the visible icon is smaller |
| Motion | springs: `Motion.measure()` (no overshoot — rings, charts, progress), `settle()` (state changes); the check pop is the Lottie completion morph | Only on a state change; `rememberReducedMotion()` (system setting or Calmer theme) shows the final state. Opacity fades keep 140 / 220 / 420 ms tweens. A reduced-motion sight still uses a one-shot up. |
| Canvas | light `#F6F8F7` · dark `#0A0F0E` | iOS `spartanBackground` still has the pre-redesign value |
| Colour | primary `#0B5B4F`, onSurfaceVariant `#46564F`, hero `#0B5B4F`, zones (text / mark / on-hero) | `SpartanBands`, `SpartanZones`, `heroColors()`, `metricAccent()`; every pair checked by `TokenContrastTest` |

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
| `SpartanCard` | The one card: flat, 20dp, a hairline outline, no shadow, both themes. Optional `onClick` with an `onClickLabel`. |
| `HeroCard` | The one filled deep-teal surface per screen (28dp). Text uses `heroColors()`; the zone uses `zoneOnHeroColor`. |
| `ScreenTitle` | Tab root title (the screen's one headline) + optional subtitle and trailing slot (e.g. `SampleDataChip`). |
| `SectionHeader` | Section title with an optional text action. |
| `StatusPill` + `Tone` | Calm status: POSITIVE, NEUTRAL, CAUTION, ALERT, INFO. Replaces red borders and shouty caps. |
| `IconTile` | Rounded 44dp icon tile tinted by category or status. |
| `ListRow` | Settings-style row: icon tile, title, subtitle, chevron. |
| `PrimaryButton` / `SecondaryButton` / `HeroTextButton` | 52dp minimum, fully round, centred labels that grow with font scale. One primary per page; `onHero = true` inverts it on a hero. |
| `DetailScaffold` / `DetailPage` | Every pushed page: quiet back arrow, large title in the content, optional sticky bottom action bar (the "Reserve" pattern). |
| `TrendChart` (`Charts.kt`) | A metric's days: labelled "your usual" band, line, day labels that thin evenly at large text, the latest point labelled, one TalkBack summary, draws in once per session. |
| `RecoveryRing` / `MeasureRing` (`Charts.kt`) | A ring that fills to exactly the value (butt caps), sweeping once with no overshoot; final state under reduced motion. |
| `CompletionCheck` / `rememberCompletionMorph` (`LottieMoments.kt`) | The green check pop and the done row's faint green, rounder card. No strikethrough. |

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
- Headings carry `heading()` semantics. Every chart is one TalkBack node whose description is the
  takeaway in a sentence ("Recovery 64%, Yellow zone, within your usual of 49 to 78%, rising over
  7 days"); its marks are not read one by one.
- Colour is never alone: the zone is always its word and number; done is a filled check plus the
  "Completed" state.
- Verified on the emulator (2026-10-25) in light, dark and Calmer, at 200% font, with no clickable
  smaller than 48dp on any tab.

See also [ACCESSIBILITY.md](ACCESSIBILITY.md).
