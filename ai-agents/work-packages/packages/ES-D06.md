# ES-D06 — Read-only staff moderation UX

Status: `MERGE_PENDING`
Priority: 135
Depends on: `ES-D04`, `ES-D05`
Internal package: yes
Claimed: 2026-08-27 from canonical `main` `500136b37c9acc30b1de8a057feb79d3d16fc400`
Implementation branch: `package/es-d06-read-only-moderation-ux`
Implementation PR: #177
Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-active.md`

## Objective

Provide fast, secure read-only Discord moderation surfaces before destructive actions exist.

## Scope

`/moderate <user>`, user/message context commands, `/moderate-minecraft`, `/linked`, `/history`, notes/cases read views, target resolution by Discord/Minecraft IDs/names with ambiguity selectors, compact ephemeral profile panel, organized history filters, authoritative linked-staff actor resolution, component custom-ID signing/expiry/replay protection, permission-aware discovery and reauthorization.

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
- [x] Exact reviewed head `10b4255102489b5c423e1bb22c8daaa009fba6f9` passed applicable hosted build/test/runtime/static/review gates.
- [ ] Merge PR #177 normally, verify containment/cleanup, and publish terminal state.

## Merge-ready checkpoint

Live GitHub was reconciled before claim and throughout validation. D04 and D05 are complete. PR #177 is the single D06 implementation PR. Canonical `main` remained `500136b37c9acc30b1de8a057feb79d3d16fc400` through the validated head. D06 allocates no migration and does not overlap the parked X03 migration work. Issue #43 remains open and does not authorize production cutover.

Exact reviewed/validated head `10b4255102489b5c423e1bb22c8daaa009fba6f9` passed Coverage run `33116219168` / job `98671451083`: Temurin Java 21.0.12+1, `clean build jacocoAggregateReport runtimeJars`, all unit/integration/MariaDB-Testcontainers tests, runtime JAR integrity, 27 provider API source types with 0 runtime leaks, aggregate JaCoCo 51.33% lines / 41.48% branches / 53.69% instructions, artifact `9664863428` digest `sha256:865c1fdb071046213a45ee4c851bead4f8298d32733eac3e84cd1bea6f68eee0`, and successful Codacy coverage upload. Paper runtime SHA-256 is `212829cb83e423bac51a2c648f8e1237d7db47c72acb2d1aa12ed9defe1dc52a`; Velocity runtime SHA-256 is `1e10827f1aeea2791bd3497f815e8ec7e8c27808ca1e1386b48499e4b53084d5`.

The same exact head passed Staff Bot Configuration Cache run `33116219157` and Sentinel Restart Artifact run `33116219081`. Codacy Static Code Analysis check `98671703302` passed with title `Your pull request is up to standards!` and zero annotations/issues. Codacy Diff Coverage check `98673935155` passed with 43.2% diff coverage and no configured gate. CodeRabbit completed successfully after the repair rerun, confirmed the authority-host, ambiguity-limit, and package-record findings addressed, and all three inline threads are resolved; no valid unresolved review thread remains. CodeRabbit's earlier docstring-coverage warning and bounded fan-out/cache suggestions are non-blocking quality suggestions rather than correctness/security defects and do not alter the package contract.

This commit updates package-state documentation only after `10b4255`; executable evidence remains attributed to `10b4255` under the state-only follow-up rule. No product source, test, migration, workflow, build/runtime configuration, or artifact contract is changed by this checkpoint.

Blockers: none. Status is `MERGE_PENDING`.

Exact next action: require the current state-only merge candidate to retain zero valid review/static findings, verify PR #177 remains mergeable against unchanged `main`, merge it with a normal merge commit, verify exact feature-head containment and safe temporary-branch cleanup, then publish D06 `COMPLETE` and dependency-derived routing without starting D07 or any second Discord package.
