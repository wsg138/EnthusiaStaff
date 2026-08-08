# `ES-R01` — Billing-independent staging bridge recovery

## 1. Package identity
`ES-R01`; validation-infrastructure recovery; primary `COMP-STAFF`; supporting repository `wsg138/EnthusiaStaff-Staging`; priority 15; not parallel-safe with staging-workflow changes.

## 2. Status
`IN_PROGRESS` / `VERIFYING` as of 2026-08-08. Repository-side implementation and repair work is merged. Repeated live evidence shows a blocked disposable-database gate before Paper boot, but terminal classification is deferred until PR #94 merges normally and the required fresh current-`main` Pi Staging proof is inspected.

## 3. Objective
Remove the shared repository-side staging deadlock without weakening validation by building the exact authorized EnthusiaStaff source SHA on public GitHub-hosted infrastructure, securely handing the verified artifact/provenance to the private trusted Pi, and requiring the existing disposable Paper boot/restart gate.

## 4. Why the package exists
ES-P02 and ES-P05 were parked because private `wsg138/EnthusiaStaff-Staging` could not allocate its required GitHub-hosted `ubuntu-latest` build under the Billing & plans restriction while `Lincoln-PI-4` remained operational. ES-R01 removed that private-hosted build dependency. The replacement route now demonstrably reaches the private trusted Pi with exact artifact provenance; the remaining observed failure is a distinct disposable-database availability prerequisite.

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

### Operational prerequisite observed at the current gate
The dedicated disposable Pi-staging MariaDB endpoint referenced by the existing `pi-staging` environment secrets must accept a connection from `Lincoln-PI-4` long enough for the guarded pre-test reset to succeed. Do not broaden ES-R01 to database administration, change credentials, use a different database, remove the reset, or permit Paper boot without that success.

## 9. Component and repository boundaries
Workflow/tooling/test/documentation only in `wsg138/EnthusiaStaff` and `wsg138/EnthusiaStaff-Staging`. Product source, runtime product behavior, migrations, production configuration, and private evidence remain outside scope.

## 10. Branches used
- `wsg138/EnthusiaStaff:package/es-r01-staging-bridge-recovery`
- `wsg138/EnthusiaStaff-Staging:package/es-r01-staging-bridge-recovery`
- `wsg138/EnthusiaStaff-Staging:package/es-r01-database-readiness`
- `wsg138/EnthusiaStaff-Staging:package/es-r01-pr-provenance-fix`
- `wsg138/EnthusiaStaff:package/es-r01-proof-retry-checkpoint` — current documentation-only verification checkpoint / PR #94

Merged implementation branches contain no unique product work. Preserve the current checkpoint branch until PR #94 is normally merged. Safe branch deletion is desirable afterward, but the connected GitHub tool surface available to this worker does not expose ref deletion; do not falsify deletion by moving refs.

## 11. PR and merge record
- Staging bridge PR #58 → normal merge `570f83e41cb80b498a82c8b5a509c42345558a46`.
- Public bridge PR #93 → normal merge `094838fa221476e0832cf821f7b4908b9402d0d9`.
- Staging database-readiness PR #59 → normal merge `313ed2815058eadeb8c823453f4152089cae01d4`.
- Staging PR-target provenance fix PR #60 → normal merge `4036d6e915c2d751bef18849107722dfd1e586a6`.
- Public PR #94 is the documentation-only verification checkpoint whose normal merge must trigger the fresh current-main proof before terminal package state is published.

## 12. Implementation checklist
Repository-side implementation is complete: public build, bounded transfer, private verifier, Pi runner restriction, fail-closed cleanup, PR-target provenance, database connection-readiness boundary, tests, operator documentation, normal implementation merges, and live bridge execution through the private verifier are complete. Mandatory post-merge current-main acceptance remains pending.

