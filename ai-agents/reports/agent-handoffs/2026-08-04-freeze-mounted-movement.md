# PR #64 handoff — frozen mounted movement

Created: 2026-08-04T13:14:00-05:00 (`America/Chicago`)

## Repository and work item

- Repository: `wsg138/EnthusiaStaff`
- Work item: prevent entity mounts from bypassing frozen-player movement restrictions
- Starting `main`: `f95d5ec404b7a4eca705bdd2ac013eb55af56a11`
- Branch: `fix/freeze-mounted-movement`
- Pull request: [#64](https://github.com/wsg138/EnthusiaStaff/pull/64)
- Migration boundary: V16 remains highest; V1–V16 are immutable

## Live reconciliation

- The previous routing record still named PR #63 as active, but live GitHub showed it already merged normally.
- PR #63 merge commit `f95d5ec404b7a4eca705bdd2ac013eb55af56a11` was current `main` at branch creation.
- PR #63 exact feature head `f308a545d0a213712ee9346655778be4d1acebb4` is contained in `main`; its review threads are resolved and its remote branch is absent.
- No open or draft pull request and no non-`main` branch existed before this work.
- The highest live Flyway migration remains V16. This PR adds no migration and changes no migration bytes.
- Owner priority remains staff mode, vanish and freeze before report notifications and escalation policy.

## Confirmed defect

The freeze runtime corrected `PlayerMoveEvent`, but mounted movement has a separate lifecycle. Applying or restoring a freeze closed the inventory and notified the player without first leaving an existing vehicle. The listener also had no explicit `EntityMountEvent` restriction, so a restricted player could attempt another mount.

## Implemented behavior

- `FreezeManager.applyOnline` and durable active-state recovery now call one shared immediate restriction method.
- The shared restriction calls `Player.leaveVehicle()`, closes the inventory and sends the existing freeze notice.
- `FreezeManager` now owns `EntityMountEvent` at `HIGHEST` priority with `ignoreCancelled = true`.
- Mount attempts are cancelled only when the mounting entity is a player whose existing `FreezeRuntimeState` is restricted.
- Pending durable verification remains fail-closed because the mount guard reuses the same restriction boundary.
- Ordinary players remain unaffected.

Existing damage, inventory, item, command, chat, teleport, portal, block, interaction, backend-switch, persistence, reconnect and offline-expiration behavior is unchanged.

## Files changed

- `paper/src/main/java/net/enthusia/staff/paper/freeze/FreezeManager.java`
- `paper/src/test/java/net/enthusia/staff/paper/freeze/FreezeInteractionCoverageTest.java`
- `ai-agents/WORKSPACE-STATE.md`
- this canonical handoff
- `ai-agents/reports/agent-handoffs/latest.md`

## Focused tests and meaningful coverage

`FreezeInteractionCoverageTest` now verifies:

1. the explicit `EntityMountEvent` handler exists;
2. it uses `HIGHEST` priority and `ignoreCancelled = true`;
3. a pending/restricted player cannot mount;
4. an ordinary player can mount;
5. the immediate restriction calls vehicle exit, inventory closure and player notification exactly once.

The test reuses the existing server-free Paper event fixture rather than adding a parallel harness. Existing `FreezeRuntimeStateTest` covers unrestricted, pending, confirmed, released and stale-generation lifecycle decisions used by the new handler.

The remaining low-level adapter paths are the existing Paper event and scheduler wiring. Exact-head Paper compilation and applicable runtime staging must prove the concrete API signatures and registration. No persistence, transaction, concurrency, migration or protocol behavior changed.

## Harsh-review findings and corrections

1. **Merge blocker fixed:** the initial implementation lacked focused tests. Direct handler and immediate-restriction tests were added.
2. **Confirmed defect fixed:** the first test implementation duplicated proxy/event fixture logic already owned by `FreezeInteractionCoverageTest`; it was consolidated and the duplicate file removed.
3. **Confirmed cleanup fixed:** an unused import in the discarded fixture was removed before consolidation.
4. **Confirmed cleanup fixed:** the retained proxy callback was simplified to the standard `Function<Method, Object>` shape.
5. **Verified existing behavior:** Velocity already denies backend switching for active durable freezes, so no duplicate backend-switch system was added.
6. **Optional future hardening:** production-like interaction with third-party plugins that deliberately interfere with dismounting remains a staging concern, not evidence of a repository defect in the ordinary Paper mount path.

## Validation contract

After the handoff commit and any valid CI/review repair, freeze one exact head. On that unchanged SHA require direct evidence for:

- clean Java 21 build and all configured unit, Paper, Velocity, persistence, protocol and MariaDB/Testcontainers tests;
- migration checksum, clean-install and upgrade tests with V1–V16 unchanged;
- compiler warnings as errors and configured static analysis;
- aggregate and diff coverage, with uncovered changed paths classified;
- exactly one valid Paper and Velocity runtime JAR, ZIP integrity, provider-leak inspection and SHA-256 identities;
- Wiki/documentation validation when applicable;
- Codacy and CodeRabbit disposition;
- zero valid unresolved review threads;
- exact-head Pi wrapper and correlated staging success when Actions executes normally.

If direct evidence shows GitHub Actions quota, billing limits, disabled Actions or equivalent platform unavailability prevented Pi repository code from executing, record Pi as not run and do not claim it passed. Any Pi run that executes repository code and exposes a product, test, migration, packaging, startup, restart or shutdown defect remains a merge blocker.

## Permanent boundaries

- LiteBans remains authoritative.
- No JAR deployment or service restart.
- No production database, player data, credentials, Discord route or hosting access.
- No EnthusiaStaff authority activation or LiteBans disable/removal.
- No issue #43 acceptance or 168-hour window.
- No production cutover, backup or restore.
- No Flyway repair, migration edit or history rewrite.

## Next action

Finish PR #64 only. Resolve valid review or CI findings, synchronize with live `main`, freeze one final head, complete the exact-head evidence comment, mark ready and merge normally only if every gate permits it, then perform post-merge containment and branch cleanup. Do not begin another feature in this session.
