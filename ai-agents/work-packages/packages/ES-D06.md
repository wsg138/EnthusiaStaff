# ES-D06 — Read-only staff moderation UX

Status: `MERGE_PENDING`
Priority: 135
Depends on: `ES-D04`, `ES-D05`
Internal package: yes
Claimed: 2026-08-27 from canonical `main` `500136b37c9acc30b1de8a057feb79d3d16fc400`
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
- [x] Repair validation run `33144490940` / job `98762443017` passed PMD with zero findings and focused `domain`, `staff-bot`, and `paper` tests before publishing the repair.
- [x] Broad branch-only Codacy diagnostic run `33144915073` / job `98763712253` classified the remaining Semgrep findings as environment-variable-name false positives; each affected constant now carries an individual line-specific explanation and the temporary diagnostic workflow is removed from the merge candidate.
- [ ] Final exact-head hosted Coverage, Staff Bot Configuration Cache, Sentinel Restart Artifact, Codacy, review, and canonical Pi staging gates must all pass before merge.
- [ ] Merge PR #177 normally, verify containment/cleanup, and publish terminal `COMPLETE` only after every applicable gate is actually satisfied.

## Current checkpoint

The historical blocked state is cleared as an actionable implementation blocker. A temporary branch-only diagnostic reproduced the repository's PMD 6.55.0 rule set against every PR-changed Java source and exposed the individual findings that hosted Codacy had only summarized. The first repair addressed resource-ownership/literal/performance findings, the compact-record parameter-reassignment finding, and mutable-map concurrency findings in test fakes. Repair run `33144490940` / job `98762443017` then reported zero findings under the repository PMD 6.55.0 rules and completed focused Java 21 tests successfully.

A later broad branch-only Codacy Analysis CLI diagnostic at `cd5f274813284b8432c6e128ca7a60ea64ad8cad` (`33144915073` / job `98763712253`) exposed ten Semgrep `hard-coded-password` reports in two files. Every report is on a constant whose literal is only the public name of an environment variable; the runtime credential/password value is still read from environment input, validated where required, and redacted from configuration rendering. These are false positives rather than embedded credentials. They are documented individually with line-specific `nosemgrep` comments instead of a repository-wide exclusion. The temporary broad diagnostic workflow is removed after providing that evidence. Its PMD 7 adapter also reported a repository-ruleset compatibility error because the custom ruleset targets the repository's PMD 6.55.0 setup; that diagnostic-version mismatch is not relabeled as a PMD product pass or failure, and the required PMD 6.55.0 run remains the authoritative PMD evidence.

No merge has been attempted. No production Discord configuration/data access, secret handling, deployment, moderation mutation, LiteBans authority change, or issue #43 acceptance has occurred. Unrelated PR #139 / ES-X03 remains independent and untouched.

## Exact next action

Treat the commit containing this record as the new frozen merge candidate. Require successful exact-head hosted build/test/static-analysis evidence, zero new valid Codacy findings, final review with no actionable findings, and applicable canonical Pi staging on that same exact head. If `main` advances, merge current `main` into this branch with normal merge history and rerun the invalidated gates. Merge PR #177 normally only after all gates pass; then prove containment, clean up the temporary branch safely, publish D06 `COMPLETE`, and stop without beginning another Discord package.
