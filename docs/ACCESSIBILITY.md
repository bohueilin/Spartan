# Spartan — Accessibility

## Contrast audit (WCAG 2.x AA) — executed 2026-07-09

Computed programmatically over the theme tokens in `ui/theme/Theme.kt` + `ui/theme/Tokens.kt`
(relative-luminance contrast ratios; AA thresholds: 4.5:1 body text, 3:1 large text/graphics).

| Pair | Dark | Light | Verdict |
|---|---|---|---|
| Body `onSurface` / `surface` | 15.68:1 | 16.81:1 | PASS |
| Body `onSurface` / `background` | 16.85:1 | 15.77:1 | PASS |
| Muted `onSurfaceVariant` / `surface` | 7.90:1 | 7.64:1 | PASS |
| Muted `onSurfaceVariant` / `surfaceVariant` | 6.53:1 | 6.39:1 | PASS |
| `primary` text / `surface` | 10.87:1 | 5.09:1 | PASS |
| `tertiary` (safety notes) / `surface` | 9.34:1 | 4.72:1 | PASS |
| `secondary` text / `surface` | 10.00:1 | 11.69:1 | PASS |
| Band **primed** / `surface` | 8.98:1 | ~~2.00:1~~ → **5.34:1** | **FIXED** |
| Band **easy** / `surface` | 9.34:1 | ~~1.92:1~~ → **5.25:1** | **FIXED** |
| Band **rest** / `surface` | 6.24:1 | ~~2.88:1~~ → **5.83:1** | **FIXED** |

**Finding + fix:** the readiness-band colors (ring, band label) passed on the dark surface but
failed 3:1 on light. `Tokens.kt` `bandColor()` is now theme-aware: light mode uses darkened
variants (`#0E7B43` / `#8F6410` / `#B23E20`), all ≥4.5:1 on white. Re-run this audit whenever a
token changes.

## Built-in accessibility contract (enforced in code/tests)

- Every interactive element carries Compose `semantics` (`contentDescription`, `stateDescription`,
  `Role.Checkbox` on check-offs); the readiness ring merges descendants into one announcement.
- 48dp minimum touch targets (checkbox hit area is 48dp around the 26dp visual;
  `CheckInScreenTest.activityCheckbox_meetsTouchTargetMinimum` asserts it on device).
- Color is never the only signal: band color is always paired with a text label ("Take it easy"),
  priority chips carry text, completed activities get strikethrough + "Completed" status text.
- Dynamic type: all text uses the Material 3 type scale (sp); no fixed-height text containers.
  Font-scale previews at 1.5×/2.0× live in `ui/screens/FontScalePreviews.kt`.

## TalkBack test script (manual pass, ~10 min — run before each release)

Setup: Settings → Accessibility → TalkBack ON. Fresh install of the release candidate.

1. **Onboarding.** Swipe through: wordmark → headline → description → name field → height field →
   Begin. Verify each field announces its label; Begin announces as a button.
2. **Check-in first-open.** Verify focus order: SPARTAN → SAMPLE DATA chip → readiness ring
   (announces "Recovery NN percent, <band>") → progress ("N of M activities done") → plan cards.
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
