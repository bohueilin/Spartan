# Spartan — Accessibility

## Redesign tokens (WCAG 2.x AA) — 2026-10-25

The world-class redesign (`SPARTAN_WORLD_CLASS_SPEC.md`) replaced the light palette. Every text and
mark pair below is asserted in `app/src/test/java/com/spartan/ui/TokenContrastTest.kt` for light,
dark, calm light and calm dark (text 4.5:1, non-text 3:1; tints composited over their surface).

| Pair | Dark | Light |
|---|---|---|
| Body `onSurface` / `surface` | 15.68:1 | 17.75:1 |
| Muted `onSurfaceVariant` (`#46564F` light) / `surface` | 7.90:1 | 7.77:1 |
| Muted `onSurfaceVariant` / `surfaceVariant` | 6.53:1 | 7.07:1 |
| Muted `onSurfaceVariant` / `background` | 8.49:1 | 7.28:1 |
| `primary` (`#0B5B4F` light) text / `surface` | 10.52:1 | 8.00:1 |
| `onPrimary` / `primary` (filled button) | 7.65:1 | 8.00:1 |
| Zone green text (`#15693C` light) / `surface` · on its 18% tint | 8.98 · 6.32:1 | 6.74 · 5.12:1 |
| Zone amber text (`#875200` light) / `surface` · on its 18% tint | 9.34 · 6.48:1 | 6.49 · 4.96:1 |
| Zone red text (`#B3261E`) / `surface` · on its 18% tint | 6.24 · 4.79:1 | 6.54 · 4.84:1 |
| `outline` (switch and field boundary) / `surface` | 4.13:1 | 3.89:1 |

**Spec colours kept as marks only.** The spec's green `#1B7A4D` is 4.39:1 on its own 14% pill
tint and the amber `#C47A00` 3.43:1 on white, so neither may be text; zone text uses the darkened
`#15693C` / `#875200`. Green and red (`#B3261E`) stay the zone marks for rings, chart lines and the
completion check. A chart line is drawn over its own shaded band and area fill, and there the
spec's amber is only 2.45:1, so the amber mark is `#A36500` (3.19:1 on its band and area, 4.75:1 on
white); every chart line is ≥3:1 on its own band (green 3.48, red 3.92, accents ≥3.41, dark ≥3.86).
The amber `#F7C948` appears only on the deep-teal hero (5.11:1).

**The hero** (`#0B5B4F` in both themes; Calmer `#2F4F4A`) is not taken from the colour scheme, so
wallpaper colours can never put a Yellow ring on a yellow card. On it: white text 8.00 / 8.98:1,
secondary `#C9E3DC` 5.91 / 6.63:1, zone green `#7EE2A8` 5.08 / 5.70:1, amber `#F7C948` 5.11 /
5.73:1, red `#FFB4AB` 4.71 / 5.29:1; each ring arc is ≥3.18:1 against its 16%-white track.

**Chart identity colours** (never zone meaning): light HRV 5.23, resting HR 6.72, sleep 7.64,
respiratory 6.02, strain 7.13:1 on white; dark all ≥9.4:1 on `surface`.

**Completion check:** the filled circle is zone green (5.33:1 light / 8.98:1 dark on the row) with
a white / near-black check (5.33 / 8.46:1). A done row keeps its normal title colour on a faint
green tint (title 15.90:1, meta 6.96:1) — no strikethrough, so it reads as done, not disabled.

The older audits below record the pre-redesign palette and the fixes that led to it.

## Contrast audit (WCAG 2.x AA) — executed 2026-07-09, re-run 2026-08-11 and 2026-10-24

Computed programmatically over the theme tokens in `ui/theme/Theme.kt` + `ui/theme/Tokens.kt`
(relative-luminance contrast ratios; AA thresholds: 4.5:1 body text, 3:1 large text/graphics).

