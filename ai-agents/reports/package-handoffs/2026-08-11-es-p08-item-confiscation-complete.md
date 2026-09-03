# ES-P08 — Item confiscation and restoration — terminal handoff

Status: `COMPLETE`.

## Scope and reconciliation

- Package start: `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.
- Implementation PR: #128, normally merged.
- Frozen executable-validation head: `27b20bb56e540161f695e624916f91620261457d`.
- Final synchronized head: `f398fd5bd8bbf4ec62f7f05313dd082948c2561b`.
- Final synchronized head is exactly contained by the normal merge with zero file delta; the temporary implementation branch is deleted.
- V18 remains the immutable Flyway boundary; ES-P08 adds no migration.
- Issue #43 remains open/deferred and LiteBans remains authoritative.
- No production data, deployment, cutover, authority change, source rewrite, or downstream provider implementation is included.

A documentation-only blocker publication advanced `main` after the product head was frozen. Owner-directed reconciliation confirmed that the blocker's live Sentinel restart requirement had been added by the worker after package selection; it did not exist in the canonical ES-P08 contract at package start. The authoritative contract explicitly deferred representative destructive/load acceptance to `ES-V03`.

Under the package-contract-integrity and frozen-product-head rules in `VALIDATION-POLICY.md`, executable evidence remained valid because exact comparison from `27b20bb...` to final synchronized head `f398fd5...` changed only eight `ai-agents` Markdown process/state/handoff files. No product source, product tests, migrations, workflows, build/runtime configuration, dependencies, artifact contracts, Sentinel manifests, or other executable inputs changed.

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

The final state-only synchronization head independently passed Wiki, Codacy static, and CodeRabbit review and retained zero valid unresolved review threads.

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

## Merge, cleanup, and next routing

PR #128 merged using a normal merge commit. The final synchronized head is exactly contained with zero file differences, and `package/es-p08-item-confiscation` is deleted. GitHub/PR #128 metadata remains the source for the exact merge SHA and parent identities; no tracked commit exists solely to embed self-referential merge identifiers.

ES-P08 is terminal `COMPLETE`.

`ES-X02 — EnthusiaCurrency destructive provider` is now dependency-complete and `READY`. A new sequential worker must reconcile live GitHub and, absent a higher-precedence `ACTIONABLE_CONTINUATION`, select ES-X02. `ES-X01` remains independently parked on the unresolved supported RoseChat repository/default-branch/source/AGENTS contract and must not block ES-X02.
