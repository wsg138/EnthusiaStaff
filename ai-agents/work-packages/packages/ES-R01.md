# `ES-R01` — Billing-independent staging bridge recovery

## 1. Package identity
`ES-R01`; validation-infrastructure recovery; primary `COMP-STAFF`; supporting repository `wsg138/EnthusiaStaff-Staging`; priority 15; not parallel-safe with staging-workflow changes.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` as of 2026-08-08. Repository-side implementation and repair work is merged and exact artifact/provenance delivery to the trusted Pi is proven. The mandatory guarded disposable Paper boot/restart acceptance remains unavailable at the existing authorized staging-database boundary.

## 3. Objective
Remove the shared repository-side staging deadlock without weakening validation by building the exact authorized EnthusiaStaff source SHA on public GitHub-hosted infrastructure, securely handing the verified artifact/provenance to the private trusted Pi, and requiring the existing disposable Paper boot/restart gate.

## 4. Why the package exists
ES-P02 and ES-P05 were parked because private `wsg138/EnthusiaStaff-Staging` could not allocate its required GitHub-hosted `ubuntu-latest` build under the Billing & plans restriction while `Lincoln-PI-4` remained operational. ES-R01 removed that private-hosted build dependency. The replacement route now demonstrably reaches the private trusted Pi with exact artifact provenance; the remaining blocker is the distinct existing disposable-database availability prerequisite.

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
The existing dedicated disposable Pi-staging MariaDB endpoint referenced by the current `pi-staging` environment contract must become reachable from `Lincoln-PI-4` long enough for the guarded pre-test reset to succeed. A future worker may resume ES-R01 only after material evidence that this condition changed. Do not broaden ES-R01 to database administration, change credentials or targets, use a different database, remove the reset, or permit Paper boot without reset success.

## 9. Component and repository boundaries
Workflow/tooling/test/documentation only in `wsg138/EnthusiaStaff` and `wsg138/EnthusiaStaff-Staging`. Product source, runtime product behavior, migrations, production configuration, and private production evidence remain outside scope.

## 10. Branches used
- `wsg138/EnthusiaStaff:package/es-r01-staging-bridge-recovery` — merged implementation; no longer present.
- `wsg138/EnthusiaStaff-Staging:package/es-r01-staging-bridge-recovery` — merged/contained.
- `wsg138/EnthusiaStaff-Staging:package/es-r01-database-readiness` — merged/contained.
- `wsg138/EnthusiaStaff-Staging:package/es-r01-pr-provenance-fix` — merged/contained.
- `wsg138/EnthusiaStaff:package/es-r01-proof-retry-checkpoint` — PR #94 checkpoint; automatically deleted after merge.
- `wsg138/EnthusiaStaff:package/es-r01-finalize-blocked` — terminal documentation publication branch; delete after its normal merge if GitHub does not auto-delete it.

All three remaining private staging refs were compared against staging `main` `4036d6e915c2d751bef18849107722dfd1e586a6` and contain no unique implementation work. The connected GitHub tool surface available to this worker has no delete-ref operation; do not falsify cleanup by moving refs.

## 11. PR and merge record
- Staging bridge PR #58 → normal merge `570f83e41cb80b498a82c8b5a509c42345558a46`.
- Public bridge PR #93 → normal merge `094838fa221476e0832cf821f7b4908b9402d0d9`.
- Staging database-readiness PR #59 → normal merge `313ed2815058eadeb8c823453f4152089cae01d4`.
- Staging PR-target provenance fix PR #60 → normal merge `4036d6e915c2d751bef18849107722dfd1e586a6`.
- Public checkpoint PR #94 frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a` → normal merge `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`.
- Terminal documentation publication: `package/es-r01-finalize-blocked`; its normal merge SHA is recorded by the final worker verification after merge.

