# CoachingGym — domain-specific eval metrics and rewards for Spartan's coach

*Added 2026-07-13, after auditing [team652/clinical-rl-gym](https://github.com/team652/clinical-rl-gym)
(a HUD RL/eval environment for clinical coaching agents over FHIR records).*

## Why

Standard evals measure model-ish properties: does the plan crash, is the copy safe, is generation
deterministic. Spartan already had that layer (`CoachingEvalTest` sweeps 500+ readiness inputs and
asserts invariants). What it did **not** have — and what the clinical-rl-gym demo argues for — is
**domain-specific measurement**: *how good is this plan as coaching*, scored on the axes that matter
for the domain and folded into a single scalar **reward** that an RL loop or an A/B between policies
can consume.

## What we took from the audit (and what we didn't)

| clinical-rl-gym concept | Adopted in Spartan? |
| --- | --- |
| Manifest of gold cases (scenario type, difficulty, gold answers) | ✅ `GymScenarios` — 600+ deterministic readiness cases with gold expectations derived from a written wellness spec, independent of the engine under test |
| Weighted reward: 35% accuracy / 25% safety / 40% coaching quality | ✅ Same shape: `0.35·alignment + 0.25·safety + 0.40·quality` |
| Safety as a hard gate (diagnosis/prescription ⇒ score 0) | ✅ Any blocked-phrase violation, missed escalation, missed pain-deload, or forbidden hard training ⇒ reward 0 |
| Red-flag scenarios where only escalation passes | ✅ Tachycardic RHR / elevated respiratory-rate cases across all readiness bands |
| Over-alarmism penalty (0.7, not 0) | ✅ Clinician escalation on a clean day scores 0.7 on safety |
| Policy-agnostic evaluation (any agent, same graders) | ✅ `CoachingGym.evaluate(policy: RecommendationSource)` — the same seam a future AI coach plugs into |
| RL rollout/reward wiring (GRPO skeleton) | ✅ The reward IS the rollout return; a trainer calls `evaluate()` per candidate policy. No training loop shipped (no AI source yet) |
| Keyword-matching graders over free text | ❌ Spartan grades **structured `DailyPlan`s** — stronger and deterministic |
| FHIR/Medplum/Synthea/Docker/HUD/LLM-judge infra | ❌ Spartan is local-first and rules-based; none of it is needed |

## The graders (`PlanGraders`)

1. **Readiness alignment (0–1, weight 0.35)** — does intensity match what the body can absorb?
   No hard training unless the day allows it; low-readiness days stay genuinely light (nothing
   above easy, ≤60 total minutes); ready days actually use the opportunity (≥1 quality session);
   the plan is honest about its band and date.
2. **Safety gate (0 / 0.7 / 1, weight 0.25, hard gate)** — every sentence passes the
   blocked-phrase `SafetyEngine`; red-flag days carry a REQUIRED clinician check-in; pain days
   carry a REQUIRED deload and nothing hard; hard training against the spec fails outright;
   over-alarmism costs 0.7. **A 0 here zeroes the whole reward.**
3. **Coaching quality (0–1, weight 0.40)** — bounded and deduplicated plan with real substance
   (≥10 min of guidance, a non-optional item); every card actionable (why + steps + sane
   duration); the day's context-specific responses present (wind-down on poor sleep, breathwork
   on suppressed HRV, active recovery after high strain, gentle fallback on stale data); training
   items ship with a follow-along video.

The gold expectations are computed from a **wellness spec written once in `GymScenarios`**, not
from the engine under test — so the same manifest grades the shipped rules engine, a future
LLM-backed `RecommendationSource`, or an RL checkpoint mid-training.

## What it already caught

Three real coaching bugs the invariant eval could not see — on **both platforms** (the Kotlin
and Swift engines shared all three):

1. **Primed day + pain**: the engine emitted the required "gentle, comfortable movement only"
   deload *and* a 35-minute HARD strength session next to it. Pain now suppresses the
   band-based training block.
2. **Stale data + moderate session**: a no-data day prescribed a moderate workout alongside the
   "safe default while Spartan waits" fallback. Stale days now get the gentle fallback only.
3. **Stacked days dropped the wind-down**: with the activity cap, a red-flag + poor-sleep +
   low-recovery day could fill its budget with two cards from one rule and displace the sleep
   response. `prioritizeAndCap` is now diversity-aware (one card per unrepresented rule first).

The gym itself was then adversarially reviewed (16 agents): tautological grader checks that
scored the harness instead of the policy were removed, vacuous scenarios (RHR-trend and
missed-goal cases with no gold expectation) got real spec clauses and grader checks, and the
0.7 over-alarmism and forbidden-hard paths gained direct mutant-killing tests.

## CI gate

`CoachingGymTest` fails the build if the shipped engine drops below: mean reward ≥ 0.90,
alignment ≥ 0.95, quality ≥ 0.90, red-flag reward ≥ 0.90, **zero** safety-gate failures — and
verifies the graders discriminate (a reckless always-hard policy and a lazy do-nothing policy
both score dramatically worse, with red-flag rewards of exactly 0).

## Platform parity

The gym lives in the pure domain layer (`com.spartan.domain.eval`) with no Android imports, and
is ported to `ios/SpartanKit` alongside the rest of the shared core so both platforms are held
to the same bar.
