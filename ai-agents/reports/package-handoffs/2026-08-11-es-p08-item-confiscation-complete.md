# ES-P08 — Item confiscation and restoration — terminal handoff

Tracked terminal status: `COMPLETE` on normal merge of implementation PR #128. Until that merge, PR #128 remains the sole `ACTIONABLE_CONTINUATION` for ES-P08.

## Scope and reconciliation

- Package start: `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.
- Package branch: `package/es-p08-item-confiscation`.
- Implementation PR: #128.
- Frozen executable-validation head: `27b20bb56e540161f695e624916f91620261457d`.
- V18 remains the immutable Flyway boundary; ES-P08 adds no migration.
- Issue #43 remains open/deferred and LiteBans remains authoritative.
- No production data, deployment, cutover, authority change, source rewrite, or downstream provider implementation is included.

A documentation-only blocker publication later advanced `main` after the product head was frozen. Owner-directed reconciliation then confirmed that the blocker's live Sentinel restart requirement had been added by the worker after package selection; it did not exist in the canonical ES-P08 contract at package start. The authoritative contract explicitly deferred representative destructive/load acceptance to `ES-V03`.

Under the package-contract-integrity and frozen-product-head rules in `VALIDATION-POLICY.md`, PR #128 may preserve its executable evidence only if its final synchronization delta after `27b20bb...` is proven process/state/documentation-only. Any executable change invalidates that reuse and requires fresh executable validation.

## Completed implementation

The existing durable inventory journal foundation was preserved: profiles, paired operations/patches, snapshots, nested item identity, leases/fencing, confiscated-asset snapshots, restoration reservation/finalization, checksum/revision guards, and restart/login recovery.

ES-P08 adds a dedicated owner-recovery boundary for case-linked item operations:

- Founder-only `/case recoveritems <case-id>` is gated by Bukkit `enthusiastaff.owner.recovery` and service-level `RESTORE_ASSETS` policy.
- Unresolved command identity is rejected before async dispatch; the service remains a secondary fail-closed guard.
- Persistence accepts only `CONFISCATION` and `RESTORE_CONFISCATED` operations.
- Recovery independently rechecks case target/profile binding, paired patch/operation state/profile/fence identity, stored quarantine resource identity, unresolved quarantine evidence, and live competing leases.
- Missing/mismatched resource evidence fails closed; no synthetic recovery key is substituted.
- Multiple unresolved case-linked item operations are ambiguous and untouched.
- Successful authorization atomically moves one exact pair from `QUARANTINED` to `PENDING`, records resolver/time/resolution metadata, and requires exactly one append-only audit event.
- The authorization command never applies an inventory image. Normal fenced checksum/revision recovery must acquire a newer fence and prove live state before commit.
- A failed retry reopens quarantine resolution fields while preserving prior authorizations in audit.

## Test and review result

New unit/MariaDB integration coverage exercises Founder/non-Founder/unresolved identity, storage loss, generic-operation exclusion, no profile-revision mutation during authorization, duplicate replay, competing leases, paired-state divergence, corrupted case target, same-case multi-scope ambiguity, re-quarantine/re-recovery, and audit behavior.

Existing adjacent suites cover exact restoration target/case/profile/scope binding, duplicate finalization, failed/quarantined reservation cancellation, restore-once semantics, nested item paths, aggregate codec limits, generic fencing/leases, and restart-style recovery.

Valid findings were fixed rather than waived. Manual review found hidden case-target divergence and optional privileged recovery-audit insertion. Codacy found four code-quality issues on a superseded head. CodeRabbit identified the no-follow-up-merge-evidence policy, stale canonical handoff state, unresolved sender reporting/test coverage, missing stored resource evidence, and nullable lease timestamp handling. Frozen product head review ended with zero valid unresolved threads.

## Required executable evidence — PASS on frozen product head

All executable evidence below is bound to `27b20bb56e540161f695e624916f91620261457d`:

- Wiki run `31555952998`: PASS.
- Coverage run `31555953013`, job `93988340387`: PASS on Java 21, including MariaDB/Testcontainers, warnings-as-errors, runtime-JAR inspection, provider-leak checks, and aggregate JaCoCo.
- Codacy static `93988413158`: PASS with zero issues; configured coverage variation passed.
- Review: zero valid unresolved threads.
- Sentinel artifact workflow `31555953004`: PASS.
- Canonical Pi public run `31555950970` attempt 1 → private run `31556350997`, job `93989465759` on trusted `Lincoln-PI-4`: PASS. It proved exact source/artifact provenance, V1–V18 first-cycle migration, V18 restart no-op, two storage-ready `SHADOW_MIGRATION` Paper cycles, clean shutdown/reap, sanitized evidence, guarded database cleanup, and public transfer cleanup.

## Live Sentinel diagnostics — explicitly NOT PASSING

The later live Sentinel restart attempts remain useful diagnostic history but are not an authoritative ES-P08 merge dependency:

- job `150`: `RESTART_CYCLE_1_RESOURCE_GATE_FAILED` at 80.3 C against the 80.0 C ceiling;
- job `151`: timed out;
- job `153`: cycle 1 completed, then `RESTART_CYCLE_2_RESOURCE_GATE_FAILED` at 81.8 C.

None is relabeled as passed. They are not substituted for a required gate. The correction is that the original ES-P08 contract never required this independent live Sentinel restart; broader destructive acceptance remains assigned to `ES-V03`.

## Merge, cleanup, and stop rule

Synchronize PR #128 with current `main` using a normal merge, preserving the frozen executable tree. Prove by exact comparison that every change after `27b20bb...` is process/state/documentation-only under `VALIDATION-POLICY.md`. Run the applicable documentation/package/static/review gates for that synchronization head and require zero valid unresolved review threads.

Then merge PR #128 using a normal merge commit only. Verify feature-head containment, resulting-main divergence, merge parents, and safe branch cleanup. Record GitHub-generated post-merge facts in PR #128 metadata/comments.

After verification, this worker stops. It does not activate or implement ES-X02 or any other package. On canonical ES-P08 completion, ES-X02 becomes dependency-complete and may be selected by a new sequential worker.
