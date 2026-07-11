# Claude Artifacts for Spartan's mobile UX work — the playbook

How to use Claude (claude.ai artifacts or Claude Code) as your design studio while pushing Spartan
toward design-award-level craft. Each artifact type below lists **when to use it** and a
**ready-to-paste prompt**. Pair them with the implementation prompts in
[UX_ROADMAP_PROMPTS.md](UX_ROADMAP_PROMPTS.md): *artifact first to decide the design, roadmap
prompt second to build it.*

A live example of #1 is already published for this repo — the interactive check-in + trajectory
design board: https://claude.ai/code/artifact/b7ac6b2c-112b-49b6-9f22-640505c794c7
(private to the owner until shared from the artifact's own share menu).

---

## 1. Interactive phone-frame prototype (the workhorse)

**Use when:** deciding any screen-level change before writing Compose/SwiftUI. Reviewable on your
phone, shareable with anyone, zero build time.

> Build an interactive HTML artifact: an iPhone-sized phone frame (390×844) rendering Spartan's
> [SCREEN] with our design tokens — bg #0A0F0E, surface #121817, accent #3FE0C8, text #EAF1EF,
> muted #9DB0AB, band colors primed #38D07E / easy #E7B25A / rest #E67A5A, 18px card radius,
> Inter-like sans. Make [THE INTERACTION] actually work in JS (tap targets ≥48px). Copy must match
> our real strings (paste from app/src/main/res/values/strings.xml). Include a side rail annotating
> spacing/type/color decisions.

## 2. Motion studies (before touching animation code)

**Use when:** designing the check-off physics, ring sweep, sheet transitions. Motion is cheaper to
iterate in CSS than in Compose.

> Create an HTML artifact with 4 variants of Spartan's activity check-off animation side by side:
> (a) 150ms scale-spring on the checkbox, (b) checkbox spring + 250ms progress-bar fill, (c) card
> settle with strikethrough wipe, (d) all three orchestrated. Each variant replayable on click,
> with a duration/easing readout, `prefers-reduced-motion` fallback shown, and a recommendation.

## 3. Design-token spec sheet

**Use when:** aligning Android/iOS/marketing on one visual system, or evolving the palette.

> Build an artifact that renders Spartan's full design system as a spec sheet: color tokens with
> hex + WCAG contrast ratios against both grounds (compute them), type scale with live samples,
> spacing/radius scale, band-color usage rules, do/don't examples. Source of truth:
> app/src/main/java/com/spartan/ui/theme/Theme.kt and Tokens.kt (paste contents).

## 4. Flow map / state diagram

**Use when:** auditing journeys (onboarding→first plan, notification→check-in, consent→disconnect).

> Create an artifact diagramming Spartan's [JOURNEY] as a state flow: every screen, decision,
> notification, and empty/error/loading state as nodes; annotate each edge with the trigger and
> the file that implements it. Highlight dead ends and missing states in red.

## 5. Copy board (voice review)

**Use when:** rewriting coaching copy, notifications, or store text — Spartan's voice (calm,
concrete, never medical) is a design surface.

> Build an artifact showing every user-facing string in [AREA] as cards: current copy, a proposed
> revision, and a verdict against our voice rules (second person, no hype, no guilt, no medical
> claims — must pass phrases like SafetyEngine would). Let me click to approve/flag each; output
> an approved list I can paste back.

## 6. Accessibility audit board

**Use when:** preparing the Inclusivity story (see APPLE_DESIGN_AWARD_RUNWAY.md).

> Create an artifact that walks Spartan's check-in screen as a screen-reader would: the exact
> announcement order and text for every element (derive from the semantics in CheckInScreen.kt,
> pasted below), flagging anything unlabeled, out of order, or verbose, with the fix for each.

## 7. Comparative teardown

**Use when:** you want the jury's-eye view.

> Build an artifact comparing Spartan's daily check-in against best-in-class daily-loop screens
> (describe, don't copy: Things 3's today list, Apple Fitness rings, Headspace's daily card) on:
> first-glance hierarchy, one-handed reach, motion restraint, emotional tone. Score each axis,
> then propose the one change with the highest craft-per-effort for Spartan.

## 8. Store-asset compositor

**Use when:** producing the 6 Play screenshots / App Store sets.

> Build an artifact that lays out Spartan's store screenshot set: 6 phone frames (1080×2400) with
> our brand background, caption typography, and slots where I'll drop real screenshots; export
> guidance per store. Captions from docs/PLAY_STORE_LISTING.md §7.

---

### Working rhythm that gets award-level results

1. **Prototype in an artifact** (minutes) → 2. **Decide** on the artifact, annotate → 3. **Build**
with the matching UX_ROADMAP_PROMPTS.md prompt → 4. **Verify on device** (TRY_IT_LOCALLY.md) →
5. **Screenshot back into an artifact** for side-by-side against the prototype. Repeat per screen.
Never skip 4 — artifacts decide *design*, devices decide *truth*.
