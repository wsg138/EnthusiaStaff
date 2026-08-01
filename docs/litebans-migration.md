# LiteBans migration

LiteBans remains the source of truth until a validated cutover commits `ACTIVE`. EnthusiaStaff reads LiteBans through a dedicated read-only MariaDB account and never writes to LiteBans.

## Imported data

The inspected source must contain the configured-prefix `bans`, `mutes`, and `history` tables. Column names are discovered from a bounded allowlist; table prefixes and identifiers are validated before SQL is built.

Inspection handles sanction, history, and audit-only tables independently. It
reports missing columns in canonical order so repeated preflight runs produce a
stable blocker list. Staff-column resolution prefers the alias for the current
sanction type while retaining the shared legacy fallback used by supported
LiteBans schemas. Inspection never invents a mapping for a column that is not
present.

The importer preserves:

- ban, IP-ban, and mute source IDs;
- active, naturally expired, and ended-early state;
- issue, expiration, and available removal timestamps;
- original public reason and staff display name;
- UUID and valid username provenance;
- source checksums and per-table high-water marks;
- IP history and IP-ban addresses as protected identity tokens.

Raw network addresses exist only transiently in migration memory. They are converted with the configured versioned HMAC and AES keys before target storage and are never written to reports or logs. A write-mode migration fails if protected identity keys are unavailable. LiteBans does not provide a trustworthy historical staff rank, so imported cases use the non-interactive `SYSTEM` rank while retaining the original staff name. Existing native cases—including historical Developer-issued cases—are not rewritten or invalidated.

An IP ban without a UUID or name is resolved only when its protected address matches exactly one player in imported LiteBans history. Zero matches, multiple matches, wildcard addresses, and malformed addresses are rejected with a durable reason code and block cutover; the importer never guesses which player owns an ambiguous address.

## Commands

Run commands from Velocity with `enthusiastaff.migration`:

```text
/estaff migration inspect
/estaff migration dry-run
/estaff migration import
/estaff migration shadow
/estaff migration final
```

`inspect` and `dry-run` create a durable migration run report but do not create cases or protected identities. `import` is permitted only in `SHADOW_MIGRATION` or `MAINTENANCE`. `shadow` is permitted only in `SHADOW_MIGRATION`. `final` performs the final incremental import and all comparisons in one stable source snapshot and is permitted only in `MAINTENANCE`.

Each write pass takes a database-wide advisory lock, recovers abandoned `RUNNING` records as failed, and reads LiteBans under a repeatable-read, read-only snapshot. A second proxy cannot migrate or cut over concurrently.

## Idempotency and reconciliation

`migration_mappings` uniquely identifies `(source_system, source_table, external_id)`. A rerun:

- imports a missing record once;
- records an unchanged record as replayed;
- transactionally reconciles active state, expiration, and removal state when LiteBans changes;
- appends a `LEGACY_SYNC` sanction event and immutable audit record for a reconciliation;
- rejects changes to immutable identity, type, issue time, original actor, or original reason for manual review.

A crash can leave already committed mappings or protected identity tokens. The next locked run marks the abandoned run failed and safely replays those rows. It never duplicates cases or sanction events for an already committed checksum.

## Shadow comparison accounting

Each shadow pass compares source and target counts, source checksums, active
state, UUID mappings, expiration values, and the resulting login, mute, and
IP-ban decisions. The result of every dimension is persisted in
`shadow_comparisons`; a successful aggregate is not inferred from counts alone.

A target mapping whose source row disappeared is reported as an extra mapping.
It makes the count comparison fail and contributes to the aggregate mismatch
count even when every remaining source row still matches. Rejected source rows
also keep the aggregate non-zero. Resolve these discrepancies at the source or
through the documented recovery process before cutover.

## Preflight

1. Back up the LiteBans and EnthusiaStaff databases and verify restoration on staging.
2. Give the LiteBans account `SELECT` only. Give the EnthusiaStaff account only the documented application privileges.
3. Configure `ES_LITEBANS_DATABASE_URL`, user, password, table prefix, and protected identity keys.
4. Run `inspect`, resolve every schema blocker, then run `dry-run`.
5. Review rejected row reason codes, counts, per-table high-water marks, UUID/name ambiguity, and timestamps.
6. Start or resume shadow mode only after the dry-run report is understood.

Do not edit a durable mapping to force a match. Correct the source data before the final run or retain the blocker with an operator incident record; there is no mismatch override.
