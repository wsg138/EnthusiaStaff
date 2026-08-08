# `ES-R01` — Billing-independent staging bridge recovery

## 1. Package identity
`ES-R01`; validation-infrastructure recovery; primary `COMP-STAFF`; supporting repository `wsg138/EnthusiaStaff-Staging`; priority 15; not parallel-safe with staging-workflow changes.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` as of 2026-08-08. Repository-side implementation, repair, terminal publication, and post-merge finalization are complete. Exact artifact/provenance delivery to the trusted Pi is proven. The mandatory guarded disposable Paper boot/restart acceptance remains blocked because the required guarded disposable-database pre-reset has not succeeded on the latest exact-main proofs.

## 3. Objective
Remove the shared repository-side staging deadlock without weakening validation by building the exact authorized EnthusiaStaff source SHA on public GitHub-hosted infrastructure, securely handing the verified artifact/provenance to the private trusted Pi, and requiring the existing disposable Paper boot/restart gate.

## 4. Why the package exists
ES-P02 and ES-P05 were parked because private `wsg138/EnthusiaStaff-Staging` could not allocate its required GitHub-hosted `ubuntu-latest` build under the Billing & plans restriction while `Lincoln-PI-4` remained operational. ES-R01 removed that private-hosted build dependency. The replacement route now demonstrably reaches the private trusted Pi with exact artifact provenance; the remaining blocker is the guarded disposable-database pre-reset prerequisite before Paper boot.

## 5. Included audit/package IDs
Validation infrastructure for ES-P02 and ES-P05 and later ordinary development packages. No product audit ID is completed or waived by ES-R01.

## 6. Included behavior
- Public ordinary GitHub-hosted Java 21 build of the exact authorized EnthusiaStaff SHA.
- Exact main-history or open same-repository PR-head source authorization.
- Fork boundary that prevents private staging credentials/execution.
- SHA-256-bound runtime package and strict provenance manifest.
- Bounded transient GitHub release transfer with exact run/release/asset correlation and cleanup.
- Private re-verification of public run, PR provenance, asset freshness, transfer digest, manifest, runtime digest and size before boot.
- Trusted self-hosted runner enforcement for `Lincoln-PI-4`.
- Guarded disposable database reset plus two-cycle Paper boot/restart harness.
- Bounded retries only for SQLState class `08xxx` connection failures; all other reset failures remain immediate failures.
- Regression fixtures for source selection, PR-target provenance, stale/missing/mismatched artifacts, digest mismatch, cleanup error paths, duplicate/cancellation boundaries, database readiness, and existing staging controls.

## 7. Explicit exclusions
No product Java behavior; no migrations; no production deployment/data/routes/credentials; no issue #43 activation or acceptance; no LiteBans authority change; no validation exception; no false pass for failed/skipped/unavailable evidence.

## 8. Dependencies
Package dependency graph: none.

### Operational prerequisite / exact unblock
The exact unblock signal is a successful guarded disposable-database pre-reset from `Lincoln-PI-4` under the unchanged current `pi-staging` environment contract, followed by the required fresh exact-current-main acceptance run. A future worker may resume ES-R01 only after material evidence that this guarded pre-reset can succeed. Do not broaden ES-R01 to database administration, change credentials or targets, use a different database, remove the reset, or permit Paper boot without reset success.

## 9. Component and repository boundaries
Workflow/tooling/test/documentation only in `wsg138/EnthusiaStaff` and `wsg138/EnthusiaStaff-Staging`. Product source, runtime product behavior, migrations, production configuration, and private production evidence remain outside scope.

## 10. Branches used
- `wsg138/EnthusiaStaff:package/es-r01-staging-bridge-recovery` — merged implementation; no longer present.
- `wsg138/EnthusiaStaff-Staging:package/es-r01-staging-bridge-recovery` — merged/contained.
- `wsg138/EnthusiaStaff-Staging:package/es-r01-database-readiness` — merged/contained.
- `wsg138/EnthusiaStaff-Staging:package/es-r01-pr-provenance-fix` — merged/contained.
- `wsg138/EnthusiaStaff:package/es-r01-proof-retry-checkpoint` — PR #94 checkpoint; automatically deleted after merge.
- `wsg138/EnthusiaStaff:package/es-r01-finalize-blocked` — PR #95 terminal publication; deleted after normal merge.
- `wsg138/EnthusiaStaff:package/es-r01-post-merge-finalization` — documentation-only branch used by the sequential worker to persist PR #95 merge/containment and the automatic post-merge staging evidence; no product implementation is carried on this branch.

All three remaining private staging refs were compared against staging `main` `4036d6e915c2d751bef18849107722dfd1e586a6` and contain no unique implementation work. The connected GitHub tool surface available to the earlier worker had no delete-ref operation; do not falsify cleanup by moving refs.

## 11. PR and merge record
- Staging bridge PR #58 → normal merge `570f83e41cb80b498a82c8b5a509c42345558a46`.
- Public bridge PR #93 → normal merge `094838fa221476e0832cf821f7b4908b9402d0d9`.
- Staging database-readiness PR #59 → normal merge `313ed2815058eadeb8c823453f4152089cae01d4`.
- Staging PR-target provenance fix PR #60 → normal merge `4036d6e915c2d751bef18849107722dfd1e586a6`.
- Public checkpoint PR #94 frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a` → normal merge `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`.
- Terminal documentation publication PR #95 frozen head `b918ec7ed7708db9b69e061d2f4bc322a94c5124` → normal merge `3ce303ce3097be647091e142e801da9a5fd9a8fc`; compare reports one commit ahead, zero behind, and no file differences; public terminal-publication branch deleted.

