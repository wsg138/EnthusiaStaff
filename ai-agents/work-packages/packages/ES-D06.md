# ES-D06 — Read-only staff moderation UX

Status: `BLOCKED`
Priority: 135
Depends on: `ES-D04`, `ES-D05`
Internal package: yes
Claimed: 2026-08-27 from canonical `main` `500136b37c9acc30b1de8a057feb79d3d16fc400`
Implementation branch: `package/es-d06-read-only-moderation-ux`
Implementation PR: #177
Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-27-es-d06-codacy-blocked.md`

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
- [x] Exact head `10b4255102489b5c423e1bb22c8daaa009fba6f9` passed full hosted build/test/runtime checks, Codacy zero-issue static analysis, diff coverage, and requested CodeRabbit review.
- [ ] Resolve contradictory later Codacy `action_required` evidence with 22 per-finding annotations before merge.
- [ ] Merge PR #177 normally, verify containment/cleanup, and publish terminal `COMPLETE` only after the blocker is cleared.

## Blocked checkpoint

Exact head `10b4255102489b5c423e1bb22c8daaa009fba6f9` passed Coverage `33116219168` / job `98671451083`, Staff Bot Configuration Cache `33116219157`, Sentinel Restart Artifact `33116219081`, Codacy Static Code Analysis `98671703302` (`Your pull request is up to standards!`, zero issues), Codacy Diff Coverage `98673935155`, and the requested CodeRabbit rerun with no actionable comments. All three prior inline review findings are confirmed addressed/resolved.

After `10b4255`, only package-state Markdown changed. Hosted Codacy check `98676412677` on state-only head `ad89be16537a7d21b3fa18dc9a54da6fd209017f` nevertheless completed `action_required`, reporting **22 new issues** and **22 annotations**. This contradictory failed evidence is authoritative until individually reconciled; it is not relabeled as passing. The currently available GitHub connector exposes the check summary/count but rejects the check-run annotations subresource, while available Codacy notification evidence exposes only aggregate counts. The worker therefore cannot safely classify or repair the 22 findings individually, and repository policy forbids broad suppressions/exclusions.

No merge was attempted. PR #177 remains open. No production Discord configuration/data access, secret handling, deployment, moderation mutation, LiteBans authority change, or issue #43 acceptance occurred.

Blocker: required hosted Codacy evidence is failed and the individual findings needed for safe triage are unavailable through the authorized evidence surfaces currently exposed to this worker.

Exact unblock: expose the 22 individual findings from Codacy check `98676412677` through an authorized per-finding surface (Codacy PR Issues/CLI/API, GitHub check-run annotations, or equivalent durable output). Then resume existing PR #177, reconcile live `main`, classify every finding individually, fix every valid D06-introduced finding, document proven false positives individually, rerun every applicable exact-head gate, and merge normally only after Codacy reports zero new valid findings.

Exact next action after unblock: resume this existing branch/PR; do not create a replacement. Complete Codacy triage/repair and exact-head validation, merge normally, verify containment and safe branch cleanup, publish D06 `COMPLETE`, and stop without beginning D07 or any second Discord package.
