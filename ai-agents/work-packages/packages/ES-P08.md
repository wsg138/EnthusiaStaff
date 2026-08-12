# `ES-P08` — Item confiscation and restoration

## 1. Package identity
`ES-P08`; Internal; primary `COMP-STAFF`; priority 70; sequential around shared inventory journals/destructive recovery.

## 2. Status
`BLOCKED`; classification `PARKED_BLOCKED`.

Implementation is preserved in PR #128 on temporary branch `package/es-p08-item-confiscation`. Frozen product head: `27b20bb56e540161f695e624916f91620261457d`. Exact package start: `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.

The product implementation and every required exact-head gate except the independent live Sentinel restart are complete. The package is not `COMPLETE` and PR #128 must not merge until live Sentinel produces literal exact-head `PAPER_RESTART_OK`.

## 3. Objective
Complete transactional case-linked item confiscation, snapshots, reservations, rollback, restoration, and explicit owner recovery.

## 4. Included audit IDs
`AUD-ASSET-001` and item-specific portions of `AUD-ASSET-005`.

## 5. Included behavior
Exact item/path selection including nested containers; durable before/after snapshots and operation IDs; reservations and duplicate prevention; checked/unchecked failure rollback; restart recovery/quarantine; authorized exact restoration; explicit Founder-authorized quarantine retry; bounded work and audit.

## 6. Explicit exclusions
Currency (`ES-X02`), market (`ES-X03`), reputation (`ES-X04`), production inventories, whole-server rollback, and representative destructive/load acceptance assigned to `ES-V03`.

## 7. Dependencies
`ES-P07` is `COMPLETE`.

## 8. Component and repository boundaries
Root inventory/asset/domain/persistence/Paper/tests/docs only. No external provider source or permanent/isolated branch.

## 9. Branch and PR policy
Temporary implementation branch `package/es-p08-item-confiscation`; implementation PR #128 is the only product PR into `main` for this package. Preserve both while blocked.

A documentation-only status-publication PR may publish this blocker state to `main` under the universal worker protocol. It must not merge or close PR #128 or alter product code. After eventual implementation merge, record merge SHA, resulting `main`, containment/divergence, branch deletion, and exact validation identifiers in PR #128 verification metadata; do not create a follow-up product commit solely for self-referential merge facts.

## 10. Implementation result
- Existing durable profiles, paired operations/patches, before snapshots, confiscated-asset snapshots, restoration reservation/finalization, nested item identity, leases/fencing, checksum/revision guards, and restart/login recovery were retained.
- Added a dedicated `InventoryRecoveryStore`, JDBC implementation, Paper coordinator, and `/case recoveritems <case-id>` route rather than expanding the existing mutation coordinator.
- Recovery accepts only case-linked `CONFISCATION` or `RESTORE_CONFISCATED` operations.
- Bukkit `enthusiastaff.owner.recovery` and service-level Founder `RESTORE_ASSETS` authorization both gate persistence.
- An unresolved command sender fails before dispatch with a cause-accurate message; the coordinator separately fails closed on a null/non-Founder actor.
- Persistence independently verifies case-target/profile binding, patch/operation state/profile/fence coherence, stored quarantine `resource_key`, unresolved quarantine identity, and absence of a competing live lease.
- Missing or mismatched recovery resource evidence fails closed; no synthetic fallback resource key is used.
- Multiple unresolved item quarantines for one case are `AMBIGUOUS`; no candidate is guessed or changed.
- Successful authorization atomically requeues only the exact pair to `PENDING`, resolves quarantine metadata, and requires exactly one append-only `INVENTORY_QUARANTINE_REQUEUED` audit write.
- The owner command never applies inventory. Normal claim/checksum/revision recovery must acquire a newer fence and prove live state before finalization.
- A failed newer-fence retry re-quarantines and clears prior resolution fields so the new unsafe state is visibly unresolved while earlier authorization remains in audit.
- Nullable JDBC reads are handled defensively even where V18 declares fields `NOT NULL`.

