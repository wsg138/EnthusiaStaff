# ES-P07 — Inventory and Ender editing runtime completion

Status: `ACTIVE` — implementation, direct wiring coverage, substantive review/static-analysis fixes and repeated harsh review complete; final refreeze in progress.

## Selection

- Selected after live reconciliation found no `ACTIONABLE_CONTINUATION`; ES-P07 and ES-P06 were the only dependency-complete `READY` packages, and ES-P07 has lower numerical priority (45).
- Starting `main`: `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.
- Branch: `package/es-p07-inventory-runtime`, created exactly from that head.
- Ready PR: `#112` to `main`.
- V18 is the immutable migration boundary; ES-P07 adds no migration.
- Issue #43 remains deferred and LiteBans remains authoritative.

## Completed implementation

- Exact logical dirty-slot writes replace stale whole-image inventory/Ender replacement.
- The complete deduplicated slot set validates before `PlayerInventory` is obtained or mutated.
- Complete serialized inventory snapshots are bounded at 32 MiB; per-item bound remains 16 MiB.
- Same-operation `APPLYING` lease replay is idempotent with the same fence while competing operations remain excluded.
- Login/recovery locks also guard damage/resurrection, consume/durability/mending, entity interaction and Paper pick/equipment-swap paths.
- Existing entity-thread scheduling, offline authoritative observations/queued patches, revision/checksum fencing, Velocity backend-switch locks, nested item serialization and lifecycle ownership are preserved.
- Tests cover aggregate bounds, mixed valid/invalid slots, MariaDB same-owner lease replay, command permission separation and direct `invsee`/`endersee` workflow/GUI wiring.
- Inventory safety documentation is updated without claiming deferred representative private acceptance.

## Review and static-analysis fixes

- Initial recovery-guard compile failed because two Paper events used the Bukkit namespace; fixed to `io.papermc.paper.event.player`. The failed run remains non-passing evidence.
- CodeRabbit found a valid partial-mutation defect on freeze `6c7ec06622b8ee20d00aa3839e5741f44a0f1976`; complete-set prevalidation and regression coverage fixed it. All review threads are resolved.
- Exact-head Codacy on freeze `2c0ad0543f9f39f242e8a75c532a77ca2afb52de` reported two valid warnings: method-level synchronization on the private bounded byte stream. Each stream is encode-local and unshared, so the unnecessary `synchronized` modifiers were removed rather than replaced with meaningless lock blocks.
- Shared confiscation/restoration callers were reviewed only to preserve their existing checksum/fence/lock semantics; ES-P08 was not implemented.

## Superseded evidence

`321a50f3ca120dba7d7e21542450d4d527dabbd3`, `c3545612d370ea237a63394e6a0401edbe650790`, `6c7ec06622b8ee20d00aa3839e5741f44a0f1976`, and `2c0ad0543f9f39f242e8a75c532a77ca2afb52de` are non-final validation revisions. Checks tied to them are not final evidence.

Sentinel attempts on `2c0ad054...` failed infrastructure/resource conditions, including 637 MB available versus the required 700 MB; no attempt reached a passing `PAPER_RESTART_OK`. Do not rerun the identical resource gate until evidence shows the host condition changed.

## Boundaries

- Confiscation/restoration remains ES-P08.
- External destructive providers remain ES-X02/ES-X03/ES-X04.
- Representative multi-backend, large/private-inventory and Java/Bedrock acceptance remains ES-V02.
- No production data, deployment, production shadow, authority/cutover, issue #43 acceptance, source rewrite or migration repair occurred.

## Exact next action

Update `agent-handoffs/latest.md`; that commit becomes the new immutable PR #112 validation head. Make no further tracked change unless an exact-head gate finds a real defect. Require fresh Java 21 full build/tests and MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, Wiki, Codacy static/coverage, CodeRabbit/zero unresolved threads, Sentinel exact artifact plus terminal `PAPER_RESTART_OK`, and canonical automatic public→private Pi staging with correlated provenance/restart/cleanup. Merge normally only if the validated head is unchanged and mergeable; then prove containment/divergence, clean the temporary branch safely, publish terminal COMPLETE/dependency-derived routing, and stop without activating another package.