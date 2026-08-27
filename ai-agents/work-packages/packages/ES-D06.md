# ES-D06 — Read-only staff moderation UX

Status: `ACTIVE`
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
- [ ] Freeze final reviewed product head and complete every applicable exact-head hosted/static/review gate.
- [ ] Merge PR #177 normally, verify containment/cleanup, and publish terminal state.

## Active checkpoint

Live GitHub was reconciled before claim and again during review. D04 and D05 are complete. PR #177 is the single D06 implementation PR. Canonical `main` remains `500136b37c9acc30b1de8a057feb79d3d16fc400`; D06 allocates no migration and does not overlap the parked X03 migration work. Issue #43 remains open and does not authorize production cutover. No production Discord configuration, production data access, or secret handling is part of this package.

Current frozen executable candidate is `9c87578586d4cf82f0ace044213e58aa534deba7`. The prior CodeRabbit authority-host and ambiguity-limit findings are repaired; their original threads are now outdated. Exact-head Coverage run `33116109321`, Staff Bot Configuration Cache run `33116109312`, and Sentinel Restart Artifact run `33116109318` were started for that candidate and remain non-passing until terminal success is observed. Static/review evidence must also be exact-head and zero-valid-finding before merge.

Blockers: none external. Current work is `ACTIONABLE_CONTINUATION` while exact-head validation/review is running.

Exact next action: inspect the terminal results for frozen executable candidate `9c87578586d4cf82f0ace044213e58aa534deba7`, repair any valid failure/finding, then use only a state/documentation-only synchronization delta if executable gates pass; resolve all valid review threads, merge PR #177 normally, verify containment and branch cleanup, and publish D06 `COMPLETE` without starting D07 or any second Discord package.
