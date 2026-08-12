# ES-P08 — Item confiscation and restoration — terminal handoff

Tracked terminal status: `COMPLETE`. This handoff is part of implementation PR #128 and becomes canonical only when that PR is normally merged to `main` after every required exact-head validation gate passes.

## Scope and reconciliation

- Package start: `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.
- Package branch: `package/es-p08-item-confiscation`.
- Implementation PR: #128.
- `main` did not advance during implementation; no merge/rebase synchronization was required before validation.
- V18 remains the immutable Flyway boundary; ES-P08 adds no migration.
- Issue #43 remains open/deferred and LiteBans remains authoritative.
- No production data, deployment, cutover, authority change, source rewrite, or downstream provider implementation is included.

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

Valid findings were fixed rather than waived. Manual review found hidden case-target divergence and optional privileged recovery-audit insertion. Codacy found four code-quality issues on a superseded head. CodeRabbit identified the no-follow-up-merge-evidence policy, stale canonical handoff state, unresolved sender reporting/test coverage, missing stored resource evidence, and nullable lease timestamp handling. PR #128 may merge only with zero valid unresolved review threads.

## Exact-head validation and evidence location

The literal final frozen feature SHA cannot be embedded in the commit that creates it without creating a self-referential loop. Therefore PR #128 is the durable verification ledger for the exact frozen SHA and all final evidence identifiers.

Before merge, PR #128 metadata must record successful exact-head:

- Wiki validation;
- Java 21 full build/tests with MariaDB/Testcontainers;
- Paper/Velocity runtime-JAR hashes and zero provider-API leaks;
- aggregate JaCoCo and configured Codacy coverage;
- Codacy static analysis with zero valid new findings;
- manual/review-thread disposition with zero valid unresolved findings;
- Sentinel artifact plus live restart to `PAPER_RESTART_OK`;
- canonical public→private Pi staging on trusted `Lincoln-PI-4` with exact source/provenance, two Paper/storage-ready `SHADOW_MIGRATION` cycles, V1–V18 first-cycle/current restart behavior, clean shutdown/failure scans, sanitized evidence, guarded database cleanup, and public transfer cleanup.

Superseded, cancelled, failed, quota-limited, or wrong-revision runs remain non-passing history.

## Merge, cleanup, and stop rule

After the exact frozen SHA passes every gate, merge PR #128 using a normal merge commit only. Then verify feature-head containment, resulting-main divergence, merge parents, and deletion of `package/es-p08-item-confiscation`. Record those GitHub-generated post-merge facts in PR #128 metadata/comments; do not create a follow-up `main` commit or PR solely to insert merge evidence into tracked files.

After verification, this worker stops. It does not activate or implement ES-X02 or any other package.
