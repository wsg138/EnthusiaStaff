# Inventory and confiscation safety

Inventory edits, confiscations, and restorations can lose or duplicate player
items if an external mutation and its durable journal disagree. EnthusiaStaff
therefore treats the inventory profile, operation, patch, snapshot, lease, and
quarantine record as one recovery boundary.

The feature remains pre-release. Automated MariaDB coverage exercises normal,
replay, fencing, divergence, quarantine, and restoration paths, but live
multi-backend Paper failure staging is still required before production use.

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

## Quarantine

Checksum mismatch, revision drift, or another ambiguous recovery condition
moves both journal rows to `QUARANTINED`, records the bounded reason in
`recovery_quarantine`, and releases the matching lease. Releasing the lease does
not make the resource safe for another destructive operation. A pending,
applying, or quarantined patch keeps the player and scope locked.

Current pre-release builds intentionally have no automatic quarantine-clear
path. Until a privileged resolution workflow is implemented and tested, a
quarantined resource remains blocked for developer or administrator recovery.
Do not delete journal rows, mark a patch applied, or change fencing tokens
manually to restore service.

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

## Operator response

For an incomplete or quarantined inventory operation:

1. Stop repeated edits, confiscations, and restorations for the affected player.
2. Record the case, player, operation, patch, profile, scope, and owning backend
   identifiers.
3. Inspect both journal states and fencing tokens, the current lease, expected
   and replacement checksums, durable profile revision, before snapshot, audit
   event, and quarantine record.
4. Compare the live inventory only through an approved recovery workflow.
5. Replay only an idempotent stage whose stored evidence proves the outcome.
6. Preserve all evidence and escalate any ambiguity.

Never delete an unknown lease, overwrite a newer profile revision, copy
confiscated contents from an unbound snapshot, or resolve a quarantine solely
because the visible inventory appears plausible.

See [Database design](database.md),
[ADR 0006](decisions/0006-fenced-inventory-and-economy-operations.md), and
[Development and validation](development.md).
