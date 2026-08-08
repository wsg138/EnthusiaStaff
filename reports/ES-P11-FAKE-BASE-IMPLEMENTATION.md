# ES-P11 fake-base implementation evidence

Date: 2026-08-07
Package: `ES-P11`
Audit item: `AUD-TESTER-003`
PR: #88
Status at publication: implementation and self-review complete; final exact-head validation/review/merge pending

## Requirement disposition

`AUD-TESTER-003` is implemented by a bounded, client-only fake-base subsystem integrated into the existing Cheat Tester operator surface. The fake base never mutates Minecraft world state. It sends virtual block changes to selected clients and restores the clients from authoritative real block data during cleanup.

This report supersedes the original project-audit observation that no fake-base implementation existed. The original audit remains historical evidence for its audited SHA; this file records the package implementation evidence for the ES-P11 branch and eventual merge.

## World-safety model

The safety boundary is deliberately stronger than a rollback scheme:

1. **No real block writes.** ES-P11 contains no fake-base `setType`, `setBlockData`, schematic paste, WorldEdit, CoreProtect rollback, or equivalent real-world mutation path.
2. **One already-loaded chunk.** Placement is constrained to the target's current loaded chunk. The feature does not request chunk loading or generation. Immediately before the final authoritative block reads, the target must still be in the same chunk as the planned anchor; a cross-chunk move rejects the operation so Folia region ownership is never guessed.
3. **Conflict refusal.** Every virtual template cell must currently be real air. The 5x5 interior floor must be solid and non-hazardous.
4. **Fixed bounds.** The release template is a fixed 7x7 structure with a hard block-count cap. Active operations are limited to 8 globally and 2 per controlling staff member, with one operation per target.
5. **Authoritative restore.** Cleanup re-reads the current real block data for every overlaid cell and sends those states to each connected viewer. It does not replay a stale world snapshot and cannot delete a real block.
6. **Client-session interruption safety.** A hard server/process interruption disconnects the client session that held the virtual view. Because no server world state was changed, there is no abandoned fake base in saved world data after restart.

## Access and visibility

- Operator must be in active staff mode.
- Operator must have `enthusiastaff.cheattester.fake-base`.
- Only the controlling staff member can manage an operation unless the operator currently has `enthusiastaff.cheattester.fake-base.manage-any`.
- Async Extend and Teleport paths re-check current staff-mode and control authority at the point the action commits, so permission/session changes while durable audit I/O is in flight cannot authorize a stale action.
- Creation re-checks the controlling staff session before target rendering begins.
- Target must be online on the current backend when the operation starts.
- Only the target and staff viewers whose Teleport action succeeds are viewers; players other than the target are never added unless they are authorized staff whose Teleport action succeeds.
- Status output contains target UUID and remaining lifetime only; no fake-base coordinates are persisted or exposed there.

## Lifecycle

- Lifetime: 5 minutes.
- Warning: approximately 1 minute before expiry.
- Controls: Extend, Clear, Teleport, Status, with clickable staff messages and `/cheattester base ...` text fallback.
- Distance cutoff: 48 blocks from the operation anchor.
- Clear paths: staff clear, timeout, target world/backend change, target disconnect, controlling staff disconnect, controlling staff leaving staff mode, render failure, lifecycle scheduler rejection/retirement, and plugin fake-base lifecycle close.
- Cleanup is idempotent through the operation's atomic close transition.

## Failure semantics

Creation requires durable audit storage before rendering. The accepted creation event is written before the target client receives virtual blocks. If the target crosses to another chunk, the real placement changes, controller authorization disappears, virtual rendering fails, or lifecycle scheduling cannot be established after render, the operation is rejected/closed and authoritative restoration is attempted where a client view may have existed.

Extend and Teleport persist an `ACCEPTED` request before their asynchronous action. A separate coordinate-free `COMMITTED` event is emitted only after the extension or staff viewer render actually succeeds. This distinguishes a durable authorized request from a later action that was safely abandoned because authority, lifecycle, teleport, or rendering changed.

