# ES-P02 — Runtime database recovery and Velocity reload — terminal handoff

Date: 2026-08-09 America/Indiana/Indianapolis

## Terminal state

`COMPLETE`.

ES-P02 was resumed as `ACTIONABLE_CONTINUATION` after ES-R01 materially changed the old staging blocker. The preserved implementation was synchronized with current `main`, reviewed, repaired for valid static-analysis findings, frozen at exact source head `90f78f902a25039515d883ca96a1b72c2265418d`, validated through the required hosted and canonical Pi gates, and merged normally by PR #70 as `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c`.

Starting/current synchronized `main` before implementation merge: `d036908a90b8c0c9ee64d08366d6e8e4b60841e0`.

## Included scope

- bounded Paper database bootstrap retries;
- bounded Velocity database bootstrap retries and operator-triggered recovery after an exhausted cycle;
- cleanup-before-retry and stale-attempt fencing;
- deterministic shutdown and safe scheduler/executor rejection handling;
- atomic Velocity configuration reload, validation, rollback, restart-required reporting, and permission checks;
- atomic/sanitized Velocity health reporting;
- focused lifecycle, concurrency, retry, reload, rollback, rejection, shutdown, and race tests;
- operator recovery documentation.

Preserved exclusions: production deployment or authority, production/private data, issue #43 acceptance, production shadow/cutover/migration work, LiteBans replacement, Flyway repair/history rewrite, unrelated provider work, and every other implementation package.

## Review

Manual final review covered lifecycle, threading, retries, cleanup, stale-state fencing, reload rollback, restart-required behavior, permissions, privacy, and migration immutability.

All three substantive CodeRabbit review threads are resolved. No valid unresolved review thread remained at merge.

Codacy Static Code Analysis check `93313758400` on frozen head `90f78f902a25039515d883ca96a1b72c2265418d` completed `SUCCESS` with zero new issues/annotations.

## Exact-head hosted validation

Coverage workflow run `31342778279`, job `93319183473`, tested frozen head `90f78f902a25039515d883ca96a1b72c2265418d` and completed `SUCCESS`.

The job completed Java 21 setup, the full configured build/test suite, MariaDB/Testcontainers and migration checks, runtime-JAR inspection, aggregate JaCoCo generation/enforcement, validation-artifact upload, and Codacy coverage upload.

`java-21-validation` artifact:

- ID `9046404003`;
- digest `sha256:fc61861c9e1a49270d8686350f791f4ad5d63ef1c4e94f5e81aad89ab6e4f598`.

Fresh Codacy coverage checks on the same head passed:

- Diff Coverage `93319857969`: `47.12%`, no configured gate;
- Coverage Variation `93319858087`: `+0.13%` against the `-1.0%` target.

## Canonical Pi staging

Canonical public Pi Staging run `31342778432` tested the same frozen source head through the public-to-private bridge.

- trusted public Paper build job `93319183919`: `SUCCESS`;
- bridge job `93319918461`: `SUCCESS`;
- correlated private run `31343077935`;
- private job `93319937672`: `SUCCESS` on trusted `Lincoln-PI-4`;
- exact artifact/provenance verification: PASS;
- guarded disposable database pre-reset and runtime preparation: PASS;
- Paper cycle 1 and storage readiness through Flyway V1–V18: PASS;
- first clean shutdown/reap: PASS;
- Paper cycle 2 and restart/persistence: PASS;
- second clean shutdown/reap: PASS;
- final guarded database cleanup: PASS;
- sanitized evidence upload: PASS;
- public transient-transfer cleanup: PASS.

Sanitized evidence recorded `server_starts_completed=2`, `storage_ready_cycles_completed=2`, and `failure_count=0`.

Private evidence artifact:

- ID `9046774374`;
- digest `sha256:d51574d879a0c3271947d9d1422bc27b24d006703a0e659b7132c0228c4d1ac6`.

Runtime Paper JAR SHA-256 recorded by sanitized evidence: `f6e7b3362b8c284a7759c72620f98f5e64db6720e23aabeb115f7ef205c4836b`.

Two earlier exact-source attempts remain historical failure evidence, not passes: private run `31340883461` was killed by SIGKILL under shared-host memory pressure before readiness, and private run `31341552172` correctly rejected a public rerun-attempt manifest mismatch before Paper executed. A fresh run-attempt-1 canonical route produced the successful proof above. No private workflow was directly dispatched and no unrelated Sentinel/Pi job was cancelled or preempted.

## Migration boundary

ES-P02 adds no migration. `V18` is the current repository boundary and V1–V18 remain immutable. Flyway repair/history rewrite remains prohibited.

## Merge, containment, cleanup, and parity

PR #70 merged through a normal merge commit `df9f4bf39ceda3911b7c084ac0c2caa188b82c7c` with parents pre-merge `main` `d036908a90b8c0c9ee64d08366d6e8e4b60841e0` and frozen head `90f78f902a25039515d883ca96a1b72c2265418d`.

The merge commit tree equals the frozen implementation-head tree. Compare confirms the merge commit is one commit ahead of the frozen head and zero behind, so all ES-P02 product work is contained with no merge-only product diff.

`package/es-p02-runtime-db-recovery` is deleted and returns 404 after containment verification. No unique package work was lost.

External parity is not applicable; ES-P02 is internal to `COMP-STAFF`.

## Production boundary

Issue #43 remains open and deferred. LiteBans remains authoritative. No production deployment, authority change, production data access, shadow window, production migration, or cutover occurred.

## Dependency-derived routing after completion

- `ES-P07 — Inventory and Ender editing runtime completion` is now dependency-complete and becomes `READY` because its only dependency, ES-P02, is complete.
- `ES-P05 — Report evidence and staff workflow completion` remains an `ACTIONABLE_CONTINUATION`; under the canonical selector, an actionable continuation is selected before a new READY package, so ES-P05 is the exact next normal sequential package.
- No next package is activated or modified by this finalization.

## Exact next action

After this documentation-only finalization merges and containment is verified, stop. The next independent sequential worker must reconcile live state and resume ES-P05 before beginning ES-P07, unless live evidence materially changes routing again.
