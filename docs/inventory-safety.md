# Inventory and confiscation safety

Inventory edits, confiscations, and restorations can lose or duplicate player
items if an external mutation and its durable journal disagree. EnthusiaStaff
therefore treats the inventory profile, operation, patch, snapshot, lease, and
quarantine record as one recovery boundary.

The feature remains pre-release. Automated MariaDB coverage exercises normal,
replay, fencing, divergence, quarantine, restoration, and privileged retry
paths, but live multi-backend destructive acceptance is still assigned to
`ES-V03` before production use.

## Durable transition model

An inventory replacement follows these stages:

1. Read the authoritative profile revision and checksum.
2. Persist the intent, before snapshot, prepared replacement, and initial
   fencing token.
3. Claim the patch before touching the live inventory.
4. Apply the exact replacement on the owning Paper entity thread.
5. Verify the resulting snapshot and checksum.
6. Advance the profile revision and both journal rows in one transaction.
7. Append the commit audit and release only the matching lease fence.

`inventory_pending_patches` and `inventory_operations` are a paired journal.
Their operation ID, profile ID, state, and fencing token must remain coherent:

| Patch state | Operation state | Meaning |
| --- | --- | --- |
| `PENDING` | `PENDING` | The replacement is durable but no application worker owns it. |
| `APPLYING` | `APPLYING` | A worker claimed the current fencing token and may apply or verify the replacement. |
| `APPLIED` | `COMMITTED` | The verified replacement and durable profile revision committed. |
| `QUARANTINED` | `QUARANTINED` | Automatic progress stopped because the evidence is ambiguous or unsafe. |

Claim, commit, and quarantine updates use the previously locked state and
fencing token as SQL predicates. Both paired updates must affect exactly one
row. A state, fence, or profile mismatch aborts and rolls back the transaction;
the store never repairs a divergent pair by guessing which row is correct.

## Fencing and replay

A prepared `PENDING` patch cannot be finalized directly, even while its
preparation lease remains live. It must first be claimed as `APPLYING`, which
issues a fencing token greater than both the patch token and any retained lease
token.

Finalization requires all of the following:

- the patch and operation rows are coherent;
- the patch is `APPLYING`;
- the supplied token matches both journal rows;
- the same operation owns an unexpired lease with that token;
- the observed replacement checksum matches the prepared replacement; and
- the durable profile still matches the expected revision and checksum, or
  already contains the exact next replacement revision.

An older worker cannot commit after another worker acquires a newer token.
Terminal retries replay the stored result and append no duplicate commit audit.
Fencing-token exhaustion fails the transaction instead of wrapping.

## Quarantine and privileged retry

Checksum mismatch, revision drift, or another ambiguous recovery condition
moves both journal rows to `QUARANTINED`, records the bounded reason in
`recovery_quarantine`, and releases the matching lease. Releasing the lease does
not make the resource safe for another destructive operation. A pending,
applying, or quarantined patch keeps the player and scope locked.

There is no automatic quarantine-clear path. For case-linked item confiscation
and restoration only, a Founder with `enthusiastaff.owner.recovery` may run:

```text
/case recoveritems <case-id>
```

This command does **not** edit the player's inventory, mark the patch applied,
or bypass checksum/revision validation. It performs one bounded MariaDB
transaction that:

1. locks the unresolved case-linked item quarantine and its paired patch and
   operation;
2. rejects multiple matching quarantines, a divergent pair, a profile/fence
   mismatch, a non-item operation, or a live competing resource lease;
3. moves the exact coherent pair from `QUARANTINED` back to `PENDING`;
4. records `resolved_at`, the Founder actor, and bounded resolution metadata on
   the quarantine row; and
5. appends an idempotent `INVENTORY_QUARANTINE_REQUEUED` audit event containing
   identifiers and fencing evidence but no inventory contents.

Normal recovery must then acquire a newer fence and prove either the expected
before image or the exact already-replaced image before committing. If the
retry still cannot prove a safe state, it quarantines again. Re-quarantine
clears the prior resolution fields so the new ambiguity is visibly unresolved;
the previous Founder authorization remains preserved in append-only audit.
Repeated recovery authorization for the same retry fence returns the recorded
result and does not write another audit event.

Do not delete journal rows, mark a patch applied, edit checksums/revisions, or
change fencing tokens manually to restore service. General inventory-edit
quarantines are intentionally outside `/case recoveritems` and remain fail
closed for a separate recovery procedure.

## Case-linked confiscation

Confiscation adds an additional set of bindings:

- case, target, actor, inventory profile, scope, operation, and patch;
- selected nested item paths and fingerprints;
- the durable confiscated-asset snapshots created before item removal; and
- a single restoration operation reserved against eligible snapshots.

Restoration can complete only when its replacement patch is verified and the
source confiscation is durably committed. Duplicate requests replay the same
reservation or terminal result. A snapshot from another case, player, profile,
scope, operation, or checksum is not interchangeable evidence.

The selection lifecycle is fenced independently:

1. Start accepts an idempotent replay only when the operation, player, scope,
   backend, actor, case, and before checksum remain identical.
2. The start transaction records the authoritative observation, acquires the
   lease, and persists the operation, before snapshot, and audit together.
3. Renewal requires the same operation, fencing token, `LOCKED` state, and
   unexpired lease.
4. Preparation rechecks the session fence, revision, before checksum, lease,
   authoritative profile, and current observation before persisting the exact
   removal patch and confiscated-asset snapshot.
5. Cancellation is idempotent only for the matching fence and cannot roll back
   an operation that already advanced beyond `LOCKED`.

The operation update, pending patch insert, and asset-snapshot insert must each
affect exactly one row. The audit insert may affect zero only when its unique
idempotency key already exists. Any other row count or write failure rolls back
the whole preparation transaction; it must never leave a validated operation
without its patch or durable confiscated assets.

## Operator response

For an incomplete or quarantined inventory operation:

1. Stop repeated edits, confiscations, and restorations for the affected player.
2. Record the case, player, operation, patch, profile, scope, and owning backend
   identifiers.
3. Inspect both journal states and fencing tokens, the current lease, expected
   and replacement checksums, durable profile revision, before snapshot, audit
   event, and quarantine record.
4. For a single coherent case-linked confiscation/restoration quarantine, a
   Founder may authorize `/case recoveritems <case-id>`; the target may be
   offline because authorization only requeues durable state.
5. Let normal checksum-verified recovery decide whether the replacement can be
   applied/finalized or must quarantine again.
6. Preserve all evidence and escalate any ambiguity. If the command reports
   multiple candidates, a live lease, or persistence failure, do not manipulate
   the rows manually.

Never delete an unknown lease, overwrite a newer profile revision, copy
confiscated contents from an unbound snapshot, or resolve a quarantine solely
because the visible inventory appears plausible.

See [Database design](database.md),
[ADR 0006](decisions/0006-fenced-inventory-and-economy-operations.md), and
[Development and validation](development.md).
