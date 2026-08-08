# ES-R01 terminal handoff — blocked after required current-main proof

Date: 2026-08-08

## Package
`ES-R01 — Billing-independent staging bridge recovery`

Terminal state: `BLOCKED` / `PARKED_BLOCKED`.

This sequential worker worked exactly ES-R01 and no second package. The worker resumed ES-R01 only because live GitHub showed incomplete post-merge finalization after terminal publication PR #95 had already merged. No product code, migration, staging controls, credentials, targets, or runtime configuration were changed, and no manual staging rerun was issued.

Repository-side bridge implementation and repairs are merged. The required fresh current-`main` staging proof after the final checkpoint merge failed at the guarded disposable Pi runtime gate, and the automatic proof triggered by PR #95's terminal-publication merge failed at the guarded disposable-database reset boundary. End-to-end staging is **NOT A PASS** and the package remains parked until the guarded pre-reset succeeds under the unchanged staging contract and a fresh exact-current-main acceptance run passes.

## Checkpoint publication and review
Public PR #94 froze at `3f90ae4e96e969a7ceac45ee9a385f068c0af14a` and passed:
- Coverage run `31252575675` / job `93091088422` on Java 21 with full build/tests/runtime inspection;
- Codacy static `93091169010`;
- Codacy diff coverage `93091677173`;
- Codacy coverage variation `93091677101`;
- CodeRabbit after one valid sequencing finding was fixed and resolved.

PR #94 merged normally as `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`. The merge has exactly two parents: pre-merge `main` `094838fa221476e0832cf821f7b4908b9402d0d9` and frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`. Compare from frozen head to merge reports one commit ahead, zero behind, no file differences. The public checkpoint branch was automatically deleted after merge.

## Required fresh current-main proof after PR #94
Public Pi Staging run `31252997554` targeted exact merged source `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`.

Public build job `93092131811`:
- ran on ordinary GitHub-hosted `ubuntu-latest` with Java 21;
- selected/checks out exact current `main` history;
- completed validation/build/package successfully;
- uploaded the verified Paper runtime package successfully.

Public bridge job `93092964130`:
- required the cross-repository token successfully;
- downloaded the exact public build artifact successfully;
- published the bounded transient transfer successfully;
- dispatched and located the private self-hosted run successfully;
- waited for the exact private verdict successfully;
- collected failure diagnostics successfully;
- removed the transient public transfer successfully;
- correctly published final staging result `failure` rather than relabeling the private failure as success.

Private staging run `31253345564` used staging control SHA `4036d6e915c2d751bef18849107722dfd1e586a6`. Private job `93092978141`:
- allocated trusted runner `Lincoln-PI-4`, runner ID `2`;
- passed trusted-runner identity assertion;
- prepared sanitized evidence;
- retrieved and verified the exact public bridge artifact successfully;
- failed at `Run guarded disposable Paper boot and restart test`;
- uploaded sanitized evidence successfully.

Sanitized evidence artifact: `9020680419`, digest `sha256:5647d2458ab4b1d594e86030d9ffe1a89ac50609093417d3fcb617ecf5b1b677`.

This run's GitHub step metadata does not expose a fresh database SQLState, so none is invented here. Precise earlier evidence remains public run `31250170297` → private run `31250450219` / job `93085892938`, where exact provenance passed and seven guarded connection attempts all returned SQLState `08000` before Paper boot.

## Terminal publication PR #95 and containment
PR #95, `ES-R01: publish terminal blocked current-main state`, froze at `b918ec7ed7708db9b69e061d2f4bc322a94c5124` and merged normally as `3ce303ce3097be647091e142e801da9a5fd9a8fc`.

Post-merge containment is exact:
- compare frozen head `b918ec7ed7708db9b69e061d2f4bc322a94c5124` → merge `3ce303ce3097be647091e142e801da9a5fd9a8fc` reports one commit ahead, zero behind;
- no file differences are present;
- the public terminal-publication branch `package/es-r01-finalize-blocked` is no longer present.

This completed the publication action that the previous handoff still described prospectively.

## Automatic exact-main proof after PR #95
PR #95's merge automatically triggered public Pi Staging run `31253869828` against exact source `3ce303ce3097be647091e142e801da9a5fd9a8fc`.

