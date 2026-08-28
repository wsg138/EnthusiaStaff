# ES-D06 — Read-only staff moderation UX

Status: `MERGE_PENDING`
Priority: 135
Depends on: `ES-D04`, `ES-D05`
Internal package: yes
Claimed: 2026-08-27 from canonical `main` `500136b37c9acc30b1de8a057feb79d3d16fc400`
Owner reassignment: 2026-08-28 to the current worker to resume and finish the existing D06 continuation.
Implementation branch: `package/es-d06-read-only-moderation-ux`
Implementation PR: #177
Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-active.md`
Historical blocked handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-codacy-blocked.md`

## Objective

Provide fast, secure read-only Discord moderation surfaces before destructive actions exist.

## Scope

`/moderate <user>`, user/message context moderation, `/moderate-minecraft`, `/linked`, `/history`, notes/cases read views, target resolution by Discord/Minecraft IDs/names with ambiguity selectors, compact ephemeral profile panel, organized history filters, authoritative linked-staff actor resolution, component custom-ID signing/expiry/replay protection, permission-aware discovery and reauthorization.

## Exclusions

No warn/mute/kick/ban/restrict side effects, evidence mutation beyond safe read/context capture primitives, AutoMod or website work.

## Validation

Interaction/target-resolution/authorization/replay/privacy tests, stale-component denial, missing-link denial, Discord-only and Bedrock-linked subjects, full CI/review. Prove Discord roles alone cannot grant moderation authority.

## Implementation checklist

- [x] Read-only slash/context command and ephemeral panel surface implemented.
- [x] Authoritative linked-staff actor resolution and read-time authorization implemented; Discord roles do not grant domain authority.
- [x] Signed, expiring, replay-resistant private component protocol implemented.
- [x] Discord-only, Java/Bedrock-linked, missing-link, ambiguity, privacy, stale/replay, and authorization paths covered by tests.
- [x] Authority bridge restricted to the exact IPv4 loopback endpoint used by Paper; alternate loopback hosts are rejected with regression coverage.
- [x] Ambiguous player resolutions are bounded to Discord's 25-choice limit and mark overflow as truncated; regression coverage exercises 30 matches.
- [x] The previously opaque Codacy/PMD blocker was reproduced per finding with repository PMD 6.55.0 rules and repaired without broad exclusions.
- [x] The sole retained PMD suppression is line-specific and documented: the authority endpoint owns its bounded executor and shuts it down both on startup rollback and `close()`.
- [x] Repair validation run `33144490940` / job `98762443017` passed PMD with zero findings and focused `domain`, `staff-bot`, and `paper` tests.
- [x] Broad diagnostic run `33188515534` / job `98907671552` reproduced the current Semgrep category and proved all ten local reports are the same `hard-coded-password` false positive on environment-variable-name constants.
- [x] Ineffective Semgrep suppressions were removed; the false-positive trigger is repaired structurally by naming environment-variable-name constants `*_ENV` rather than misleading `*_KEY`, while leaving every public environment-variable literal and runtime behavior unchanged.
- [ ] Exact-head hosted Coverage, Staff Bot Configuration Cache, Sentinel Restart Artifact/verdict, Codacy, review, and canonical Pi staging gates must all pass on the commit containing this record before merge.
- [ ] Immediately before merge, reconcile the PR against the then-current `main`, require mergeability, and use a normal merge commit if `main` advanced; any changed executable head must rerun invalidated exact-head gates.
- [ ] Merge PR #177 normally, verify containment/no unique work/cleanup, and publish terminal `COMPLETE` only after every applicable gate is actually satisfied.

## Current checkpoint

The historical implementation blocker is cleared. A temporary branch-only PMD diagnostic exposed concrete findings; the first repair addressed resource ownership, literal/performance findings, compact-record parameter reassignment, and mutable-map concurrency in test fakes. Repository-native PMD 6.55.0 repair validation `33144490940` / job `98762443017` then reported zero findings and completed focused Java 21 tests successfully.

A broad Codacy CLI diagnostic at `cd5f274813284b8432c6e128ca7a60ea64ad8cad` (`33144915073` / job `98763712253`) identified ten Semgrep `Semgrep_codacy.java.security.hard-coded-password` reports in exactly two files. Every report was a public environment-variable name, not embedded credential material. Generic and rule-specific `nosemgrep` comments did not reliably clear hosted/current CLI findings and are therefore not used as the terminal repair.

The current diagnostic branch was fast-forwarded from exact D06 source head `7572017f59057b7e50a75d5d6d193b71ea93fe63` and run `33188515534` / job `98907671552` independently reproduced the same ten reports, all on declarations named `*_KEY`. It found no additional Semgrep category. The D06 repair committed with this record removes that misleading identifier pattern (`*_KEY` → `*_ENV`) while preserving the literal environment names and all secret/runtime semantics. No repository-wide analyzer exclusion is added.

The preceding exact head `7572017f59057b7e50a75d5d6d193b71ea93fe63` passed Coverage `33183626083` / job `98890856338`, Staff Bot Configuration Cache `33183626002` / job `98890856444`, and durable Sentinel restart job `321` with `PAPER_RESTART_OK`; Codacy Static check `98891617701` remained `action_required` with seven hosted issues and is explicitly non-passing. Those executable passes are superseded by this source repair and are not reused as exact-head evidence.

One live CodeRabbit inline finding correctly required the final current-`main`/mergeability gate to appear in the package checklist. That gate is now explicit above. The thread may be resolved only after this repair is published and the reviewer-facing evidence is updated.

No merge has been attempted. No production Discord configuration/data access, secret handling, deployment, moderation mutation, LiteBans authority change, or issue #43 acceptance has occurred. Unrelated PR #139 / ES-X03 and PR #178 / ES-D13 remain independent and untouched.

## Exact next action

Treat the commit containing this record as the new merge candidate and publish its immutable SHA on PR #177. Run every applicable exact-head hosted/static/review/Sentinel/canonical-Pi gate on that same head. Require hosted Codacy to report zero new valid findings and zero valid unresolved review threads. Immediately before merge, re-read `main`; if it advanced, preserve legitimate concurrent work with a normal merge commit and rerun invalidated gates. Merge PR #177 normally only after all gates pass, then prove containment, clean up temporary D06/diagnostic branches when safe, publish terminal D06 `COMPLETE` state durably, and stop without beginning another Discord package.
