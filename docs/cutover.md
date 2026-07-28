# LiteBans cutover

Cutover changes network moderation authority and must be performed in a scheduled maintenance window. It does not remove old jars or delete LiteBans data.

## Preconditions

- Verified backups and a tested restore procedure exist.
- The uninterrupted shadow window passes.
- Every configured Paper backend has an authenticated persistent Velocity connection.
- Network identity, database, and website secrets are present where enabled.
- Recovery quarantine, outbox dead letters, incomplete inventory work, incomplete economy work, and pending restoration reservations are zero.
- Staff understand the rollback procedure and old jars remain available.

## Procedure

1. Stop player traffic and revoke legacy punishment-write permissions for the window.
2. Run `/estaff cutover status` and save the output with the change record.
3. Enter the write fence with `/estaff cutover maintenance`.
4. Verify both EnthusiaStaff and legacy punishment commands reject new writes.
5. Run `/estaff migration final`. This is a `CUTOVER` run: final incremental reconciliation and count/checksum/active/UUID/expiration/login/mute/IP comparisons share one repeatable-read source snapshot.
6. Run `/estaff cutover status` again. Do not continue while any non-window blocker remains.
7. Activate with `/estaff cutover activate CONFIRM-ACTIVE-CUTOVER`.
8. Verify the durable cutover ID, linked final migration run, `ACTIVE` mode, authenticated backends, login denial, mute enforcement, and one normal login.
9. Observe logs, dead letters, database latency, and recovery queues before returning traffic.

The final transaction locks operational state, reassembles evidence inside that transaction, records the complete assessment and blocker list, links the final migration run, appends audit, and transitions to `ACTIVE`. Success is reported only after commit.

## Founder early-window override

Use only when the comparison and recovery evidence is otherwise clean:

```text
/estaff cutover override I_UNDERSTAND_CUTOVER_BLOCKERS <written incident reason>
```

The exact acknowledgement and reason are required and audited. The override can waive only the 168-hour/daily-coverage requirement. It cannot make a mismatch, unresolved operation, active migration, absent maintenance fence, or absent final run acceptable.

## Abort before activation

While still in `MAINTENANCE`, a Founder can return safely to a new shadow window:

```text
/estaff cutover abort CONFIRM-ABORT-MAINTENANCE <reason>
```

The abort is transactional and audited. It resets shadow continuity.

Do not remove `LiteBans.jar`, Staff++, Punishments, or TigerReports support jars during this procedure. Removal occurs only after the post-cutover acceptance period described in the upgrade manifest.
