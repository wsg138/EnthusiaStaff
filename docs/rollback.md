# Rollback and emergency freeze

Rollback is a controlled authority change, not a database deletion. Imported cases, mappings, reconciliation events, historical Developer cases, and cutover audit remain intact.

## Before `ACTIVE`

If final import or verification fails, stay in `MAINTENANCE`. Resolve the failure or run:

```text
/estaff cutover abort CONFIRM-ABORT-MAINTENANCE <reason>
```

This returns to `SHADOW_MIGRATION`, keeps LiteBans authoritative, and resets the uninterrupted shadow window. No target records are deleted.

## After `ACTIVE`

If authoritative behavior is unsafe, a Founder must stop destructive writes immediately:

```text
/estaff cutover freeze CONFIRM-READ-ONLY-FAILURE <incident reason>
```

This transaction moves `ACTIVE` to `READ_ONLY_FAILURE`, appends an audit event, disables destructive application services, and causes configured active-authority login checks to fail closed. It does not automatically return authority to LiteBans because punishments created after cutover may not exist in the legacy database.

Then:

1. Keep player traffic stopped.
2. Preserve database, proxy, backend, and outbox evidence.
3. Identify every case and sanction created or changed after the cutover timestamp.
4. Restore or reinstall the prior jars without enabling player traffic.
5. Reconcile post-cutover sanctions into the selected authority with a reviewed, idempotent procedure.
6. Verify ban, mute, IP-ban, expiration, UUID, count, and checksum parity.
7. Resolve or quarantine incomplete inventory/economy work and dead letters.
8. Enable exactly one authority and run staged login/chat tests before reopening traffic.

There is intentionally no one-command `READ_ONLY_FAILURE` to LiteBans transition. Automating that transition without proving post-cutover sanction parity could unban players or lose moderation changes.

## Jar rollback order

1. Freeze EnthusiaStaff authority and traffic.
2. Restore compatible integration jars/APIs.
3. Restore LiteBans on Velocity and required Paper servers.
4. Restore Staff++/Punishments/report compatibility only where still required.
5. Reconcile data and validate one authority.
6. Remove or disable the failed new jars only after evidence and configuration are preserved.

Never restore a database over newer production data without a reviewed reconciliation plan and a verified backup of the current state.
