# ES-P07 package handoff — inventory and Ender runtime completion

Date: 2026-08-10
Package: `ES-P07`
Status: `COMPLETE`
Classification: terminal

## Selection and package scope

- Original package start: legitimate `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.
- Existing package branch/PR were preserved through the continuation lifecycle: `package/es-p07-inventory-runtime`, PR #112.
- The package was temporarily parked on Pi/Sentinel resource availability, then resumed as `ACTIONABLE_CONTINUATION` when the private Pi runner materially became available. ES-P06 was not started.
- Current target `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` was normally merged into the package branch as `562e8647063c3fa09b4349d167dc41d1a1660553` before final validation; that synchronization changed no ES-P07 product file.
- Final frozen implementation head: `70b279998bbcc9a3ddd68b5f6e060d5a60662323`.
- V18 remained the immutable Flyway ceiling; ES-P07 added no migration.
- Issue #43 remains open/deferred and LiteBans remains authoritative.

## Completed behavior

- Exact logical dirty-slot application replaces stale whole-image inventory/Ender writes.
- Every deduplicated logical slot is validated before obtaining or mutating player inventory state.
- Complete serialized inventory snapshots are bounded at 32 MiB; the existing 16 MiB per-item bound remains.
- Same-operation `APPLYING` lease replay is idempotent with the same fencing token while competing operations remain excluded.
- Login/recovery locks additionally guard damage/resurrection, consume/durability/mending, entity interaction, Paper pick-item, and equipment-swap mutation paths.
- Authoritative player lookup, entity-thread scheduling, offline queued patches, revision/checksum fencing, Velocity backend-switch locks, nested item serialization, and lifecycle ownership remain preserved.
- Tests cover aggregate snapshot bounds, mixed valid/out-of-range slot failure without partial mutation, MariaDB same-owner lease replay, invsee/endersee command wiring, entity scheduling, GUI view/edit separation, and explicit non-null default-false permission metadata.
- Inventory and confiscation safety documentation was updated without implementing ES-P08 or claiming ES-V02 acceptance.

## Review and static history

- Initial recovery-guard compilation failed because two Paper events were imported from the Bukkit namespace; this was corrected and the failed run remains non-passing.
- CodeRabbit found a valid partial-mutation defect on an earlier freeze; full slot-set prevalidation plus regression coverage fixed it.
- Exact-head Codacy found two unnecessary method-level synchronization warnings in an encode-local bounded stream; the synchronization was removed.
- After the normal main synchronization, CodeRabbit found two valid final-review issues: explicit permission-default field presence was not proven, and tracked freeze-state wording was stale. Both were fixed.
- Exact-head Codacy then found one valid repeated-literal maintainability warning in the strengthened permission test. Final head `70b279998bbcc9a3ddd68b5f6e060d5a60662323` uses one `DEFAULT_FIELD` constant while preserving the presence and false-value assertions.
- A later CodeRabbit request asked that the final commit literal SHA be embedded in three files that are themselves part of that commit. The worker dispositioned this as invalid/self-referential: editing those files would create a different commit SHA and immediately stale the inserted value. PR metadata and GitHub HEAD already recorded the literal frozen SHA. The thread was resolved without changing the validated tree.
- Final CodeRabbit status is success and the valid unresolved review-thread count is zero.

## Frozen exact-head hosted evidence

Frozen implementation head: `70b279998bbcc9a3ddd68b5f6e060d5a60662323`.

Passing evidence:

- Validate Wiki run `31433081524` / job `93600876249` — success.
- Hosted Coverage run `31433081255`, successful exact-head job `93601334744` — Java 21 clean/full build and tests, MariaDB/Testcontainers, runtime-JAR inspection, aggregate JaCoCo report, validation artifact and Codacy coverage upload all passed.
- Aggregate JaCoCo: 47.14% lines, 38.24% branches, 49.80% instructions.
- Paper JAR: 9,148,983 bytes; SHA-256 `814697276458912c1bbb8fe5aaf98952fa7f1eb6c625b3195deaff598538aa8e`; provider API leaks 0.
- Velocity JAR: 7,891,242 bytes; SHA-256 `12ba1199024cfc9cd228307742dc3cc4c8d25b017bb0d117bb8b97c3e99b8c44`; provider API leaks 0.
- Hosted validation artifact `9080140711`, digest `sha256:7c8a1df6bd5deaa2719febd588ab0c39925728291b475088f5e601e5b1e3624a`.
- Codacy static — success, zero annotations.
- Codacy diff coverage — 31.58%, success; no separate diff-coverage gate configured.
- Codacy coverage variation — -0.04%, success against the -1.0% target.
- CodeRabbit/reviewer — success; zero valid unresolved threads.

## Sentinel runtime evidence

- Exact frozen-head Sentinel artifact: `9079917694`, digest `sha256:5e67feb5a4461cc468289a6ef063ad66b3b22c08f70dede750a32f501ab72132`.
- Exact-head Sentinel restart job 85 reached terminal `PAPER_RESTART_OK`.
- Result: Paper reached readiness and stopped cleanly twice against one disposable state.
- Earlier Sentinel/resource results and all results bound to superseded heads remain non-final and are not reused.

## Canonical public-to-private Pi evidence

Two final-head attempts before the passing run remain explicitly non-passing:

1. Public run `31433079712` attempt 1 dispatched private run `31433686668`; trusted runner setup passed but the private bridge failed before Paper because the transient public artifact returned HTTP 404.
2. A rerun-failed attempt reused the already-successful attempt-1 build artifact. Correlated private run `31436257506` downloaded/checksum-verified the artifact but correctly rejected its manifest because the request required build attempt 2 while the artifact remained bound to attempt 1. Provenance was not weakened.

The worker triggered a genuinely fresh pull-request validation event without changing the product head. Fresh public run `31437103701`, attempt 1, then passed end-to-end:

- Exact source: `70b279998bbcc9a3ddd68b5f6e060d5a60662323`.
- Public build job `93613604898`: success.
- Public bridge job `93615441840`: success, including exact artifact publication, private dispatch/correlation, private verdict, transient public transfer removal, and final success publication.
- Correlated private run `31437719313` / job `93615505782`: success on trusted `Lincoln-PI-4`, runner ID 2, using staging-controls head `6954daf2e392eb898f7cb2fb51462b69228d11fa`.
- Exact bridge source/provenance/checksum/allowlist verification passed. The public build run/attempt was `31437103701-1`.
- Runtime JAR in private evidence: `EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`, SHA-256 `0da1ad3900cfb730087ed5a1cd44fd8902c0ed11cb2c43a641db21dc34fec0ae`.
- Two Paper starts and two storage-ready cycles completed in required `SHADOW_MIGRATION` mode.
- First cycle applied V1 through V18 and entered `SHADOW_MIGRATION`.
- Restart verified schema v18 current with no migration necessary and re-entered `SHADOW_MIGRATION`.
- Both shutdown checks and critical-failure scans passed; `failure_count=0`.
- Guarded post-test database reset removed 69 disposable objects and passed.
- Sanitized evidence artifact `9082068813`, digest `sha256:db97da3300f462a091986dd8f752bd5e7fb374983bc6a2da8eaec94a96a28ea2`.
- Unrelated legitimate Staging/Sentinel jobs were preserved and allowed to complete; no shared-service work was cancelled to obtain this result.

## Merge and cleanup evidence

- Final pre-merge reconciliation confirmed `main` still exactly `2d8fcf27b0bac980211149ae8f7f4e7798998ee5`, PR #112 mergeable at unchanged frozen head `70b279998bbcc9a3ddd68b5f6e060d5a60662323`, issue #43 still open/deferred, V18 still the migration ceiling, and zero valid unresolved review findings.
- PR #112 merged with the required normal merge method as `c96b0a2047e2e720bb4f18d32cf8c254d0302508`.
- The merge commit has exactly two parents: pre-merge `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` and frozen feature head `70b279998bbcc9a3ddd68b5f6e060d5a60662323`.
- Comparison from the frozen feature head to the merge commit is one commit ahead, zero behind, with zero file differences.
- GitHub automatically deleted `package/es-p07-inventory-runtime`; the ref returns 404.
- External component parity is not applicable for internal `COMP-STAFF` scope.

## Terminal routing

- ES-P07 is `COMPLETE`.
- ES-P06 remains `READY` at priority 60.
- ES-P08 is now dependency-complete and `READY` at priority 70.
- ES-X01 remains `BLOCKED` / `PARKED_BLOCKED` on the unresolved supported RoseChat standalone repository/default branch/source/AGENTS contract.
- ES-X02 remains blocked by incomplete ES-P08; ES-X03/ES-X04 remain blocked by ES-P08/ES-X02.
- ES-V02 remains blocked by incomplete ES-P06, ES-X01, ES-X03 and ES-X04; ES-V03 remains blocked by ES-P08/ES-X02/ES-X03/ES-X04.
- ES-A01 remains deferred on ES-V02/ES-V03, owner authorization, and issue #43; ES-QA01 remains blocked by ES-A01.
- This worker does not activate ES-P06, ES-P08, or any other package. A new sequential worker must reconcile live state; absent a new actionable continuation, current numerical priority places ES-P06 before ES-P08.

## Systems not disturbed

- No ES-P06 or ES-P08 implementation was started.
- No ES-X01 provider API/repository was invented.
- No V1–V18 migration was edited or repaired.
- No production data, production shadow, deployment, cutover, LiteBans removal, punishment-authority change, or source rewrite occurred.
- No unrelated legitimate Sentinel/Staging jobs were cancelled or preempted.
- Broader ES-V02 representative Java/Bedrock/multi-backend/private-inventory acceptance was not claimed.

This documentation-only terminal publication closes ES-P07. After its normal merge and temporary terminal-branch cleanup are verified, this worker stops.