## 12. Implementation checklist
Repository-side implementation is complete: public build, bounded transfer, private verifier, Pi runner restriction, fail-closed cleanup, PR-target provenance, database connection-readiness boundary, tests, operator documentation, normal implementation merges, and live bridge execution through the private verifier are complete. End-to-end Paper runtime acceptance is blocked externally before a successful guarded boot/restart cycle.

## 13. Acceptance criteria state
- **PASS:** exact source builds successfully on ordinary public GitHub-hosted infrastructure without private hosted minutes.
- **PASS:** private staging allocates trusted `Lincoln-PI-4` and independently verifies exact public artifact/run/source provenance.
- **PASS:** no private `ubuntu-latest` job is required.
- **PASS:** same-repository PR and fork trust boundaries are enforced and tested.
- **PASS:** failed/missing/mismatched/expired evidence fails closed; transient transfer cleanup has been verified after failed private runs.
- **BLOCKED / NOT A PASS:** guarded disposable-database / Paper boot-restart acceptance does not complete on the required fresh current-main proof.
- **NOT CLAIMED:** ES-P02 and ES-P05 are not validated by ES-R01 and remain parked.

## 14. Test evidence
- Staging PR #58 exact-head Staging Controls CI: green on `Lincoln-PI-4`.
- Staging PR #59 exact-head run `31249532617`, job `93083557688`: green, including bounded SQLState `08xxx` retry fixture and all existing staging/Sentinel controls.
- Staging PR #60 exact-head run `31250097746`, job `93084990928`: green, including valid `pull_request_target` provenance, wrong base/control rejection, clean failure-path cleanup, database readiness, storage readiness, successful-cycle, issue #43 prerequisite, and Sentinel fixtures.
- Public PR #93 frozen head `cccadbd1885f78db517ff643f941d04bd0fba2a3`: full build/tests/runtime inspection, Codacy static with zero issues, diff coverage, and coverage variation passed. CodeRabbit was rate-limited and produced no review threads.
- PR #94 frozen checkpoint head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`: Coverage run `31252575675` / job `93091088422` succeeded on Java 21 with full build/tests/runtime inspection/aggregate coverage; Codacy static `93091169010`, diff coverage `93091677173`, and coverage variation `93091677101` succeeded; CodeRabbit succeeded after one valid terminal-status sequencing issue was fixed and resolved.
- Required fresh current-main proof after PR #94 merge: public Pi Staging run `31252997554` at `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`; public build job `93092131811` succeeded; bridge job `93092964130` dispatched private run `31253345564`; private job `93092978141` reached trusted runner `Lincoln-PI-4` ID `2`, passed exact artifact verification, then failed at the guarded disposable Paper boot/restart step; sanitized evidence artifact `9020680419`, digest `sha256:5647d2458ab4b1d594e86030d9ffe1a89ac50609093417d3fcb617ecf5b1b677`, uploaded successfully; public failure diagnostics and transient transfer cleanup succeeded. The result remains failure and is not represented as a pass.

## 15. Static/review evidence
All valid static-analysis findings found during implementation were repaired before merge. PR #58, #59, and #60 had no unresolved review threads at merge. PR #93 Codacy was green with zero issues. On PR #94, CodeRabbit's valid process finding that terminal status must wait for the post-merge current-main proof was accepted and fixed before final validation; the thread was resolved/outdated and the exact frozen head then reported CodeRabbit success.

## 16. Documentation
`docs/pi-staging-bridge.md` documents trust boundaries, artifact/provenance handoff, failure recovery, retention, and package resumption. Historical checkpoint handoffs remain preserved. Terminal handoff: `ai-agents/reports/package-handoffs/2026-08-08-es-r01-final-blocked-current-main.md`.

## 17. Security and privacy state
No secrets are included in public transfer artifacts/logs. The private Pi verifier receives only source/run/release identities and independently revalidates them. Private database credentials remain environment secrets and are not printed. The database reset still refuses unsafe targets and Paper is not allowed to boot until reset success.

