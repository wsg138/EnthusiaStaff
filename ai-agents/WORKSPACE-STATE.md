# EnthusiaStaff workspace state

Last updated: 2026-08-03

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current legitimate `main` at PR #57 start | `8c63f29923cf6c01624371adffcfceb3ddf71a0c` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `IDLE — PR #57 requires live merge verification` |
| Intended post-merge state | PR #57 merged normally into `main`; resulting `main` contains the reviewed feature head; the feature branch is deleted or otherwise confirmed clean; LiteBans remains authoritative and no deployment, production access, authority activation, shadow window or cutover occurs |
| Pull request to verify | `#57 — Reconcile active staff mode with live rank changes` |
| Feature branch to verify | `fix/staffmode-live-rank-reconciliation` |
| Completed work item | Reconcile active staff-mode inventory, game-mode and tool profiles with the player's current explicit rank, and durably restore the original snapshot when rank authority is removed |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-staffmode-live-rank-reconciliation.md` |
| Exact validation/merge evidence | Read PR #57 live. Require one unchanged exact feature head synchronized with current `main`; terminal results for every configured Java 21, MariaDB/Testcontainers, migration immutability, runtime-JAR/provider-leak, coverage, Codacy/static-analysis, wiki/documentation, applicable public/private Pi and review gate; zero unresolved valid threads; exact run/job/artifact identities and hashes; normal merge commit; resulting `main`; feature-head containment; no unmerged branch commits; and branch cleanup. |
| External blocker | Supported RoseChat private-message provider contract remains unavailable. See `ai-agents/reports/agent-handoffs/2026-08-02-pr50-rosechat-provider-blocker.md`; implementation requires the supported callback/event API, lifecycle and delivery semantics, identity/duplicate fields, threading guarantees, version coordinates, privacy fields, and provider-present/missing behavior. Route it through a focused blocker issue or handoff, never issue #43. |

## Start-state reconciliation for PR #57

- PR #56 merged normally as `8c63f29923cf6c01624371adffcfceb3ddf71a0c`.
- The PR #56 feature branch was removed and no pull request was open or draft before PR #57.
- Only `main` remained active before `fix/staffmode-live-rank-reconciliation` was created.
- PR #57 started from exact `main` `8c63f29923cf6c01624371adffcfceb3ddf71a0c`.
- V16 was the live highest migration; PR #57 adds no migration and V1–V16 remain immutable.

## PR #57 completed behavior

The active staff session previously cached the rank used at activation. Online permission changes could therefore leave a demoted staff member with a broader cached profile or leave a promoted staff member incorrectly restricted until reconnect.

PR #57 now enforces:

- inventory, Ender and game-mode actions resolve the live explicit EnthusiaStaff rank before authority is granted;
- the first action after a rank change is cancelled and starts one serialized profile correction;
- a bounded periodic check corrects an idle active session within one second;
- only one periodic entity check per active player can be pending during scheduler lag;
- promotions and demotions replace the temporary staff inventory, required game mode and rank-specific tool set without recapturing or overwriting the original durable snapshot;
- rejected game-mode application is treated as activation failure rather than publishing an incorrect profile;
- removal of the explicit player rank enters the existing durable exit and exact restore workflow;
- unresolved and `SYSTEM` ranks fail closed during live enforcement and recovery before profile activation;
- existing entry, reconnect recovery, transition fencing, staff-tool transfer, Helper, Mod, Developer, Admin and Founder boundaries remain intact;
- no command, permission, configuration, provider, database or migration behavior changes.

## Harsh-review result

The complete diff received a separate harsh review. It found and fixed three confirmed defects:

1. a cancelled or rejected game-mode mutation could otherwise leave the new cached rank published with the wrong live mode; profile application now verifies the required mode before completing;
2. the one-second reconciler could enqueue duplicate entity checks during scheduler lag; one bounded pending-check marker per active player now prevents queue growth;
3. the public recovery entry point could accept `SYSTEM` and begin player-profile activation before periodic reconciliation; both recovery checks now apply the same fail-closed reconciliation policy before activation.

No tracked merge blocker remains before exact-head validation. Full Paper event-object staging remains useful optional runtime confidence beyond the directly tested decision policies and configured Pi boot/restart gate.

## Owner priorities and selection guardrails

Current owner priority order:

1. Staff mode, vanish, and freeze.
2. Report notification completion.
3. Escalation-policy completion.

PR #57 is one bounded priority-one correctness item. Do not combine reload/disable recovery, vanish, freeze, general inventory editing, confiscation, report notification or escalation work into it.

## Pi evidence routing

The public Pi wrapper uses `pull_request_target`, so commit-scoped workflow listings may omit it. Inspect the public wrapper check, annotations and summary, then follow the correlated private `wsg138/EnthusiaStaff-Staging` run and artifacts.

Cancelled, superseded, skipped, stale-head, different-revision and merge-ref-only results are historical only. PR #57 requires terminal successful public and private Pi evidence for the final exact feature head when the workflow is applicable.

## Migration boundary

| Field | Value |
| --- | --- |
| Highest live migration | V16 |
| PR #57 migration | None |
| Immutable history | V1–V16 |
| Next expected number | V17 unless live state is newer |
| Locked checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an applied migration or use Flyway repair.

## Production boundary

LiteBans remains authoritative. Issue #43 remains open specifically for production-cutover acceptance. PR #57 does not authorize deployment, production access, production Discord use, authority activation, a production shadow window, LiteBans disablement or removal, final production migration, or live cutover.

## Next route

1. Apply the complete exact-head gate to PR #57 and merge normally only after every applicable check and review gate succeeds for one unchanged synchronized head.
2. Record the merge commit, resulting `main`, feature-head containment, no unmerged branch commits and branch cleanup in one post-merge PR comment.
3. After PR #57 is complete, freshly reconcile the remaining priority-one staff mode, vanish and freeze gaps; reload/disable recovery is a candidate, not preselected work.
4. The RoseChat private-message evidence item remains externally blocked until the supported provider contract described above becomes available.
5. Do not begin the next work item in the PR #57 session.
