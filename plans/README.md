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

Build note (from project memory): Android compiles need `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home` (`/usr/libexec/java_home` does not resolve on this machine); don't pipe gradle output in a way that masks exit codes. iOS app targets cannot be compiled on this machine (Command Line Tools only) — self-review Swift diffs against the cited lines.

Rules for every executor: one numbered requirement at a time; no `// ...existing code` elisions; if the code found doesn't match a cited line, STOP and report the requirement number; finish with a self-audit restating each requirement number with done/blocked + file:line evidence.