| Pair | Dark | Light | Verdict |
|---|---|---|---|
| Body `onSurface` / `surface` | 15.68:1 | 16.81:1 | PASS |
| Body `onSurface` / `background` | 16.85:1 | 15.77:1 | PASS |
| Muted `onSurfaceVariant` / `surface` | 7.90:1 | 7.64:1 | PASS |
| Muted `onSurfaceVariant` / `surfaceVariant` | 6.53:1 | 6.39:1 | PASS |
| `primary` text / `surface` | 10.87:1 | ~~5.09:1~~ → **6.67:1** | PASS |
| `tertiary` (safety notes) / `surface` | 9.34:1 | ~~4.72:1~~ → **6.51:1** | PASS |
| `secondary` text / `surface` | 10.00:1 | 11.69:1 | PASS |
| Band **primed** / `surface` | 8.98:1 | ~~2.00:1~~ → ~~5.34:1~~ → **6.34:1** | **FIXED** |
| Band **easy** / `surface` | 9.34:1 | ~~1.92:1~~ → **6.51:1** | **FIXED** |
| Band **rest** / `surface` | 6.24:1 | ~~2.88:1~~ → **6.82:1** | **FIXED** |

**Finding + fix (2026-07-09):** the readiness-band colors (ring, band label) passed on the dark
surface but failed 3:1 on light. `Tokens.kt` `bandColor()` is now theme-aware: light mode uses
darkened variants. Re-run this audit whenever a token changes.

## Text-on-tinted-chip pairs (added 2026-08-11)

The original audit only tested text on plain surfaces. The app's chips render 11sp-bold small
text on the *same color at 12–18% alpha* — a tinted container that lowers contrast below the
plain-surface number. Every light token that appears on its own tint was darkened one step
(`primary` `#0E7C6E`→`#0B685C`, `tertiary` `#9A6A1B`→`#7E5613`, `easyLight` `#8F6410`→`#7C570E`,
`restLight` `#B23E20`→`#A0381D`; dark tokens already passed and are unchanged). The iOS token
set (`ios/SpartanApp/Sources/SpartanApp.swift`) mirrors the same values. Light-mode results:

| Chip | Pair | Light | Verdict |
|---|---|---|---|
| REQUIRED priority | `primary` on 14% tint / white card | ~~4.21:1~~ → **5.40:1** | **FIXED** |
| IMPROVES pill | `primary` on 12% tint / white card | ~~4.32:1~~ → **5.57:1** | **FIXED** |
| CONNECTED | `primary` on 16% tint / white card | ~~4.08:1~~ → **5.23:1** | **FIXED** |
| Day-complete title | `primary` on 10% tint / background | ~~4.20:1~~ → **5.41:1** | **FIXED** |
| Import banner title | `primary` on 12% tint / background | ~~4.07:1~~ → **5.25:1** | **FIXED** |
| SAMPLE DATA | `tertiary` on 16% tint / background | ~~3.62:1~~ → **4.85:1** | **FIXED** |
| Goal "Keep going" | `tertiary` on 14% tint / white card | ~~3.95:1~~ → **5.29:1** | **FIXED** |
| DUE urgency | `easyLight` on 18% tint / white card | ~~4.12:1~~ → **5.00:1** | **FIXED** |
| OVERDUE urgency | `restLight` on 18% tint / white card | ~~4.43:1~~ → **5.11:1** | **FIXED** |
| POSITIVE status pill ("Better than usual", "On track") | `primedLight` on 14% tint / white card | ~~4.39:1~~ → **5.14:1** | **FIXED** (2026-10-24) |
| `primedLight` on an 18% tint | `primedLight` on 18% tint / white card | ~~4.14:1~~ → **4.83:1** | **FIXED** (2026-10-24) |
| CAUTION / ALERT status pills | `easyLight` / `restLight` on 14% tint / white card | **5.31:1** / **5.47:1** | PASS |
| INFO / NEUTRAL status pills | `primary` / `onSurfaceVariant` on 14% tint / white card | **5.40:1** / **6.17:1** | PASS |
| White on `primary` (filled buttons) | — | ~~5.09:1~~ → **6.67:1** | PASS |

**Finding + fix (2026-10-24):** `StatusPill` draws its label in the tone colour on a 14% tint of
that colour. The light `primedLight` (`#0E7B43`) passed on white (5.34:1) but fell to 4.39:1 on its
own pill, and the Tokens.kt comment claimed a check that had not covered it. `primedLight` is now
`#0B6E3D` (6.34:1 on white). The same token is the GREEN recovery zone and the PRIMED band, so the
verdict text, ring and Primed label darken with it. Dark-mode pills already passed (`primedDark` on
its 14% tint over `surface`: 6.92:1). The iOS mirror (`spartanBandPrimed` in
`ios/SpartanApp/Sources/SpartanApp.swift`) and the palette table in
`docs/VISUAL_AND_VIDEO_GUIDE.md` now use `#0B6E3D` too, so all three agree.

