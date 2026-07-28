# ADR 0006: Fenced journals for inventory and economy mutations

**Status:** Accepted

## Decision

Inventory and economy mutations use per-player database leases with fencing tokens, optimistic revisions, before snapshots, explicit stage transitions, verification, and quarantine. Offline inventory editing runs only on the owning available server after network-wide offline proof; otherwise a durable patch applies before interaction at login.

## Consequences

Some edits are delayed instead of guessed. Stale workers cannot commit after lease loss. Recovery can distinguish safe replay, compensation, and ambiguous states without overwriting newer player data.
