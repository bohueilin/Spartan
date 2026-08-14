# Design-consult handoff plans (2026-08-12, commit 91c0816)

Executor-proofed plans for all Tier 1–2 findings from the full design review. Execute in order; each is independent and self-contained. Plans never reference the review conversation — everything needed is inside each file.

| # | Plan | Tier | Platforms | Status |
|---|---|---|---|---|
| 001 | [Honor reduced-motion](001-reduced-motion.md) | 1 | Android + iOS | DONE |
| 002 | [Heading semantics for screen readers](002-heading-semantics.md) | 1 | Android + iOS | DONE |
| 003 | [Non-text contrast: required signal, outlines, track, skeleton](003-nontext-contrast.md) | 1 | Android + iOS | DONE |
| 004 | [Complete sample-data provenance](004-complete-provenance.md) | 2 | Android + iOS | DONE |
| 005 | [Review tab: no confident zeros](005-review-fake-zeros.md) | 2 | Android | DONE |
| 006 | [Recovery number renders still](006-still-recovery-number.md) | 2 | Android | DONE |

Round 2 (written after re-review of round 1 at commit cad6100 — Tier 3–5 remainder):

| # | Plan | Tier | Platforms | Status |
|---|---|---|---|---|
| 007 | [DUE border alpha clears 3:1](007-due-border-alpha.md) | 3 | Android | DONE |
| 008 | [Loading states for the other four tabs](008-tab-loading-states.md) | 3 | Android | DONE |
| 009 | [Working skeleton pulse](009-skeleton-pulse.md) | 4 | Android | DONE |
| 010 | [iOS pressed + disabled states](010-ios-pressed-disabled.md) | 4 | iOS | DONE |
| 011 | [One card radius](011-radius-unification.md) | 5 | Android + iOS | DONE |
| 012 | [Wordmark tracking + iOS motion tokens](012-tracking-and-motion-tokens.md) | 5 | Android + iOS | DONE |
| 013 | [Icons, dead token, hardcoded strings](013-icons-strings-dead-token.md) | 5 | Android | DONE |

Round-2 ordering note: run 008 before 009 (009 edits the skeleton composable 008 may relocate); run 010 before 012 (012 tokenizes the duration 010 introduces). Line numbers cite commit cad6100; the drift rule applies as before.

Round 3 (written after final re-review of round 2 at commit 0a20c32 — verifier-surfaced residuals):

| # | Plan | Tier | Platforms | Status |
|---|---|---|---|---|
| 014 | [Sync-failure states for the four tabs](014-tab-sync-failure-states.md) | 3 | Android | DONE |
| 015 | [Re-enable Begin: name optional by design](015-ios-begin-fallback.md) | 4 | iOS | DONE |
| 016 | [DUE border margin 0.7 → 0.75](016-due-alpha-margin.md) | 6 | Android | DONE |
| 017 | [Locale-safe casing + tab labels](017-locale-case-and-tab-labels.md) | 5 | Android | TODO |
| 018 | [OutlinedCardButton full tap target](018-ios-outlined-button-hit-target.md) | 3 | iOS | TODO |
| 019 | [Skeleton pulse parity](019-ios-skeleton-pulse-parity.md) | 5 | iOS | TODO |

Round-3 ordering note: 014 before nothing in particular; 015 and 018 both touch iOS view files edited by plan 010 — run them in numeric order. 018/019 came out of the adversarial cross-plan sweep of round 2 (8-agent verification, 2026-08-13), which confirmed all 68 round-2 requirements and flagged exactly these residuals.

Accepted as-is, no plan (round-3 review decisions): the iOS check spring stays untokenized — it is the app's one deliberate platform-native motion accent; tokenize only when a second spring appears. The Glance widget wordmark cannot carry tracking — platform limitation, accepted. Plan 011's ConnectedChip 6→8dp and the two extra 18dp cards are the Target's own definition of correct. Deferred as a product decision, not planned: localizing the MetricExplainers long-form corpus; porting the urgency-border system to iOS; a custom type scale.

Build note (from project memory): Android compiles need `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (`/usr/libexec/java_home` does not resolve on this machine); don't pipe gradle output in a way that masks exit codes. iOS app targets cannot be compiled on this machine (Command Line Tools only) — self-review Swift diffs against the cited lines.

Rules for every executor: one numbered requirement at a time; no `// ...existing code` elisions; if the code found doesn't match a cited line, STOP and report the requirement number; finish with a self-audit restating each requirement number with done/blocked + file:line evidence.
