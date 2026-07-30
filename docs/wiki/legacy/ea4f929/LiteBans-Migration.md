# LiteBans Migration

LiteBans remains authoritative until EnthusiaStaff completes a validated final
import and cutover transaction. EnthusiaStaff connects to LiteBans with a
dedicated read-only MariaDB account and never writes to its tables.

## Imported state

The importer supports the configured-prefix `bans`, `mutes`, and `history`
tables. It preserves source IDs, active/expired/ended state, issue and
expiration time, public reason, staff display name, UUID/name provenance,
checksums, and per-table high-water marks.

Raw network addresses exist only transiently in migration memory. They are
converted into versioned HMAC equality tokens and AES-GCM protected values
before target storage. Ambiguous, wildcard, or malformed IP ownership is
rejected; the importer never guesses a player.

## Velocity commands

Migration commands require the Velocity migration permission:

```text
/estaff migration inspect
/estaff migration dry-run
/estaff migration import
/estaff migration shadow
/estaff migration final
```

- `inspect` validates the source schema.
- `dry-run` records a report without creating target cases or protected
  identities.
- `import` writes only in `SHADOW_MIGRATION` or `MAINTENANCE`.
- `shadow` compares source and target only in `SHADOW_MIGRATION`.
- `final` performs the last incremental import and comparisons inside one
  stable source snapshot during `MAINTENANCE`.

## Preflight

1. Back up both databases and prove restoration in staging.
2. Grant the LiteBans account `SELECT` only.
3. Configure source connection, prefix, bounded batch size, and protected
   network identity keys.
4. Run `inspect` and resolve every blocker.
5. Run `dry-run` and review rejected rows, counts, checksums, timestamps,
   high-water marks, and identity ambiguity.
6. Begin shadow mode only after the dry-run is understood.

## Idempotency and recovery

Each mapping is unique by source system, table, and external ID. Reruns import a
missing record once, replay unchanged records, and transactionally reconcile
supported mutable state. Changes to immutable identity, type, issue time,
original actor, or original reason require manual review.

A locked run marks abandoned `RUNNING` records failed and safely replays
already committed mappings. It must not duplicate cases, sanction events, or
protected identity observations.

There is no mismatch override. Correct source data or retain the blocker with a
reviewed incident record.

See [[Shadow Mode and Cutover]] and the
[migration specification](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/litebans-migration.md).
