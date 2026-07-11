# Apple Design Award Runway

How Spartan's iOS app (`ios/SpartanApp` + `ios/SpartanKit`) could become a credible Apple Design
Award contender — and what it honestly takes.

## 1. Reality check

Apple Design Awards go to **shipped apps with real users**. Winners are typically apps with 1–3+
years of sustained polish, meaningful adoption, and reviews that prove people love using them. The
jury evaluates the **live product on a device** — not the repo, not the architecture, not the eval
harness. Spartan today is a source-complete SwiftUI shell that has **not yet been built on a
machine with Xcode**, has no App Store presence, and no users. A v1 cannot win an ADA, and nobody
should promise otherwise. What a v1 *can* do is establish the craft trajectory: ship, learn from
real users, and close the gap release by release until the app genuinely belongs in the
conversation. This document plans for that — the award is the side effect, not the goal.

What the jury actually evaluates:

- **The shipped App Store build**, hands-on, on current hardware (including ProMotion, Dynamic
  Island, Apple Watch where relevant).
- **State-of-the-art platform integration** — the app should feel like it could only exist on iOS.
- **Attention to detail**: transitions, haptics, sound, empty states, error states, first-run.
- **Accessibility as table stakes**, not a checkbox — VoiceOver, Dynamic Type, Reduce Motion.
- **A point of view**: one memorable idea executed exceptionally, not many ideas executed adequately.

## 2. The six ADA categories and where Spartan fits

| Category | What it rewards | Spartan fit |
|---|---|---|
| **Interaction** | Intuitive, effortless interfaces; input methods that feel inevitable | **Primary target** |
| **Inclusivity** | Great experiences for everyone: VoiceOver, Dynamic Type, cognitive load, language | **Primary target** |
| **Delight and Fun** | Memorable, engaging experiences with personality | Secondary |
| **Visuals and Graphics** | Stunning imagery, drawing, animation quality | Supporting, not the lead |
| **Innovation** | Novel use of Apple technologies to do something new | Not the lead — coaching apps exist |
| **Social Impact** | Apps that measurably improve lives or the world | Plausible long-term, unprovable at v1 |

**Why Interaction is the right primary bet:** Spartan's core model — readiness in, *one* plan out —
is an interaction thesis. The user opens the app once in the morning, sees a single readiness ring,
and gets a short, ordered list of concrete activities with check-offs. No dashboard to decode, no
graph safari. That "one glance → one plan → check things off" loop is exactly the kind of focused,
effortless interaction the category rewards — if every touch in that loop (ring reveal, card
expand, check-off, band change) is executed to an obsessive standard.

**Why Inclusivity is the winnable co-bet:** Inclusivity is the one category where excellence is
achievable through engineering discipline rather than luck. Spartan's calm, low-cognitive-load,
single-accent design is already inclusive by philosophy; making it flawless under VoiceOver,
Dynamic Type at AX5, Reduce Motion, and one-handed use is verifiable, testable work.

## 3. Gap analysis: where Spartan is today

Be honest about the starting line. Per `ios/README.md`, SpartanKit builds and tests green on
macOS, but the SwiftUI app layer is source-complete and **unverified** — this machine has only
Command Line Tools.

| ADA criterion | Spartan today | What closing it takes |
|---|---|---|
| Shipped app, real users | No App Store presence; no TestFlight; zero users | Xcode build pass, signing, TestFlight beta, App Review (health-app notes in `ios/docs/IOS_RELEASE_CHECKLIST.md`), then months of live iteration |
| Runs beautifully on device | Never launched on a device or simulator | `xcodegen + xcodebuild` on an Xcode machine; device QA matrix (SE → Pro Max) |
| Haptics | None on iOS (Android roadmap item 1.1 exists; no Swift counterpart) | Core Haptics vocabulary: check-off, band change, plan complete |
| Motion craft | Static SwiftUI views; no springs, no reduced-motion handling | Spring-based motion tokens; `accessibilityReduceMotion` respected everywhere; 120Hz validation |
| Live Activities / Dynamic Island | None | ActivityKit for in-progress activities (roadmap 4.1) |
| Widgets | None on iOS (Android has a Glance widget) | WidgetKit family: next activity, readiness ring (roadmap 4.2) |
| Apple Watch | No watchOS app | watchOS app + complications (roadmap 4.3) |
| VoiceOver | Labels written in `CheckInView.swift` (e.g. ring `a11yLabel`), never tested on device | Full VoiceOver audit on hardware; rotor, grouping, announcements |
| Dynamic Type | Not stress-tested; layouts unverified at AX sizes | Test every screen at AX1–AX5; no truncation, no broken layouts |
| Siri / App Intents | None | App Intents: "What's my plan?", "Mark stretching done" (roadmap 4.4) |
| Trust story | Strong on paper: local-first, no trackers, mock-first, honest sample-data labeling | Ship it, state it plainly in the App Store listing, never regress |

## 4. Release-by-release runway

### v1.0 — Ship it (the non-negotiable first step)

Nothing else on this list matters until the app exists on the App Store.

- Get one clean `xcodegen generate && xcodebuild` pass on a machine with Xcode + iOS SDK; fix
  whatever falls out (the honest expectation: something will).
