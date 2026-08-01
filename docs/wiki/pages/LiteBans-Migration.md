# LiteBans Migration

LiteBans migration is a controlled data and authority transition. Importing
rows is not the same as completing cutover.

> LiteBans remains authoritative until preflight, import, exact 168-hour shadow
> comparison, final reconciliation, and cutover all pass.

## Imported records

Target import includes:

- Bans
- IP/network bans
- Mutes
- Active and expired state
- Reasons
- Staff names
- Issue and expiration times
- UUID and name identity
- External LiteBans IDs

Kicks, warnings, and notes are not ordinary public imported punishment history,
but skipped counts may be audited.

## Safety properties

Migration must provide:

- Source schema inspection
- Dry run
- Mapping and conflict report
- Count and checksum report
- Active and expiration report
- Duplicate detection
- Durable run state
- Resume
- Idempotent rerun
- Reconciliation
- Rollback plan
- Final cutover report

Raw addresses must not be imported into staff-visible records. Use protected
network tokens and reject ambiguous ownership.

### Source schema inspection

The configured table prefix is validated before metadata lookup. The inspector
requires the prefixed `bans`, `mutes`, and `history` tables, maps only bounded
known column aliases, and treats `kicks` and `warnings` as audit-only inputs.
Missing columns are reported in a stable canonical order so operators can
compare repeated preflight results. Staff-column selection prefers the alias for
the current sanction type while retaining the supported shared legacy fallback.
No absent column is inferred or silently substituted.

## Concurrency

Only one migration run may own the migration lease. Source reads should use a
repeatable snapshot. Abandoned runs must be detected and recovered without
duplicating cases or sanction events.

Do not edit durable mapping rows to “make counts match.”

## Preflight checklist

- Exact source schema/version identified
- Source is readable through restricted migration credentials
- Target schema is current
- Current and previous names resolve
- UUID mappings are unambiguous
- Active states and expiration units are understood
- Network identity key versions are available
- Duplicate external IDs are handled
- Dry run creates no authoritative enforcement
- Every mismatch category has an owner and resolution

## Public visibility

Imported cases become public only under the configured public mode. Fully
overturned cases are hidden, revoked cases show `Revoked`, expired cases show
`Expired`, and private cases remain private.

## Commands and implementation

Migration control is primarily a Velocity/operator workflow. Exact command
syntax must be taken from the deployed build and verified help output; do not
copy an old command list into a production runbook without checking command
ownership and current implementation.

Continue with [[Shadow Mode and Cutover]].
