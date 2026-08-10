# ES-P07 — Inventory and Ender editing runtime completion

Status: `ACTIVE`

## Selection and classification

- Selected package: `ES-P07 — Inventory and Ender editing runtime completion`.
- Classification at selection: `READY`.
- Selection reason: live reconciliation found no `ACTIONABLE_CONTINUATION`; `ES-P07` and `ES-P06` were the only dependency-complete `READY` packages, and ES-P07 has the lower numerical priority (45).
- `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED` because no supported RoseChat standalone repository/default branch/source/AGENTS can be resolved.
- All dependency-blocked validation/destructive packages remain parked.

## Starting state

- Worker type: generic sequential package worker.
- EnthusiaStaff starting/default head: `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.
- Temporary branch: `package/es-p07-inventory-runtime` created exactly from that head.
- Starting open EnthusiaStaff PRs: none.
- Starting ES-P07 branch/PR/handoff: none.
- Issue #43 remains open/deferred; LiteBans remains authoritative.
- Current immutable migration boundary: V18 (`V18__cheat_tester_session_journal.sql`). No ES-P07 migration is presently planned.

## Verified standalone heads at selection

These are reconciliation facts only; ES-P07 is an internal package and does not modify these repositories.

- `wsg138/enthusia-site`: `2fe7d59c1c5e12db0b7ba792fc9e2af4d24337c2`
- `wsg138/EnthusiaCurrency`: `9696501a01cc11f6e5220c5297a6f34b64204e61`
- `wsg138/EnthusiaMarket`: `bc24f1010642d6042307bc13a32fb33cc94e8883`
- `wsg138/EnthusiaCommend`: `2083061b8aeaa7fb3adaf89746f91a45e3a03e59`
- RoseChat provider repository: unresolved.

## Package scope

Included:

- `/invsee` and `/endersee` runtime command/GUI wiring and authorization.
- Online live inventory mutation on the correct Paper/Folia entity thread.
- Offline authoritative-scope observation and durable queued edits.
- Optimistic revisions, locks/leases, stale-state rejection, idempotency and login-before-interaction recovery.
- Concurrent viewer synchronization, disconnect/switch fencing and restart/crash recovery.
- Armor, offhand, Ender contents and nested item preservation.
- Bounded inventory payload handling and command/text fallback suitable for Java/Bedrock operation.
- Tests, Wiki/operational documentation, exact-head validation and review.

Preserved exclusions:

- Item confiscation/restoration belongs to ES-P08.
- Provider-backed destructive work belongs to ES-X02/ES-X03/ES-X04.
- Production player data is out of scope.
- Representative private large-inventory/Java-Bedrock/distributed acceptance remains ES-V02.
- No deployment, production authority/cutover, issue #43 acceptance, production shadow window or source-data rewrite.

## Baseline findings

The existing implementation already has strong journal foundations (`JdbcInventoryJournalStore`, optimistic revisions, durable patches, lease/fencing transitions, pre-login recovery) and a live coordinator. The project audit correctly identified that the command/GUI/runtime mutation path lacked direct proof. The implementation pass will focus on real runtime correctness rather than replacing the persistence design.

Current source review also confirms:

- command target lookup is asynchronous and opening is scheduled back to the viewer;
- live capture/apply is scheduled to the target entity;
- offline edits are converted to durable patches rather than direct player-file edits;
- login recovery fails closed when durable verification is unavailable;
- migration history is already V1–V18 and remains immutable.

## Exact next action

Trace the complete inventory coordinator, codecs, command registration, permissions, persistence transitions, Velocity ownership/switch fences and existing tests; then implement the first coherent ES-P07 runtime/test checkpoint on this branch. Do not begin another package.