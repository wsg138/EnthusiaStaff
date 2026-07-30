# Inventory and Confiscation Safety

Inventory edits and case-linked confiscations are destructive workflows. Treat
their MariaDB journals, snapshots, leases, fencing tokens, and quarantine
records as one integrity boundary. Do not bypass a stage or manually mark an
operation complete because the player inventory appears correct.

> **Pre-release status:** The journal and MariaDB recovery paths have automated
> coverage, but live multi-backend Paper staging is still required before these
> workflows are production-ready.

## Safety model

An inventory mutation follows this order:

1. Capture the authoritative inventory revision, checksum, and before snapshot.
2. In one database transaction, acquire the per-player, per-scope lease and
   persist the operation, before snapshot, prepared patch, and audit event.
3. Claim the prepared patch for application.
4. Re-read the live inventory and reject a stale or conflicting state.
5. Apply the exact replacement on the owning Paper entity thread.
6. Capture and verify the resulting checksum.
7. Commit the new durable revision, audit event, and terminal operation state.
8. Release the matching lease.

A stale worker cannot finalize work under an older fencing token. A replacement
that conflicts with the authoritative revision or observed inventory is
quarantined instead of being guessed or silently retried.

## Durable preparation invariants

Preparation validates that both supplied checksums match their snapshots before
opening the transaction. Inside the transaction it:

1. replays an existing operation only when its player, scope, backend, actor,
   case, type, revision, checksums, and changed slots still match;
2. verifies restoration reservations and required network-wide offline state;
3. locks and verifies the authoritative profile and current observation;
4. rejects a stale revision or an existing blocking patch;
5. acquires the lease and fencing token;
6. inserts the operation, before snapshot, pending patch, and audit event.

Each insert must affect exactly one row. A serialization, constraint, row-count,
or audit failure rolls back the entire transaction, including the newly
acquired lease. A reused idempotency key with different mutation evidence is a
conflict, not a successful replay.

The patch row and its operation row advance as a checked pair. They retain the
same operation, profile, and fencing token. `PENDING`, `APPLYING`, and
`QUARANTINED` match directly; an `APPLIED` patch pairs with a `COMMITTED`
operation. A direct finalization from `PENDING`, a partial row update, or any
state or fence divergence rolls back instead of repairing one row from the
other.

## Online and queued edits

For an online edit, the target inventory is authoritative and Bukkit mutations
run on the target's entity scheduler. The durable patch is prepared before the
live inventory changes.

If the target leaves after preparation, the patch remains durable. Login
recovery claims the patch and compares the loaded inventory with both the
expected before checksum and prepared replacement checksum before allowing
interaction. An already-applied replacement can be finalized idempotently;
unrelated inventory contents enter quarantine.

Offline editing additionally requires network-wide offline proof, the
configured owning backend, the expected scope, and an unchanged durable
revision. If those proofs are incomplete, direct replacement is not safe.

## Case-linked confiscation

Confiscation is separate from ordinary inventory editing:

- the case, target, actor, profile, scope, operation, patch, and asset snapshots
  remain bound together;
- selected nested item paths and fingerprints must still match at commit time;
- the durable confiscated-asset snapshot exists before items are removed;
- restoration reserves eligible snapshots for one restoration operation;
- restoration completes only after its replacement patch is verified and the
  source confiscation is durably committed;
- duplicate restoration requests replay the same result instead of granting
  the items again.

Do not edit confiscated snapshot rows or reservation markers by hand. A
partially repaired binding can create an item-loss or duplication path.

The selection phase has its own fenced lifecycle. An idempotent start must
retain the same operation, player, scope, backend, actor, case, and before
checksum. Renewal requires the matching fence, `LOCKED` operation state, and
live lease. Preparation rechecks the session and authoritative inventory
before it updates the operation and inserts the pending patch and confiscated
asset snapshot. Each non-replay write must affect exactly one row; an audit may
affect zero only for its existing idempotency key. Any other row count rolls
back the transaction. Cancellation is idempotent for the matching fence but
cannot reverse a selection after it advances beyond `LOCKED`.

## Runtime workflow boundaries

The Paper implementation keeps user-interface decisions separate from durable
recovery stages:

1. Viewer, permission, target, slot, page, and nested-selection checks run
   before a mutation is planned.
2. Durable start and lease renewal establish continued ownership of both
   inventory and Currency movement locks.
3. Restoration or confiscation planning verifies the captured revision,
   selected fingerprints, and snapshot checksums before preparing a patch.
4. Patch preparation and claim establish the current fencing token before
   Paper state can change.
5. Apply, rollback, quarantine, and finalization are distinct stages so an
   exception cannot be mistaken for a committed operation.
6. Disconnect handling either cancels before durability or leaves a durable
   patch for bounded login recovery.

These boundaries describe one state machine, not independent shortcuts.
Operators must still use the durable operation and patch rows together when
diagnosing an incident.

## Recovery states

Operators should interpret the patch states as follows:

| Patch state | Operation state | Meaning |
| --- | --- | --- |
| `PENDING` | `PENDING` | Durable replacement exists but has not been claimed for application. |
| `APPLYING` | `APPLYING` | A worker owns the current fence and may be changing or verifying the live inventory. |
| `APPLIED` | `COMMITTED` | The replacement and durable profile revision were verified and committed. |
| `QUARANTINED` | `QUARANTINED` | Evidence was stale, conflicting, incomplete, or otherwise unsafe to finalize automatically. |

An expired lease permits a recovery worker to reclaim the operation with a new
fencing token. It does not prove that an external mutation did or did not
happen.

Quarantine releases the matching lease but the durable patch continues to
block destructive work for that player and scope. Current pre-release builds
have no automatic quarantine-clear path. Keep the operation blocked and
escalate it for privileged recovery; do not delete rows or change states and
tokens manually.

## Incident response

When an inventory operation is incomplete:

1. Stop repeated edits or restoration attempts for the affected player.
2. Record the case, operation, patch, profile, scope, and owning backend IDs.
3. Inspect both operation and patch states, their fencing tokens, the current
   lease, before snapshot, expected checksum, replacement checksum, durable
   profile revision, audit event, and quarantine record.
4. Compare the live inventory only through an approved recovery workflow.
5. Replay a documented idempotent stage when the evidence proves it is safe.
6. Quarantine ambiguity and preserve all evidence for privileged review.

Never delete the journal, release an unknown lease, overwrite a newer
inventory revision, or restore confiscated contents from an unbound snapshot.

See the source-controlled
[inventory safety guide](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/inventory-safety.md),
[inventory and economy fencing decision](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/decisions/0006-fenced-inventory-and-economy-operations.md),
[database model](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/database.md),
and [[Recovery and Troubleshooting]].