## 12. Implementation checklist
Repository-side implementation is complete: public build, bounded transfer, private verifier, Pi runner restriction, fail-closed cleanup, PR-target provenance, database reset/readiness boundary, tests, operator documentation, normal implementation merges, terminal publication, and post-merge state reconciliation are complete. End-to-end Paper runtime acceptance remains blocked before a successful guarded boot/restart cycle.

## 13. Acceptance criteria state
- **PASS:** exact source builds successfully on ordinary public GitHub-hosted infrastructure without private hosted minutes.
- **PASS:** private staging allocates trusted `Lincoln-PI-4` and independently verifies exact public artifact/run/source provenance.
- **PASS:** no private `ubuntu-latest` job is required.
- **PASS:** same-repository PR and fork trust boundaries are enforced and tested.
- **PASS:** failed/missing/mismatched/expired evidence fails closed; transient transfer cleanup has been verified after failed private runs.
- **BLOCKED / NOT A PASS:** guarded disposable-database / Paper boot-restart acceptance does not complete on exact merged-main proof.
- **NOT CLAIMED:** ES-P02 and ES-P05 are not validated by ES-R01 and remain parked.

## 14. Test evidence
- Staging PR #58 exact-head Staging Controls CI: green on `Lincoln-PI-4`.
- Staging PR #59 exact-head run `31249532617`, job `93083557688`: green, including bounded SQLState `08xxx` retry fixture and all existing staging/Sentinel controls.
- Staging PR #60 exact-head run `31250097746`, job `93084990928`: green, including valid `pull_request_target` provenance, wrong base/control rejection, clean failure-path cleanup, database readiness, storage readiness, successful-cycle, issue #43 prerequisite, and Sentinel fixtures.
- Public PR #93 frozen head `cccadbd1885f78db517ff643f941d04bd0fba2a3`: full build/tests/runtime inspection, Codacy static with zero issues, diff coverage, and coverage variation passed. CodeRabbit was rate-limited and produced no review threads.
- PR #94 frozen checkpoint head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`: Coverage run `31252575675` / job `93091088422` succeeded on Java 21 with full build/tests/runtime inspection/aggregate coverage; Codacy static `93091169010`, diff coverage `93091677173`, and coverage variation `93091677101` succeeded; CodeRabbit succeeded after one valid terminal-status sequencing issue was fixed and resolved.
- Required fresh current-main proof after PR #94 merge: public Pi Staging run `31252997554` at `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`; public build job `93092131811` succeeded; bridge job `93092964130` dispatched private run `31253345564`; private job `93092978141` reached trusted runner `Lincoln-PI-4` ID `2`, passed exact artifact verification, then failed at the guarded disposable Paper boot/restart step; sanitized evidence artifact `9020680419`, digest `sha256:5647d2458ab4b1d594e86030d9ffe1a89ac50609093417d3fcb617ecf5b1b677`, uploaded successfully; public failure diagnostics and transient transfer cleanup succeeded. The result remains failure and is not represented as a pass.
- Automatic exact-main proof after PR #95 terminal-publication merge: public Pi Staging run `31253869828` at `3ce303ce3097be647091e142e801da9a5fd9a8fc`; public build job `93094217219` succeeded; bridge job `93094873681` dispatched/correlated private run `31254151964` and completed transient transfer cleanup; private job `93094893264` allocated `Lincoln-PI-4` runner ID `2`, passed identity and exact bridge-artifact verification, then failed at the guarded disposable Paper boot/restart step with `ERROR: Refused or failed to clear the dedicated disposable Pi database before boot`; sanitized evidence artifact `9020895148`, digest `sha256:fdd89c15bfab6374990e4c0129006e391ca6d0f417ed8bbc06b07bd2914b32cf`, uploaded successfully. This newer run is also **NOT A PASS**. It proves another guarded disposable-database reset failure before Paper boot but exposes no fresh SQLState or root-cause proof, so it does not establish current endpoint unreachability or an unchanged underlying cause.