Public build job `93094217219` succeeded and produced the verified Paper runtime.

Public bridge job `93094873681`:
- required the cross-repository token successfully;
- downloaded the exact public artifact successfully;
- published the bounded transient transfer successfully;
- dispatched and correlated private run `31254151964` successfully;
- collected failure diagnostics successfully;
- removed the transient public transfer successfully;
- correctly failed the final staging verdict because private staging failed.

Private job `93094893264`:
- allocated trusted `Lincoln-PI-4`, runner ID `2`;
- passed runner identity assertion;
- retrieved and verified the exact public bridge artifact successfully;
- failed at `Run guarded disposable Paper boot and restart test` with `ERROR: Refused or failed to clear the dedicated disposable Pi database before boot`;
- therefore did not boot Paper;
- uploaded sanitized evidence successfully as artifact `9020895148`, digest `sha256:fdd89c15bfab6374990e4c0129006e391ca6d0f417ed8bbc06b07bd2914b32cf`.

This newer run exposes no fresh SQLState or root-cause proof, so none is invented. Earlier seven-attempt SQLState `08000` evidence remains historical diagnostic evidence only. The new run proves another guarded disposable-database reset failure before Paper boot; it does **not** prove current endpoint unreachability or that the underlying database condition is unchanged. It is **NOT A PASS**.

## Exact blocker and unblock
No safe repository-side implementation remains.

Exact blocker: the required guarded disposable-database pre-reset did not succeed before Paper boot under the current `pi-staging` environment contract. The newest run does not establish the current root cause beyond that observed reset failure.

Exact unblock signal: a successful guarded disposable-database pre-reset from `Lincoln-PI-4` under the unchanged current `pi-staging` environment contract, followed by the required fresh exact-current-main acceptance run.

Do not:
- change database targets or credentials;
- use another database;
- remove or weaken the disposable reset;
- allow Paper to boot before reset success;
- broaden ES-R01 into database administration;
- manually repeat an identical staging attempt merely because time passed or documentation changed.

After material evidence that the guarded pre-reset can succeed, resume ES-R01 before any new package and require one fresh exact-current-main proof through public hosted build → bounded transfer → private exact provenance → guarded pre-reset → Paper boot cycle 1 → restart/cycle 2 → guarded post-reset → sanitized evidence → correlated public success → transfer cleanup. Only then mark ES-R01 `COMPLETE`. Do not start ES-P02 in that same worker.

## Dependent package routing
- ES-P02 PR #70 remains `BLOCKED` / `PARKED_BLOCKED`; branch drift is not actionable while ES-R01 remains parked.
- ES-P05 PR #81 remains `BLOCKED` / `PARKED_BLOCKED`; its implementation and hosted validation remain preserved.
- ES-P07, ES-P06, ES-P08, ES-X01, ES-X02, ES-X03, ES-X04, and ES-QA01 remain dependency-blocked planned work.
- ES-V01, ES-V02, ES-V03, and ES-A01 remain deferred under their existing private/acceptance contracts.
- No package is currently dependency-complete `READY` while ES-R01 still lacks the required successful guarded pre-reset and acceptance proof.

## Migration, authority, and production boundaries
- V18 remains immutable/current; ES-R01 changed no migration.
- Issue #43 remains open/deferred.
- LiteBans remains authoritative.
- No production data/configuration, credentials, punishment/player records, raw addresses, deployment, production migration/cutover, Flyway history repair, or ES-V02 execution was touched.

## Branch cleanup
- Public PR #94 checkpoint branch was automatically deleted after normal merge.
- Public PR #95 terminal-publication branch is deleted after normal merge.
- The three private staging ES-R01 implementation branches were previously compared against staging `main` and contain no unique work. They remain contained refs; no false deletion claim is made.
- This post-merge finalization uses only `package/es-r01-post-merge-finalization` and contains documentation/package-state changes only.

## Stop boundary
After this documentation-only post-merge finalization is normally merged, exact containment is verified, and the temporary finalization branch is cleaned when safe, stop. Do not select, prepare, synchronize, or rerun ES-P02, ES-P05, or any other package in this worker.
