<div align="center">

<img src="docs/assets/spartan-github.png" alt="Spartan — your daily readiness, decided" width="100%" />

# SPARTAN

**Your daily readiness, decided.**

*A personal health coach that reads your WHOOP recovery and hands you one disciplined,
doable plan for today — with the reasons behind every action.*

`Android · Kotlin/Compose` &nbsp;·&nbsp; `iOS · Swift/SwiftUI` &nbsp;·&nbsp; `Local-first` &nbsp;·&nbsp; `No accounts · No ads · No trackers`

</div>

---

## The problem

Wearables are excellent at measuring and terrible at deciding. They hand you a recovery score at
6 a.m. and leave the hard part — *so what should I actually do today?* — to you. Most people
either ignore the data or over-train against it.

**Spartan closes that gap.** Every morning it turns your recovery, sleep, strain, HRV, and resting
heart rate into a short plan of 2–4 concrete activities: what to do, why it matters, how long it
takes, and when to do it. Check things off, snooze them, or let Spartan find a free slot in your
calendar. That's the whole app — deliberately.

## What it does

| | |
|---|---|
| **Reads your body** | WHOOP recovery, sleep, strain, HRV, resting HR, respiratory rate — read-only, with your consent. **Import your WHOOP CSV export** (Settings → Connections) to run on your real data with zero credentials, or ship with clearly labeled sample data until you do. |
| **Decides the day** | A transparent rules engine (not a black box) maps readiness to a plan: recovery days protect you, primed days green-light quality training, concerning vitals always suppress intensity and suggest a clinician — never a diagnosis. |
| **Explains the numbers** | Tap any metric for plain-language education: what it is, what moves it, what a good pattern looks like. |
| **Shows you how** | Every training activity links a **specific follow-along video**, curated from evidence-minded channels for adults ~40 starting out (Fitness Blender, Team Body Project, HASfit, Juice & Toya, growwithjo, Walk at Home, Yoga With Adriene, Caroline Girvan) — beginner, low-impact, well-cued. Picks are **ranked to your age and which metrics are off-target**: gentler, joint-friendly sessions lead when you're 40+ or a number needs to move. Each trainable metric has a "Train this metric" section that hands you the exact guided sessions. |
| **Tracks the work** | Checking off a session takes a 5-second debrief (minutes, effort, pain) that *adapts next week's plan* — pain automatically deloads you. |
| **Shows the payoff** | *"Where this can take you"*: honest, capped ranges for resting HR, HRV, and recovery after 8 weeks at your actual consistency — typical ranges, never promises. |
| **Nudges, never nags** | A 7:15 morning digest, a guilt-free 19:00 reminder only when activities remain, quiet hours respected, one tap from notification to plan. |

## Privacy is the architecture

Your health data never leaves your device. No Spartan servers, no accounts, no analytics, no
advertising SDKs — verified by audit, not just promised. OAuth tokens live in hardware-backed
encrypted storage; disconnecting an integration clears them; one tap deletes everything.
A blocked-phrase **SafetyEngine** machine-checks every sentence of coaching copy so the app can
never make a medical claim. Details: [privacy policy](docs/PRIVACY_POLICY.md) ·
[security checklist](docs/SECURITY_PRIVACY_CHECKLIST.md).

## Engineering, verified

| Surface | Status |
|---|---|
| Android app (Kotlin · Compose · Hilt · Room) | **96 unit/Robolectric tests, 0 failures · 0 lint errors · 3.2 MB R8 release build** |
| iOS core (`ios/SpartanKit`, same rules & copy) | **40 tests · 30,000+ assertions, 0 failures** via `swift run SpartanChecks` |
| Coaching quality | An **eval harness generates 750+ plans** per run and asserts safety/correctness invariants on every one — identical on both platforms |
| Domain metrics & rewards | A **[CoachingGym](docs/COACH_GYM.md)** grades every plan on readiness alignment, hard-gated safety, and coaching quality (0.35/0.25/0.40 weighted reward) across 600+ gold scenarios — the eval/RL seam any future AI recommendation source is measured against |
| CI | GitHub Actions: tests + coverage, lint, debug/release assembly, emulator instrumentation job — zero secrets needed |

The iOS SwiftUI shell is source-complete and awaits its first Xcode build
([ios/README.md](ios/README.md)).

## Try it

```bash
git clone https://github.com/bohueilin/Spartan.git
# open in Android Studio → Run ▶  (sample data, no credentials needed)
```

Full walkthrough — emulator or device, CLI or Studio, plus a tour of what to inspect:
**[docs/TRY_IT_LOCALLY.md](docs/TRY_IT_LOCALLY.md)**

## Documentation

| | |
|---|---|
| Product & design | [PRD](docs/Spartan_PRD.md) · [Architecture](docs/Spartan_Architecture.md) · [Decisions](docs/Spartan_Decisions.md) · [UX roadmap prompts](docs/UX_ROADMAP_PROMPTS.md) |
| Launch | [Launch readiness](docs/LAUNCH_READINESS.md) · [Play checklist](docs/RELEASE_CHECKLIST.md) · [App Store checklist](ios/docs/IOS_RELEASE_CHECKLIST.md) · [Store listing](docs/PLAY_STORE_LISTING.md) |
| Quality & trust | [Compliance pack (PII/PHI · HIPAA · GDPR)](docs/COMPLIANCE.md) · [Accessibility](docs/ACCESSIBILITY.md) · [Cert-pinning runbook](docs/CERT_PINNING_RUNBOOK.md) · [Enhancement backlog (with statuses)](docs/Spartan_Enhancements.md) · [ADA runway](docs/APPLE_DESIGN_AWARD_RUNWAY.md) |

## Status

**1.0.0 — submission-ready on Android; iOS one Xcode pass away.** Runs on labeled sample data by
default; **WHOOP CSV import brings your real data in with no credentials** (WHOOP app →
App Settings → Data Export, then Settings → Connections → *Import WHOOP export*); live WHOOP and
Google Calendar OAuth integrations are built and flag-gated behind production registration.

> Spartan offers wellness and fitness guidance, not medical advice, and is not a medical device.
> For any health concern, talk with a qualified clinician.
