# ES-P08 — Item confiscation and restoration

Status: `ACTIVE` / `ACTIONABLE_CONTINUATION`.

## Selection and reconciliation

- Exact package start: live `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.
- Required branch: `package/es-p08-item-confiscation`.
- No open/draft pull request and no pre-existing ES-P08 branch existed at claim time.
- All incomplete packages were classified before selection: ES-P08 was the only dependency-safe `READY` package; ES-X01 remains parked on the unresolved supported RoseChat repository/source contract; ES-X02/ES-X03/ES-X04/ES-V02/ES-V03/ES-A01/ES-QA01 remain parked on dependencies or explicit owner/production conditions.
- `ES-P07` is complete, satisfying ES-P08's only package dependency.
- V18 is the current immutable Flyway boundary. ES-P08 will add V19 only if a new schema is actually required; V1–V18 remain byte-immutable.
- Issue #43 remains open/deferred and LiteBans remains authoritative. No production deployment, shadow window, authority change, cutover, source rewrite, or private-data acceptance is authorized.

## Source baseline

The existing implementation already provides substantial foundations: durable case-linked confiscation sessions, before snapshots, network inventory leases, Currency movement locks, exact nested item paths/fingerprints, atomic patch preparation, checksum/revision fencing, exact confiscated-asset snapshots, reservation/finalization, duplicate replay, bounded work, quarantine, and login/restart recovery for pending patches.

The current persistence layer also validates that a restoration reservation's source player and case target match the requested target/profile/scope before a restoration patch can be prepared. A wrong target therefore fails closed and its pre-patch reservation can be cancelled rather than receiving another player's assets.

The confirmed completion gap is explicit owner recovery. Current documentation states that `QUARANTINED` inventory operations intentionally have no automatic or privileged resolution path, leaving the player/scope blocked indefinitely. ES-P08 requires a Founder-only, audited, idempotent recovery path that preserves quarantine evidence and never guesses ambiguous live inventory state.

## Planned bounded completion

1. Add a typed item-recovery result/port operation for exact case-linked quarantined item operations only.
2. Requeue only a coherent paired `QUARANTINED` item patch/operation under transaction and fencing invariants, resolve the existing quarantine record with actor/time/reason evidence, and append an idempotent audit event. Requeue itself must not mutate player inventory; normal checksum-guarded recovery must still decide whether to apply/finalize or quarantine again.
3. Add Founder-only `/case recoveritems <case-id>` routing that works even while the case target is offline, because the recovery action only authorizes a safe retry rather than directly rewriting inventory.
4. Strengthen item-specific integration/unit tests for duplicate recovery, wrong/non-item operation rejection, paired-state divergence rollback, audit/quarantine resolution, target binding, stale/retry paths, codecs/nested selections, and failure behavior.
5. Update operator/Wiki/permissions/state documentation, then perform harsh review and exact-head hosted/static/Sentinel/Pi validation before any normal merge.

## Stop/resume rule

If interrupted, resume this exact package/branch/PR before selecting any other package. Do not activate ES-X02 or any downstream package until ES-P08 is terminally complete and canonical state has been published.