Safety cleanup is not blocked on the database. Once virtual content exists, cleanup proceeds even if a later lifecycle audit cannot be written; the original creation event remains durable evidence and the runtime logs the audit failure. This avoids retaining fake client state merely to preserve a secondary audit write.

Region/entity scheduler retirement during cleanup is logged, not falsely counted as a successful restore. If the viewer has already disconnected or the runtime is terminating, client-session teardown is the final cleanup boundary and no server world artifact remains.

## Durable audit and migration impact

ES-P11 adds no Flyway migration. V18 remains the current immutable migration boundary.

Coordinate-free lifecycle evidence uses the existing `audit_events` ledger through `FakeBaseAuditStore` / `JdbcFakeBaseAuditStore`. Records contain:

- event ID;
- operation correlation ID;
- staff and target UUIDs;
- server ID;
- action;
- outcome;
- reason code;
- timestamp.

Fake-base coordinates are intentionally excluded from the domain audit record and JSON payload.

## Direct automated evidence

Unit/direct tests added by ES-P11 cover:

- template non-emptiness, block cap, coordinate bounds, duplicate cells, and two-block doorway;
- placement staying inside the target's already-loaded chunk;
- refusal of unloaded chunks, occupied real cells, unsafe floors, and invalid world height;
- four-minute warning behavior, extension reset, five-minute expiry, viewer close behavior, and idempotent close;
- bounded coordinate-free audit-domain validation;
- plugin command/permission metadata.

MariaDB/Testcontainers integration coverage proves that fake-base lifecycle evidence persists through the existing audit ledger and that the highest successful Flyway migration remains V18.

A complete pre-freeze package head `a0fc7c63b547cfa84a89aa116c4297d2c0b25f36` already passed the hosted Java 21 build/tests/MariaDB/Testcontainers/runtime-JAR workflow and Wiki validation. That run is retained only as diagnostic evidence because subsequent self-review found and fixed stale-authorization, Folia-region, and post-render scheduler failure races. Final passing evidence must come from the later frozen head.

## Self-review findings resolved before freeze

The manual package review found and fixed all of the following before final validation:

- approximate concurrent operation limits were replaced with exact synchronized registration;
- fake-base audit JSON moved to the repository's canonical JSON serializer;
- expiry/warning reads and extension/reset transitions were serialized so a stale warning cannot consume a newly extended warning window;
- live Bukkit `Player` state was removed from worker-thread audit callbacks;
- virtual render and authoritative restore errors now fail/log safely;
- target rendering is refused if the target changes chunks while audit I/O is pending, preserving Folia region ownership for final real-block reads;
- controlling staff authorization is re-checked before creation render and before asynchronous Extend/Teleport commits;
- manage-any authority is re-checked at commit time rather than only when the command begins;
- Extend/Teleport now distinguish durable accepted requests from actually committed actions;
- lifecycle scheduler exceptions/retirement after render immediately close and restore the operation rather than leaving an unscheduled virtual view;
- render completion re-checks operation ownership so a close racing the client render immediately restores real blocks instead of leaving stale virtual blocks;
- an unreachable fake-base tab-completion branch was removed.

Exact run/job identifiers, final frozen head, static/review evidence, merge SHA, and containment result are recorded in the ES-P11 package file and handoff after validation completes.

## Bedrock and distributed boundary

The operator surface has command/text fallback and fake-base block rendering uses Paper's supported virtual multi-block-change API rather than direct NMS/ProtocolLib packet internals. Target creation is backend-local and disconnect/server transfer terminates local ownership.

Representative Java/Bedrock behavior, multi-backend presentation, and private environment acceptance remain assigned to `ES-V02`. ES-P11 must not relabel unavailable private acceptance as a passing package test.

## Explicit exclusions

- real schematic/world paste;
- CoreProtect/general rollback replacement;
- unbounded or configurable arbitrary structures;
- fake-base coordinates as case evidence;
- automatic punishment or cheating verdict;
- production deployment/cutover;
- unrelated Cheat Tester features already owned by ES-P10.
