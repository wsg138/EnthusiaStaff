# PR #58 handoff — staff-mode disable recovery

Last updated: 2026-08-03

## Routing

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| State | `IDLE — PR #58 requires live merge verification` |
| Pull request | `#58 — Recover staff sessions across Paper disable` |
| Branch | `fix/staffmode-disable-recovery` |
| Starting main | `03971345a8c3cd079deda9f38b2f471dcbbcfd42` |
| Highest migration | V16 |
| Migration impact | None; V1–V16 remain immutable |
| Commands, permissions, configuration | No change |

Live GitHub state overrides this handoff. Exact final feature SHA, run/job/artifact IDs, coverage values, JAR hashes, Pi evidence, merge commit and resulting `main` belong in PR #58 live metadata rather than in another tracked commit.

## Selected work

No pull request or non-`main` branch was active after PR #57 merged normally. The owner-priority route was therefore staff mode, vanish and freeze. PR #57's canonical handoff explicitly deferred reload/disable recovery, making this the next bounded priority-one correctness item.

Before PR #58, a clean Paper disable or plugin reload could close the runtime while a staff session remained durably `ACTIVE`. The player could retain the temporary staff inventory and game mode while the old listeners disappeared, and the next runtime could resume the temporary profile instead of restoring the exact saved snapshot.

## Implemented behavior

PR #58 now:

- fences shutdown producers before teardown;
- closes non-database integrations and drains already accepted bounded worker operations;
- while MariaDB remains open, locks this backend's remaining `ACTIVE` and `EXITING` staff sessions and changes them transactionally to `RECOVERY_REQUIRED`;
- writes one audit event for each newly transitioned session;
- leaves existing `RECOVERY_REQUIRED` sessions untouched, making repeated shutdown recovery idempotent;
- leaves sessions owned by other backend server IDs untouched;
- rolls back the entire server-scoped transition when any audit write fails;
- closes MariaDB only after the recovery transition;
- routes `RECOVERY_REQUIRED` through `beginExit` before checksum-verified restoration, allowing a successful restart recovery to close the durable session;
- keeps recovery interaction fences active when queueing, persistence, entity scheduling, restoration or checksum verification cannot complete;
- clears the recovery fence only after a verified durable close or player disconnect;
- avoids player/entity mutation from `onDisable` and reuses the existing owning-scheduler restoration path after the next enable or login.

## Tests

Focused coverage includes:

- `PaperShutdownCoordinatorTest`
  - proves worker drain precedes staff-session recovery and MariaDB close;
  - proves later cleanup stages still run when earlier stages throw.
- `StaffSessionShutdownRecoveryIntegrationTest`
  - proves `ACTIVE` and `EXITING` transition to `RECOVERY_REQUIRED`;
  - proves existing recovery rows are not duplicated;
  - proves backend isolation and repeated-call idempotency;
  - proves `RECOVERY_REQUIRED -> EXITING -> CLOSED` exact-checksum recovery;
  - proves audit failure rolls back every server-scoped state transition.
- `StaffModeActivationCoordinatorTest`
  - preserves the transition fence when recovery queue submission is rejected;
  - preserves the transition fence when recovery persistence fails;
  - preserves existing successful activation and successful recovery behavior.

The configured exact-head Java 21, unit, Paper, Velocity, MariaDB/Testcontainers, migration, runtime-JAR, provider-leak, coverage, static-analysis, wiki/documentation, review and applicable Pi checks must be read live from PR #58. Cancelled, superseded, stale-head, merge-ref-only or different-revision runs are historical only.

## Harsh whole-diff review

The separate whole-diff review found and fixed these confirmed defects:

1. the first integration tests reused one backend ID in the shared MariaDB container and were order-dependent; each scenario now has an isolated server ID;
2. `RECOVERY_REQUIRED` previously restored the snapshot without entering `EXITING`, while `completeExit` only closes `EXITING`; recovery now performs the durable state transition first and the integration test proves closure;
3. recovery initially acquired no transition fence while asynchronous work was queued; the fence now covers database lookup, entity restoration and durable verification;
4. scheduler retirement, queue rejection, persistence failure, restore failure and checksum mismatch previously released the fence while recovery remained unresolved; those paths now remain fail-closed until disconnect or verified closure;
5. the shared activation coordinator had the same fail-open behavior when recovery could not be queued or persisted; it now preserves the fence with direct unit coverage;
6. the new integration test used a boolean equality assertion reported by static analysis; it now uses the direct boolean assertion.

No confirmed merge blocker is intentionally deferred. Remaining broader staff-mode command/world-interaction restrictions, full vanish completion and full freeze completion remain separate owner-priority work rather than being expanded into this bounded recovery PR.

## Coverage classification

Review meaningful changed production paths, not only the configured threshold. The directly tested paths are transaction scope, idempotency, rollback, backend isolation, durable recovery closure, shutdown ordering, continuation after cleanup failure, recovery queue rejection and recovery-persistence failure.

Framework composition and live Folia entity scheduling remain thin adapters over those directly tested policies and stores. Any final low changed-line coverage must be classified in the consolidated PR evidence with the exact uncovered lines, indirect tests and Pi runtime evidence; do not merge if lifecycle, recovery, concurrency, persistence or permission correctness remains unproved.

## Migration and production boundaries

- No Flyway migration is added or edited.
- V1–V16 remain immutable; never use Flyway repair.
- LiteBans remains authoritative.
- No deployment, production JAR upload, service restart, production database/player-data access, production credential use, Discord production route, authority activation, LiteBans disablement/removal, issue #43 acceptance, 168-hour window, production backup/restore or cutover is authorized.
- Merging dormant reviewed development code is not deployment or production acceptance.

## Completion gate

Before merge, PR #58 must have one unchanged head synchronized with current `main`, a complete harsh re-review, terminal successful configured checks, reviewed changed paths and coverage, zero valid unresolved review threads, successful exact-head public Pi wrapper and correlated private staging run when applicable, and one consolidated evidence comment. Merge only with a normal merge commit and the expected exact head.

After merge, verify the merge commit, resulting `main`, feature-head containment, intended tree, no unmerged feature commits, remote branch cleanup and local cleanup in one post-merge PR comment. Do not create a direct follow-up commit to `main` for merge evidence.

## Next route

After PR #58 is completely merged or genuinely blocked, freshly reconcile the remaining owner-priority staff mode, vanish and freeze gaps and select exactly one. Rank-aware vanish completion is the likely next player/staff-visible candidate, but it is not preselected and must not begin in this session. Report notification completion remains priority two, escalation-policy completion remains priority three, and the RoseChat private-message provider contract remains a separate external blocker that must not be routed through issue #43.
