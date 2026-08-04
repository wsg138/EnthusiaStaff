# EnthusiaStaff workspace state

Last updated: 2026-08-03

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current legitimate `main` at PR #58 start | `03971345a8c3cd079deda9f38b2f471dcbbcfd42` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `IDLE — PR #58 requires live merge verification` |
| Intended post-merge state | PR #58 merged normally into `main`; resulting `main` contains the reviewed feature head; the feature branch is deleted or otherwise confirmed clean; LiteBans remains authoritative and no deployment, production access, authority activation, shadow window or cutover occurs |
| Pull request to verify | `#58 — Recover staff sessions across Paper disable` |
| Feature branch to verify | `fix/staffmode-disable-recovery` |
| Completed work item | Durably route backend-owned open staff sessions through exact restoration after Paper disable or reload, and keep unresolved recovery fail-closed |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-staffmode-disable-recovery.md` |
| Exact validation/merge evidence | Read PR #58 live. Require one unchanged exact feature head synchronized with current `main`; terminal results for every configured Java 21, MariaDB/Testcontainers, migration immutability, runtime-JAR/provider-leak, coverage, Codacy/static-analysis, wiki/documentation, applicable public/private Pi and review gate; zero unresolved valid threads; exact run/job/artifact identities and hashes; normal merge commit; resulting `main`; feature-head containment; no unmerged branch commits; and branch cleanup. |
| External blocker | Supported RoseChat private-message provider contract remains unavailable. See `ai-agents/reports/agent-handoffs/2026-08-02-pr50-rosechat-provider-blocker.md`; implementation requires the supported callback/event API, lifecycle and delivery semantics, identity/duplicate fields, threading guarantees, version coordinates, privacy fields, and provider-present/missing behavior. Route it through a focused blocker issue or handoff, never issue #43. |

## Start-state reconciliation for PR #58

- PR #57 merged normally as `03971345a8c3cd079deda9f38b2f471dcbbcfd42`.
- The PR #57 feature head is contained in `main`, its branch was removed, and no pull request or non-`main` branch remained before PR #58.
- PR #58 started from exact `main` `03971345a8c3cd079deda9f38b2f471dcbbcfd42`.
- V16 was the live highest migration; PR #58 adds no migration and V1–V16 remain immutable.
- The owner-priority route was staff mode, vanish and freeze. PR #57's handoff explicitly deferred reload/disable restoration, so PR #58 selected that one bounded correctness item.

## PR #58 completed behavior

Before PR #58, Paper disable or reload could leave a durable staff session `ACTIVE` while the player retained the temporary staff inventory/game mode and the enforcing listeners disappeared. The next runtime could resume the temporary profile rather than restore the exact saved snapshot.

PR #58 now:

- stops operational producers and closes non-database integrations before final storage work;
- drains accepted bounded worker operations before the shutdown recovery transaction;
- transactionally locks and changes this backend's remaining `ACTIVE` and `EXITING` staff sessions to `RECOVERY_REQUIRED` while MariaDB remains open;
- writes one audit record per newly transitioned session, leaves existing recovery rows untouched, isolates other backend IDs and rolls back all state changes if any audit insert fails;
- closes MariaDB only after the recovery transaction;
- routes `RECOVERY_REQUIRED` through `beginExit` before checksum-verified restoration so successful restart recovery closes the durable session;
- keeps recovery interaction fences active across queue rejection, persistence failure, entity-scheduler retirement, restoration failure and checksum mismatch;
- clears the fence only after verified durable closure or disconnect;
- performs no player/entity mutation from `onDisable`.

## Focused tests

- `PaperShutdownCoordinatorTest` proves shutdown order and continued cleanup after earlier failures.
- `StaffSessionShutdownRecoveryIntegrationTest` proves server scope, `ACTIVE`/`EXITING` transition, existing-recovery idempotency, backend isolation, exact recovery closure and full rollback on audit failure against MariaDB/Testcontainers.
- `StaffModeActivationCoordinatorTest` proves rejected recovery queues and failed recovery persistence remain fail-closed while successful activation/recovery behavior remains intact.

## Harsh-review result

The complete diff received a separate harsh review. It found and fixed:

1. order-dependent integration scenarios sharing one backend ID;
2. `RECOVERY_REQUIRED` restoration attempting `completeExit` without first entering `EXITING`;
3. missing transition fencing during asynchronous recovery;
4. fail-open fence release on scheduler, queue, persistence, restore and checksum failures;
5. the same fail-open recovery behavior in the shared activation coordinator;
6. a static-analysis boolean equality assertion in the new integration test.

No confirmed merge blocker is intentionally deferred. Remaining broader staff-mode transition restrictions, full vanish work and full freeze work remain separate owner-priority items.

## Owner priorities and selection guardrails

Current owner priority order:

1. Staff mode, vanish, and freeze.
2. Report notification completion.
3. Escalation-policy completion.

PR #58 is one bounded priority-one correctness item. Do not combine full vanish, freeze, general inventory editing, confiscation, report notification or escalation work into it.

## Pi evidence routing

The public Pi wrapper uses `pull_request_target`, so commit-scoped workflow listings may omit it. Inspect the public wrapper check, annotations and summary, then follow the correlated private `wsg138/EnthusiaStaff-Staging` run and artifacts.

Cancelled, superseded, skipped, stale-head, different-revision and merge-ref-only results are historical only. PR #58 requires terminal successful public and private Pi evidence for the final exact feature head when the workflow is applicable.

## Migration boundary

| Field | Value |
| --- | --- |
| Highest live migration | V16 |
| PR #58 migration | None |
| Immutable history | V1–V16 |
| Next expected number | V17 unless live state is newer |
| Locked checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an applied migration or use Flyway repair.

## Production boundary

LiteBans remains authoritative. Issue #43 remains open specifically for production-cutover acceptance. PR #58 does not authorize deployment, production access, production Discord use, authority activation, a production shadow window, LiteBans disablement or removal, final production migration, or live cutover.

## Next route

1. Apply the complete exact-head gate to PR #58 and merge normally only after every applicable check and review gate succeeds for one unchanged synchronized head.
2. Record the merge commit, resulting `main`, feature-head containment, no unmerged branch commits and branch cleanup in one post-merge PR comment.
3. After PR #58 is complete, freshly reconcile the remaining priority-one staff mode, vanish and freeze gaps; rank-aware vanish completion is a likely staff-visible candidate but is not preselected.
4. The RoseChat private-message evidence item remains externally blocked until the supported provider contract described above becomes available.
5. Do not begin the next work item in the PR #58 session.
