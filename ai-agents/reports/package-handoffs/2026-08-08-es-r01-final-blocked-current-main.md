# ES-R01 terminal handoff — blocked after required current-main proof

Date: 2026-08-08

## Package
`ES-R01 — Billing-independent staging bridge recovery`

Terminal state: `BLOCKED` / `PARKED_BLOCKED`.

This sequential worker worked exactly ES-R01 and no second package. Repository-side bridge implementation and repairs are merged. The required fresh current-`main` staging proof after the final checkpoint merge again failed at the guarded disposable Pi runtime gate, so end-to-end staging is **NOT A PASS** and the package is parked on an external prerequisite.

## Checkpoint publication and review
Public PR #94 froze at `3f90ae4e96e969a7ceac45ee9a385f068c0af14a` and passed:
- Coverage run `31252575675` / job `93091088422` on Java 21 with full build/tests/runtime inspection;
- Codacy static `93091169010`;
- Codacy diff coverage `93091677173`;
- Codacy coverage variation `93091677101`;
- CodeRabbit after one valid sequencing finding was fixed and resolved.

PR #94 merged normally as `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`. The merge has exactly two parents: pre-merge `main` `094838fa221476e0832cf821f7b4908b9402d0d9` and frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`. Compare from frozen head to merge reports one commit ahead, zero behind, no file differences. The public checkpoint branch was automatically deleted after merge.

## Required fresh current-main proof
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

The final run's GitHub step metadata does not expose a fresh database SQLState, so none is invented here. Precise earlier evidence remains public run `31250170297` → private run `31250450219` / job `93085892938`, where exact provenance passed and seven guarded connection attempts all returned SQLState `08000` before Paper boot. Taken together, the evidence proves the bridge/provenance path is working and the remaining failure is at the existing guarded disposable staging-database/runtime boundary.

## Exact blocker and unblock
No safe repository-side implementation remains.

Exact blocker: the existing authorized disposable Pi-staging MariaDB endpoint is not currently usable by the guarded Paper boot/restart path from `Lincoln-PI-4` under the current `pi-staging` environment contract.

Exact unblock: material evidence that this same authorized endpoint is reachable from `Lincoln-PI-4` under that unchanged contract.

Do not:
- change database targets or credentials;
- use another database;
- remove or weaken the disposable reset;
- allow Paper to boot before reset success;
- broaden ES-R01 into database administration;
- manually repeat an identical staging attempt merely because time passed or documentation changed.

After material evidence of the unblock condition, resume ES-R01 before any new package and require one fresh exact-current-main proof through public hosted build → bounded transfer → private exact provenance → guarded pre-reset → Paper boot cycle 1 → restart/cycle 2 → guarded post-reset → sanitized evidence → correlated public success → transfer cleanup. Only then mark ES-R01 `COMPLETE`. Do not start ES-P02 in that same worker.

## Dependent package routing
- ES-P02 PR #70 remains `BLOCKED` / `PARKED_BLOCKED`; branch drift is not actionable while ES-R01 remains parked.
- ES-P05 PR #81 remains `BLOCKED` / `PARKED_BLOCKED`; its implementation and hosted validation remain preserved.
- No package is currently dependency-complete `READY` while this unchanged ES-R01 prerequisite blocks the staging route.

## Migration, authority, and production boundaries
- V18 remains immutable/current; ES-R01 changed no migration.
- Issue #43 remains open/deferred.
- LiteBans remains authoritative.
- No production data/configuration, credentials, punishment/player records, raw addresses, deployment, production migration/cutover, Flyway history repair, or ES-V02 execution was touched.

## Branch cleanup
- Public PR #94 checkpoint branch was automatically deleted after normal merge.
- The three private staging ES-R01 implementation branches were compared against staging `main` and contain no unique work.
- The connected GitHub tool surface available to this worker does not expose delete-ref; those contained private refs are not falsely moved or called deleted.
- Terminal publication branch `package/es-r01-finalize-blocked` should be removed after its normal merge if GitHub does not auto-delete it.

## Stop boundary
After the terminal documentation publication merges normally and containment is verified, stop. Do not select or prepare ES-P02, ES-P05, or any other package in this worker.
