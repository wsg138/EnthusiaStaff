# ES-P07 — Inventory and Ender editing runtime completion

Status: `ACTIVE` / `ACTIONABLE_CONTINUATION` — implementation complete; final static-analysis correction applied; fresh exact-head validation pending.

## Selection and reconciliation

- Original package start: legitimate `main` `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.
- Branch: `package/es-p07-inventory-runtime`; implementation PR `#112`.
- ES-P07 was later parked on unavailable Pi/Sentinel resources. This worker resumed it before ES-P06 when the private Pi runner materially became available.
- An old private Pi attempt reached `Lincoln-PI-4` but failed before Paper because a transient bridge artifact returned HTTP 404. That remains non-passing evidence.
- Current target `main` `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` was normally merged into the package branch as `562e8647063c3fa09b4349d167dc41d1a1660553`, restoring PR mergeability without changing ES-P07 product files.
- V18 remains the immutable migration boundary; ES-P07 adds no migration. Issue #43 remains deferred and LiteBans remains authoritative.

## Completed implementation

- Exact logical dirty-slot writes replace stale whole-image inventory/Ender replacement.
- The complete deduplicated slot set validates before `PlayerInventory` is obtained or mutated.
- Complete serialized inventory snapshots are bounded at 32 MiB; per-item bound remains 16 MiB.
- Same-operation `APPLYING` lease replay is idempotent with the same fence while competing operations remain excluded.
- Login/recovery locks also guard damage/resurrection, consume/durability/mending, entity interaction and Paper pick/equipment-swap paths.
- Existing entity-thread scheduling, offline authoritative observations/queued patches, revision/checksum fencing, Velocity backend-switch locks, nested item serialization and lifecycle ownership are preserved.
- Tests cover aggregate bounds, mixed valid/invalid slots, MariaDB same-owner lease replay, command permission separation and direct `invsee`/`endersee` workflow/GUI wiring.
- Both permission nodes must contain an explicit non-null `default` field before their false values are asserted; the metadata field name is represented by one `DEFAULT_FIELD` constant to satisfy the final Codacy maintainability finding.
- Inventory safety documentation is updated without claiming deferred broader private acceptance.

## Review and static-analysis history

- Initial recovery-guard compile failed because two Paper events used the Bukkit namespace; fixed to `io.papermc.paper.event.player`. The failed run remains non-passing evidence.
- CodeRabbit found a valid partial-mutation defect on freeze `6c7ec06622b8ee20d00aa3839e5741f44a0f1976`; complete-set prevalidation and regression coverage fixed it.
- Exact-head Codacy on freeze `2c0ad0543f9f39f242e8a75c532a77ca2afb52de` reported two valid method-level synchronization warnings; the encode-local unshared bounded stream no longer uses unnecessary method synchronization.
- Synchronized candidate `562e8647063c3fa09b4349d167dc41d1a1660553` passed Java 21 clean/full tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, aggregate JaCoCo/Codacy upload, Wiki and exact Sentinel artifact publication. CodeRabbit then found two valid final-review issues: a missing explicit metadata-field-presence assertion and stale tracked freeze-state wording.
- Review-correction head `8b055b8851dd185ae5e8969148aaa11e0d1985e4` fixed those issues and resolved the review threads, but exact-head Codacy static analysis found one valid warning because `"default"` appeared four times in the strengthened test. This checkpoint replaces the repeated literal with one `DEFAULT_FIELD` constant.
- Therefore `8b055b...` and all earlier heads are superseded for final acceptance, including hosted successes, runtime work and review/static results.

## Superseded evidence

- Exact synchronized-head Sentinel artifact run `31431731862` produced artifact `9079413472`, digest `sha256:3d0dff42e624891030ab1af6f19d7a6cd82c58b13c62da2603d44ad958034ff3`; later Sentinel job 83 was not a final pass.
- Exact synchronized-head Coverage run `31431731890` / job `93596510081` succeeded with aggregate 47.14% line, 38.24% branch and 49.80% instruction coverage; Paper JAR SHA-256 `7de4853ecc8d005a6277178284936a51988dee161991df8cbd6ba8649b22f8fa`; Velocity JAR SHA-256 `cefb70557829777b4c9fff3a78bf6a14731fa347fa6f3f60fd451a04e661c19a`; provider API leaks zero; validation artifact `9079582840`, digest `sha256:2d4d04410a34473f951577fbf0a4b8bacc14b81ca0c74b4744d0d284c04aff1a`. Those results are diagnostic only.
- Head `8b055b8851dd185ae5e8969148aaa11e0d1985e4` had Wiki success, exact Sentinel artifact success and all CodeRabbit threads resolved, but Codacy static was `action_required` with the valid repeated-literal annotation. All of its other running or completed gates are superseded by this static correction and must not be used as final proof.

## Boundaries

- Confiscation/restoration remains ES-P08.
- External destructive providers remain ES-X02/ES-X03/ES-X04.
- Representative broader multi-backend, large/private-inventory and Java/Bedrock acceptance remains ES-V02.
- No production data, deployment, production shadow, authority/cutover, issue #43 acceptance, source rewrite or migration repair occurred.
- ES-P06 remains READY but is not activated. ES-X01 remains PARKED_BLOCKED on the unresolved supported RoseChat integration repository/source contract.

## Exact next action

The commit containing this final Codacy test cleanup and tracked-state correction is the new immutable PR #112 validation head. Record its literal SHA in PR metadata, then make no further tracked change unless a final gate finds a real defect. Require fresh Java 21 clean/full build/tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, Wiki, configured Codacy/static coverage, CodeRabbit/reviewer completion with zero valid unresolved review threads, Sentinel exact artifact plus terminal `PAPER_RESTART_OK`, and canonical automatic public→private Pi staging with correlated private provenance, Paper restart/MariaDB/Flyway evidence and cleanup. Merge normally only if the exact head remains unchanged and mergeable; then prove containment/divergence, clean the temporary branch safely, publish terminal COMPLETE/dependency-derived routing, and stop without activating another package.
