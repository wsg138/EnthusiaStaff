# Staff-mode live rank reconciliation handoff

Date: 2026-08-03

## Repository and work item

- Repository: `wsg138/EnthusiaStaff`
- Work item: reconcile an active staff-mode profile with the player's current explicit rank
- Starting `main`: `8c63f29923cf6c01624371adffcfceb3ddf71a0c`
- Branch: `fix/staffmode-live-rank-reconciliation`
- Pull request: [#57 — Reconcile active staff mode with live rank changes](https://github.com/wsg138/EnthusiaStaff/pull/57)
- Expected committed state: `IDLE — PR #57 requires live merge verification`

## Live start-state reconciliation

- PR #56 was already merged normally into exact `main` `8c63f29923cf6c01624371adffcfceb3ddf71a0c`.
- Its feature branch was removed and its feature head is contained in `main`.
- No pull request was open or draft before PR #57.
- No non-main branch remained active before the PR #57 branch was created.
- V16 was the live highest migration. V1–V16 remain immutable and PR #57 adds no migration.

## Confirmed gap

`StaffModeManager` published the rank used during activation into an in-memory cache. Later game-mode and inventory enforcement trusted that cache. If LuckPerms or another permission source changed an online player's explicit rank, the active session did not correct the temporary inventory, tools or game mode until reconnect recovery. A demotion could therefore retain broader active-session behavior, while a promotion could remain incorrectly restricted. Complete removal of the explicit player rank also did not immediately start restoration while the player stayed online.

## Implemented behavior

- Inventory click, drag, Ender open and game-mode transitions resolve the live explicit rank before granting authority.
- A changed or removed rank cancels the triggering action and acquires the existing per-player transition fence.
- One idempotent periodic reconciliation task checks active online sessions every second.
- `pendingRankChecks` permits only one queued periodic entity check per active player and is cleared on completion, retirement or quit.
- Promotion or demotion reapplies the temporary staff inventory, required game mode and rank-specific tools on the player entity scheduler.
- The original durable snapshot and session identity are preserved; profile correction does not begin a second staff session.
- The required game mode is verified after mutation. Rejection or cancellation is treated as a profile-application failure and routes to durable restoration.
- Rank removal and profile-application failure use the existing bounded-worker `beginExit` plus exact restore/checksum workflow.
- Unresolved and `SYSTEM` ranks fail closed for ordinary inventory mutation, Ender access and advanced tools.
- The reconciliation scheduler is start-once and plugin-owned, so reload does not duplicate it and Paper cancels it with plugin lifecycle.
- No command, permission, configuration, persistence, protocol, provider or database schema changes.

## Material files

- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeManager.java`
- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeRankReconciliationPolicy.java`
- `paper/src/main/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicy.java`
- `paper/src/main/java/net/enthusia/staff/paper/PaperRuntimeComponents.java`
- `paper/src/test/java/net/enthusia/staff/paper/staff/StaffModeRankReconciliationPolicyTest.java`
- `paper/src/test/java/net/enthusia/staff/paper/staff/StaffModeAccessPolicyTest.java`

## Tests

`StaffModeRankReconciliationPolicyTest` proves:

- unchanged explicit player ranks keep the current profile;
- every promotion and demotion among Helper, Mod, Developer, Admin and Founder replaces the profile;
- a missing cached rank with a valid live player rank applies the live profile;
- a missing live rank or `SYSTEM` exits the player session.

`StaffModeAccessPolicyTest` preserves all prior rank, game-mode, Ender and staff-tool transfer assertions and adds fail-closed ordinary inventory and advanced-tool coverage for unresolved and `SYSTEM` ranks.

## Harsh whole-diff review

The complete PR diff was separately reviewed against current `main`, including startup, reload, shutdown, restart/recovery, Folia/Paper scheduling, bounded work, asynchronous persistence, idempotency, partial failures, rank hierarchy, console/system boundaries, inventory paths, Bedrock usability, provider boundaries, sensitive data, tests and documentation.

Two confirmed defects were fixed:

1. **Profile publication after rejected game-mode change.** The first implementation could update the cached rank after another listener cancelled the required game-mode mutation. `applyStaffState` now verifies the live mode and throws into the durable recovery path when it was rejected.
2. **Duplicate pending periodic checks.** The first implementation could enqueue repeated entity tasks during scheduler lag because the transition fence was acquired only after execution. A separate bounded pending-check set now limits each active player to one queued periodic check and clears on every retirement path.

No tracked merge blocker or confirmed defect remains before exact-head validation.

Optional cleanup deferred:

- full Paper event-object integration tests beyond the thin listeners and directly tested policies;
- broader staff-mode reload/disable restoration, which is a separate work item;
- remaining vanish, freeze, inventory and tool-action work.

## Commit checkpoint

- `1bdc87e97cf9d9ddd7621ff700b2ef84f9784c79` — implementation and focused tests.
- `cebfa81b7e32dd517a1cab779c77d2c0e1f83381` — harsh-review corrections for verified game-mode application and bounded periodic checks.
- Final tracked-state commit and exact frozen head must be read from PR #57 live metadata.

## Validation contract

Before merge, require one unchanged head synchronized with current `main` and direct evidence for:

- Java 21 build, unit tests and applicable Paper, Velocity, persistence, protocol and MariaDB/Testcontainers tests;
- clean-install, upgrade and checksum migration tests with V1–V16 unchanged;
- exactly one valid Paper runtime JAR and one valid Velocity runtime JAR, provider-leak inspection and SHA-256 identities;
- aggregate coverage, configured Codacy upload/static analysis, wiki and documentation validation;
- CodeRabbit and human review with zero valid unresolved threads;
- successful exact-head public Pi wrapper and correlated private staging run when applicable;
- one consolidated exact-head evidence comment.

## Production and migration boundaries

LiteBans remains authoritative. PR #57 is dormant development work only. It does not authorize deployment, JAR upload, production restart, production data or credential access, production Discord use, authority activation, issue #43 acceptance, a shadow window, LiteBans disablement/removal, final migration or cutover.

V16 remains the highest migration. V1–V16 are immutable; no Flyway repair or history rewrite is permitted.

## Next route

Complete PR #57 only. After normal merge, verify the merge commit, resulting `main`, feature-head containment, absence of unmerged branch commits and branch cleanup, then stop. The next recommended priority-one candidate is staff-mode reload/disable recovery after fresh reconciliation; it is not part of this PR.
