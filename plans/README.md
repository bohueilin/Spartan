# Design-consult handoff plans (2026-08-12, commit 91c0816)

Executor-proofed plans for all Tier 1–2 findings from the full design review. Execute in order; each is independent and self-contained. Plans never reference the review conversation — everything needed is inside each file.

| # | Plan | Tier | Platforms | Status |
|---|---|---|---|---|
| 001 | [Honor reduced-motion](001-reduced-motion.md) | 1 | Android + iOS | DONE |
| 002 | [Heading semantics for screen readers](002-heading-semantics.md) | 1 | Android + iOS | DONE |
| 003 | [Non-text contrast: required signal, outlines, track, skeleton](003-nontext-contrast.md) | 1 | Android + iOS | DONE |
| 004 | [Complete sample-data provenance](004-complete-provenance.md) | 2 | Android + iOS | DONE |
| 005 | [Review tab: no confident zeros](005-review-fake-zeros.md) | 2 | Android | DONE |
| 006 | [Recovery number renders still](006-still-recovery-number.md) | 2 | Android | TODO |

Build note (from project memory): Android compiles need `JAVA_HOME=$(/usr/libexec/java_home -v 17)`; don't pipe gradle output in a way that masks exit codes. iOS app targets cannot be compiled on this machine (Command Line Tools only) — self-review Swift diffs against the cited lines.

Rules for every executor: one numbered requirement at a time; no `// ...existing code` elisions; if the code found doesn't match a cited line, STOP and report the requirement number; finish with a self-audit restating each requirement number with done/blocked + file:line evidence.
