# LiteBans cutover

Cutover changes network moderation authority and must be performed in a scheduled maintenance window. It does not remove old jars or delete LiteBans data.

The complete production-like rehearsal and evidence gate is in [cutover-acceptance.md](cutover-acceptance.md). A green CI run does not replace that acceptance record.

## Preconditions

- Verified backups and a tested restore procedure exist.
- The uninterrupted shadow window passes.
- Every configured Paper backend has an authenticated persistent Velocity connection.
- Network identity, database, and website secrets are present where enabled.
- The MariaDB pool has at least two connections. One connection may hold the operational-state fence while the guarded authoritative operation uses another.
- Recovery quarantine, outbox dead letters, incomplete inventory work, incomplete economy work, and pending restoration reservations are zero.
- Staff understand the rollback procedure and old jars remain available.

## Authoritative write fence

`MAINTENANCE`, `SHADOW_MIGRATION`, `DEGRADED`, and `READ_ONLY_FAILURE` are enforced at the persistence boundary, not only by command checks. The fence locks the singleton `operational_state` row and holds that lock until the delegated authoritative transaction finishes. A transition into maintenance therefore waits for any already-running punishment write, and no later guarded write can begin under the old mode.

The guarded paths are:

- direct punishment creation;
- punishment-request submission, lease acquisition, approval, denial, and expiry;
- sanction revocation and duration changes;
- automatic network-identity sanction inheritance and manual alt-relationship mutations.

Network-identity observations are still recorded while writes are fenced, but automatic inherited sanctions are suppressed. The LiteBans importer uses its migration-specific transaction path and is not routed through the normal authoritative writer fence.

Within one server process, guarded writers queue through a fair serializer. The database row lock coordinates the fence across Paper and Velocity processes. `BOOTSTRAP` allows direct persistence initialization, while runtime command and service guards still prevent external punishment authority before `ACTIVE`.

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

The final transaction locks operational state, reassembles and deeply validates every admitted shadow summary, records the complete assessment and blocker list, links the final migration run, appends audit, and transitions to `ACTIVE`. Exact affected-row counts are required. A failed state, cutover-record, or audit write rolls back the complete activation. Success is reported only after commit.

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

## Verification coverage and remaining acceptance work

MariaDB integration coverage verifies maintenance fencing, active pass-through, transition ordering against an in-flight write, operation with the minimum two-connection pool, network-observation preservation with inheritance suppression, corrupt evidence rejection, final-run selection, activation idempotency, and full rollback when audit persistence fails.

Restart-recovery coverage also verifies that an abandoned migration run fails closed before a replacement begins, a committed activation can be retried after restart without a duplicate cutover record or audit, and emergency freeze remains durable and non-duplicating across another restart.

This code does not authorize or perform a production cutover. Before release, the project still requires a real uninterrupted 168-hour shadow window, a final incremental import rehearsal from a production-like backup, the complete acceptance record in [cutover-acceptance.md](cutover-acceptance.md), rollback rehearsal, and post-cutover reconciliation evidence.

Do not remove `LiteBans.jar`, Staff++, Punishments, or TigerReports support jars during this procedure. Removal occurs only after the post-cutover acceptance period described in the upgrade manifest.
