# Inventory and Confiscation Safety

Inventory edits, Ender chest edits, item confiscation, economy confiscation, and
restoration are destructive workflows. They must use durable snapshots,
revisions, leases, fencing tokens, exact verification, audit, and recovery.

> **Current status:** Domain and MariaDB recovery paths have significant
> automated coverage, but multi-backend Paper staging, concurrent viewer
> behavior, provider integration, and privileged quarantine recovery remain
> incomplete. Do not treat these tools as production-ready solely because the
> commands open.

## Choose the correct workflow

| Need | Correct tool |
| --- | --- |
| View inventory | `/invsee <player|uuid>` |
| View Ender chest | `/endersee <player|uuid>` |
| Ordinary authorized edit | Inventory/Ender editor with edit permission |
| Remove case-linked evidence or illicit assets | Confiscation workflow |
| Remove currency | Economy confiscation through Currency API |
| Return confiscated items | `/case restoreitems <case-id>` |
| Recover ambiguous operation | Owner recovery/quarantine workflow |

Do not delete evidence through ordinary inventory editing and call it
confiscation. Confiscation requires a case-bound before snapshot and dupe-safe
restoration record.

## Before viewing or editing

Confirm:

- Target UUID and current name
- Online/offline state network-wide
- Owning backend and inventory scope
- Whether the profile is HUB or SMP
- Whether another staff viewer is active
- Whether a pending patch, lock, lease, confiscation, or recovery record exists
- Whether you have view or edit authority
- Whether the action is tied to a legitimate staff purpose

Viewing does not imply editing permission.

## Safe mutation sequence

A safe inventory mutation follows this order:

1. Capture authoritative revision, checksum, and before snapshot.
2. Acquire the per-player and per-scope lease inside a database transaction.
3. Persist operation, snapshot, prepared patch, audit, and fencing token.
4. Re-read the live inventory.
5. Reject stale or conflicting state.
6. Apply the exact replacement on the owning Paper thread.
7. Capture and verify the resulting checksum.
8. Commit the durable profile revision and terminal operation state.
9. Release the matching lease.

A stale worker cannot finalize under an old fence. An expired lease permits
recovery to take ownership; it does not prove whether the live mutation
occurred.

## Online editing

The online player's inventory is authoritative. Multiple viewers must remain
coordinated and receive live updates. Closing a viewer stops observation; it
must not write a stale full clone back over newer changes.

If the target disconnects after durable preparation, leave the patch for login
recovery. Do not open a second edit and do not “fix” the inventory from a
screenshot.

## Offline editing

Direct offline editing is safe only when all of these are proven:

- Player is offline across the network
- Correct owning backend and scope are known
- Exclusive lease is acquired
- Player data is not being saved
- Durable revision is current
- Before snapshot is saved
- Atomic replacement and reread verification are available

Otherwise use a queued patch that applies before player interaction on login.

## Nested containers

Shulkers and bundles require exact nested paths and item fingerprints. If the
container changed after selection, the selection is stale and must be rebuilt.

Normal click and shift-click behavior in confiscation must follow the configured
selection rules. Never infer that visually similar stacks are the same asset.

## Item confiscation

Confiscation binds together:

- Case
- Target
- Actor
- Backend and scope
- Operation and patch
- Selected nested paths and fingerprints
- Before snapshot
- Confiscated asset snapshot
- Audit and restoration eligibility

Delete items only after the durable confiscated snapshot commits.

Expected lifecycle:

```text
PREPARED -> LOCKED -> SNAPSHOT_SAVED -> VALIDATED -> COMMITTED -> UNLOCKED
```

Startup recovery must finish, roll back, or quarantine incomplete work.

## Economy confiscation

Economy removal must use the EnthusiaCurrency moderation API, not raw database
writes. The plan may include bank, physical currency, inventory, Ender chest,
shulkers, and bundles according to configuration.

Safe flow:

1. Calculate exact total.
2. Reject over-removal.
3. Build exact removal plan.
4. Acquire all movement and provider locks.
5. Save complete before snapshot.
6. Apply through provider API.
7. Verify exact final total.
8. Commit audit.
9. Roll back or quarantine uncertainty.

Do not “balance” an uncertain result with a second manual withdrawal.

## Restoration

```text
/case restoreitems <case-id>
```

Restoration must be idempotent and bound to the original case, operation,
snapshot, player, scope, and expected inventory revision. A repeated request
must replay the same result, not grant another copy.

Founder authority is required by the current permission aggregate.

## Recovery states

| Patch state | Operation state | Meaning |
| --- | --- | --- |
| `PENDING` | `PENDING` | Durable replacement exists but is not claimed. |
| `APPLYING` | `APPLYING` | Current fenced worker may be applying or verifying. |
| `APPLIED` | `COMMITTED` | Replacement and durable revision verified. |
| `QUARANTINED` | `QUARANTINED` | Evidence is stale, conflicting, incomplete, or unsafe. |

Quarantine blocks more destructive work for the affected player and scope.
Never clear it by deleting rows, changing state fields, or releasing an unknown
lease manually.

## Incident checklist

When work is incomplete:

1. Stop all repeated edits, confiscations, withdrawals, and restorations.
2. Record case, operation, patch, profile, scope, backend, and actor.
3. Record both state values and fencing tokens.
4. Preserve before/replacement checksums and snapshots.
5. Check current lease owner and expiry.
6. Compare live state only through approved recovery.
7. Replay only an idempotent documented stage.
8. Quarantine ambiguity.

See [[Recovery and Troubleshooting]].