## 15. Static/review evidence
All valid static-analysis findings found during implementation were repaired before merge. PR #58, #59, and #60 had no unresolved review threads at merge. PR #93 Codacy was green with zero issues. On PR #94, CodeRabbit's valid process finding that terminal status must wait for the post-merge current-main proof was accepted and fixed before final validation; the thread was resolved/outdated and the exact frozen head then reported CodeRabbit success. PR #95 was documentation-only terminal publication and merged normally; post-merge containment is exact.

## 16. Documentation
`docs/pi-staging-bridge.md` documents trust boundaries, artifact/provenance handoff, failure recovery, retention, and package resumption. Historical checkpoint handoffs remain preserved. Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-08-es-r01-final-blocked-current-main.md`.

## 17. Security and privacy state
No secrets are included in public transfer artifacts/logs. The private Pi verifier receives only source/run/release identities and independently revalidates them. Private database credentials remain environment secrets and are not printed. The database reset still refuses unsafe targets and Paper is not allowed to boot until reset success.

## 18. Migration impact
None. V18 remains immutable/current; ES-R01 added or modified no Flyway migration.

## 19. Bedrock considerations
Not applicable to this infrastructure repair. Representative Java/Bedrock acceptance remains assigned to later validation packages.

## 20. Distributed-runtime considerations
Only staging orchestration is in scope. No distributed product acceptance is claimed.

## 21. External-provider considerations
GitHub Actions and the existing self-hosted runner are validation infrastructure. No third-party artifact host or new external service was introduced. The terminal blocked gate is the already-configured guarded disposable staging-database pre-reset before Paper boot.

## 22. Completion definition
ES-R01 may become `COMPLETE` only after a fresh exact-current-main bridge run succeeds through: public hosted build → bounded transfer → private exact provenance verification → guarded pre-reset → Paper boot cycle 1 → restart/cycle 2 → guarded post-reset → sanitized evidence upload → public correlated success → transient release/tag cleanup. Neither the required post-PR-#94 current-main proof nor the automatic exact-PR-#95-merge proof satisfied that definition, so the package remains parked rather than completed.

## 23. Resume state
No worker is assigned after this post-merge finalization publication completes. Resume ES-R01 only after material evidence that the guarded disposable-database pre-reset can succeed under the unchanged current `pi-staging` contract. Resume ES-R01 before any new package; do not start ES-P02 in the same worker that later completes or re-parks ES-R01.

## 24. Live proof evidence
### First merged-main proof
Public run `31249125885`, source/control `094838fa221476e0832cf821f7b4908b9402d0d9`:
- public hosted build job `93082543002` succeeded on GitHub-hosted runner ID `1000009805`;
- private run `31249402654`, job `93083246690`, allocated `Lincoln-PI-4` runner ID `2`;
- exact release/run/digest/manifest verifier passed;
- disposable database pre-reset failed on SQLState `08000` before Paper boot;
- transient release `367158184` and tag `es-r01-staging-31249125885-1` were confirmed deleted afterward.

### Corrected PR-target proof
Public PR #94 head `4acb4853c5ce00805ff206e3d0bb28a2458e82c8`, public Pi Staging run `31250170297`:
- ordinary public hosted build job `93085175893` succeeded and uploaded the exact runtime package;
- correlated private run `31250450219`, job `93085892938`, allocated `Lincoln-PI-4` runner ID `2`;
- corrected PR-target provenance verifier passed using source/PR head `4acb4853c5ce00805ff206e3d0bb28a2458e82c8` and trusted base/workflow control SHA `094838fa221476e0832cf821f7b4908b9402d0d9`;
- guarded database pre-reset attempted seven total connections; all seven returned SQLState `08000`; Paper never booted;
- sanitized evidence artifact `9019842260`, digest `sha256:d0d203f707940c05d9d5728120d4a207b6cd0ad68357aeb7ea907561bf6bacc4`, uploaded successfully;
- public bridge cleanup succeeded.

### Required current-main terminal proof after PR #94
Public Pi Staging run `31252997554`, exact source/control `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`:
- public hosted Java 21 build job `93092131811` succeeded and uploaded the verified runtime package;
- bridge job `93092964130` published bounded transient transfer, dispatched/correlated private run `31253345564`, collected failed-run diagnostics, and removed the transient transfer successfully;
- private job `93092978141` allocated `Lincoln-PI-4`, runner ID `2`; runner identity and exact bridge artifact verification succeeded;
- guarded disposable Paper boot/restart step failed; sanitized evidence upload succeeded;
- evidence artifact `9020680419`, digest `sha256:5647d2458ab4b1d594e86030d9ffe1a89ac50609093417d3fcb617ecf5b1b677`;
- GitHub step metadata for this run does not expose a fresh SQLState, so this package does not invent one. Earlier repeated SQLState `08000` proof remains historical diagnostic evidence for the guarded database boundary.

### Automatic exact-main proof after terminal publication PR #95
Public Pi Staging run `31253869828`, exact source/control `3ce303ce3097be647091e142e801da9a5fd9a8fc`:
- public build job `93094217219` succeeded;
- bridge job `93094873681` completed exact artifact download, bounded transient transfer, private dispatch/correlation, failed-run diagnostics, and transfer cleanup, then correctly failed the final staging verdict;
- private run `31254151964`, job `93094893264`, allocated trusted `Lincoln-PI-4`, runner ID `2`, and passed exact artifact verification;
- guarded disposable Paper boot/restart failed before Paper boot with `ERROR: Refused or failed to clear the dedicated disposable Pi database before boot`;
- sanitized evidence artifact `9020895148`, digest `sha256:fdd89c15bfab6374990e4c0129006e391ca6d0f417ed8bbc06b07bd2914b32cf`, uploaded successfully;
- no fresh SQLState or root-cause proof was exposed in this run. Earlier seven-attempt SQLState `08000` evidence remains historical context only and is not projected onto this newer reset failure.

## 25. Remaining checklist
No safe repository-side implementation or validation rerun is presently actionable. Post-merge state must be persistently published, the documentation-only finalization PR must merge normally, its containment must be verified, and the worker must stop without selecting another package. Future product/validation work is conditional only on material evidence that the guarded disposable-database pre-reset can succeed under the unchanged staging contract.

## 26. Known blocked gate
The latest exact-main evidence proves that the required guarded disposable staging-database clear/pre-reset did not succeed before Paper boot. Earlier proof captured seven consecutive connection attempts returning SQLState `08000`, but the later PR-#94 and PR-#95 exact-main runs expose no fresh SQLState and therefore do not prove the present endpoint-reachability state or current root cause. The package explicitly forbids bypassing the database gate, changing to an unapproved target, or broadening into database administration.

## 27. Final evidence state
Terminal `BLOCKED` / `PARKED_BLOCKED`. Repository-side bridge repair, terminal publication, exact merge containment, and exact provenance delivery are proven. End-to-end runtime acceptance is **NOT A PASS**. The exact resume signal is a successful guarded pre-reset under the unchanged staging contract, followed by a fresh exact-current-main acceptance run.

## 28. Merge and synchronization record
Implementation merges: `570f83e41cb80b498a82c8b5a509c42345558a46`, `094838fa221476e0832cf821f7b4908b9402d0d9`, `313ed2815058eadeb8c823453f4152089cae01d4`, `4036d6e915c2d751bef18849107722dfd1e586a6`. Checkpoint PR #94 merged normally as `689ff337dd8a33f0bd417d952a7cad5581cb9d9e` from frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`; compare reports one commit ahead, zero behind, no file differences; checkpoint branch auto-deleted. Terminal publication PR #95 merged normally as `3ce303ce3097be647091e142e801da9a5fd9a8fc` from frozen head `b918ec7ed7708db9b69e061d2f4bc322a94c5124`; compare reports one commit ahead, zero behind, no file differences; publication branch deleted. No product/migration source changed and no cross-repository source parity requirement applies. Private merged temporary refs are fully contained by staging `main`; no false cleanup claim is made.