## 13. Acceptance criteria state
- **PASS:** exact source builds successfully on ordinary public GitHub-hosted infrastructure without private hosted minutes.
- **PASS:** private staging allocates trusted `Lincoln-PI-4` and independently verifies exact public artifact/run/source provenance.
- **PASS:** no private `ubuntu-latest` job is required.
- **PASS:** same-repository PR and fork trust boundaries are enforced and tested.
- **PASS:** failed/missing/mismatched/expired evidence fails closed; transient transfer cleanup has been verified after failed private runs.
- **BLOCKED GATE OBSERVED:** guarded disposable-database reset has not succeeded in prior live attempts, so Paper boot/restart acceptance has not begun.
- **PENDING:** fresh post-PR-#94-merge current-main proof and terminal package classification.
- **NOT CLAIMED:** ES-P02 and ES-P05 are not validated by ES-R01 and remain parked.

## 14. Test evidence
- Staging PR #58 exact-head Staging Controls CI: green on `Lincoln-PI-4`.
- Staging PR #59 exact-head run `31249532617`, job `93083557688`: green, including bounded SQLState `08xxx` retry fixture and all existing staging/Sentinel controls.
- Staging PR #60 exact-head run `31250097746`, job `93084990928`: green, including valid `pull_request_target` provenance, wrong base/control rejection, clean failure-path cleanup, database readiness, storage readiness, successful-cycle, issue #43 prerequisite, and Sentinel fixtures.
- Public PR #93 frozen head `cccadbd1885f78db517ff643f941d04bd0fba2a3`: full build/tests/runtime inspection, Codacy static with zero issues, diff coverage, and coverage variation passed. CodeRabbit was rate-limited and produced no review threads.
- PR #94 checkpoint head `4eac5351abe0e5eb6e7053811f6bb3deaa85d884`: Coverage run `31252041814` / job `93089754760` succeeded on Java 21 with full build/tests/runtime inspection/aggregate coverage; Codacy static `93089887948`, diff coverage `93090375498`, and coverage variation `93090375337` succeeded; automatic Pi build `93089753098` succeeded and private run `31252319002` reached `Lincoln-PI-4` before the guarded boot test failed. CodeRabbit identified one valid terminal-status sequencing issue, fixed by the next checkpoint commit before final freeze.

## 15. Static/review evidence
All valid static-analysis findings found during implementation were repaired before merge. PR #58, #59, and #60 had no unresolved review threads at merge. PR #93 Codacy was green with zero issues. Unavailable/rate-limited CodeRabbit evidence is recorded as unavailable, not approval. On PR #94, CodeRabbit's valid process finding that terminal status must wait for the post-merge current-main proof was accepted and fixed before final validation.

## 16. Documentation
`docs/pi-staging-bridge.md` documents trust boundaries, artifact/provenance handoff, failure recovery, retention, and package resumption. Active checkpoint handoff: `ai-agents/reports/package-handoffs/2026-08-08-es-r01-pr-target-provenance-correction.md`. Database-gate evidence: `ai-agents/reports/package-handoffs/2026-08-08-es-r01-blocked-staging-database.md`.

## 17. Security and privacy state
No secrets are included in public transfer artifacts/logs. The private Pi verifier receives only source/run/release identities and independently revalidates them. Private database credentials remain environment secrets and are not printed. The database reset still refuses unsafe targets and Paper is not allowed to boot until reset success.

## 18. Migration impact
None. V18 remains immutable/current; ES-R01 added or modified no Flyway migration.

## 19. Bedrock considerations
Not applicable to this infrastructure repair. Representative Java/Bedrock acceptance remains assigned to later validation packages.

## 20. Distributed-runtime considerations
Only staging orchestration is in scope. No distributed product acceptance is claimed.

## 21. External-provider considerations
GitHub Actions and the existing self-hosted runner are validation infrastructure. No third-party artifact host or new external service was introduced. The currently observed blocked gate is the already-configured disposable staging database endpoint.

## 22. Completion definition
ES-R01 may become `COMPLETE` only after a fresh exact-current-main bridge run succeeds through: public hosted build → bounded transfer → private exact provenance verification → guarded pre-reset → Paper boot cycle 1 → restart/cycle 2 → guarded post-reset → sanitized evidence upload → public correlated success → transient release/tag cleanup. PR #94 must merge normally first so its resulting current-main proof can be observed. If that proof instead confirms the same external database gate and no safe repository action remains, ES-R01 may then be terminally classified `BLOCKED` / `PARKED_BLOCKED` and that post-merge fact must be published to `main`.

