# EnthusiaStaff workspace state

Last updated: 2026-08-04

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current legitimate `main` at PR #59 start | `8bed23c521f907aa134453445e77f17df75a3743` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `ACTIVE — PR #59 exact-head validation and review pending` |
| Pull request | `#59 — Reconcile vanish visibility with live rank changes` |
| Feature branch | `fix/vanish-live-rank-reconciliation` |
| Starting main | `8bed23c521f907aa134453445e77f17df75a3743` |
| Work item | Reconcile cached vanish viewer/target rank authority with live explicit permissions, durably correct persisted target rank, and disable unauthorized vanish without breaking startup recovery |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-04-vanish-live-rank-reconciliation.md` |
| Migration boundary | V16 is highest; PR #59 adds no migration; V1–V16 remain immutable |
| Production authority | LiteBans remains authoritative; no deployment or cutover authority is granted |
| External blocker | RoseChat private-message evidence remains separately blocked pending the supported provider contract; never route that blocker through issue #43 |
| Intended post-merge status | Merge PR #59 normally only after all gates pass; verify resulting `main`, feature-head containment, and branch cleanup; do not deploy or access production |

## Live start-state reconciliation

- PR #58 merged normally as `8bed23c521f907aa134453445e77f17df75a3743`.
- Its feature head is contained in `main`, its branch was removed, and no pull request or non-`main` branch was active before PR #59.
- PR #59 began from exact `main` `8bed23c521f907aa134453445e77f17df75a3743`.
- V16 remains the live highest migration; PR #59 changes no schema or migration bytes.
- Owner priority remains staff mode, vanish, and freeze before report notifications and escalation policy.

## Confirmed defect

`VanishManager` cached each online viewer rank and each vanished target rank. Online permission changes did not reconcile those caches until reconnect or another incidental vanish-related event. A demoted viewer could retain visibility to vanished ranks they no longer supervised, a promoted viewer could remain incorrectly restricted, a vanished target could remain classified under an old rank, and rank removal could leave a former staff member hidden.

## Implemented behavior

PR #59 now:

- starts one plugin-owned periodic rank reconciler;
- checks known staff, active staff-mode players, vanished players, and pending cleanup every second;
- performs a bounded full online rank-discovery pass every five seconds so newly promoted players are still detected without five permission checks per ordinary player every second;
- resolves live permissions only on each player's owning entity scheduler;
- bounds periodic work to one queued rank check per player;
- updates changed viewer authority and refreshes only that viewer;
- updates a changed vanished-target rank in memory and persists the new rank asynchronously;
- verifies lower-rank authorization against the durable staff-session store when in-memory staff-mode state is not yet known;
- treats completed staff-mode exit or confirmed missing durable session as requiring vanish disable;
- keeps valid persisted Helper/Mod/Developer vanish intact while asynchronous startup recovery still has an open durable staff session;
- retries failed durable rank/disable writes with bounded backoff;
- keeps one state write and one durable staff-session check per player in flight;
- token-fences staff-session checks so disconnect/reconnect cannot apply stale results;
- clears all transient per-player reconciliation markers on quit;
- preserves quit-message suppression before removing runtime visibility state;
- keeps ordinary toggle, spectator masking, configured hierarchy, and incremental viewer/target refresh behavior.

## Focused tests

- `VanishRankReconciliationPolicyTest` covers every promotion/demotion, rank removal, `SYSTEM`, durable-session unknown/active/inactive/exited states, independent Admin/Founder behavior, and stale durable-disable retry.
- `VanishAudienceCoordinatorTest` covers viewer-owner scheduling, reconnect fencing, retired/rejected cleanup, current-session snapshots, incremental recovery, and stale-target removal.
- Existing visibility hierarchy, spectator-tab policy, packet masking, field preservation, unauthorized removal, and fail-closed tests remain part of the configured suite.

## Harsh-review corrections already applied

1. Removed quit-time reconciliation that could clear vanish before quit-message suppression.
2. Prevented startup from disabling valid lower-rank vanish before asynchronous staff-mode recovery completes.
3. Added a durable cleanup path for staff-mode exit writes that collide or fail.
4. Replaced the temporary in-memory-only startup workaround with a durable staff-session lookup, covering the crash window between staff-session closure and vanish disable.
5. Added token fencing and retry backoff for asynchronous staff-session verification.
6. Ensured event-driven viewer-rank refreshes also refresh the viewer's existing visibility relationships.
7. Cleared the pending staff-mode-exit marker on quit to prevent process-lifetime growth.
8. Replaced the all-player-per-second permission scan with one-second tracked-player checks plus five-second full discovery.
9. Split the reconciliation and policy branches to satisfy configured method-size and complexity limits.

## Exact-head completion gate

Before merge, require one unchanged head synchronized with current `main` and direct terminal evidence for:

- Java 21 build and unit/integration tests;
- migration checksum and immutability checks with V1–V16 unchanged;
- exactly one valid Paper and one valid Velocity runtime JAR;
- provider-class leak inspection and artifact SHA-256 identities;
- aggregate coverage and configured Codacy/static-analysis upload;
- wiki/documentation validation;
- CodeRabbit, Codacy, and human review with zero valid unresolved threads;
- exact-head public Pi wrapper and correlated private staging run when applicable;
- one consolidated exact-head evidence comment.

Cancelled, superseded, skipped, stale-head, different-revision, and merge-ref-only runs are not acceptable evidence.

## Production boundary

PR #59 is dormant development work only. It does not authorize deployment, production data or credential access, production Discord use, authority activation, a real shadow window, LiteBans disablement/removal, final migration, issue #43 acceptance, or live cutover.

## Next route

1. Finish PR #59 only: resolve all valid findings, freeze one exact head, complete every applicable gate, and merge normally only with zero unresolved valid threads.
2. Record the merge commit, resulting `main`, feature-head containment, no unmerged branch commits, and branch cleanup in PR metadata.
3. After PR #59 is complete, freshly select one bounded remaining priority-one staff mode, vanish, or freeze item.
4. Keep the RoseChat provider blocker separate and do not use issue #43 as a general blocker queue.
5. Do not begin another feature in the PR #59 session.