## 11. Test result
New unit/integration coverage proves non-Founder/unresolved actors never reach recovery persistence; missing storage fails closed; exact Founder actor/case/time delegation; generic inventory-edit quarantines cannot use case-item recovery; recovery authorization does not advance profile revision; duplicate authorization replays without duplicate audit; live competing leases block without mutation; paired-state divergence and case-target corruption roll back; same-case multi-scope ambiguity remains untouched; and failed newer-fence recovery reopens quarantine and can be independently re-authorized.

Existing adjacent suites continue to cover exact restoration target/case/profile/scope binding, duplicate finalization, failed/quarantined reservation cancellation, restore-once semantics, nested path depth/index/round-trip behavior, aggregate inventory-image size limits, generic journal fencing/leases, and restart-style already-replaced recovery.

## 12. Review/static result
Valid findings were fixed rather than waived. Manual review found and fixed hidden case-target divergence and optional privileged recovery-audit insertion. Codacy findings on superseded heads were fixed. CodeRabbit findings covering merge-evidence policy, canonical handoff state, unresolved sender reporting/test coverage, missing stored recovery resource evidence, and nullable lease timestamp handling are addressed. Exact-head manual review found no additional valid release blocker and valid unresolved review-thread count is zero.

Exact frozen-head hosted/static evidence is durable in PR #128. Codacy static reports zero issues. The CodeRabbit docstring-coverage UI item is advisory, not a repository gate, and is not represented as a pass.

## 13. Migration impact
V18 remains the immutable Flyway boundary. ES-P08 adds no migration because the existing quarantine schema already provides required resolution fields. V1–V18 remain byte-immutable.

## 14. Security/privacy and distributed guarantees
No inventory contents are written to owner-recovery audit metadata. Uncertain identity/state/resource/fence/lease evidence fails closed. Recovery authorization is idempotent and does not bypass normal fenced application. No production data, deployment, authority change, cutover, private-data acceptance, or source rewrite is part of this package.

## 15. Exact-head validation state
Frozen product head `27b20bb56e540161f695e624916f91620261457d` has successful exact-head Wiki; Java 21 full build/tests including MariaDB/Testcontainers; Paper/Velocity runtime-JAR/provider-leak checks; aggregate JaCoCo/Codacy coverage; Codacy static with zero issues; manual/review-thread disposition; Sentinel artifact build; and canonical public→private Pi staging on trusted `Lincoln-PI-4`.

Canonical Pi public run `31555950970` attempt 1 and correlated private run `31556350997` / job `93989465759` passed exact provenance, two storage-ready `SHADOW_MIGRATION` Paper cycles, V1–V18 first-cycle migration, V18 restart no-op behavior, clean shutdown/failure scans, sanitized evidence, guarded disposable-database cleanup, and transfer cleanup.

The required live Sentinel restart is the sole unresolved executable gate:
- job `150`: non-passing `RESTART_CYCLE_1_RESOURCE_GATE_FAILED` before product acceptance because temperature was 80.3 C at/above the 80.0 C ceiling;
- job `151`: remained resource-gated and ultimately timed out; non-passing;
- job `153`: exact-head restart remains queued under the trusted resource gate; latest observed host state was 596 MB available below the 700 MB minimum and 83.3 C at/above the 80.0 C ceiling.

Queued, timed-out, resource-gated, skipped, cancelled, superseded, merge-ref-only, or wrong-revision results are not passing evidence. No infrastructure exception is authorized for ES-P08.

## 16. Resume and completion rule
Current blocker is environmental Sentinel host capacity/temperature, not a demonstrated product defect. Do not repeatedly issue identical restart requests merely to probe the same unavailable condition.

Exact unblock condition: live evidence must show the trusted Sentinel resource condition changed, or currently queued exact-head job `153` must reach terminal `PAPER_RESTART_OK`. Then reclassify ES-P08 as `ACTIONABLE_CONTINUATION`, reconcile live `main`, PR #128/head/reviews/checks, and obtain/verify literal exact-head `PAPER_RESTART_OK` before merge.

When all required exact-head gates pass, merge PR #128 by a normal merge commit only. Verify feature-head containment, resulting-main divergence, and safe implementation-branch cleanup; publish generated merge facts in PR #128 metadata. Then mark ES-P08 `COMPLETE` and update dependency-derived statuses without activating `ES-X02` in the same worker.

Issue #43 remains open/deferred and LiteBans remains authoritative.
