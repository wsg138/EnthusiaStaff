# `ES-P08` — Item confiscation and restoration

## 1. Package identity
`ES-P08`; Internal; primary `COMP-STAFF`; priority 70; sequential around shared inventory journals/destructive recovery.

## 2. Status
`COMPLETE`.

Implementation PR #128 merged normally. The final synchronized implementation branch head was `f398fd5bd8bbf4ec62f7f05313dd082948c2561b`; its merge is exactly one commit ahead with zero file differences, and `package/es-p08-item-confiscation` is deleted.

Exact package start: `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.

Frozen executable-validation head: `27b20bb56e540161f695e624916f91620261457d`.

The post-frozen-head delta was proven to contain only eight canonical process/state/documentation Markdown files. Under `VALIDATION-POLICY.md`, executable evidence remains correctly attributed to the frozen product head because no product source, product tests, migrations, workflows, build/runtime configuration, dependencies, artifact contracts, Sentinel manifests, or other executable inputs changed.

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

## 9. Branch and PR result
Implementation PR #128 merged normally to `main`. GitHub post-merge verification proves the synchronized head is contained with zero file delta and the temporary implementation branch is deleted.

Post-merge merge SHA/parent/containment facts are retained in GitHub/PR #128 verification metadata rather than creating self-referential tracked history solely to embed those identifiers.

## 10. Implementation result
- Existing durable profiles, paired operations/patches, before snapshots, confiscated-asset snapshots, restoration reservation/finalization, nested item identity, leases/fencing, checksum/revision guards, and restart/login recovery were retained.
- Added a dedicated `InventoryRecoveryStore`, JDBC implementation, Paper coordinator, and `/case recoveritems <case-id>` route rather than expanding the existing mutation coordinator.
- Recovery accepts only case-linked `CONFISCATION` or `RESTORE_CONFISCATED` operations.
- Bukkit `enthusiastaff.owner.recovery` and service-level Founder `RESTORE_ASSETS` authorization both gate persistence.
- An unresolved command sender fails before dispatch with a cause-accurate message; the coordinator separately fails closed on a null/non-Founder actor.
- Persistence independently verifies case-target/profile binding, patch/operation state/profile/fence coherence, the stored quarantine `resource_key`, unresolved quarantine identity, and absence of a competing live lease.
- Missing or mismatched recovery resource evidence fails closed; no synthetic fallback resource key is used.
- Multiple unresolved item quarantines for one case are `AMBIGUOUS`; no candidate is guessed or changed.
- Successful authorization atomically requeues only the exact pair to `PENDING`, resolves quarantine metadata, and requires exactly one append-only `INVENTORY_QUARANTINE_REQUEUED` audit write.
- The owner command never applies inventory. Normal claim/checksum/revision recovery must acquire a newer fence and prove live state before finalization.
- A failed newer-fence retry re-quarantines and clears the prior resolution fields so the new unsafe state is visibly unresolved while earlier authorization remains in audit.
- Nullable JDBC reads are handled defensively even where the current V18 schema declares the fields `NOT NULL`.

## 11. Test result
New unit/integration coverage proves:
- non-Founder and unresolved actors never reach recovery persistence;
- missing storage fails closed;
- exact Founder actor/case/time delegation;
- generic inventory-edit quarantines cannot use case-item recovery;
- recovery authorization does not advance the inventory profile revision;
- duplicate authorization replays without duplicate audit;
- live competing leases block without mutation;
- paired-state divergence and case-target corruption roll back;
- two unresolved same-case item quarantines across scopes remain untouched/ambiguous;
- failed newer-fence recovery reopens quarantine and can be independently re-authorized.

Existing adjacent suites continue to cover exact restoration target/case/profile/scope binding, duplicate finalization, failed/quarantined reservation cancellation, restore-once semantics, nested path depth/index/round-trip behavior, aggregate inventory-image size limits, generic journal fencing/leases, and restart-style already-replaced recovery.

## 12. Review/static result
Valid findings were fixed rather than waived:
- manual review: hidden case-target divergence and optional privileged recovery-audit insertion;
- Codacy: four code-quality findings on a superseded head;
- CodeRabbit: follow-up-merge-evidence policy, canonical-handoff state, unresolved sender reporting/test coverage, missing stored recovery resource evidence, and nullable lease timestamp handling.

Frozen product head `27b20bb56e540161f695e624916f91620261457d` ended with zero valid unresolved review threads and passed the recorded hosted/static review gates. The final documentation-only synchronization head also passed Wiki, Codacy static, CodeRabbit review, and retained zero valid unresolved review threads.

## 13. Migration impact
V18 remains the immutable Flyway boundary. ES-P08 adds no migration because the existing quarantine schema already provides `resolved_at`, `resolved_by`, and `resolution_json`. V1–V18 remain byte-immutable.

## 14. Security/privacy and distributed guarantees
No inventory contents are written to owner-recovery audit metadata. Uncertain identity/state/resource/fence/lease evidence fails closed. Recovery authorization is idempotent and does not bypass normal fenced application. No production data, deployment, authority change, cutover, private-data acceptance, or source rewrite is part of this package.

## 15. Final validation result
The authoritative package contract at package start did not require an independent live Sentinel restart. ES-P08 destructive representative/load acceptance was explicitly deferred to `ES-V03`. A later worker-added tracking requirement for `PAPER_RESTART_OK` was therefore not a legitimate new merge dependency and was corrected under the package-contract-integrity rule in `VALIDATION-POLICY.md`.

Required executable evidence for the frozen product head is complete:
- Wiki validation passed;
- Java 21 full build/tests including MariaDB/Testcontainers passed;
- Paper/Velocity runtime-JAR inspection and zero provider-API leaks passed;
- aggregate JaCoCo and configured Codacy coverage passed;
- Codacy static analysis passed with zero valid new findings;
- manual/CodeRabbit review disposition has zero valid unresolved findings;
- canonical public→private Pi staging passed on trusted `Lincoln-PI-4` with exact provenance, two Paper/storage-ready `SHADOW_MIGRATION` cycles, V1–V18 first-cycle/current restart behavior, clean shutdown/failure scans, sanitized evidence, guarded cleanup, and public transfer cleanup;
- the exact-head Sentinel artifact-producing workflow passed.

Live Sentinel restart jobs 150, 151, and 153 remain explicit **non-passing diagnostic history**. They are not relabeled as passes and are not substituted for any required gate: job 150 failed its cycle-1 temperature resource gate, job 151 timed out, and job 153 completed cycle 1 but failed the cycle-2 temperature resource gate. Those diagnostics do not reopen the original package acceptance contract.

## 16. Completion and next routing
ES-P08 is `COMPLETE`. Its implementation PR is merged, the validated executable head is durably recorded, synchronized-head containment is exact, and the temporary implementation branch is deleted.

`ES-X02 — EnthusiaCurrency destructive provider` is now dependency-complete and `READY`. A new sequential worker may select it after live reconciliation, unless a newly discovered higher-precedence `ACTIONABLE_CONTINUATION` exists. This finalization does not activate or implement ES-X02.

Issue #43 remains open/deferred and LiteBans remains authoritative.
