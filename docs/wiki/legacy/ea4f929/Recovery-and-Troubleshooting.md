# Recovery and Troubleshooting

Recovery must preserve evidence and prove external state. Do not delete journal
rows, force state transitions, release unknown leases, or retry destructive
operations manually without understanding the durable operation record.

## First response

1. Stop the affected destructive capability or player traffic.
2. Record runtime mode, health output, correlation IDs, and affected case or
   operation IDs.
3. Preserve MariaDB, proxy, backend, outbox, and provider evidence.
4. Identify the authoritative owner of the affected state.
5. Determine whether the operation is safely replayable, already committed,
   compensated, or ambiguous.
6. Resume only a documented idempotent stage; quarantine ambiguity.

Never copy secrets, raw network addresses, private evidence, or database
credentials into public issues or Wiki pages.

## Common failures

### MariaDB unavailable

New punishments and destructive edits must stop. Do not authorize from a stale
cache. Restore database connectivity, verify Flyway state, and allow durable
workers to reclaim expired leases before enabling writes.

### Schema checksum or future version

Remain in `READ_ONLY_FAILURE` or `BOOTSTRAP`. Do not edit Flyway history
automatically. Compare the deployed artifact, migration files, and database
history, then use a reviewed repair or rollback procedure.

### Paper–Velocity channel failure

There is no cleartext fallback. Check key/trust-store paths, password
environments, certificate validity, SAN/host match, per-backend HMAC secrets,
allowlist identity, and listener reachability. Durable outbox messages must
remain pending until authenticated delivery returns.

### Inventory or economy operation incomplete

Inspect the operation state, lease owner and fencing token, before snapshot,
prepared replacement or removal plan, provider result, verification checksum,
and quarantine reason. Do not unlock or mark terminal unless exact state is
proved. Ambiguous external effects require privileged recovery.

### Outbox delivery stalled

Check due time, attempt count, current lease, last sanitized error, circuit
state, destination configuration, and dead-letter state. Acknowledgement means
the consumer durably recorded the result, not merely that a network request
succeeded.

### Migration mismatch

Keep LiteBans authoritative. Compare exact source IDs, checksums, timestamps,
UUID mapping, active state, and protected identity evidence. Correct the source
or implementation; do not edit durable mappings to force parity.

## Emergency freeze after cutover

If active authority is unsafe:

```text
/estaff cutover freeze CONFIRM-READ-ONLY-FAILURE <incident reason>
```

Then keep traffic stopped, identify every post-cutover sanction, reconcile it
into the selected authority, resolve incomplete asset operations, prove parity,
and enable exactly one authority before reopening.

There is intentionally no automatic transition from `READ_ONLY_FAILURE` back
to LiteBans. Automatic failback could lose post-cutover sanctions.

## Evidence for support

Provide sanitized:

- exact application commit and artifact hash;
- Java, Paper, Velocity, and MariaDB versions;
- operational mode and health categories;
- case, operation, migration-run, or message IDs;
- timestamps and bounded sanitized errors;
- reproduction steps and whether the issue survived restart.

Do not provide secrets, raw IP addresses, private messages, staff notes,
confiscated contents, or appeal evidence unless using an approved private
incident channel.

See the
[rollback runbook](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/rollback.md)
and [database model](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/database.md).