## 18. Migration impact
None. V18 remains immutable/current; ES-R01 added or modified no Flyway migration.

## 19. Bedrock considerations
Not applicable to this infrastructure repair. Representative Java/Bedrock acceptance remains assigned to later validation packages.

## 20. Distributed-runtime considerations
Only staging orchestration is in scope. No distributed product acceptance is claimed.

## 21. External-provider considerations
GitHub Actions and the existing self-hosted runner are validation infrastructure. No third-party artifact host or new external service was introduced. The terminal blocked gate is the already-configured disposable staging database endpoint.

## 22. Completion definition
ES-R01 may become `COMPLETE` only after a fresh exact-current-main bridge run succeeds through: public hosted build → bounded transfer → private exact provenance verification → guarded pre-reset → Paper boot cycle 1 → restart/cycle 2 → guarded post-reset → sanitized evidence upload → public correlated success → transient release/tag cleanup. The required post-PR-#94 current-main proof did not satisfy that definition, so the package is parked rather than completed.

## 23. Resume state
No worker is assigned after the terminal publication merges. Resume ES-R01 only after material evidence that the exact authorized staging-database unblock condition changed. Resume ES-R01 before any new package; do not start ES-P02 in the same worker that later completes or re-parks ES-R01.

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

### Required current-main terminal proof
Public Pi Staging run `31252997554`, exact source/control `689ff337dd8a33f0bd417d952a7cad5581cb9d9e`:
- public hosted Java 21 build job `93092131811` succeeded and uploaded the verified runtime package;
- bridge job `93092964130` published bounded transient transfer, dispatched/correlated private run `31253345564`, collected failed-run diagnostics, and removed the transient transfer successfully;
- private job `93092978141` allocated `Lincoln-PI-4`, runner ID `2`; runner identity and exact bridge artifact verification succeeded;
- guarded disposable Paper boot/restart step failed; sanitized evidence upload succeeded;
- evidence artifact `9020680419`, digest `sha256:5647d2458ab4b1d594e86030d9ffe1a89ac50609093417d3fcb617ecf5b1b677`;
- GitHub step metadata for this final run does not expose a fresh SQLState, so this package does not invent one. Earlier repeated SQLState `08000` proof remains the precise diagnostic evidence for the same guarded database boundary.

## 25. Remaining checklist
No safe repository-side implementation remains. Terminal publication must merge normally, exact containment must be verified, and the worker must stop without selecting another package. Future work is conditional only on material evidence of the exact database unblock condition.

## 26. Known blocked gate
The existing authorized disposable staging MariaDB endpoint is not currently usable by the guarded Pi staging path. Earlier proof captured seven consecutive connection attempts returning SQLState `08000`; the mandatory fresh current-main run again failed at the guarded disposable boot/restart step after exact provenance verification. The package explicitly forbids bypassing the database gate, changing to an unapproved target, or broadening into database administration.

## 27. Final evidence state
Terminal `BLOCKED` / `PARKED_BLOCKED`. Repository-side bridge repair and exact provenance delivery are proven. End-to-end runtime acceptance is **NOT A PASS**. Exact unblock and resume behavior are recorded above.

## 28. Merge and synchronization record
Implementation merges: `570f83e41cb80b498a82c8b5a509c42345558a46`, `094838fa221476e0832cf821f7b4908b9402d0d9`, `313ed2815058eadeb8c823453f4152089cae01d4`, `4036d6e915c2d751bef18849107722dfd1e586a6`. Checkpoint PR #94 merged normally as `689ff337dd8a33f0bd417d952a7cad5581cb9d9e` from frozen head `3f90ae4e96e969a7ceac45ee9a385f068c0af14a`; compare reports one commit ahead, zero behind, no file differences; checkpoint branch auto-deleted. No product/migration source changed and no cross-repository source parity requirement applies. Private merged temporary refs are fully contained by staging `main`; the available connected tool surface has no delete-ref operation, so no false cleanup claim is made.