## 23. Resume state
Assigned to the current sequential worker until terminal post-merge classification is published. Current branch: `wsg138/EnthusiaStaff:package/es-r01-proof-retry-checkpoint`, PR #94. Do not select another package.

## 24. Live proof evidence
### First merged-main proof
Public run `31249125885`, source/control `094838fa221476e0832cf821f7b4908b9402d0d9`:
- public hosted build job `93082543002` succeeded on GitHub-hosted runner ID `1000009805`;
- private run `31249402654`, job `93083246690`, allocated `Lincoln-PI-4` runner ID `2`;
- exact release/run/digest/manifest verifier passed;
- disposable database pre-reset failed on SQLState `08000` before Paper boot;
- transient release `367158184` and tag `es-r01-staging-31249125885-1` were confirmed deleted afterward.

### Corrected live PR-target proof
Public PR #94 head `4acb4853c5ce00805ff206e3d0bb28a2458e82c8`, public Pi Staging run `31250170297`:
- ordinary public hosted build job `93085175893` succeeded and uploaded the exact runtime package;
- correlated private run `31250450219`, job `93085892938`, allocated `Lincoln-PI-4` runner ID `2`;
- corrected PR-target provenance verifier **passed** using source/PR head `4acb4853c5ce00805ff206e3d0bb28a2458e82c8` and trusted base/workflow control SHA `094838fa221476e0832cf821f7b4908b9402d0d9`;
- verified public release ID `367163460`, asset ID `506237999`, asset `enthusiastaff-staging-4acb4853c5ce-31250170297-1.zip`, transfer SHA-256 `05ed21b6279b46283853e952214513b2871f838712509ac9e4e514c11ac82488`;
- verified runtime `EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`, 9,123,435 bytes, SHA-256 `cfb526a90994803d64858b649a6452b23b5c12438461fb8f66d5cab18a21c449`;
- guarded database pre-reset attempted seven total connections. All seven returned SQLState `08000`; evidence recorded retry attempts 1/7 through 6/7 and `connection_retry_result=exhausted`; Paper never booted;
- sanitized evidence artifact `9019842260`, digest `sha256:d0d203f707940c05d9d5728120d4a207b6cd0ad68357aeb7ea907561bf6bacc4`, uploaded successfully;
- public bridge cleanup succeeded; release `367163460` and tag `es-r01-staging-31250170297-1` both return 404 after cleanup.

## 25. Remaining checklist
Freeze the corrected PR #94 checkpoint head; require all applicable exact-head hosted/static/review validation and zero valid unresolved review findings; merge PR #94 normally; verify resulting public `main`, containment, and safe cleanup; inspect the automatic fresh current-main Pi Staging proof; then publish exactly one terminal ES-R01 outcome. If the proof succeeds, publish `COMPLETE`. If it fails only on the unchanged authorized disposable-database availability prerequisite, publish `BLOCKED` / `PARKED_BLOCKED` with exact current-main evidence through a small documentation-only finalization PR. Stop without starting ES-P02.

## 26. Known blocked gate
The dedicated disposable staging MariaDB endpoint has been unavailable from the trusted Pi in prior proofs, including seven consecutive guarded connection attempts returning SQLState `08000` in private run `31250450219`. This currently blocks end-to-end acceptance, but final terminal classification waits for the mandatory post-PR-#94-merge proof. The package explicitly forbids bypassing the database gate, changing to an unapproved target, or broadening into database administration.

## 27. Final evidence state
Not terminal yet. Repository-side bridge repair is merged and its exact provenance path has live success evidence. End-to-end runtime acceptance is **NOT A PASS**. PR #94's post-merge current-main proof must determine whether ES-R01 becomes `COMPLETE` or terminally `BLOCKED`.

## 28. Merge and synchronization record
Implementation merges: `570f83e41cb80b498a82c8b5a509c42345558a46`, `094838fa221476e0832cf821f7b4908b9402d0d9`, `313ed2815058eadeb8c823453f4152089cae01d4`, `4036d6e915c2d751bef18849107722dfd1e586a6`. No product/migration source was changed and no cross-repository source parity requirement applies. PR #94 remains the documentation-only verification checkpoint until it normally merges. Temporary implementation refs may remain only because the available connected GitHub tool surface has no delete-ref operation; live containment/merged PRs are the authority and no unique implementation work remains on them.
