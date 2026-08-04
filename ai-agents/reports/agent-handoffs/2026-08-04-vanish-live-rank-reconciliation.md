# PR #59 handoff — vanish live-rank reconciliation

Last updated: 2026-08-04

## Routing

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Pull request | `#59 — Reconcile vanish visibility with live rank changes` |
| Branch | `fix/vanish-live-rank-reconciliation` |
| Starting main | `8bed23c521f907aa134453445e77f17df75a3743` |
| State | `ACTIVE — exact-head validation and review pending` |
| Highest migration | V16 |
| Migration impact | None; V1–V16 remain immutable |
| Commands, permissions, configuration | No change |

Live GitHub state overrides this handoff. The final feature SHA, workflow/job/artifact IDs, coverage values, JAR hashes, Pi evidence, review resolution, merge commit, and resulting `main` belong in PR #59 live metadata.

## Start-state reconciliation

- PR #58 merged normally as `8bed23c521f907aa134453445e77f17df75a3743`.
- Its feature branch was removed and no pull request or non-`main` branch remained active before PR #59.
- PR #59 began from exact `main` `8bed23c521f907aa134453445e77f17df75a3743`.
- V16 was the live highest migration and remains unchanged.
- Owner priority was staff mode, vanish, and freeze; the PR #58 handoff identified rank-aware vanish as the likely next staff-visible candidate.

## Confirmed defect

`VanishManager` cached online viewer ranks and vanished target ranks. Live LuckPerms or equivalent permission changes did not reconcile those caches until reconnect or another incidental event. Consequences included stale supervising visibility after demotion, stale restrictions after promotion, old target-rank classification, and former staff remaining vanished after rank removal.

## Implemented behavior

PR #59 now:

- runs one idempotent plugin-owned reconciliation task every second;
- schedules permission reads and visibility mutations on each player's entity scheduler;
- permits only one queued periodic check per online player;
- immediately updates viewer authority and refreshes only that viewer when the live rank changes;
- updates a vanished target's in-memory rank classification and durably persists the new rank;
- removes viewer authority and disables vanish after explicit rank removal or `SYSTEM` resolution;
- verifies Helper/Mod/Developer authorization against `StaffSessionStore.active` when in-memory staff-mode recovery is not yet complete;
- preserves valid startup vanish while an open durable staff session exists;
- disables lower-rank vanish when the durable staff session is confirmed absent or staff-mode exit completed;
- retries collided or failed staff-mode-exit vanish cleanup;
- bounds durable session checks and vanish writes independently;
- token-fences session checks against disconnect/reconnect;
- backs off failed verification and persistence attempts;
- suppresses quit messages before retiring runtime visibility state;
- keeps Admin/Founder independent vanish semantics and the configured rank matrix.

## Focused tests

`VanishRankReconciliationPolicyTest` proves:

- unchanged viewer ranks require no work;
- every promotion/demotion updates viewer authority;
- missing and `SYSTEM` ranks remove viewer authority;
- authorized target-rank changes require durable replacement;
- unknown lower-rank staff-session state defers to durable verification;
- confirmed inactive and completed-exit states disable lower-rank vanish;
- Admin and Founder remain independent of staff-mode state;
- rank removal and stale durable state disable safely.

`VanishAudienceCoordinatorTest` additionally proves:

- viewer-owned scheduling;
- reconnect fencing;
- retirement cleanup after stale or rejected scheduling;
- current-session snapshots;
- incremental pair recovery;
- removed targets are skipped by queued work.

## Harsh whole-diff review

Confirmed defects fixed during review:

1. quit-time reconciliation could clear vanish before quit-message suppression;
2. startup could disable valid lower-rank vanish before asynchronous staff-mode recovery completed;
3. a collided staff-mode-exit write could leave lower-rank vanish active indefinitely;
4. an in-memory-only exit marker could not cover a crash between staff-session closure and vanish disable;
5. asynchronous durable-session checks initially lacked reconnect fencing;
6. event-driven viewer-rank changes could update the cache without refreshing existing viewer relationships.

No confirmed defect is intentionally deferred inside this bounded work item. Remaining entity/tracker suppression, integrations, Java/Bedrock staging, complete Folia runtime verification, freeze work, and report-provider work remain separate.

## Validation contract

Before merge, require one unchanged exact head synchronized with current `main` and terminal direct evidence for:

- Java 21 build, unit tests, applicable MariaDB/Testcontainers tests;
- clean-install, upgrade, migration checksum, and V1–V16 immutability checks;
- exactly one Paper and one Velocity runtime JAR, ZIP validity, provider-leak inspection, and SHA-256 identities;
- aggregate coverage and configured Codacy upload/static analysis;
- wiki/documentation validation;
- CodeRabbit and human review with zero valid unresolved threads;
- successful exact-head public Pi wrapper and correlated private staging run when applicable;
- one consolidated exact-head evidence comment.

Reject cancelled, superseded, skipped, stale-head, different-revision, and merge-ref-only evidence.

## Production boundaries

LiteBans remains authoritative. PR #59 does not authorize deployment, production access, production Discord use, authority activation, a real shadow window, LiteBans disablement/removal, final production migration, issue #43 acceptance, or live cutover.

## External blocker preserved

The RoseChat private-message evidence boundary remains blocked pending an accessible supported provider contract defining callback type, delivery/cancellation lifecycle, sender/recipient identity, duplicate behavior, threading, version coordinates, privacy-safe fields, and provider present/missing/reload behavior. Keep this in the focused blocker handoff or issue, never issue #43.

## Next route

1. Complete PR #59 only.
2. Resolve every valid review finding and freeze one exact head.
3. Complete the full validation gate and merge normally only when all applicable checks are terminal and zero valid unresolved threads remain.
4. Record merge evidence and branch cleanup in PR metadata.
5. After PR #59 is complete, freshly select one bounded remaining priority-one staff mode, vanish, or freeze item.
6. Do not begin another feature in this session.
