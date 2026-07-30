# Recovery and Troubleshooting

Recovery starts by preserving evidence and stopping conflicting work. It does
not start by retrying the same destructive command.

## First response

1. Stop repeated actions for the affected player, case, asset, or provider.
2. Record exact time, backend, actor, target UUID, and visible error.
3. Record case, sanction, report, draft, operation, patch, migration, message,
   and session IDs that apply.
4. Run `/estaff status`.
5. Run the narrowest relevant verify command, then `/estaff verify full` if
   needed.
6. Preserve logs and sanitized stack traces.
7. Identify whether authoritative state committed.
8. Replay only a documented idempotent stage.
9. Quarantine ambiguity.

## Operational modes

| Mode | Meaning |
| --- | --- |
| `BOOTSTRAP` | Configuration, schema, and integrations are being established |
| `DEGRADED` | Safe reads remain; affected unsafe actions are disabled |
| `SHADOW_MIGRATION` | LiteBans authoritative; EnthusiaStaff compares only |
| `ACTIVE` | EnthusiaStaff authoritative |
| `MAINTENANCE` | Sensitive writes/reconnect evidence suppressed for planned work |
| `READ_ONLY_FAILURE` | Destructive work blocked pending recovery |

Do not change mode merely to bypass an error.

## MariaDB unavailable

Expected behavior: no new punishment or destructive edit. Safe status, history,
and recovery inspection may remain.

Check:

- Connection and TLS
- Credential scope
- Pool exhaustion
- Schema version
- Migration checksum
- Database time
- Long-running transactions
- Lease/recovery worker backlog

Do not point the plugin at a different database as an emergency shortcut.

## Schema checksum or future version

Stop startup or unsafe work. Verify that the migration files match the exact
artifact. Do not repair the Flyway/Liquibase history table manually without an
approved database recovery plan.

## Paper–Velocity channel failure

New network sanctions must not be accepted when network enforcement cannot be
proven. Check certificate chain, hostname, key/trust stores, server allowlist,
protocol version, clock skew, replay rejection, queue bounds, and reconnect
backoff.

An online player must not be required as message transport.

## Punishment command timed out

Do not immediately re-run. Look up the idempotency/case/draft result. A client
timeout may occur after durable commit. Repeating with changed evidence can
create a conflict; repeating with the same key should replay the original
result.

## Inventory or economy operation incomplete

Stop all edits and transfers for the target/scope. Inspect the operation and
patch as one state machine, including fences, lease, snapshots, checksums,
provider result, and audit. See [[Inventory and Confiscation Safety]].

## Staff mode restoration failure

Keep the staff member out of normal gameplay. Preserve the original session
snapshot. Do not overwrite it by toggling staff mode repeatedly. Escalate to
owner recovery.

## Outbox delivery stalled

Check:

- Due time
- Attempt count
- Lease owner and fence
- Sanitized last error
- Circuit-breaker state
- Destination configuration
- Dead-letter state
- Consumer acknowledgement

A successful HTTP request is not necessarily a durable consumer acknowledgement.

## Migration mismatch

Keep LiteBans authoritative. Compare exact source IDs, checksums, timestamps,
UUID mapping, active state, expiration, and protected network identity. Correct
the source interpretation or implementation; do not edit mappings to force
parity.

## Emergency freeze after cutover

If EnthusiaStaff authority is unsafe:

1. Enter `READ_ONLY_FAILURE` using the approved Founder command.
2. Stop traffic and destructive actions.
3. Identify every post-cutover sanction and asset operation.
4. Reconcile into one selected authority.
5. Resolve incomplete operations.
6. Prove parity and command ownership.
7. Re-enable exactly one authority.

There is intentionally no automatic failback to LiteBans.

## Support evidence

Provide sanitized:

- Exact source commit and artifact hashes
- Java, Paper, Velocity, and MariaDB versions
- Operational mode and health categories
- Stable case/operation/message IDs
- Timestamps
- Bounded sanitized errors
- Reproduction steps
- Whether restart changed the behavior

Do not provide secrets, raw addresses, private messages, coordinates, staff
notes, confiscated contents, or appeal media in ordinary support channels.
