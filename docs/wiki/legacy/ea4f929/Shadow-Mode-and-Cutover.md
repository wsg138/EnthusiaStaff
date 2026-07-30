# Shadow Mode and Cutover

## Shadow mode

`SHADOW_MIGRATION` is non-authoritative. LiteBans continues enforcing bans,
mutes, and IP bans while EnthusiaStaff imports and compares the outcomes it
would produce.

Ordinary cutover requires:

- at least 168 hours of uninterrupted observation;
- at least seven successful summary runs;
- no gap longer than 26 hours;
- zero failed or mismatching run after the window begins;
- complete comparison rows for every accepted run.

Failure or mismatch resets the uninterrupted window. Time in `MAINTENANCE`
does not count. Frequent short-interval runs cannot substitute for daily
coverage.

Each pass compares source/mapped counts, checksums, active state, UUID mapping,
expiration, login denial, mute decisions, and protected-identity IP-ban
decisions.

## Cutover prerequisites

- Verified backups and a tested restore procedure
- A passing uninterrupted shadow window
- Authenticated persistent-channel connections from every required backend
- Required database, network identity, and enabled-service secrets
- Zero unresolved recovery/quarantine work, dead letters, incomplete
  inventory/economy operations, or pending restoration reservations
- A reviewed rollback procedure with legacy artifacts retained

## Maintenance procedure

1. Stop player traffic and legacy punishment writes.
2. Record `/estaff cutover status`.
3. Enter the write fence with `/estaff cutover maintenance`.
4. Prove both legacy and new punishment commands reject writes.
5. Run `/estaff migration final`.
6. Re-run `/estaff cutover status` and stop on any non-window blocker.
7. Activate with `/estaff cutover activate CONFIRM-ACTIVE-CUTOVER`.
8. Verify the durable cutover ID, linked final run, `ACTIVE` mode, authenticated
   backends, login denial, mute enforcement, and a normal login.
9. Observe recovery queues, dead letters, latency, and runtime health before
   returning traffic.

Activation locks operational state, reassembles evidence inside the final
transaction, appends audit, and reports success only after commit.

## Override and abort

A Founder override can waive only the duration and cadence requirement:

```text
/estaff cutover override I_UNDERSTAND_CUTOVER_BLOCKERS <incident reason>
```

It cannot waive mismatches, unresolved operations, an active migration, absent
maintenance fencing, or a missing final run.

Before activation, abort safely with:

```text
/estaff cutover abort CONFIRM-ABORT-MAINTENANCE <reason>
```

This returns to `SHADOW_MIGRATION`, preserves imported records, and resets the
shadow window.

Do not remove LiteBans or compatibility jars during cutover. Jar removal is a
separate post-acceptance operation.

Source runbooks:

- [Shadow mode](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/shadow-mode.md)
- [Cutover](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/cutover.md)
- [Rollback](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/rollback.md)
