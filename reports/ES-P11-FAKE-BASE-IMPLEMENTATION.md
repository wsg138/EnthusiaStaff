# ES-P11 fake-base implementation evidence

Date: 2026-08-07
Package: `ES-P11`
Audit item: `AUD-TESTER-003`
PR: #88
Status at publication: implementation and review hardening complete; final exact-head validation/merge pending

## Requirement disposition

`AUD-TESTER-003` is implemented by a bounded, client-only fake-base subsystem integrated into the existing Cheat Tester operator surface. The fake base never mutates Minecraft world state. It sends virtual block changes to selected clients and restores clients from authoritative real block data during cleanup.

## World-safety model

1. **No real block writes.** ES-P11 contains no fake-base `setType`, `setBlockData`, schematic paste, WorldEdit, CoreProtect rollback, or equivalent world mutation path.
2. **One already-loaded chunk.** Placement is constrained to the target's current loaded chunk; the feature does not request chunk loading/generation. Immediately before final reads, the target must still be in the planned anchor chunk.
3. **Conflict refusal.** Every virtual template cell must currently be real air. The 5x5 interior floor must be solid and non-hazardous.
4. **Fixed bounds.** The release template is 7x7 with a hard block-count cap. Active operations are limited to 8 globally, 2 per controller, and one per target.
5. **Authoritative restore.** Cleanup re-reads real block data for every overlaid cell and sends those states to connected viewers; it cannot delete/replace a real block.
6. **Client-session interruption safety.** A process interruption disconnects the client session that held the virtual view. Because no server world state changed, no fake-base artifact can remain in saved world data.

## Access and visibility

- Operator must be in active staff mode and have `enthusiastaff.cheattester.fake-base`.
- Cross-controller management additionally requires current `enthusiastaff.cheattester.fake-base.manage-any`; manage-any explicitly inherits the base permission.
- Async creation, Extend, and Teleport re-check current authority when committing.
- Only the target and staff viewers whose Teleport action succeeds receive the virtual structure; all other players are excluded.
- Status output is bounded and contains target UUID plus remaining lifetime; fake-base coordinates are not persisted.

## Lifecycle

- Lifetime: 5 minutes.
- Warning: approximately 1 minute before expiry.
- Extend: starts a fresh five-minute window only before the existing deadline. A request at or after `expiresAt` is rejected even if the lifecycle cleanup tick has not yet run, so an expired operation cannot be revived.
- Controls: Extend, Clear, Teleport, Status.
- Distance cutoff: 48 blocks from the operation anchor.
- Clear paths: staff clear, timeout, target world/backend change, target disconnect, controlling-staff disconnect/staff-mode exit, render failure, lifecycle scheduler rejection/retirement, and plugin lifecycle close.
- Cleanup is idempotent through the operation close transition.

## Failure semantics

Creation requires durable audit storage before rendering. If final placement changes, controller authorization disappears, rendering fails, or lifecycle scheduling cannot be established, the operation is rejected/closed and authoritative restoration is attempted where a client view may have existed.

Extend and Teleport persist an `ACCEPTED` request before asynchronous work. A separate coordinate-free `COMMITTED` event is emitted only after the extension or staff render succeeds. Safety cleanup is not blocked on secondary audit writes.

Render completion rechecks operation ownership. If close wins after viewer admission but before/while rendering, the viewer is removed and current real block data is immediately restored. Region/entity scheduler retirement during cleanup is logged rather than falsely reported as a successful restore.

## Durable audit and migration impact

ES-P11 adds no Flyway migration. V18 remains the immutable aggregate migration boundary. Coordinate-free lifecycle evidence uses the existing `audit_events` ledger through `FakeBaseAuditStore` / `JdbcFakeBaseAuditStore` and includes event/correlation IDs, staff/target UUIDs, server ID, action, outcome, reason code, and timestamp. Coordinates are deliberately excluded.

## Automated evidence

Direct tests cover template bounds/block cap/duplicates/doorway/height coupling; loaded-chunk placement and conflict/unsafe-floor/world-height refusal; warning/extension/expiry/idempotent viewer lifecycle; extension refusal exactly at and after expiry; bounded coordinate-free audit values; and semantic least-privilege plugin permission metadata.

MariaDB/Testcontainers integration writes fake-base lifecycle evidence through the production runtime binding into the existing `audit_events` ledger, asserts the row exists, verifies correlation/actor/target/action/outcome/JSON fields, and rejects coordinate/location leakage. The repository's existing migration suite remains responsible for V18 clean-install/upgrade/checksum validation because ES-P11 adds no migration file.

## Review findings resolved

Manual review and actual CodeRabbit reviews found and resolved:

- approximate concurrency admission;
- noncanonical audit JSON;
- warning/extension synchronization races;
- worker/region-thread Bukkit access;
- virtual render/restore exception handling;
- stale cross-chunk Folia reads;
- stale controlling-staff/manage-any authority during async operations;
- accepted-vs-committed control evidence;
- post-render lifecycle scheduler failure;
- render/close races that could leave stale virtual client blocks;
- ambiguous viewer documentation and missing canonical handoff routing;
- permission inheritance and brittle metadata/integration assertions;
- template-height coupling;
- unreachable tab completion;
- extension of an operation at/after its deadline before cleanup tick execution.

Product/review hardening is complete through `3564d6942e669f9c79c7c952a32285f98f46fcf3`; final merge evidence must come from the later state-checkpoint frozen head and may not reuse superseded green runs.

## Bedrock and distributed boundary

The operator surface has command/text fallback and rendering uses Paper's supported virtual multi-block-change API rather than direct NMS/ProtocolLib packet internals. Representative Java/Bedrock behavior, multi-backend presentation, and private/Pi acceptance remain assigned to `ES-V02`. Automatically triggered zero-runner Billing & plans failures are **NOT A PASS** and are not an ES-P11 completion gate or owner-approved exception.

## Explicit exclusions

- real schematic/world paste;
- CoreProtect/general rollback replacement;
- unbounded/configurable arbitrary structures;
- fake-base coordinates as case evidence;
- automatic punishment or cheating verdict;
- production deployment/cutover;
- unrelated Cheat Tester features already owned by ES-P10.

Final exact-head run/check/review IDs, merge SHA, resulting `main`, containment, branch cleanup, and terminal publication are recorded after the package completes.
