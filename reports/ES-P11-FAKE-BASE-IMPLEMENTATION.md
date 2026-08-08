# ES-P11 fake-base implementation evidence

Date: 2026-08-08
Package: `ES-P11`
Audit item: `AUD-TESTER-003`
PR: #88
Status at publication: product implementation/review/static remediation complete; final state-inclusive exact-head validation/merge pending

## Requirement disposition

`AUD-TESTER-003` is implemented by a bounded, client-only fake-base subsystem integrated into the existing Cheat Tester operator surface. The fake base never mutates Minecraft world state. It sends virtual block changes to selected clients and restores clients from authoritative real block data during cleanup.

## World-safety model

1. **No real block writes.** ES-P11 contains no fake-base `setType`, `setBlockData`, schematic paste, WorldEdit, CoreProtect rollback, or equivalent world mutation path.
2. **One already-loaded chunk.** Placement is constrained to the target's current loaded chunk; the feature does not request chunk loading/generation. Immediately before final reads, the target must still be in the planned anchor chunk.
3. **Conflict refusal.** Every virtual template cell must currently be real air. The 5x5 interior floor must be solid and non-hazardous.
4. **Fixed bounds.** The release template is 7x7 with a hard block-count cap. Active operations are limited to 8 globally, 2 per controller, and one per target.
5. **Authoritative restore.** Cleanup re-reads real block data for every overlaid cell and sends those states to connected viewers; it cannot delete/replace a real block.
6. **Client-session interruption safety.** A process interruption disconnects the client session that held the virtual view. Because no server world state changed, no fake-base artifact can remain in saved world data.

## Access and lifecycle

- Operator must be in active staff mode and have `enthusiastaff.cheattester.fake-base`.
- Cross-controller management additionally requires current `enthusiastaff.cheattester.fake-base.manage-any`; manage-any explicitly inherits the base permission while both are default-false.
- Async creation, Extend, and Teleport re-check current authority when committing.
- Only the target and staff viewers whose Teleport action succeeds receive the virtual structure.
- Lifetime is 5 minutes; warning is approximately 1 minute before expiry; distance cutoff is 48 blocks.
- Extend starts a new five-minute window only before the current deadline. A request at or after `expiresAt` is rejected even if cleanup has not yet ticked, so an expired operation cannot be revived.
- Clear paths include staff clear, timeout, target world/backend change, target/controller disconnect, staff-mode exit, render failure, lifecycle scheduler rejection/retirement, distance, and plugin lifecycle close.
- Cleanup is idempotent through the operation close transition.

## Failure semantics

Creation requires durable audit storage before rendering. If final placement changes, controller authorization disappears, rendering fails, or lifecycle scheduling cannot be established, the operation is rejected/closed and authoritative restoration is attempted where a client view may have existed.

Extend and Teleport persist an `ACCEPTED` request before asynchronous work. A separate coordinate-free `COMMITTED` event is emitted only after the extension or staff render succeeds. Safety cleanup is not blocked on secondary audit writes.

Render completion rechecks operation ownership. If close wins after viewer admission but before/while rendering, the viewer is removed and current real block data is restored. Region/entity scheduler retirement during cleanup is logged rather than falsely reported as a successful restore.

## Durable audit and migration impact

ES-P11 adds no Flyway migration. V18 remains the immutable aggregate migration boundary. Coordinate-free lifecycle evidence uses the existing `audit_events` ledger and contains event/correlation IDs, staff/target UUIDs, server ID, action, outcome, reason code, and timestamp. Coordinates are deliberately excluded.

## Automated coverage

Direct tests cover template bounds/block cap/duplicates/doorway/height coupling; loaded-chunk placement and conflict/unsafe-floor/world-height refusal; warning/extension/expiry/idempotent viewer lifecycle; extension refusal exactly at and after expiry; bounded coordinate-free audit values; and semantic least-privilege plugin permission metadata.

MariaDB/Testcontainers integration writes fake-base lifecycle evidence through the production runtime binding into the existing `audit_events` ledger, asserts the row exists, verifies correlation/actor/target/action/outcome/JSON fields, and rejects coordinate/location leakage. The repository's migration suite remains responsible for V18 clean-install/upgrade/checksum validation because ES-P11 adds no migration file.

## Review and static-analysis findings resolved

Manual review and actual CodeRabbit reviews resolved approximate concurrency admission, noncanonical audit JSON, warning/extension synchronization races, worker/region-thread Bukkit access, virtual render/restore exception handling, stale cross-chunk Folia reads, stale controlling-staff/manage-any authority, accepted-vs-committed control evidence, post-render lifecycle scheduler failure, render/close races, viewer documentation, permission inheritance, template-height coupling, brittle metadata/integration assertions, unreachable tab completion, and extension at/after expiry before cleanup.

Configured Codacy analysis then found maintainability issues in the expanded code. Remediation proceeded 23 → 9 → 5 → 1 → 0. The command router, placement predicates, template builder, and manager hotspots were split into smaller methods; low-level audit persistence, authoritative world-block reads, and presentation helpers were extracted. Narrow repository-consistent PMD suppressions remain only where structurally justified. A manual review of the refactor found no new world mutation, worker-thread Bukkit access, stale authorization, or cleanup defect.

## Validated product-head evidence

Product head `dafa710e44cc3c4ff7af1ee367d679f95ea8fd3f` passed:

- Wiki run `31239760132`, job/check `93058657612`.
- Coverage/build run `31239760133`, job/check `93058683147`, including Java 21 build/tests, MariaDB/Testcontainers, migration validation, runtime-JAR inspection, artifact publication, JaCoCo, and Codacy coverage upload.
- Aggregate JaCoCo: 47.02% lines, 38.15% branches, 49.64% instructions.
- Paper runtime JAR: 9,123,435 bytes; SHA-256 `22ceb5f71b387a7a9bc369aa7a3a7313d696d9477c41e6cb5bb5bc58310a9f1c`; provider API leaks 0.
- Velocity runtime JAR: 7,863,915 bytes; SHA-256 `8529a2b8c12c8392ccba61735c5b190bc66b09a4f982fc12d3280c29a7ee3a99`; provider API leaks 0.
- Artifact `9016711895`, digest `sha256:fac889493926350db66ca4f5e89c5f356d30d682613b99e483b4d863abf30f82`.
- Codacy static `93059044531`: success, zero annotations.
- Codacy Diff Coverage `93059225105`: success, 40.81%.
- Codacy Coverage Variation `93059224975`: success, -0.45%.

The automatic private product-head attempt did not execute product code: public wrapper `31239759170` / check `93058655372` dispatched private run `31239763060`; Ubuntu job `93058666371` had runner ID `0`, empty runner name, `steps: []`, and GitHub's Billing & plans rejection; Pi `93058670370` skipped. This is **NOT A PASS**, is not an ES-P11 completion gate, and creates no ES-P11 infrastructure exception. Representative Java/Bedrock/distributed acceptance remains assigned to `ES-V02`.

Because state/evidence files are part of PR #88, the next state-only checkpoint becomes the final frozen candidate. Final merge evidence must correspond to that exact unchanged SHA; the green product-head results above cannot substitute for exact-head final validation.

## Explicit exclusions

- real schematic/world paste;
- CoreProtect/general rollback replacement;
- unbounded/configurable arbitrary structures;
- fake-base coordinates as case evidence;
- automatic punishment or cheating verdict;
- production deployment/cutover;
- unrelated Cheat Tester features already owned by ES-P10.

Final exact-head run/check/review IDs, implementation merge SHA, resulting `main`, containment, branch cleanup, and terminal publication are recorded after the package completes.