Lab and manual blood-pressure statuses use the NEUTRAL pill (`onSurfaceVariant`, 6.17:1 above),
whatever the clinical status. The status ("Clinical normal", "Above range", …) is said in words on
the pill. Colouring an in-range value green or an out-of-range one amber would turn a clinical
reference range into an alert. Only personal-baseline signals ("Better than usual") keep the
POSITIVE / CAUTION tones.

## Non-text contrast (WCAG 1.4.11, 3:1 for UI components)

| Component | Pair | Dark | Light | Verdict |
|---|---|---|---|---|
| Unchecked action check ring (2dp) | ring on `surface` | ~~2.06:1~~ (`outline`) → **7.90:1** | ~~2.35:1~~ (`outline`) → **7.64:1** | **FIXED** (2026-10-24) |

The unchecked ring is the only visual sign of the Today screen's main control, so it now uses
`onSurfaceVariant`, the role M3's Checkbox uses for its unchecked border.

## Built-in accessibility contract (enforced in code/tests)

- Every interactive element carries Compose `semantics` (`contentDescription`, `stateDescription`,
  `Role.Checkbox` on check-offs); the readiness ring merges descendants into one announcement.
- 48dp minimum touch targets (checkbox hit area is 48dp around the 28dp ring, with a round,
  unbounded 24dp-radius ripple that fills the target;
  `CheckInScreenTest.activityCheckbox_meetsTouchTargetMinimum` asserts it on device).
- Color is never the only signal: the recovery zone is always its word and number ("Yellow ·
  64%"), band color is paired with a text label, priority chips carry text, and a completed
  activity shows a filled check and its "Completed" state (no strikethrough since 2026-10-25).
- Every chart is one TalkBack node whose description states the value, the user's usual range
  and the trend; the verdict hero is one stop that reads headline, zone, number, comparison,
  targets and data freshness.
- Dynamic type: all text uses the Material 3 type scale (sp); no fixed-height text containers and
  no truncation of key copy. `StatusPill`, the `ListRow` subtitle and the video card's title and
  meta wrap instead of clipping or ellipsizing; the activity sheet's pills sit in a `FlowRow`.
  Font-scale previews at 1.5×/2.0× live in `ui/screens/FontScalePreviews.kt`.
- One documented exception: navigation-bar labels (`SpartanRoot` `NavLabel`) follow the system-bar
  convention and scale up to at most 1.3×, stepping down to the largest scale that keeps the word on
  one line (never below the default size). A single word cannot reflow, so this keeps "Progress"
  whole instead of breaking it mid-word at 200%. Ellipsis is only a last resort below 1.0× fit
  (narrower than ~300dp). Everything else on screen follows the full font scale.

## TalkBack test script (manual pass, ~10 min — run before each release)

Setup: Settings → Accessibility → TalkBack ON. Fresh install of the release candidate.

1. **Onboarding.** Swipe through: wordmark → headline → description → name field → height field →
   Begin. Verify each field announces its label; Begin announces as a button.
2. **Today first-open.** Verify focus order: "Today" title → greeting and date → SAMPLE DATA chip →
   the hero (announces headline, "Recovery <zone>, NN percent", comparison, targets, freshness) →
   the primary action ("Start today's plan with <title>") → progress → plan cards.
3. **Expand a card.** Double-tap an activity card: verify the expand action is announced
   ("Expand <title>") and the why/steps content is readable in order.
4. **Check off an activity.** Focus the checkbox: verify it announces the activity title +
   "Not completed" + checkbox role; double-tap; verify state changes to "Completed" and the
   progress line updates on next focus.
5. **Overflow menu.** Verify "More options for <title>" is announced; menu items (Snooze / Skip /
   Find a time) are reachable and actionable.
6. **Sync-failure banner** (airplane mode with real integrations, or force): verify the banner text
   is reachable by swipe and read in full.
7. **Connections.** Verify each integration card reads: title → status → description → scope list →
   action button, and the button announces its action ("Connect", "Disconnect").
8. **Privacy.** Verify the delete flow: button announces; confirmation dialog traps focus; Cancel
   and Delete are both reachable.
9. **Bottom navigation.** Verify each tab announces name + selected state.
10. **Font scale 2.0 + TalkBack together** (worst case): repeat steps 2–4; verify nothing is
    clipped or unreachable.

Record the pass/fail per step in the release PR description.
