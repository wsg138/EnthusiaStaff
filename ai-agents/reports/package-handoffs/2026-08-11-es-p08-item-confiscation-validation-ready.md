# ES-P08 — Item confiscation and restoration — validation ready

Status: `ACTIVE` / `ACTIONABLE_CONTINUATION`; implementation complete and ready for one exact-head validation freeze.

## Live reconciliation

- Package start remains live `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.
- Branch: `package/es-p08-item-confiscation`.
- Implementation PR: #128, ready for review.
- `main` did not advance during implementation, so no upstream merge or rebase was required.
- V18 remains the immutable Flyway ceiling. ES-P08 adds no migration.
- Issue #43 remains open/deferred and LiteBans remains authoritative.
- ES-X01 and all downstream provider/validation/cutover packages remain parked; this worker owns only ES-P08.

## Completed implementation

The pre-existing durable confiscation/restoration foundation was retained: profiles, paired operations/patches, before snapshots, leases/fencing, nested paths/fingerprints, durable confiscated-asset snapshots, restoration reservations, checksum/revision guards, and login/restart recovery.

ES-P08 adds an explicit Founder-authorized recovery path for one coherent quarantined case-linked item operation:

- `/case recoveritems <case-id>` is gated by Bukkit `enthusiastaff.owner.recovery` and independently by service-level Founder `RESTORE_ASSETS` authority.
- The command only authorizes a durable retry; it never edits inventory itself.
- Persistence accepts only `CONFISCATION` or `RESTORE_CONFISCATED` operations and independently rechecks case-target/profile binding, paired state/profile/fencing identity, unresolved quarantine identity/resource key, and absence of a competing live lease.
- More than one unresolved matching item operation is `AMBIGUOUS` and leaves every candidate untouched.
- A successful authorization atomically returns the exact pair from `QUARANTINED` to `PENDING`, records resolver/time/resolution metadata, and requires exactly one append-only `INVENTORY_QUARANTINE_REQUEUED` audit event without inventory contents.
- Duplicate authorization after a successful retry is explicit `REPLAYED` and does not append another audit.
- Normal recovery must then acquire a newer fence and prove expected-before or exact-replacement live state before finalization. Ambiguity quarantines again.
- Re-quarantine clears the old resolution fields so the new unsafe state is visibly unresolved while prior authorization remains in audit.

## Completed tests

New coverage proves:

- non-Founder authorization never reaches persistence;
- missing recovery storage fails closed;
- Founder delegation preserves exact actor/case/time;
- generic inventory-edit quarantines cannot use case-item recovery;
- owner authorization does not advance the inventory profile revision;
- duplicate authorization replays without duplicate audit;
- a newer claimed fence can re-quarantine and later be separately re-authorized;
- a live competing resource lease blocks recovery without mutation;
- divergent patch/operation state rolls the transaction back;
- corrupted case-target/profile binding rolls the transaction back instead of hiding the quarantine;
- multiple unresolved same-case item operations across distinct scopes remain ambiguous and untouched.

Existing adjacent suites provide direct evidence for exact restoration target/case/profile/scope binding, duplicate finalization, cancellation on failed/quarantined restoration, restore-once semantics, nested path round trips/depth/index limits, aggregate inventory-image size limits, generic operation fencing/lease behavior, and restart-style already-replaced recovery.

## Review and static history

Harsh manual review found and fixed two substantive issues before freeze:

1. case-target corruption was initially filtered out by the SQL join; it is now loaded and explicitly rejected as divergent evidence so corruption cannot be hidden while recovering another candidate;
2. the privileged recovery audit initially used optional `INSERT IGNORE`; it now requires exactly one audit insert inside the same transaction, so an audit anomaly rolls back the preceding requeue/quarantine-resolution writes.

Codacy reported four code-quality findings on a superseded head: repeated `PENDING`/`QUARANTINED` test literals and two conditional magic numbers. All four were fixed. No superseded Codacy result is final evidence.

CodeRabbit attempted review after PR #128 left draft state but reported that the repository review quota was exhausted. Its generic status is not counted as a substantive review pass. No CodeRabbit inline review threads exist. The final review record must therefore distinguish the quota limitation from the exact-head manual full-diff review rather than claiming a bot pass.

## Diagnostic/pre-freeze evidence only

Earlier implementation heads produced useful but non-final evidence, including successful Java 21 Paper artifact builds and a full Java/MariaDB/Testcontainers/Codacy coverage run. Those heads were later superseded by valid manual/static fixes and must not be reused as final proof.

The final implementation tree immediately before this canonical publication also compiled successfully as an exact Sentinel Paper artifact and had Wiki validation green; its full hosted/Pi jobs were still running when the package state publication began. Those runs are diagnostic only because this canonical publication changes the feature SHA.

## Freeze rule

After the remaining validation-ready canonical files are published, capture the resulting literal branch SHA externally in PR metadata as the frozen feature head. From that point, do not modify repository content unless a valid final blocker requires a fix and a new freeze.

Required final evidence on that one frozen SHA:

- Validate Wiki;
- Java 21 full build/tests with MariaDB/Testcontainers;
- runtime Paper/Velocity JAR inspection and zero provider-API leaks;
- aggregate JaCoCo and configured Codacy coverage gate;
- Codacy static analysis with zero valid new findings;
- exact-head manual full-diff review with zero valid unresolved findings, while recording CodeRabbit quota honestly;
- exact Sentinel Paper artifact plus live Sentinel restart to terminal `PAPER_RESTART_OK` because Paper runtime changed;
- canonical public→private Pi staging on trusted `Lincoln-PI-4` with exact source/provenance, two Paper/storage-ready `SHADOW_MIGRATION` cycles, V1–V18 first-cycle/current no-op restart behavior, clean shutdown/failure scans, sanitized evidence, and guarded cleanup.

## Stop/resume boundary

If interrupted before terminal completion, resume PR #128 on `package/es-p08-item-confiscation`; do not select another package. After all frozen-head evidence passes, merge PR #128 by normal merge commit only, prove containment/divergence, clean the temporary branch, publish terminal canonical state (using the permitted docs-only finalization PR for post-merge facts if necessary), and stop without activating ES-X02.