- Device QA on at least iPhone SE and a Pro-class device; fix layout breaks.
- First-pass VoiceOver and Dynamic Type sweep — fix blockers, log the rest.
- TestFlight beta with real WHOOP users (the mock-first design means anyone can try it day one).
- App Store listing from `ios/docs/APP_STORE_LISTING.md` + privacy labels; submit; ship.
- Success metric: the app is live, crash-free, and a stranger can use it without explanation.

### v1.1 — Platform-native depth (feel like an iOS app, not a port)

- **Core Haptics vocabulary**: a small, consistent set — soft tick on check-off, distinct pattern
  on plan completion, gentle pulse on readiness-band change. Design it as a vocabulary, not
  scattered `UIImpactFeedbackGenerator` calls.
- Spring-based motion system mirroring Android roadmap Tier 1 (ring sweep + count-up, check-off
  spring, skeleton crossfade), with `accessibilityReduceMotion` snapping everything instantly.
- **Inclusivity excellence pass**: full VoiceOver audit on hardware (grouped elements, state
  announcements on check-off, live-region for sync status), Dynamic Type AX1–AX5 on every screen,
  Bold Text / Increase Contrast / Reduce Transparency verified.
- WidgetKit family: next-activity and readiness-ring widgets, small/medium/lock-screen.
- 120Hz ProMotion validation: no dropped frames on scroll or ring animation; Instruments traces.

### v1.2 — The signature interaction (see §5)

- Pick one candidate from §5, prototype it, test it with beta users, and polish it until it is the
  thing people describe when they describe the app.
- App Intents + Siri: query today's plan, complete an activity by voice; Shortcuts support.
- Sound design (optional, subtle): a single completion sound that matches the haptic.
- Trust story as differentiator: no accounts, no tracking, no subscription dark patterns —
  say it in the listing and in-app. (StoreKit-free at this stage; monetization is a later,
  separate decision that must not compromise this.)

### v2.0 — Ecosystem: watch + Live Activities

- **watchOS app**: readiness ring + today's plan on the wrist; check off from the watch;
  complications for readiness score and next activity.
- **Live Activities + Dynamic Island**: an in-progress activity (e.g. a 20-minute walk) lives in
  the Island with time remaining and a check-off on completion.
- Handoff/state sync between phone and watch that never conflicts or double-counts.
- This is the release where "state-of-the-art platform integration" becomes claimable.

## 5. The signature interaction

ADA winners are remembered for one interaction. Candidates, in rough order of promise:

1. **The morning reveal ("drawing the bow")** — the readiness ring as a living morning ritual:
   the day starts folded; a deliberate pull-down gesture stretches and releases to reveal the
   ring sweeping to today's score and the plan cascading in beneath it, with matched haptic
   tension-and-release. Fits the Spartan name and the once-a-morning usage model.
2. **Check-off physics** — completing an activity is *the* repeated act. Make it physical:
   spring overshoot, paired haptic, the progress bar visibly absorbing the completed card's
   energy. Cheapest to build, felt dozens of times a week.
3. **"Where this takes you" projection scrub** — drag on the readiness ring to scrub a projection
   of how completing (or skipping) today's plan bends the trend line. Highest wow, highest risk:
   projections must stay honest, rules-based, and clearly framed as estimates, never promises.

How to prototype and test: build each as a throwaway SwiftUI prototype (one screen, fake data)
before integrating; test on hardware, not simulator — haptics and 120Hz cannot be judged
otherwise; put two variants in front of TestFlight users and keep whichever one they mention
unprompted. Every candidate must degrade gracefully under Reduce Motion and VoiceOver — the
signature interaction cannot be exclusionary, or it forfeits the Inclusivity bet.

## 6. What NOT to do

- **No gratuitous animation.** Motion that doesn't communicate state is noise. Spartan's calm is
  the brand; an ADA jury can smell award-bait choreography instantly.
- **No gamification overload.** No XP, no confetti storms, no guilt-streaks. The existing
  "streak-safe" consistency framing (roadmap 2.3) is the right instinct — keep it.
- **No dark patterns.** No notification spam, no fake urgency, no paywall traps. The trust story
  is a competitive asset; one dark pattern destroys it.
- **No feature sprawl to impress.** Innovation-category chasing (AI chat! social feeds!) dilutes
  the one-glance-one-plan thesis that makes Interaction winnable.
- **No medical-claim creep.** Every string still passes the SafetyEngine copy rules. "Readiness
  signal, not health verdict" is both a legal line and a design principle.

## 7. Milestones and honest odds

| Milestone | Signal it sends |
|---|---|
| v1.0 live on the App Store | The prerequisite exists; everything before this is theory |
| v1.1: zero VoiceOver/Dynamic Type defects on device | Inclusivity bar credibly met |
| v1.2: beta users describe the signature interaction unprompted | Interaction bar credibly met |
| v2.0: watch + Live Activities shipped and stable | Platform-integration bar met |
| 12+ months live, strong ratings, organic press/feature interest | Actually in the candidate pool |

Honest framing: there is no ADA submission form for most of its history — Apple finds you (a
developer nomination process has existed in recent years, but discovery still dominates). Even at
full craft, the award is a lottery among dozens of excellent apps per category per year. The
rational goal is **design-award-quality craft**: an app that would not embarrass itself next to
winners. Build to that bar, ship, keep polishing, and let the award — or an App Store feature,
which is the far more probable payoff of the same work — be a side effect.
